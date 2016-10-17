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

package com.google.turbine.lower;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.google.turbine.binder.Binder;
import com.google.turbine.bytecode.AsmUtils;
import com.google.turbine.parse.Parser;
import com.google.turbine.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.tools.javac.api.JavacTool;
import com.sun.tools.javac.nio.JavacPathFileManager;
import com.sun.tools.javac.util.Context;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.MethodNode;

/** Support for bytecode diffing-integration tests. */
public class IntegrationTestSupport {

  /**
   * Normalizes order of members, attributes, and constant pool entries, to allow diffing bytecode.
   */
  public static Map<String, byte[]> sortMembers(Map<String, byte[]> in) {
    List<ClassNode> classes = toClassNodes(in);
    for (ClassNode n : classes) {
      sortMembersAndAttributes(n);
    }
    return toByteCode(classes);
  }

  /**
   * Canonicalizes bytecode produced by javac to match the expected output of turbine. Includes the
   * same normalization as {@link #sortMembers}, as well as removing everything not produced by the
   * header compiler (code, debug info, etc.)
   */
  @SuppressWarnings("UnnecessaryCast") // external ASM uses raw collection types
  public static Map<String, byte[]> canonicalize(Map<String, byte[]> in) {
    List<ClassNode> classes = toClassNodes(in);

    // drop anonymous classes
    classes = classes.stream().filter(n -> !n.name.matches(".*\\$[0-9]+.*")).collect(toList());

    // collect all inner classes attributes
    Map<String, InnerClassNode> infos = new HashMap<>();
    for (ClassNode n : classes) {
      for (InnerClassNode innerClassNode : (List<InnerClassNode>) n.innerClasses) {
        infos.put(innerClassNode.name, innerClassNode);
      }
    }

    for (ClassNode n : classes) {
      removeImplementation(n);
      removeUnusedInnerClassAttributes(infos, n);
      sortMembersAndAttributes(n);
    }

    return toByteCode(classes);
  }

  private static Map<String, byte[]> toByteCode(List<ClassNode> classes) {
    Map<String, byte[]> out = new LinkedHashMap<>();
    for (ClassNode n : classes) {
      ClassWriter cw = new ClassWriter(0);
      n.accept(cw);
      out.put(n.name, cw.toByteArray());
    }
    return out;
  }

