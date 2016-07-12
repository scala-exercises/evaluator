/*
 * Copyright 2010 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.twitter.util

import com.twitter.conversions.string._
import com.twitter.io.StreamIO
import java.io._
import java.math.BigInteger
import java.net.URLClassLoader
import java.security.MessageDigest
import java.util.Random
import java.util.jar.JarFile
import scala.collection.mutable
import scala.io.Source
import scala.reflect.internal.util.{BatchSourceFile, Position}
import scala.tools.nsc.interpreter.AbstractFileClassLoader
import scala.tools.nsc.io.{AbstractFile, VirtualDirectory}
import scala.tools.nsc.reporters.{Reporter, AbstractReporter}
import scala.tools.nsc.{Global, Settings}
import scala.util.matching.Regex

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

  val cache = new mutable.HashMap[String, Class[_]]()

  trait MessageCollector {
    val messages: Seq[List[String]]
  }

  val reporter = messageHandler getOrElse new AbstractReporter with MessageCollector {
    val settings = StringCompiler.this.settings
    val messages = new mutable.ListBuffer[List[String]]

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
  def applyProcessed[T](code: String, resetState: Boolean, jars: Seq[File]): T = {
    val id = uniqueId(code)
    val className = "Evaluator__" + id
    applyProcessed(className, code, resetState, jars)
  }

  /**
   */
  def applyProcessed[T](className: String, code: String, resetState: Boolean, jars: Seq[File]): T = {
    val jarUrls = jars.map(jar => new java.net.URL(s"file://${jar.getAbsolutePath}")).toArray
    val urlClassLoader = new URLClassLoader(jarUrls , compiler.getClass.getClassLoader)
    val classLoader = new AbstractFileClassLoader(compilerOutputDir, urlClassLoader)

    val cls = compiler(
      wrapCodeInClass(className, code), className, resetState, classLoader
    )
    cls.getConstructor().newInstance().asInstanceOf[() => T].apply().asInstanceOf[T]
  }

  /**
   * Converts the given file to evaluable source.
   */
  def toSource(file: File): String = {
    Source.fromFile(file).mkString
  }

  /**
   * Compile an entire source file into the virtual classloader.
   */
  def compile(code: String) {
    compiler(code)
  }

  /**
   * Check if code is Eval-able.
   * @throws CompilerException if not Eval-able.
   */
  def check(code: String) {
    val id = uniqueId(code)
    val className = "Evaluator__" + id
    val wrappedCode = wrapCodeInClass(className, code)
    compile(wrappedCode) // may throw CompilerException
  }

  /**
   * Check if files are Eval-able.
   * @throws CompilerException if not Eval-able.
   */
  def check(files: File*) {
    val code = files.map { Source.fromFile(_).mkString }.mkString("\n")
    check(code)
  }

  /**
   * Check if stream is Eval-able.
   * @throws CompilerException if not Eval-able.
   */
  def check(stream: InputStream) {
    check(Source.fromInputStream(stream).mkString)
  }

  private[util] def uniqueId(code: String, idOpt: Option[Int] = Some(Eval.jvmId)): String = {
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

  /*
   * Try to guess our app's classpath.
   * This is probably fragile.
   */
  lazy val impliedClassPath: List[String] = {
    def getClassPath(cl: ClassLoader, acc: List[List[String]] = List.empty): List[List[String]] = {
      val cp = cl match {
        case urlClassLoader: URLClassLoader => urlClassLoader.getURLs.filter(_.getProtocol == "file").
          map(u => new File(u.toURI).getPath).toList
        case _ => Nil
      }
      cl.getParent match {
        case null => (cp :: acc).reverse
        case parent => getClassPath(parent, cp :: acc)
      }
    }

    val classPath = getClassPath(this.getClass.getClassLoader)
    val currentClassPath = classPath.head

    // if there's just one thing in the classpath, and it's a jar, assume an executable jar.
    currentClassPath ::: (if (currentClassPath.size == 1 && currentClassPath(0).endsWith(".jar")) {
      val jarFile = currentClassPath(0)
      val relativeRoot = new File(jarFile).getParentFile()
      val nestedClassPath = new JarFile(jarFile).getManifest.getMainAttributes.getValue("Class-Path")
      if (nestedClassPath eq null) {
        Nil
      } else {
        nestedClassPath.split(" ").map { f => new File(relativeRoot, f).getAbsolutePath }.toList
      }
    } else {
      Nil
    }) ::: classPath.tail.flatten
  }

  lazy val compilerOutputDir = target match {
    case Some(dir) => AbstractFile.getDirectory(dir)
    case None => new VirtualDirectory("(memory)", None)
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
  private val jvmId = java.lang.Math.abs(new Random().nextInt())

  class CompilerException(val messages: List[List[String]]) extends Exception(
    "Compiler exception " + messages.map(_.mkString("\n")).mkString("\n"))
}
