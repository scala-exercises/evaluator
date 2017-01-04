/*
 * scala-exercises-evaluator-server
 * Copyright (C) 2015-2016 47 Degrees, LLC. <http://www.47deg.com>
 */

package org.scalaexercises.evaluator

import java.io.{File, PrintWriter}
import java.lang.reflect.InvocationTargetException
import java.math.BigInteger
import java.security.MessageDigest
import java.util.concurrent.TimeoutException

import coursier._
import monix.execution.Scheduler
import org.scalaexercises.evaluator.Eval.CompilerException
import org.xeustechnologies.jcl.{JarClassLoader, JclObjectFactory}

import scala.concurrent.duration._
import scala.language.reflectiveCalls
import scala.reflect.internal.util.ScalaClassLoader.URLClassLoader
import scala.reflect.internal.util.{AbstractFileClassLoader, Position}
import scala.tools.nsc.io.{AbstractFile, VirtualDirectory}
import scala.tools.nsc.reporters._
import scala.tools.nsc.{Global, Settings}
import scala.util.Try
import scala.util.control.NonFatal
import scalaz.Scalaz._
import scalaz._
import scalaz.concurrent.Task

class Evaluator(timeout: FiniteDuration = 20.seconds)(
  implicit S: Scheduler
) {
  type Remote = String

  def remoteToRepository(remote: Remote): Repository =
    MavenRepository(remote)

  def dependencyToModule(dependency: Dependency): coursier.Dependency =
    coursier.Dependency(
      Module(dependency.groupId, dependency.artifactId),
      dependency.version
    )

  def resolveArtifacts(remotes: Seq[Remote],
                       dependencies: Seq[Dependency]): Task[Resolution] = {
    val resolution = Resolution(dependencies.map(dependencyToModule).toSet)
    val repositories: Seq[Repository] = Cache.ivy2Local +: remotes.map(
        remoteToRepository)
    val fetch = Fetch.from(repositories, Cache.fetch())
    resolution.process.run(fetch)
  }

  def fetchArtifacts(
    remotes: Seq[Remote],
    dependencies: Seq[Dependency]): Task[coursier.FileError \/ List[File]] =
    for {
      resolution <- resolveArtifacts(remotes, dependencies)
      artifacts <- Task.gatherUnordered(
                    resolution.artifacts.map(Cache.file(_).run)
                  )
    } yield artifacts.sequenceU

  def createEval(jars: Seq[File]): Eval = {

    def createTempDir(tmpName: String): File = {
      val f = new File(tmpName)
      f.mkdirs()
      f
    }
    val targetDir = Some(createTempDir("evaluator/classes"))

    new Eval(target = targetDir, jars = jars.toList)
  }

  private[this] def evaluate[T](code: String, jars: Seq[File]): EvalResult[T] = {
    val eval = createEval(jars)

    val result = for {
      _      ← Try(eval.check(code))
      result ← Try(eval.execute[T](code, resetState = true, jars = jars))
    } yield result

    val errors = eval.errors

    result match {
      case scala.util.Success(r) ⇒ EvalSuccess[T](errors, r, "")
      case scala.util.Failure(t) ⇒
        t match {
          case _: Eval.CompilerException ⇒ CompilationError(errors)
          case e: InvocationTargetException =>
            e.printStackTrace()
            EvalRuntimeError(
              errors,
              Option(RuntimeError(e.getTargetException, None)))
          case NonFatal(e) ⇒
            EvalRuntimeError(errors, Option(RuntimeError(e, None)))
          case e ⇒ GeneralError(e)
        }
    }
  }

  def eval[T](
    code: String,
    remotes: Seq[Remote] = Nil,
    dependencies: Seq[Dependency] = Nil
  ): Task[EvalResult[T]] = {
    for {
      allJars <- fetchArtifacts(remotes, dependencies)
      result <- allJars match {
                 case \/-(jars) =>
                   Task({
                     evaluate(code, jars)
                   }).timed(timeout)
                     .handle({
                       case _: TimeoutException => Timeout[T](timeout)
                     })
                 case -\/(fileError) =>
                   Task.now(UnresolvedDependency(fileError.describe))
               }
    } yield result
  }
}

/**
  * Dynamic scala compiler. Lots of (slow) state is created, so it may be advantageous to keep
  * around one of these and reuse it.
  */
