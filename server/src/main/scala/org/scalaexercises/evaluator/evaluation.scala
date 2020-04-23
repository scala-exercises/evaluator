/*
 * Copyright 2016-2020 47 Degrees <https://47deg.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.scalaexercises.evaluator

import java.io.{ByteArrayOutputStream, Closeable, File}
import java.math.BigInteger
import java.security.MessageDigest
import java.util.jar.JarFile

import cats.effect._
import cats.effect.syntax.concurrent.catsEffectSyntaxConcurrent
import cats.implicits._
import coursier._
import coursier.cache.{ArtifactError, FileCache}
import coursier.util.Sync
import org.scalaexercises.evaluator.Eval.CompilerException
import org.scalaexercises.evaluator.{Dependency => EvaluatorDependency}

import scala.jdk.CollectionConverters.ConcurrentMapHasAsScala
import scala.concurrent.duration._
import scala.reflect.internal.util.{AbstractFileClassLoader, BatchSourceFile, Position}
import scala.reflect.internal.util.ScalaClassLoader.URLClassLoader
import scala.tools.nsc.io.{AbstractFile, VirtualDirectory}
import scala.tools.nsc.reporters._
import scala.tools.nsc.{Global, Settings}
import scala.util.{Failure, Success, Try}
import scala.util.control.NonFatal

class Evaluator[F[_]: Sync](timeout: FiniteDuration = 20.seconds)(
    implicit F: ConcurrentEffect[F],
    T: Timer[F]
) {
  type Remote = String

  private[this] def convert(errors: (Position, String, String)): (String, List[CompilationInfo]) = {
    val (pos, msg, severity) = errors
    (severity, CompilationInfo(msg, Some(RangePosition(pos.start, pos.point, pos.end))) :: Nil)
  }

  def remoteToRepository(remote: Remote): Repository = MavenRepository(remote)

  def dependencyToModule(dependency: EvaluatorDependency): Dependency = {
    val exclusions: Set[(Organization, ModuleName)] =
      dependency.exclusions
        .fold(List[(Organization, ModuleName)]())(
          _.map(ex => (Organization(ex.organization), ModuleName(ex.moduleName)))
        )
        .toSet

    coursier
      .Dependency(
        Module(Organization(dependency.groupId), ModuleName(dependency.artifactId)),
        dependency.version
      )
      .withExclusions(exclusions)
  }

  val cache: FileCache[F] = FileCache[F].noCredentials

  def fetch(remotes: Seq[Remote], dependencies: Seq[EvaluatorDependency]) =
    Fetch[F](cache)
      .addDependencies(dependencies.map(dependencyToModule): _*)
      .addRepositories(remotes.map(remoteToRepository): _*)
      .addRepositories(coursier.LocalRepositories.ivy2Local)
      .io
      .attempt

  def createEval(jars: Seq[File]) =
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

      override lazy val compilerMessageHandler: Option[Reporter] = Some(new FilteringReporter {
        override def settings: Settings = compilerSettings

        override def doReport(pos: Position, msg: String, severity: Severity): Unit =
          errors += convert((pos, msg, severity.toString))

        override def reset() = {
          super.reset()
          errors = Map.empty
        }
      })

    }

  private[this] def evaluate[T](code: String, jars: Seq[File]): F[EvalResult[T]] = {
    F.bracket(F.delay(createEval(jars))) { evalInstance =>
      val outCapture = new ByteArrayOutputStream

      F.delay[EvalResult[T]](Console.withOut(outCapture) {

        val result = Try(evalInstance.execute[T](code, resetState = true, jars = jars))

        val errors = evalInstance.errors

        result match {
          case scala.util.Success(r) => EvalSuccess[T](errors, r, outCapture.toString)
          case scala.util.Failure(t) =>
            t match {
              case e: CompilerException => CompilationError(errors)
              case NonFatal(e) =>
                EvalRuntimeError(errors, Option(RuntimeError(e, None)))
              case e => GeneralError(e)
            }
        }
      })
    }(EI => F.delay(EI.clean()))

  }

  def eval[T](
      code: String,
      remotes: Seq[Remote] = Nil,
      dependencies: Seq[EvaluatorDependency] = Nil
  ): F[EvalResult[T]] = {
    for {
      allJars <- fetch(remotes, dependencies)
      result <- allJars match {
        case Right(jars) =>
          evaluate(code, jars)
            .timeoutTo(timeout, Timeout[T](timeout).asInstanceOf[EvalResult[T]].pure[F])
        case Left(ex) => F.pure(UnresolvedDependency[T](ex.getMessage))
      }
    } yield result
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
) extends Closeable {

  val cache = new java.util.concurrent.ConcurrentHashMap[String, Class[_]].asScala

  trait MessageCollector {
    val messages: scala.collection.mutable.ListBuffer[List[String]]
  }

  val reporter = messageHandler getOrElse new FilteringReporter with MessageCollector {
    val settings = StringCompiler.this.settings
    val messages = new scala.collection.mutable.ListBuffer[List[String]]

    def doReport(pos: Position, message: String, severity: Severity) = {
      increment(severity)
      val severityName = severity match {
        case ERROR   => "error: "
        case WARNING => "warning: "
        case _       => ""
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
           pos.finalPosition.lineContent.stripLineEnd ::
             (" " * (pos.column - 1) + "^") ::
             Nil
         } else {
           Nil
         })
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
    global.cleanup
    cache.clear()
    reporter.reset()
  }

  def findClass(className: String, classLoader: ClassLoader): Option[Class[_]] =
    Try(cache.getOrElseUpdate(className, classLoader.loadClass(className))) match {
      case Success(cls) => Some(cls)
      case Failure(_)   => None
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

    if (reporter.hasErrors || reporter.hasWarnings) {
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
      classLoader: ClassLoader
  ): Class[_] = {
    synchronized {
      apply(code)
      val clazz = findClass(className, classLoader).get // fixme

      if (resetState) reset()
      clazz
    }
  }

  override def close(): Unit = {
    global.cleanup
    global.close()
    reporter.reset()
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
  private lazy val compilerPath =
    try {
      classPathOfClass("scala.tools.nsc.Interpreter")
    } catch {
      case e: Throwable =>
        throw new RuntimeException(
          "Unable to load Scala interpreter from classpath (scala-compiler jar is missing?)",
          e
        )
    }

  private lazy val libPath =
    try {
      classPathOfClass("scala.AnyVal")
    } catch {
      case e: Throwable =>
        throw new RuntimeException(
          "Unable to load scala base object from classpath (scala-library jar is missing?)",
          e
        )
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

  lazy val jarUrls        = jars.map(jar => new java.net.URL(s"file://${jar.getAbsolutePath}"))
  lazy val urlClassLoader = new URLClassLoader(jarUrls, compiler.getClass.getClassLoader)
  lazy val classLoader    = new AbstractFileClassLoader(compilerOutputDir, urlClassLoader)

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

  def clean(): Unit = {
    urlClassLoader.close()
    compiler.close()
    compilerMessageHandler.foreach(_.reset())
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

    def checkCurrentClassPath: List[String] = currentClassPath match {
      case List(jarFile) if jarFile.endsWith(".jar") =>
        val relativeRoot = new File(jarFile).getParentFile
        val jarResource: Resource[IO, JarFile] =
          Resource.make(IO(new JarFile(jarFile)))(jar => IO(jar.close()))

        val nestedClassPath: IO[String] =
          jarResource
            .use(jar => IO(jar.getManifest.getMainAttributes.getValue("Class-Path")))
            .handleError {
              case scala.util.control.NonFatal(e) =>
                throw new CompilerException(List(List(e.getMessage)))
            }

        nestedClassPath.map {
          case ncp if ncp eq null => Nil
          case ncp =>
            ncp
              .split(" ")
              .map(f => new File(relativeRoot, f).getAbsolutePath)
              .toList

        }.unsafeRunSync
      case _ => Nil
    }

    // if there's just one thing in the classpath, and it's a jar, assume an executable jar.
    currentClassPath ::: checkCurrentClassPath ::: classPath.tail.flatten
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
