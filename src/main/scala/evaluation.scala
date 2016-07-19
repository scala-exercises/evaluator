/*
 * scala-exercises-evaluator
 * Copyright (C) 2015-2016 47 Degrees, LLC. <http://www.47deg.com>
 */

package org.scalaexercises.evaluator


import scala.language.reflectiveCalls

import java.io.{ File, InputStream }
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.jar.JarFile
import java.util.concurrent.TimeoutException
import java.math.BigInteger
import java.util.concurrent._
import java.security._

import scala.tools.nsc.{ Global, Settings }
import scala.tools.nsc.reporters._
import scala.tools.nsc.io.{ VirtualDirectory, AbstractFile }
import scala.reflect.internal.util.{ Position, NoPosition, BatchSourceFile, AbstractFileClassLoader }

import scalaz._; import Scalaz._
import scala.util.Try
import scala.util.control.NonFatal
import scala.concurrent._
import scala.concurrent.duration._
import scalaz.concurrent.Task

import coursier._

class SandboxClassLoader(root: AbstractFile, parent: ClassLoader) extends AbstractFileClassLoader(root, parent){}

class SandboxPolicy extends Policy{
  override def getPermissions(domain: ProtectionDomain): PermissionCollection = {
    domain.getClassLoader match {
      case sandbox: SandboxClassLoader => {
        sandboxPermissions
      }
      case _ => {
        allPermissions
      }
    }
  }

  def sandboxPermissions: PermissionCollection = {
    val pc = new Permissions()
    pc.setReadOnly()
    pc
  }

  def allPermissions: PermissionCollection = {
    val pc = new Permissions()
    pc.add(new AllPermission())
    pc.setReadOnly()
    pc
  }
}

class SandboxSecurityManager() extends SecurityManager{}

class Evaluator(timeout: FiniteDuration = 20.seconds) {
  type Remote = String

  private[this] def convert(errors: (Position, String, String)): (String, List[CompilationInfo]) = {
    val (pos, msg, severity) = errors
    (severity, CompilationInfo(msg, Some(RangePosition(pos.start, pos.point, pos.end))) :: Nil)
  }

  def remoteToRepository(remote: Remote): Repository =
    MavenRepository(remote)

  def dependencyToModule(dependency: Dependency): coursier.Dependency =
    coursier.Dependency(
      Module(dependency.groupId, dependency.artifactId), dependency.version
    )

  def resolveArtifacts(remotes: Seq[Remote], dependencies: Seq[Dependency]): Task[Resolution] = {
    val resolution = Resolution(dependencies.map(dependencyToModule).toSet)
    val repositories: Seq[Repository] = remotes.map(remoteToRepository)
    val fetch = Fetch.from(repositories, Cache.fetch(new File("/tmp"))) // fixme
    resolution.process.run(fetch)
  }

  def fetchArtifacts(remotes: Seq[Remote], dependencies: Seq[Dependency]): Task[coursier.FileError \/ List[File]] = for {
    resolution <- resolveArtifacts(remotes, dependencies)
    artifacts <- Task.gatherUnordered(
      resolution.artifacts.map(Cache.file(_).run)
    )
  } yield artifacts.sequenceU

  def createEval(jars: Seq[File]): Eval = {
    new Eval(jars = jars.toList) {
      @volatile var errors: Map[String, List[CompilationInfo]] = Map.empty

      override lazy val compilerSettings: Settings = new EvalSettings(None){
        if (!jars.isEmpty) {
          val newJars = jars.mkString(File.pathSeparator)
          classpath.value = newJars + File.pathSeparator + classpath.value
        }
      }

      override lazy val compilerMessageHandler: Option[Reporter] = Some(new AbstractReporter {
        override val settings: Settings = compilerSettings
        override def displayPrompt(): Unit = ()
        override def display(pos: Position, msg: String, severity: this.type#Severity): Unit = {
          errors += convert((pos, msg, severity.toString))
        }
        override def reset() = {
          super.reset()
          errors = Map.empty
        }
      })
    }
  }