class StringCompiler(
  lineOffset: Int,
  targetDir: Option[File],
  output: AbstractFile,
  settings: Settings,
  messageHandler: Option[Reporter]
) {

  val cache = new scala.collection.mutable.HashMap[String, Class[_]]()

  trait MessageCollector {
    val messages: Seq[List[String]]
  }

  val reporter: Reporter = messageHandler getOrElse new AbstractReporter
    with MessageCollector {
    val settings: Settings = StringCompiler.this.settings
    val messages           = new scala.collection.mutable.ListBuffer[List[String]]

    def display(pos: Position, message: String, severity: Severity) {
      severity.count += 1
      val severityName = severity match {
        case ERROR   => "error: "
        case WARNING => "warning: "
        case _       => ""
      }
      // the line number is not always available
      val lineMessage = try {
        "line " + (pos.line - lineOffset)
      } catch {
        case _: Throwable => ""
      }
      messages += (severityName + lineMessage + ": " + message) ::
        (if (pos.isDefined) {
           pos.finalPosition.lineContent.stripLineEnd ::
             (" " * (pos.column - 1) + "^") ::
               Nil
         } else {
           Nil
         })
    }

    def displayPrompt {
      // no.
    }

    override def reset {
      super.reset
      messages.clear()
    }
  }

  val global = new Global(settings, reporter)

  def reset() {
    targetDir match {
      case None =>
        output.asInstanceOf[VirtualDirectory].clear()
      case Some(_) =>
        output.foreach { abstractFile =>
          if (abstractFile.file == null || abstractFile.file.getName.endsWith(
                ".class")) {
            abstractFile.delete()
          }
        }
    }
    cache.clear()
    reporter.reset()
  }

  def findClass(className: String,
                classLoader: ClassLoader): Option[Class[_]] = {
    synchronized {
      cache.get(className).orElse {
        try {
          val cls = classLoader.loadClass(className)
          cache(className) = cls
          Some(cls)
        } catch {
          case _: ClassNotFoundException => None
        }
      }
    }
  }

  /**
    * Compile scala code. It can be found using the above class loader.
    */
  def compile(scalaSource: File): Unit = {
    val compiler = new global.Run

    compiler.compile(List(scalaSource.getAbsolutePath))

    if (reporter.hasErrors || reporter.WARNING.count > 0) {
      val msgs: List[List[String]] = reporter match {
        case collector: MessageCollector =>
          collector.messages.toList
        case _ =>
          List(List(reporter.toString))
      }

      throw new Eval.CompilerException(msgs)
    }
  }

  def compile(scalaSource: File,
              className: String,
              resetState: Boolean = true): Unit = {
    synchronized {
      if (resetState) reset()

      compile(scalaSource)
    }
  }
}

/**
  * Evaluates files, strings, or input streams as Scala code, and returns the result.
  *
  * If `target` is `None`, the results are compiled to memory (and are therefore ephemeral). If
  * `target` is `Some(path)`, the path must point to a directory, and classes will be saved into
  * that directory. You can optionally pass a list of JARs to include to the classpath during
  * compilation and evaluation.
  *
  * The flow of evaluation is:
  * - wrap code in an `apply` method in a generated class
  * - compile the class adding the jars to the classpath
  * - construct an instance of that class
  * - return the result of `apply()`
  */
