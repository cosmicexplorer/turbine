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
import scala.meta.internal.semanticdb.*;
import semanticdb.Reader;

public class RscClassPathBinder {
  public static MutableClassPath bindClasspathFromExistingAndRscOutput(
    MutableClassPath existing,
    Collection<String> rscOutput
  ) throws IOException {
    // Extract the existing classpath.
    ImmutableMap<ClassSymbol, BytecodeBoundClassProvider> envMap = existing.envMap;
    // NB: We do NOT touch the module env, as it is a Java 9 feature!!!
    ImmutableMap<ModuleSymbol, ModuleInfo> moduleEnvMap = existing.moduleEnvMap;

    Reader semanticDbReader = new Reader();

    ImmutableMap.Builder<ClassSymbol, BytecodeBoundClassProvider> envBuilder = new ImmutableMap.Builder<ClassSymbol, BytecodeBoundClassProvider>();
    envBuilder.putAll(envMap);

    for (String path : rscOutput) {
      Reader.Index index = semanticDbReader.load(path);
      for (Map.Entry<String, SymbolInformation> infoEntry : index.infos.entrySet()) {
        SymbolInformation.Kind kind = infoEntry.getValue().kind();
        if (kind.isClass() || kind.isTrait() || kind.isObject() || kind.isInterface()) {
          ClassSymbol sym = new ClassSymbol(infoEntry.getValue().displayName());
          RscBoundClass boundClass = new RscBoundClass(index, infoEntry.getValue());
          System.out.println(infoEntry.getKey());
          System.out.println(infoEntry.getValue());
          System.out.println("====");
          envBuilder.put(sym, boundClass);
        }
      }
    }

    return new MutableClassPath(envBuilder.build(), moduleEnvMap);
  }
}
