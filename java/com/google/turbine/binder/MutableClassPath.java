package com.google.turbine.binder;

import com.google.common.base.Function;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableMap;
import com.google.turbine.binder.BytecodeBoundClassProvider;
import com.google.turbine.binder.ClassPath;
import com.google.turbine.binder.bound.ModuleInfo;
import com.google.turbine.binder.bytecode.BytecodeBinder;
import com.google.turbine.binder.bytecode.BytecodeBoundClass;
import com.google.turbine.binder.env.Env;
import com.google.turbine.binder.env.SimpleEnv;
import com.google.turbine.binder.lookup.SimpleTopLevelIndex;
import com.google.turbine.binder.lookup.TopLevelIndex;
import com.google.turbine.binder.sym.ClassSymbol;
import com.google.turbine.binder.sym.ModuleSymbol;
import com.google.turbine.zip.Zip;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class MutableClassPath implements ClassPath {

  public final ImmutableMap<ClassSymbol, BytecodeBoundClassProvider> envMap;
  public final ImmutableMap<ModuleSymbol, ModuleInfo> moduleEnvMap;
  private final Env<ClassSymbol, BytecodeBoundClassProvider> env;
  private final Env<ModuleSymbol, ModuleInfo> moduleEnv;
  private final TopLevelIndex index;

  public MutableClassPath(
    ImmutableMap<ClassSymbol, BytecodeBoundClassProvider> envMap,
    ImmutableMap<ModuleSymbol, ModuleInfo> moduleEnvMap
  ) {
    this.envMap = envMap;
    this.moduleEnvMap = moduleEnvMap;
    this.env = new SimpleEnv<ClassSymbol, BytecodeBoundClassProvider>(this.envMap);
    this.moduleEnv = new SimpleEnv<ModuleSymbol, ModuleInfo>(this.moduleEnvMap);
    this.index = SimpleTopLevelIndex.of(this.envMap.keySet());
  }

  @Override
  public Env<ClassSymbol, BytecodeBoundClassProvider> env() {
    return this.env;
  }

  @Override
  public Env<ModuleSymbol, ModuleInfo> moduleEnv() {
    return this.moduleEnv;
  }

  @Override
  public TopLevelIndex index() {
    return this.index;
  }
}