  def createClassLoader(eval: Eval, jars: Seq[File]): ClassLoader = {
    val jarUrls = jars.map(jar => new java.net.URL(s"file://${jar.getAbsolutePath}")).toArray
    val urlClassLoader = new URLClassLoader(jarUrls , this.getClass.getClassLoader)
    new SandboxClassLoader(eval.compilerOutputDir, urlClassLoader)
  }

  private[this] def evaluate[T](eval: Eval, code: String, classLoader: ClassLoader): EvalResult[T] = {
    val result: Try[T] = for {
      _ ← Try(eval.check(code))
      result ← Try({
        eval.execute[T](code, resetState = true, classLoader = classLoader)
      })
    } yield result

    val errors = Map.empty[String, List[CompilationInfo]] // fixme: eval.errors.toMap.asInstanceOf[EvalResult.CI]

    result match {
      case scala.util.Success(r) ⇒ EvalSuccess[T](errors, r, "")
      case scala.util.Failure(t) ⇒ t match {
        case e: Eval.CompilerException ⇒ CompilationError(errors)
        case e: SecurityException => SecurityViolation(e.getMessage)
        case NonFatal(e)               ⇒ EvalRuntimeError(errors, Option(RuntimeError(e, None)))
        case e                         ⇒ GeneralError(e)
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
        case \/-(jars) => {
          val eval = createEval(jars)
          val classLoader = createClassLoader(eval, jars)

          Task.fork(Task.delay({
            evaluate(eval, code, classLoader)
          })).timed(timeout).handle({
            case err: TimeoutException => Timeout[T](timeout)
          })
        }
        case -\/(fileError) => Task.now(UnresolvedDependency(fileError.describe))

      }


    } yield result
  }
}

/**
  * Dynamic scala compiler. Lots of (slow) state is created, so it may be advantageous to keep
  * around one of these and reuse it.
  */