case class Eval(target: Option[File] = None, jars: List[File] = Nil) {
  @volatile var errors: Map[String, List[CompilationInfo]] = Map.empty

  val compilerOutputDir: AbstractFile = target match {
    case Some(dir) => AbstractFile.getDirectory(dir)
    case None      => new VirtualDirectory("(memory)", None)
  }

  protected lazy val compilerSettings: Settings = new EvalSettings(
    compilerOutputDir)

  protected lazy val compilerMessageHandler: Option[Reporter] = Some(
    new AbstractReporter {
    override val settings: Settings = compilerSettings
    override def displayPrompt(): Unit = ()
    override def display(pos: Position,
                         msg: String,
                         severity: this.type#Severity): Unit = {
      errors += convert((pos, msg, severity.toString))
    }
    override def reset(): Unit = {
      super.reset()
      errors = Map.empty
    }
    private[this] def convert(
      errors: (Position, String, String)): (String, List[CompilationInfo]) = {
      val (pos, msg, severity) = errors
      (severity,
       CompilationInfo(msg, Some(RangePosition(pos.start, pos.point, pos.end))) :: Nil)
    }
  })

  // Primary encapsulation around native Scala compiler
  private[this] lazy val compiler = new StringCompiler(
    codeWrapperLineOffset,
    target,
    compilerOutputDir,
    compilerSettings,
    compilerMessageHandler
  )

  /**
    * Check if code is Eval-able.
    * @throws CompilerException if not Eval-able.
    */
  def check(code: String) {
    val id          = uniqueId(code)
    val className   = "Evaluator__" + id
    val wrappedCode = wrapCodeInClass(className, code)

    val scalaSource = createScalaSource(className, wrappedCode)

    compiler.compile(scalaSource)
  }

  /**
    * Will generate a className of the form `Evaluator__<unique>`,
    * where unique is computed from the jvmID (a random number)
    * and a digest of code
    */
  def execute[T](code: String, resetState: Boolean, jars: Seq[File]): T = {
    val id        = uniqueId(code)
    val className = "Evaluator__" + id
    execute(className, code, resetState, jars)
  }

  def execute[T](className: String,
                 code: String,
                 resetState: Boolean,
                 jars: Seq[File]): T = {

    val urlClassLoader =
      new URLClassLoader(jars map (_.toURI.toURL), NullLoader)
    val classLoader =
      new AbstractFileClassLoader(compilerOutputDir, urlClassLoader)
    val jcl = new JarClassLoader(classLoader)

    import collection.JavaConverters._
    val jarUrls = (jars map (_.getAbsolutePath)).toList
    jcl.addAll(jarUrls.asJava)
    jcl.add(compilerOutputDir.file.toURI.toURL)

    val wrappedCode = wrapCodeInClass(className, code)

    compiler.compile(
      createScalaSource(className, wrappedCode),
      className,
      resetState
    )

    val factory      = JclObjectFactory.getInstance()
    val instantiated = factory.create(jcl, className)
    val method       = instantiated.getClass.getMethod("run")
    val result: Any  = Option(method.invoke(instantiated)).getOrElse((): Unit)

    result.asInstanceOf[T]
  }

  private[this] def createScalaSource(fileName: String, code: String) = {
    val path           = s"temp/src/main/scala/"
    val scalaSourceDir = new File(path)
    val scalaSource    = new File(s"$path/$fileName.scala")

    scalaSourceDir.mkdirs()

    val writer = new PrintWriter(scalaSource)

    writer.write(code)
    writer.close()
    scalaSource
  }

  private[this] def uniqueId(code: String,
                             idOpt: Option[Int] = Some(Eval.jvmId)): String = {
    val digest = MessageDigest.getInstance("SHA-1").digest(code.getBytes())
    val sha    = new BigInteger(1, digest).toString(16)
    idOpt match {
      case Some(i) => sha + "_" + i
      case _       => sha
    }
  }

  /*
   * Wraps source code in a new class with an apply method.
   * NB: If this method is changed, make sure `codeWrapperLineOffset` is correct.
   */
  private[this] def wrapCodeInClass(className: String, code: String) = {
    s"""
class $className extends java.io.Serializable {
  def run() = {
    $code
  }
}
"""
  }

  /*
   * Defines the number of code lines that proceed evaluated code.
   * Used to ensure compile error messages report line numbers aligned with user's code.
   * NB: If `wrapCodeInClass(String,String)` is changed, make sure this remains correct.
   */
  private[this] val codeWrapperLineOffset = 2

  class EvalSettings(output: AbstractFile) extends Settings {
    nowarnings.value = true // warnings are exceptions, so disable
    outputDirs.setSingleOutput(output)
    if (jars.nonEmpty) {
      val newJars = (jars :+ output.file).mkString(File.pathSeparator)
      classpath.value = newJars
      bootclasspath.value = newJars
    }
  }
}

object NullLoader extends ClassLoader {
  override final def loadClass(className: String, resolve: Boolean): Class[_] = {
    if (className.startsWith("java.")) super.loadClass(className, resolve)
    else
      throw new ClassNotFoundException(
        "No classes can be loaded from the null loader")
  }
}

object Eval {
  private val jvmId = java.lang.Math.abs(new java.util.Random().nextInt())

  class CompilerException(val messages: List[List[String]])
      extends Exception(
        "Compiler exception " + messages.map(_.mkString("\n")).mkString("\n"))
}
