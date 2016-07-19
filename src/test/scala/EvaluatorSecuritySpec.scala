/*
 * scala-exercises-evaluator
 * Copyright (C) 2015-2016 47 Degrees, LLC. <http://www.47deg.com>
 */

package org.scalaexercises.evaluator

import scalaz._; import Scalaz._
import scala.concurrent.duration._
import org.scalatest._
import java.util.concurrent._
import java.security._

class EvaluatorSecuritySpec extends FunSpec with Matchers with BeforeAndAfter {
  val evaluator = new Evaluator(10 seconds)

  before {
    Policy.setPolicy(new SandboxPolicy())
    System.setSecurityManager(new SecurityManager())
  }

  describe("evaluation security") {
    it("doesn't allow code to call System.exit") {
      val result: EvalResult[Unit] = evaluator.eval("System.exit(1)").run

      result should matchPattern {
        case SecurityViolation(_) ⇒
      }
    }

    it("doesn't allow to install a security manager") {
      val code = """
import java.security._

class MaliciousSecurityManager extends SecurityManager{
  override def checkPermission(perm: Permission): Unit = {
    // allow anything to happen by not throwing a security exception
  }
}

System.setSecurityManager(new MaliciousSecurityManager())
"""
      val result: EvalResult[Unit] = evaluator.eval(code).run

      result should matchPattern {
        case SecurityViolation(_) ⇒
      }
    }

    it("doesn't allow the creation of a class loader") {
      val code = """
val cl = new java.net.URLClassLoader(Array())
cl.loadClass("java.net.URLClassLoader")
"""
      val result: EvalResult[Unit] = evaluator.eval(code).run

      result should matchPattern {
        case SecurityViolation(_) ⇒
      }
    }

    it("doesn't allow access to the Unsafe instance") {
      val code = """
import sun.misc.Unsafe

Unsafe.getUnsafe
"""
      val result: EvalResult[Unit] = evaluator.eval(code).run

      result should matchPattern {
        case SecurityViolation(_) ⇒
      }
    }

    it("doesn't allow access to the sun reflect package") {
      val code = """
import sun.reflect.Reflection
Reflection.getCallerClass(2)
"""
      val result: EvalResult[Unit] = evaluator.eval(code).run

      result should matchPattern {
        case SecurityViolation(_) ⇒
      }
    }

    it("doesn't allow setting a custom policy") {
      val code = """
import java.security._

class MaliciousPolicy extends Policy {
  override def getPermissions(domain: ProtectionDomain): PermissionCollection = {
    new AllPermission().newPermissionCollection()
  }
}

Policy.setPolicy(new MaliciousPolicy())
"""
      val result: EvalResult[Unit] = evaluator.eval(code).run

      result should matchPattern {
        case SecurityViolation(_) ⇒
      }
    }

    it("doesn't allow executing commands") {
      val code = """
Runtime.getRuntime.exec("ls /")
"""
      val result: EvalResult[Unit] = evaluator.eval(code).run

      result should matchPattern {
        case SecurityViolation(_) ⇒
      }
    }

  }
}