  private static List<ClassNode> toClassNodes(Map<String, byte[]> in) {
    List<ClassNode> classes = new ArrayList<>();
    for (byte[] f : in.values()) {
      ClassNode n = new ClassNode();
      new ClassReader(f).accept(n, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

      classes.add(n);
    }
    return classes;
  }

  /** Remove elements that are omitted by turbine, e.g. private and synthetic members. */
  @SuppressWarnings("UnnecessaryCast") // external ASM uses raw collection types
  private static void removeImplementation(ClassNode n) {
    n.innerClasses =
        ((List<InnerClassNode>) n.innerClasses)
            .stream()
            .filter(x -> (x.access & (Opcodes.ACC_SYNTHETIC | Opcodes.ACC_PRIVATE)) == 0)
            .collect(toList());

    n.methods =
        ((List<MethodNode>) n.methods)
            .stream()
            .filter(x -> (x.access & Opcodes.ACC_SYNTHETIC) == 0)
            .filter(x -> (x.access & Opcodes.ACC_PRIVATE) == 0 || x.name.equals("<init>"))
            .filter(x -> !x.name.equals("<clinit>"))
            .collect(toList());

    n.fields =
        ((List<FieldNode>) n.fields)
            .stream()
            .filter(x -> (x.access & (Opcodes.ACC_SYNTHETIC | Opcodes.ACC_PRIVATE)) == 0)
            .collect(toList());
  }

  /** Remove synthetic members, and apply a standard sort order. */
  private static void sortMembersAndAttributes(ClassNode n) {
    Collections.<InnerClassNode>sort(
        n.innerClasses,
        Comparator.comparing((InnerClassNode x) -> x.name)
            .thenComparing(x -> x.outerName)
            .thenComparing(x -> x.innerName)
            .thenComparing(x -> x.access));

    Collections.<MethodNode>sort(
        n.methods,
        Comparator.comparing((MethodNode x) -> x.name)
            .thenComparing(x -> x.desc)
            .thenComparing(x -> x.signature)
            .thenComparing(x -> x.access));

    Collections.<FieldNode>sort(
        n.fields,
        Comparator.comparing((FieldNode x) -> x.name)
            .thenComparing(x -> x.desc)
            .thenComparing(x -> x.signature)
            .thenComparing(x -> x.access));
  }

  /**
   * Remove InnerClass attributes that are no longer needed after member pruning. This requires
   * visiting all descriptors and signatures in the bytecode to find references to inner classes.
   */
  @SuppressWarnings("UnnecessaryCast") // external ASM uses raw collection types
  private static void removeUnusedInnerClassAttributes(
      Map<String, InnerClassNode> infos, ClassNode n) {
    Set<String> types = new HashSet<>();
    {
      types.add(n.name);
      collectTypesFromSignature(types, n.signature);
      if (n.superName != null) {
        types.add(n.superName);
      }
      types.addAll((List<String>) n.interfaces);
    }
    for (MethodNode m : (List<MethodNode>) n.methods) {
      collectTypesFromSignature(types, m.desc);
      collectTypesFromSignature(types, m.signature);
      types.addAll((List<String>) m.exceptions);
    }
    for (FieldNode f : (List<FieldNode>) n.fields) {
      collectTypesFromSignature(types, f.desc);
      collectTypesFromSignature(types, f.signature);
    }

    List<InnerClassNode> used = new ArrayList<>();
    for (InnerClassNode i : (List<InnerClassNode>) n.innerClasses) {
      if (i.outerName != null && i.outerName.equals(n.name)) {
        // keep InnerClass attributes for any member classes
        used.add(i);
      } else if (types.contains(i.name)) {
        // otherwise, keep InnerClass attributes that were referenced in class or member signatures
        addInnerChain(infos, used, i.name);
      }
    }
    addInnerChain(infos, used, n.name);
    n.innerClasses = used;
  }

  /**
   * For each preserved InnerClass attribute, keep any information about transitive enclosing
   * classes of the inner class.
   */
  private static void addInnerChain(
      Map<String, InnerClassNode> infos, List<InnerClassNode> used, String i) {
    while (infos.containsKey(i)) {
      InnerClassNode info = infos.get(i);
      used.add(info);
      i = info.outerName;
    }
  }

  /** Save all class types referenced in a signature. */
  private static void collectTypesFromSignature(Set<String> classes, String signature) {
    if (signature == null) {
      return;
    }
    // signatures for qualified generic class types are visited as name and type argument pieces,
    // so stitch them back together into a binary class name
    final Set<String> classes1 = classes;
    new SignatureReader(signature)
        .accept(
            new SignatureVisitor(Opcodes.ASM5) {
              private final Set<String> classes = classes1;
              // class signatures may contain type arguments that contain class signatures
              Deque<List<String>> pieces = new ArrayDeque<>();

              @Override
              public void visitInnerClassType(String name) {
                pieces.peek().add(name);
              }

              @Override
              public void visitClassType(String name) {
                pieces.push(new ArrayList<>());
                pieces.peek().add(name);
              }

              @Override
              public void visitEnd() {
                classes.add(Joiner.on('$').join(pieces.pop()));
                super.visitEnd();
              }
            });
  }

  static Map<String, byte[]> runTurbine(
      Map<String, String> input, ImmutableList<Path> classpath, Iterable<Path> bootclasspath)
      throws IOException {
    List<Tree.CompUnit> units = input.values().stream().map(Parser::parse).collect(toList());

    Binder.BindingResult bound = Binder.bind(units, classpath, bootclasspath);
    return Lower.lowerAll(bound.units(), bound.classPathEnv());
  }

  static Map<String, byte[]> runJavac(
      Map<String, String> sources, Iterable<Path> classpath, Iterable<? extends Path> bootclasspath)
      throws Exception {

    FileSystem fs = Jimfs.newFileSystem(Configuration.unix());

    Path srcs = fs.getPath("srcs");
    Path out = fs.getPath("out");

    Files.createDirectories(out);

    ArrayList<Path> inputs = new ArrayList<>();
    for (Map.Entry<String, String> entry : sources.entrySet()) {
      Path path = srcs.resolve(entry.getKey());
      if (path.getParent() != null) {
        Files.createDirectories(path.getParent());
      }
      Files.write(path, entry.getValue().getBytes(UTF_8));
      inputs.add(path);
    }

    JavacTool compiler = JavacTool.create();
    DiagnosticCollector<JavaFileObject> collector = new DiagnosticCollector<>();
    JavacPathFileManager fileManager = new JavacPathFileManager(new Context(), true, UTF_8);
    fileManager.setLocation(StandardLocation.PLATFORM_CLASS_PATH, bootclasspath);
    fileManager.setLocation(StandardLocation.CLASS_OUTPUT, ImmutableList.of(out));
    fileManager.setLocation(StandardLocation.CLASS_PATH, classpath);

    JavacTask task =
        compiler.getTask(
            new PrintWriter(System.err, true),
            fileManager,
            collector,
            ImmutableList.of(),
            ImmutableList.of(),
            fileManager.getJavaFileObjectsFromPaths(inputs));

    assertThat(task.call()).named(collector.getDiagnostics().toString()).isTrue();

    List<Path> classes = new ArrayList<>();
    Files.walkFileTree(
        out,
        new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult visitFile(Path path, BasicFileAttributes attrs)
              throws IOException {
            if (path.getFileName().toString().endsWith(".class")) {
              classes.add(path);
            }
            return FileVisitResult.CONTINUE;
          }
        });
    Map<String, byte[]> result = new LinkedHashMap<>();
    for (Path path : classes) {
      String r = out.relativize(path).toString();
      result.put(r.substring(0, r.length() - ".class".length()), Files.readAllBytes(path));
    }
    return result;
  }

