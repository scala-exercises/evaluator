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
  val exitVM = "exitVM.*".r
  val securityManager = "createSecurityManager"

  val enabled = new InheritableThreadLocal[Boolean]()
  enabled.set(false)

  override def checkPermission(perm: Permission): Unit = {
    val isEnabled = enabled.get() // may return null
    val inSandbox = isEnabled.asInstanceOf[Boolean] // fixme: if i use Option i get a ClassCircularityError :_[ -- al
    if (inSandbox) {
      sandboxedCheck(perm) match {
        case Right(result) => ()//println("Check OK " + result)
        case Left(msg) => throw new SecurityException(msg)
      }
    }
  }

  def sandboxedCheck(perm: Permission): Either[String, String] = {
    perm.getName match {
      case exitVM() => Left("Can not exit the VM in sandboxed code")
      case `securityManager` => Left("Can not replace the security manager in sandboxed code")
      case other => Right(other)
    }
  }
}
