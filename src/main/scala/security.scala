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
  /**
    *  An executor for creating sandboxed threads.
    */
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

class SandboxFlag extends InheritableThreadLocal[Boolean]{
  override def initialValue: Boolean = false
  override def set(value: Boolean): Unit = {
    if (get() && !value){
      throw new SecurityException("The sandbox can't be disabled ")
    }

    super.set(value)
  }
}

/**
  * A self-protecting security manager for the external code execution sandbox. It's
  * self-protecting in the sense that doesn't allow executed code to change the sandbox
  * settings or replace the JVM's security manager.
  *
  * References:
  *  - Evaluating the flexibility of the Java Sandbox https://www.cs.cmu.edu/~clegoues/docs/coker15acsac.pdf
  */
class SandboxedSecurityManager extends SecurityManager {
  val enabled = new SandboxFlag()

  override def checkPermission(perm: Permission): Unit = {
    if (enabled.get()) {
      sandboxedCheck(perm) match {
        case Right(result) => ()
        case Left(msg) => throw new SecurityException(msg)
      }
    }
  }

  // todo: write or execute any file
  // todo: setPolicy
  // todo: setProperty.package.access

  // runtime
  val exitVM = "exitVM.*".r
  val securityManager = ".+SecurityManager".r
  val classLoader = "createClassLoader"
  val accessDangerousPackage = "accessClassInPackage.sun.*".r
  val suppressAccessChecks = "suppressAccessChecks"

  def checkRuntimePermission(perm: RuntimePermission): Either[String, String] = {
    perm.getName match {
      case exitVM() => Left("Can not exit the VM in sandboxed code")
      case securityManager() => Left("Can not replace the security manager in sandboxed code")
      case `classLoader` => Left("Can not create a class loader in sandboxed code")
      case accessDangerousPackage() => Left("Can not access dangerous pacakges in sandboxed code")
      case other => {
        Right(other)
      }
    }
  }

  // reflection

  def checkReflectionPermission(perm: java.lang.reflect.ReflectPermission): Either[String, String] = {
    perm.getName match {
      case `suppressAccessChecks` => Left("Can not suppress access checks in sandboxed code")
      case other => Right(other)
    }
  }

  def sandboxedCheck(perm: Permission): Either[String, String] = {
    perm match {
      case awt: java.awt.AWTPermission => Left("Can not access the AWT APIs in sanboxed code")
      case rt: RuntimePermission => checkRuntimePermission(rt)
      case ref: java.lang.reflect.ReflectPermission => checkReflectionPermission(ref)
      case other =>      Right(other.getName)
    }
  }
}
