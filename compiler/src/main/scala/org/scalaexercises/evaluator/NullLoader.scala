/*
 * scala-exercises - evaluator-compiler
 * Copyright (C) 2015-2016 47 Degrees, LLC. <http://www.47deg.com>
 */

package org.scalaexercises.evaluator

object NullLoader extends ClassLoader {
  override final def loadClass(className: String, resolve: Boolean): Class[_] = {
    if (className.startsWith("java.")) super.loadClass(className, resolve)
    else
      throw new ClassNotFoundException("No classes can be loaded from the null loader")
  }
}
