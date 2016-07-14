package org.scalaexercises.evaluator

import java.util.logging._
import java.util.concurrent._
import java.security._
import scala.concurrent._

// see http://docs.oracle.com/javase/8/docs/technotes/guides/security/permissions.html

// permission categories:
// file
// socket
// net
// security
// runtime
// property
// awt
// reflection
// serializable


object SandboxedExecution {
  def executor: ExecutorService = {
    val threadFactory = new ThreadFactory {

      override def newThread(r: Runnable): Thread = {
        val th = new Thread(r){
          override def run() = {
            System.getSecurityManager match {
              case sm: SandboxedSecurityManager => {
                sm.enabled.set(true)
              }
              case _ => ()
            }
            super.run()
          }
        }
        th.setName(s"sandboxed-thread-${scala.util.Random.nextInt()}")
        th
      }
    }
    Executors.newCachedThreadPool(threadFactory)
  }
}

class SandboxedSecurityManager extends SecurityManager {
  val enabled = new InheritableThreadLocal[Boolean](){
    override def initialValue: Boolean = false
    override def set(value: Boolean): Unit = {
      if (get()){
        throw new SecurityException("The sandbox can't be disabled ")
      }
      super.set(value)
    }
  }

  override def checkPermission(perm: Permission): Unit = {
    if (enabled.get()) {
      sandboxedCheck(perm) match {
        case Right(result) => ()
        case Left(msg) => throw new SecurityException(msg)
      }
    }
  }

  // runtime
  val exitVM = "exitVM.*".r
  val securityManager = ".+SecurityManager".r

  def checkRuntimePermission(perm: RuntimePermission): Either[String, String] = {
    perm.getName match {
      case exitVM() => Left("Can not exit the VM in sandboxed code")
      case securityManager() => Left("Can not replace the security manager in sandboxed code")
      case other => Right(other)
    }
  }

  def sandboxedCheck(perm: Permission): Either[String, String] = {
    perm match {
      case awt: java.awt.AWTPermission => Left("Can not access the AWT APIs in sanboxed code")
      case rt: RuntimePermission => checkRuntimePermission(rt)
      case other =>      Right(other.getName)
    }
  }
}