  /** Normalizes and stringifies a collection of class files. */
  public static String dump(Map<String, byte[]> compiled) throws Exception {
    compiled = canonicalize(compiled);
    StringBuilder sb = new StringBuilder();
    List<String> keys = new ArrayList<>(compiled.keySet());
    Collections.sort(keys);
    for (String key : keys) {
      String na = key;
      if (na.startsWith("/")) {
        na = na.substring(1);
      }
      sb.append(String.format("=== %s ===\n", na));
      sb.append(AsmUtils.textify(compiled.get(key)));
    }
    return sb.toString();
  }

  static class TestInput {

    final Map<String, String> sources;
    final Map<String, String> classes;

    public TestInput(Map<String, String> sources, Map<String, String> classes) {
      this.sources = sources;
      this.classes = classes;
    }

    static TestInput parse(String text) {
      Map<String, String> sources = new LinkedHashMap<>();
      Map<String, String> classes = new LinkedHashMap<>();
      String className = null;
      String sourceName = null;
      List<String> lines = new ArrayList<>();
      for (String line : Splitter.on('\n').split(text)) {
        if (line.startsWith("===")) {
          if (sourceName != null) {
            sources.put(sourceName, Joiner.on('\n').join(lines) + "\n");
          }
          if (className != null) {
            classes.put(className, Joiner.on('\n').join(lines) + "\n");
          }
          lines.clear();
          sourceName = line.substring(3, line.length() - 3).trim();
          className = null;
        } else if (line.startsWith("%%%")) {
          if (className != null) {
            classes.put(className, Joiner.on('\n').join(lines) + "\n");
          }
          if (sourceName != null) {
            sources.put(sourceName, Joiner.on('\n').join(lines) + "\n");
          }
          className = line.substring(3, line.length() - 3).trim();
          lines.clear();
          sourceName = null;
        } else {
          lines.add(line);
        }
      }
      if (sourceName != null) {
        sources.put(sourceName, Joiner.on('\n').join(lines) + "\n");
      }
      if (className != null) {
        classes.put(className, Joiner.on('\n').join(lines) + "\n");
      }
      lines.clear();
      return new TestInput(sources, classes);
    }
  }
}