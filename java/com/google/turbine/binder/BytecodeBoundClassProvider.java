package com.google.turbine.binder;

import com.google.turbine.binder.bound.BoundClass;
import com.google.turbine.binder.bound.HeaderBoundClass;
import com.google.turbine.binder.bound.TypeBoundClass;
import com.google.turbine.bytecode.ClassFile;

public interface BytecodeBoundClassProvider extends BoundClass, HeaderBoundClass, TypeBoundClass {
  /** The jar file the symbol was loaded from. */
  public String jarFile();

  /** The class file the symbol was loaded from. */
  public ClassFile classFile();
}
