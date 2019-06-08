package com.google.turbine.binder;

import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import scala.collection.JavaConverters;
import scala.meta.internal.semanticdb.Access;
import scala.meta.internal.semanticdb.ClassSignature;
import scala.meta.internal.semanticdb.PrivateAccess;
import scala.meta.internal.semanticdb.ProtectedAccess;
import scala.meta.internal.semanticdb.PublicAccess;
import scala.meta.internal.semanticdb.Scala;
import scala.meta.internal.semanticdb.SymbolInformation;
import scala.meta.internal.semanticdb.TypeRef;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.turbine.binder.bound.AnnotationMetadata;
import com.google.turbine.binder.sym.ClassSymbol;
import com.google.turbine.binder.sym.TyVarSymbol;
import com.google.turbine.bytecode.ClassFile;
import com.google.turbine.model.TurbineTyKind;
import com.google.turbine.type.AnnoInfo;
import com.google.turbine.type.Type;
import com.google.turbine.type.Type.ClassTy;

import org.checkerframework.checker.nullness.qual.Nullable;
import scala.meta.internal.semanticdb.*;

import semanticdb.Reader;

public class RscBoundClass implements BytecodeBoundClassProvider {

  private Reader.Index index;
  private SymbolInformation info;

  public RscBoundClass(Reader.Index index, SymbolInformation info) {
    this.index = index;
    this.info = info;
  }

  private String noEmpty(String s) {
    if (s.startsWith("_empty_/")) return s.substring("_empty_/".length());
    return s;
  }

  @Override
  public TurbineTyKind kind() {
    SymbolInformation.Kind kind = info.kind();
    if (kind.isClass() || kind.isObject()) return TurbineTyKind.CLASS;
    if (kind.isTrait() || kind.isInterface()) return TurbineTyKind.INTERFACE;
    System.out.println("[error] Bad kind: " + info.kind().toString());
    return null;
  }

  @Nullable
  @Override
  public ClassSymbol owner() {
    String owner = new Scala.ScalaSymbolOps(info.symbol()).owner();
    String noe = noEmpty(owner);
    if (noe.isEmpty()) return null;
    return new ClassSymbol(noe);
  }

  // TODO
  @Override
  public ImmutableMap<String, ClassSymbol> children() {
    return ImmutableMap.of();
  }

  // TODO https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html
  @Override
  public int access() {
    int acc = 0;
    Access access = info.access();
    int properties = info.properties();

    if (access instanceof PublicAccess) acc |= 0x1;
    if (access instanceof PrivateAccess) acc |= 0x2;
    if (access instanceof ProtectedAccess) acc |= 0x4;
//    if ((properties & SymbolInformation.Property.STATIC$.MODULE$.value()) > 0) acc |= 0x8;
//    if ((properties & SymbolInformation.Property.FINAL$.MODULE$.value()) > 0) acc |= 0x10;
    // TODO Volatile, Transient, Synthethic, Enum?

    return acc;
  }

  // TODO wat
  @Override
  public ImmutableMap<String, TyVarSymbol> typeParameters() {
    return ImmutableMap.of();
  }

  @Override
  public ClassSymbol superclass() {
    ClassSignature sig = ((ClassSignature) info.signature());

    Collection<scala.meta.internal.semanticdb.Type> parents =
        JavaConverters.asJavaCollection(sig.parents());

    for (scala.meta.internal.semanticdb.Type t : parents) {
      TypeRef parent = (TypeRef) t;
      SymbolInformation pinfo = index.infos.get(parent.symbol());
      if (pinfo.kind().isClass()) {
        return new ClassSymbol(noEmpty(pinfo.symbol()));
      }
    }

    return ClassSymbol.OBJECT;
  }

  @Override
  public ImmutableList<ClassSymbol> interfaces() {
    List<ClassSymbol> res = new ArrayList<>();
    ClassSignature sig = ((ClassSignature) info.signature());

    Collection<scala.meta.internal.semanticdb.Type> parents =
        JavaConverters.asJavaCollection(sig.parents());

    for (scala.meta.internal.semanticdb.Type t : parents) {
      TypeRef parent = (TypeRef) t;
      SymbolInformation pinfo = index.infos.get(parent.symbol());
      if (pinfo.kind().isTrait() || pinfo.kind().isInterface()) {
        res.add(new ClassSymbol(noEmpty(pinfo.symbol())));
      }
    }

    return res.stream().collect(ImmutableList.toImmutableList());
  }

  @Override
  public ClassTy superClassType() {
    return ClassTy.OBJECT;
  }

  @Override
  public ImmutableList<Type> interfaceTypes() {
    return ImmutableList.of();
  }

  @Override
  public ImmutableMap<TyVarSymbol, TyVarInfo> typeParameterTypes() {
    return ImmutableMap.of();
  }

  @Override
  public ImmutableList<FieldInfo> fields() {
    return ImmutableList.of();
  }

  @Override
  public ImmutableList<MethodInfo> methods() {
    return ImmutableList.of();
  }

  @Override
  public AnnotationMetadata annotationMetadata() {
    return new AnnotationMetadata(
        RetentionPolicy.RUNTIME,
        ImmutableSet.of(),
        ClassSymbol.OBJECT
    );
  }

  @Override
  public ImmutableList<AnnoInfo> annotations() {
    return ImmutableList.of();
  }

  @Override
  public String jarFile() {
    return "LOL.jar";
  }

  @Override
  public ClassFile classFile() {
    return new ClassFile(
        1,
        info.displayName(),
        "signature",
        superclass().simpleName(),
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList(),
        ImmutableList.of(),
        null);
  }

}
