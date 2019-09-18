/*
 *
 *  scala-exercises - evaluator-server
 *  Copyright (C) 2015-2019 47 Degrees, LLC. <http://www.47deg.com>
 *
 */

package org.scalaexercises.evaluator

import java.io.{ByteArrayOutputStream, File}
import java.math.BigInteger
import java.net.URLClassLoader
import java.security.MessageDigest
import java.util.jar.JarFile

import cats.effect.{Concurrent, ConcurrentEffect, ContextShift, Timer}
import cats.implicits._
import coursier._
import coursier.cache.{ArtifactError, FileCache}
import coursier.util.Sync
import org.scalaexercises.evaluator.Eval.CompilerException

import scala.concurrent.duration._
import scala.language.reflectiveCalls
import scala.reflect.internal.util.{AbstractFileClassLoader, BatchSourceFile, Position}
import scala.tools.nsc.io.{AbstractFile, VirtualDirectory}
import scala.tools.nsc.reporters._
import scala.tools.nsc.{Global, Settings}
import scala.util.Try
import scala.util.control.NonFatal

class Evaluator[F[_]: Sync](timeout: FiniteDuration = 20.seconds)(
    implicit CS: ContextShift[F],
    F: ConcurrentEffect[F],
    T: Timer[F]) {
  type Remote = String

  private[this] def convert(errors: (Position, String, String)): (String, List[CompilationInfo]) = {
    val (pos, msg, severity) = errors
    (severity, CompilationInfo(msg, Some(RangePosition(pos.start, pos.point, pos.end))) :: Nil)
  }

  def remoteToRepository(remote: Remote): Repository = MavenRepository(remote)

  def dependencyToModule(dependency: Dependency): coursier.Dependency =
    coursier.Dependency.of(
      Module(Organization(dependency.groupId), ModuleName(dependency.artifactId)),
      dependency.version
    )

  val cache: FileCache[F] = FileCache[F].noCredentials

  def resolveArtifacts(remotes: Seq[Remote], dependencies: Seq[Dependency]): F[Resolution] = {
    Resolve[F](cache)
      .addDependencies(dependencies.map(dependencyToModule): _*)
      .addRepositories(remotes.map(remoteToRepository): _*)
      .addRepositories(coursier.LocalRepositories.ivy2Local)
      .io
  }

  def fetchArtifacts(
      remotes: Seq[Remote],
      dependencies: Seq[Dependency]): F[Either[ArtifactError, List[File]]] =
    for {
      resolution        <- resolveArtifacts(remotes, dependencies)
      gatheredArtifacts <- resolution.artifacts().toList.traverse(cache.file(_).run)
      artifacts = gatheredArtifacts.foldRight(Right(Nil): Either[ArtifactError, List[File]]) {
        case (Right(file), acc) => acc.map(file :: _)
        case (Left(ae), _)      => Left(ae)
      }
    } yield artifacts

  def createEval(jars: Seq[File]) = {
    new Eval(jars = jars.toList) {
      @volatile var errors: Map[String, List[CompilationInfo]] = Map.empty

      override lazy val compilerSettings: Settings = new EvalSettings(None) {
        if (jars.nonEmpty) {
          val newJars = jars.mkString(File.pathSeparator)
          classpath.value = newJars + File.pathSeparator + classpath.value

          (jars map (_.toString)).filter(_.endsWith(".jar")).find(_.contains("paradise")) match {
            case Some(compilerJar) => plugin.value ++= List(compilerJar)
            case None              =>
          }
        }
      }

      override lazy val compilerMessageHandler: Option[Reporter] = Some(new AbstractReporter {
        override val settings: Settings    = compilerSettings
        override def displayPrompt(): Unit = ()
        override def display(pos: Position, msg: String, severity: this.type#Severity): Unit =
          errors += convert((pos, msg, severity.toString))
        override def reset() = {
          super.reset()
          errors = Map.empty
        }
      })
    }
  }

  private[this] def evaluate[T](code: String, jars: Seq[File]): EvalResult[T] = {
    val eval = createEval(jars)

    val outCapture = new ByteArrayOutputStream

    Console.withOut(outCapture) {

      val result = for {
        _      ← Try(eval.check(code))
        result ← Try(eval.execute[T](code, resetState = true, jars = jars))
      } yield result

      val errors = eval.errors

      result match {
        case scala.util.Success(r) ⇒ EvalSuccess[T](errors, r, outCapture.toString)
        case scala.util.Failure(t) ⇒
          t match {
            case e: CompilerException ⇒ CompilationError(errors)
            case NonFatal(e) ⇒
              EvalRuntimeError(errors, Option(RuntimeError(e, None)))
            case e ⇒ GeneralError(e)
          }
      }
    }
  }

  def eval[T](
      code: String,
      remotes: Seq[Remote] = Nil,
      dependencies: Seq[Dependency] = Nil
  ): F[EvalResult[T]] = {
    for {
      allJars <- fetchArtifacts(remotes, dependencies)
      result <- allJars match {
        case Right(jars) =>
          val fallback: EvalResult[T] = Timeout[T](timeout)
          val success: F[EvalResult[T]] =
            timeoutTo(F.delay { evaluate(code, jars) }, timeout, fallback)
          success
        case Left(fileError) =>
          val failure: F[EvalResult[T]] = F.pure(UnresolvedDependency[T](fileError.describe))
          failure
      }
    } yield result
  }

  def timeoutTo[A](fa: F[A], after: FiniteDuration, fallback: A): F[A] = {

    Concurrent[F].race(fa, T.sleep(after)).flatMap {
      case Left(a)  => a.pure[F]
      case Right(_) => fallback.pure[F]
    }
  }
}

/**
 * The code in this file was taken and only slightly modified from
 *
 * https://github.com/twitter/util/blob/302235a473d20735e5327d785e19b0f489b4a59f/util-eval/src/main/scala/com/twitter/util/Eval.scala
 *
 * Twitter, Inc.
 *
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

    def display(pos: Position, message: String, severity: Severity) = {
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

    def displayPrompt = {
      // no.
    }

    override def reset = {
      super.reset
      messages.clear()
    }
  }

  val global = new Global(settings, reporter)

  def reset() = {
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
  def apply(code: String) = {
    // if you're looking for the performance hit, it's 1/2 this line...
    val compiler    = new global.Run
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
      throw new CompilerException(msgs)
    }
  }

  /**
   * Compile a new class, load it, and return it. Thread-safe.
   */
  def apply(
      code: String,
      className: String,
      resetState: Boolean = true,
      classLoader: ClassLoader): Class[_] = {
    synchronized {
      if (resetState) reset()

      apply(code)
      findClass(className, classLoader).get // fixme
    }
  }
}

