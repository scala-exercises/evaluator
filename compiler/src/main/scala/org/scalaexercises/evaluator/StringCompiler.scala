/*
 * scala-exercises - evaluator-compiler
 * Copyright (C) 2015-2016 47 Degrees, LLC. <http://www.47deg.com>
 */

package org.scalaexercises.evaluator

import java.io.File

import scala.tools.nsc.{Global, Settings}
import scala.tools.nsc.io.{AbstractFile, VirtualDirectory}
import scala.tools.nsc.reporters.{AbstractReporter, Reporter}
import scala.reflect.internal.util.Position

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

  val reporter: Reporter = messageHandler getOrElse new AbstractReporter with MessageCollector {
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
          if (abstractFile.file == null || abstractFile.file.getName.endsWith(".class")) {
            abstractFile.delete()
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

  def compile(scalaSource: File, className: String, resetState: Boolean = true): Unit = {
    synchronized {
      if (resetState) reset()

      compile(scalaSource)
    }
  }
}
