package com.google.turbine.binder;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.turbine.binder.BytecodeBoundClassProvider;
import com.google.turbine.binder.bound.AnnotationMetadata;
import com.google.turbine.binder.bound.BoundClass;
import com.google.turbine.binder.bound.HeaderBoundClass;
import com.google.turbine.binder.bound.TypeBoundClass;
import com.google.turbine.binder.env.Env;
import com.google.turbine.binder.sym.ClassSymbol;
import com.google.turbine.binder.sym.FieldSymbol;
import com.google.turbine.binder.sym.MethodSymbol;
import com.google.turbine.binder.sym.TyVarSymbol;
import com.google.turbine.bytecode.ClassFile;
import com.google.turbine.bytecode.ClassFile.AnnotationInfo;
import com.google.turbine.bytecode.ClassFile.AnnotationInfo.ElementValue;
import com.google.turbine.bytecode.ClassFile.AnnotationInfo.ElementValue.ArrayValue;
import com.google.turbine.bytecode.ClassFile.AnnotationInfo.ElementValue.ConstTurbineClassValue;
import com.google.turbine.bytecode.ClassFile.AnnotationInfo.ElementValue.EnumConstValue;
import com.google.turbine.bytecode.ClassFile.AnnotationInfo.ElementValue.Kind;
import com.google.turbine.bytecode.ClassFile.MethodInfo.ParameterInfo;
import com.google.turbine.bytecode.ClassReader;
import com.google.turbine.bytecode.sig.Sig;
import com.google.turbine.bytecode.sig.Sig.ClassSig;
import com.google.turbine.bytecode.sig.Sig.ClassTySig;
import com.google.turbine.bytecode.sig.Sig.TySig;
import com.google.turbine.bytecode.sig.SigParser;
import com.google.turbine.model.Const;
import com.google.turbine.model.TurbineElementType;
import com.google.turbine.model.TurbineFlag;
import com.google.turbine.model.TurbineTyKind;
import com.google.turbine.type.AnnoInfo;
import com.google.turbine.type.Type;
import com.google.turbine.type.Type.ClassTy;
import com.google.turbine.type.Type.IntersectionTy;
import java.lang.annotation.RetentionPolicy;
import java.util.Map;
import java.util.function.Function;
import org.checkerframework.checker.nullness.qual.Nullable;

public class RscBoundClass implements BytecodeBoundClassProvider {

  @Override
  public TurbineTyKind kind() {
    return TurbineTyKind.CLASS;
  }

  @Nullable
  @Override
  public ClassSymbol owner() {
    return ClassSymbol.OBJECT;
  }

  @Override
  public ImmutableMap<String, ClassSymbol> children() {
    return new ImmutableMap<?, ?>();
  }

  @Override
  public int access() {
    return 1;
  }

  @Override
  public ImmutableMap<String, TyVarSymbol> typeParameters() {
    return new ImmutableMap<?, ?>();
  }

  @Override
  public ClassSymbol superclass() {
    return ClassSymbol.OBJECT;
  }

  @Override
  public ImmutableList<ClassSymbol> interfaces() {
    return new ImmutableList<?>();
  }

  @Override
  public ClassTy superClassType() {
    return ClassTy.OBJECT;
  }

  @Override
  public ImmutableList<Type> interfaceTypes() {
    return new ImmutableList<?>();
  }

  @Override
  public ImmutableMap<TyVarSymbol, TyVarInfo> typeParameterTypes() {
    return new ImmutableMap<?, ?>();
  }

  @Override
  public ImmutableList<FieldInfo> fields() {
    return new ImmutableList<?>();
  }

  @Override
  public ImmutableList<MethodInfo> methods() {
    return new ImmutableList<?>();
  }

  @Override
  public AnnotationMetadata annotationMetadata() {
    return AnnotationMetadata.DEFAULT_TARGETS;
  }

  @Override
  public ImmutableList<AnnoInfo> annotations() {
    return new ImmutableList<?>();
  }

  @Override
  public String jarFile() {
    return "LOL.jar";
  }

  @Override
  public ClassFile classFile() {
    return new ClassFile(
      1,
      "name",
      "signature",
      "superClass",
      new List<?>(),
      new List<?>(),
      new List<?>(),
      new List<?>(),
      new List<?>(),
      new ImmutableList<?>(),
      null);
  }

}
