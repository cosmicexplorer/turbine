/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.turbine.binder;

import com.google.common.base.Function;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableMap;
import com.google.turbine.binder.BytecodeBoundClassProvider;
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

/** Sets up an environment for symbols on the classpath. */
public class ClassPathBinder {

  /**
   * The prefix for repackaged transitive dependencies; see {@link
   * com.google.turbine.deps.Transitive}.
   */
  public static final String TRANSITIVE_PREFIX = "META-INF/TRANSITIVE/";

  /** Creates an environment containing symbols in the given classpath. */
  public static ClassPath bindClasspath(Collection<Path> paths) throws IOException {
    return bindClasspathMutable(paths);
  }

  public static MutableClassPath bindClasspathMutable(Collection<Path> paths) throws IOException {
    // TODO(cushon): this is going to require an env eventually,
    // e.g. to look up type parameters in enclosing declarations
    Map<ClassSymbol, BytecodeBoundClassProvider> transitive = new LinkedHashMap<>();
    Map<ClassSymbol, BytecodeBoundClassProvider> map = new HashMap<>();
    Map<ModuleSymbol, ModuleInfo> modules = new HashMap<>();
    Env<ClassSymbol, BytecodeBoundClassProvider> benv =
        new Env<ClassSymbol, BytecodeBoundClassProvider>() {
          @Override
          public BytecodeBoundClassProvider get(ClassSymbol sym) {
            return map.get(sym);
          }
        };
    for (Path path : paths) {
      try {
        bindJar(path, map, modules, benv, transitive);
      } catch (IOException e) {
        throw new IOException("error reading " + path, e);
      }
    }
    for (Map.Entry<ClassSymbol, BytecodeBoundClassProvider> entry : transitive.entrySet()) {
      ClassSymbol symbol = entry.getKey();
      map.putIfAbsent(symbol, entry.getValue());
    }
    return new MutableClassPath(ImmutableMap.copyOf(map), ImmutableMap.copyOf(modules));
  }

  private static void bindJar(
      Path path,
      Map<ClassSymbol, BytecodeBoundClassProvider> env,
      Map<ModuleSymbol, ModuleInfo> modules,
      Env<ClassSymbol, BytecodeBoundClassProvider> benv,
      Map<ClassSymbol, BytecodeBoundClassProvider> transitive)
      throws IOException {
    // TODO(cushon): don't leak file descriptors
    for (Zip.Entry ze : new Zip.ZipIterable(path)) {
      String name = ze.name();
      if (!name.endsWith(".class")) {
        continue;
      }
      if (name.startsWith(TRANSITIVE_PREFIX)) {
        ClassSymbol sym =
            new ClassSymbol(
                name.substring(TRANSITIVE_PREFIX.length(), name.length() - ".class".length()));
        transitive.computeIfAbsent(
            sym,
            new Function<ClassSymbol, BytecodeBoundClassProvider>() {
              @Override
              public BytecodeBoundClassProvider apply(ClassSymbol sym) {
                return new BytecodeBoundClass(sym, toByteArrayOrDie(ze), benv, path.toString());
              }
            });
        continue;
      }
      if (name.substring(name.lastIndexOf('/') + 1).equals("module-info.class")) {
        ModuleInfo moduleInfo =
            BytecodeBinder.bindModuleInfo(path.toString(), toByteArrayOrDie(ze));
        modules.put(new ModuleSymbol(moduleInfo.name()), moduleInfo);
        continue;
      }
      ClassSymbol sym = new ClassSymbol(name.substring(0, name.length() - ".class".length()));
      env.putIfAbsent(
          sym, new BytecodeBoundClass(sym, toByteArrayOrDie(ze), benv, path.toString()));
    }
  }

  private static Supplier<byte[]> toByteArrayOrDie(Zip.Entry ze) {
    return Suppliers.memoize(
        new Supplier<byte[]>() {
          @Override
          public byte[] get() {
            return ze.data();
          }
        });
  }
}