/**
 * The code in this file was taken and only slightly modified from
 *
 * https://github.com/twitter/util/blob/302235a473d20735e5327d785e19b0f489b4a59f/util-eval/src/main/scala/com/twitter/util/Eval.scala
 *
 * Twitter, Inc.
 *
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
      throw new RuntimeException(
        "Unable to load Scala interpreter from classpath (scala-compiler jar is missing?)",
        e)
  }

  private lazy val libPath = try {
    classPathOfClass("scala.AnyVal")
  } catch {
    case e: Throwable =>
      throw new RuntimeException(
        "Unable to load scala base object from classpath (scala-library jar is missing?)",
        e)
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
  def execute[T](code: String, resetState: Boolean, jars: Seq[File]): T = {
    val id        = uniqueId(code)
    val className = "Evaluator__" + id
    execute(className, code, resetState, jars)
  }

  def execute[T](className: String, code: String, resetState: Boolean, jars: Seq[File]): T = {
    val jarUrls = jars
      .map(jar => new java.net.URL(s"file://${jar.getAbsolutePath}"))
      .toArray
    val urlClassLoader =
      new URLClassLoader(jarUrls, compiler.getClass.getClassLoader)
    val classLoader =
      new AbstractFileClassLoader(compilerOutputDir, urlClassLoader)

    val cls = compiler(
      wrapCodeInClass(className, code),
      className,
      resetState,
      classLoader
    )
    cls
      .getConstructor()
      .newInstance()
      .asInstanceOf[() => T]
      .apply()
      .asInstanceOf[T]
  }

  /**
   * Check if code is Eval-able.
   * @throws CompilerException if not Eval-able.
   */
  def check(code: String) = {
    val id          = uniqueId(code)
    val className   = "Evaluator__" + id
    val wrappedCode = wrapCodeInClass(className, code)
    compiler(wrappedCode)
  }

  private[this] def uniqueId(code: String, idOpt: Option[Int] = Some(Eval.jvmId)): String = {
    val digest = MessageDigest.getInstance("SHA-1").digest(code.getBytes())
    val sha    = new BigInteger(1, digest).toString(16)
    idOpt match {
      case Some(id) => sha + "_" + id
      case _        => sha
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
    val path     = getClass.getResource(resource).getPath
    if (path.indexOf("file:") >= 0) {
      val indexOfFile      = path.indexOf("file:") + 5
      val indexOfSeparator = path.lastIndexOf('!')
      List(path.substring(indexOfFile, indexOfSeparator))
    } else {
      require(path.endsWith(resource))
      List(path.substring(0, path.length - resource.length + 1))
    }
  }

  /*
   * Try to guess our app's classpath.
   * This is probably fragile.
   */
  lazy val impliedClassPath: List[String] = {
    def getClassPath(cl: ClassLoader, acc: List[List[String]] = List.empty): List[List[String]] = {
      val cp = cl match {
        case urlClassLoader: URLClassLoader =>
          urlClassLoader.getURLs
            .filter(_.getProtocol == "file")
            .map(u => new File(u.toURI).getPath)
            .toList
        case _ => Nil
      }
      cl.getParent match {
        case null   => (cp :: acc).reverse
        case parent => getClassPath(parent, cp :: acc)
      }
    }

    val classPath        = getClassPath(this.getClass.getClassLoader)
    val currentClassPath = classPath.head

    // if there's just one thing in the classpath, and it's a jar, assume an executable jar.
    currentClassPath ::: (if (currentClassPath.size == 1 && currentClassPath(0)
                              .endsWith(".jar")) {
                            val jarFile = currentClassPath(0)
                            val relativeRoot =
                              new File(jarFile).getParentFile()
                            val nestedClassPath =
                              new JarFile(jarFile).getManifest.getMainAttributes
                                .getValue("Class-Path")
                            if (nestedClassPath eq null) {
                              Nil
                            } else {
                              nestedClassPath
                                .split(" ")
                                .map { f =>
                                  new File(relativeRoot, f).getAbsolutePath
                                }
                                .toList
                            }
                          } else {
                            Nil
                          }) ::: classPath.tail.flatten
  }

  lazy val compilerOutputDir = target match {
    case Some(dir) => AbstractFile.getDirectory(dir)
    case None      => new VirtualDirectory("(memory)", None)
  }

  class EvalSettings(targetDir: Option[File]) extends Settings {
    nowarnings.value = true // warnings are exceptions, so disable
    outputDirs.setSingleOutput(compilerOutputDir)
    private[this] val pathList = compilerPath ::: libPath
    bootclasspath.value = pathList.mkString(File.pathSeparator)
    classpath.value = (pathList ::: impliedClassPath).mkString(File.pathSeparator)
  }
}

object Eval {
  private val jvmId = java.lang.Math.abs(new java.util.Random().nextInt())

  class CompilerException(val messages: List[List[String]])
      extends Exception("Compiler exception " + messages.map(_.mkString("\n")).mkString("\n"))
}
