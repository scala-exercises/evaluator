/*
 * scala-exercises-evaluator-compiler
 * Copyright (C) 2015-2016 47 Degrees, LLC. <http://www.47deg.com>
 */

package org.scalaexercises.evaluator

import java.io.{File, PrintWriter}
import java.math.BigInteger
import java.security.MessageDigest

import org.scalaexercises.evaluator.Eval.CompilerException
import org.xeustechnologies.jcl.{JarClassLoader, JclObjectFactory}

import java.net.URLClassLoader
import scala.reflect.internal.util.{AbstractFileClassLoader, Position}
import scala.tools.nsc.Settings
import scala.tools.nsc.io.{AbstractFile, VirtualDirectory}
import scala.tools.nsc.reporters._

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

    import collection.JavaConverters._
    val urlClassLoader =
      new URLClassLoader((jars map (_.toURI.toURL)).toArray, NullLoader)
    val classLoader =
      new AbstractFileClassLoader(compilerOutputDir, urlClassLoader)
    val jcl = new JarClassLoader(classLoader)

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

object Eval {
  private val jvmId = java.lang.Math.abs(new java.util.Random().nextInt())

  class CompilerException(val messages: List[List[String]])
      extends Exception(
        "Compiler exception " + messages.map(_.mkString("\n")).mkString("\n"))
}