private class StringCompiler(
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

  val reporter = messageHandler getOrElse new AbstractReporter with MessageCollector {
    val settings = StringCompiler.this.settings
    val messages = new scala.collection.mutable.ListBuffer[List[String]]

    def display(pos: Position, message: String, severity: Severity) {
      severity.count += 1
      val severityName = severity match {
        case ERROR   => "error: "
        case WARNING => "warning: "
        case _ => ""
      }
      // the line number is not always available
      val lineMessage =
        try {
          "line " + (pos.line - lineOffset)
        } catch {
          case _: Throwable => ""
        }
      messages += (severityName + lineMessage + ": " + message) ::
      (if (pos.isDefined) {
        pos.inUltimateSource(pos.source).lineContent.stripLineEnd ::
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
      case None => {
        output.asInstanceOf[VirtualDirectory].clear()
      }
      case Some(t) => {
        output.foreach { abstractFile =>
          if (abstractFile.file == null || abstractFile.file.getName.endsWith(".class")) {
            abstractFile.delete()
          }
        }
      }
    }
    cache.clear()
    reporter.reset()
  }

  def findClass(className: String, classLoader: ClassLoader): Option[Class[_]] = {
    synchronized {
      cache.get(className).orElse {
        try {
          val cls = classLoader.loadClass(className)
          cache(className) = cls
          Some(cls)
        } catch {
          case e: ClassNotFoundException => None
        }
      }
    }
  }

  /**
    * Compile scala code. It can be found using the above class loader.
    */
  def apply(code: String) {
    // if you're looking for the performance hit, it's 1/2 this line...
    val compiler = new global.Run
    val sourceFiles = List(new BatchSourceFile("(inline)", code))
    // ...and 1/2 this line:
    compiler.compileSources(sourceFiles)

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

  /**
    * Compile a new class, load it, and return it. Thread-safe.
    */
  def apply(code: String, className: String, resetState: Boolean = true, classLoader: ClassLoader): Class[_] = {
    synchronized {
      if (resetState) reset()

      apply(code)
      findClass(className, classLoader).get // fixme
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
 * - contruct an instance of that class
 * - return the result of `apply()`
 */
class Eval(target: Option[File] = None, jars: List[File] = Nil) {
  private lazy val compilerPath = try {
    classPathOfClass("scala.tools.nsc.Interpreter")
  } catch {
    case e: Throwable =>
      throw new RuntimeException("Unable to load Scala interpreter from classpath (scala-compiler jar is missing?)", e)
  }

  private lazy val libPath = try {
    classPathOfClass("scala.AnyVal")
  } catch {
    case e: Throwable =>
      throw new RuntimeException("Unable to load scala base object from classpath (scala-library jar is missing?)", e)
  }

  // For derived classes to provide an alternate compiler message handler.
  protected lazy val compilerMessageHandler: Option[Reporter] = None

  // For derived classes do customize or override the default compiler settings.
  protected lazy val compilerSettings: Settings = new EvalSettings(target)

  // Primary encapsulation around native Scala compiler
  private[this] lazy val compiler = new StringCompiler(
    codeWrapperLineOffset,
    target,
    compilerOutputDir,
    compilerSettings,
    compilerMessageHandler
  )

  /**
   * Will generate a classname of the form Evaluater__<unique>,
   * where unique is computed from the jvmID (a random number)
   * and a digest of code
   */
  def execute[T](code: String, resetState: Boolean, classLoader: ClassLoader): T = {
    val id = uniqueId(code)
    val className = "Evaluator__" + id
    execute(className, code, resetState, classLoader)
  }

  def execute[T](className: String, code: String, resetState: Boolean, classLoader: ClassLoader): T = {
    val cls = compiler(
      wrapCodeInClass(className, code), className, resetState, classLoader
    )

    cls.getConstructor().newInstance().asInstanceOf[() => T].apply().asInstanceOf[T]
  }

  // def runClass[T](cls: Class[_]): T = {
  //   cls.getConstructor().newInstance().asInstanceOf[() => T].apply()
  // }

  /**
   * Check if code is Eval-able.
   * @throws CompilerException if not Eval-able.
   */
  def check(code: String) {
    val id = uniqueId(code)
    val className = "Evaluator__" + id
    val wrappedCode = wrapCodeInClass(className, code)
    compiler(wrappedCode)
  }

  private[this] def uniqueId(code: String, idOpt: Option[Int] = Some(Eval.jvmId)): String = {
    val digest = MessageDigest.getInstance("SHA-1").digest(code.getBytes())
    val sha = new BigInteger(1, digest).toString(16)
    idOpt match {
      case Some(id) => sha + "_" + id
      case _ => sha
    }
  }

  /*
   * Wraps source code in a new class with an apply method.
   * NB: If this method is changed, make sure `codeWrapperLineOffset` is correct.
   */
  private[this] def wrapCodeInClass(className: String, code: String) = {
    s"""
class ${className} extends (() => Any) with java.io.Serializable {
  def apply() = {
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

  /*
   * For a given FQ classname, trick the resource finder into telling us the containing jar.
   */
  private def classPathOfClass(className: String) = {
    val resource = className.split('.').mkString("/", "/", ".class")
    val path = getClass.getResource(resource).getPath
    if (path.indexOf("file:") >= 0) {
      val indexOfFile = path.indexOf("file:") + 5
      val indexOfSeparator = path.lastIndexOf('!')
      List(path.substring(indexOfFile, indexOfSeparator))
    } else {
      require(path.endsWith(resource))
      List(path.substring(0, path.length - resource.length + 1))
    }
  }

  lazy val compilerOutputDir = target match {
    case Some(dir) => AbstractFile.getDirectory(dir)
    case None => new VirtualDirectory("(memory)", None)
  }

  class EvalSettings(targetDir: Option[File]) extends Settings {
    nowarnings.value = true // warnings are exceptions, so disable
    outputDirs.setSingleOutput(compilerOutputDir)
    private[this] val pathList  = compilerPath ::: libPath
    bootclasspath.value = pathList.mkString(File.pathSeparator)
    classpath.value = pathList.mkString(File.pathSeparator)
  }
}


object Eval {
  private val jvmId = java.lang.Math.abs(new java.util.Random().nextInt())

  def enableSandbox = {
    Policy.setPolicy(new SandboxPolicy())
    System.setSecurityManager(new SandboxSecurityManager())
  }

  def disableSandbox = {
    // Policy.setPolicy(null)
    // System.setSecurityManager(null)
  }

  class CompilerException(val messages: List[List[String]]) extends Exception(
    "Compiler exception " + messages.map(_.mkString("\n")).mkString("\n"))
}

