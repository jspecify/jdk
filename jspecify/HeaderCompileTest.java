/*
 * Copyright 2026 The JSpecify Authors.
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

import static java.lang.classfile.Attributes.code;
import static java.lang.classfile.Attributes.signature;
import static java.nio.file.Files.mismatch;

import java.lang.classfile.Annotation;
import java.lang.classfile.Attribute;
import java.lang.classfile.AttributedElement;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.TypeAnnotation;
import java.lang.classfile.attribute.RecordAttribute;
import java.lang.classfile.attribute.RecordComponentInfo;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleParameterAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleTypeAnnotationsAttribute;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Sanity checks for the jar produced by {@code HeaderCompile.java}. */
public final class HeaderCompileTest {
  private static final String NULL_MARKED = "Lorg/jspecify/annotations/NullMarked;";
  private static final String NULLABLE = "Lorg/jspecify/annotations/Nullable;";
  private static final Set<String> JSPECIFY_DESCRIPTORS =
      Set.of(
          NULL_MARKED,
          "Lorg/jspecify/annotations/NullUnmarked;",
          NULLABLE,
          "Lorg/jspecify/annotations/NonNull;");

  public static void main(String[] args) throws Exception {
    if (args.length < 1 || args.length > 2) {
      throw new IllegalArgumentException(
          "Usage: java HeaderCompileTest.java <stubs.jar> [<second-run-stubs.jar>]");
    }
    var jarPath = Path.of(args[0]);
    if (args.length == 2) {
      long diffOffset = mismatch(jarPath, Path.of(args[1]));
      check(diffOffset == -1, "Two runs produced different jars at byte offset " + diffOffset);
    }

    try (var jar = new JarFile(jarPath.toFile())) {
      var models = new LinkedHashMap<String, ClassModel>();

      var enumeration = jar.entries();
      while (enumeration.hasMoreElements()) {
        JarEntry entry = enumeration.nextElement();
        var name = entry.getName();
        check(name.endsWith(".class"), "Unexpected non-class entry: " + name);
        check(!name.startsWith("org/jspecify/"), "Bundled JSpecify annotation class: " + name);

        byte[] bytes;
        try (var in = jar.getInputStream(entry)) {
          bytes = in.readAllBytes();
        }
        var model = ClassFile.of().parse(bytes);
        for (MethodModel method : model.methods()) {
          check(
              method.findAttribute(code()).isEmpty(),
              "Unexpected Code attribute in " + name + "#" + method.methodName());
        }
        check(classHasJSpecifyAnnotation(model), "Unannotated class was not omitted: " + name);
        models.put(name, model);
      }

      // Public @NullMarked class with @Nullable type annotations and generic signatures.
      var arrayDeque = requireClass(models, "java/util/ArrayDeque.class");
      check(hasDeclarationAnnotation(arrayDeque, NULL_MARKED), "ArrayDeque missing @NullMarked");
      check(arrayDeque.findAttribute(signature()).isPresent(), "ArrayDeque missing Signature");
      check(
          arrayDeque.methods().stream()
              .anyMatch(
                  m -> m.methodName().equalsString("peekFirst") && hasTypeAnnotation(m, NULLABLE)),
          "ArrayDeque.peekFirst missing @Nullable");

      // Nested interface with its own annotations is retained, while an unannotated nested class
      // inside a @NullMarked outer class is omitted.
      check(models.containsKey("java/util/Map.class"), "Missing java/util/Map.class");
      check(models.containsKey("java/util/Map$Entry.class"), "Missing java/util/Map$Entry.class");
      check(
          !models.containsKey("java/lang/Thread$State.class"),
          "Unannotated nested class Thread$State should be omitted");

      // Annotated package-private class (and package-private methods).
      var abstractStringBuilder = requireClass(models, "java/lang/AbstractStringBuilder.class");
      check(
          abstractStringBuilder.methods().stream()
              .anyMatch(
                  m ->
                      m.methodName().equalsString("append")
                          && m.methodType()
                              .equalsString("(Ljava/lang/String;)Ljava/lang/AbstractStringBuilder;")
                          && hasTypeAnnotation(m, NULLABLE)),
          "AbstractStringBuilder.append(String) missing @Nullable");
      check(
          abstractStringBuilder.methods().stream()
              .anyMatch(m -> m.methodName().equalsString("compareTo")),
          "AbstractStringBuilder missing package-private compareTo method");

      // Non-java.base modules.
      check(
          models.containsKey("com/sun/tools/attach/VirtualMachine.class"),
          "Missing jdk.attach VirtualMachine.class");
      check(models.containsKey("org/w3c/dom/Node.class"), "Missing org/w3c/dom/Node.class");
    }

    System.out.println("PASS");
  }

  private static ClassModel requireClass(Map<String, ClassModel> models, String entryName) {
    var model = models.get(entryName);
    check(model != null, "Missing expected class entry: " + entryName);
    return model;
  }

  private static boolean classHasJSpecifyAnnotation(ClassModel model) {
    if (elementHasJSpecifyAnnotation(model)) {
      return true;
    }
    for (FieldModel field : model.fields()) {
      if (elementHasJSpecifyAnnotation(field)) {
        return true;
      }
    }
    for (MethodModel method : model.methods()) {
      if (elementHasJSpecifyAnnotation(method)) {
        return true;
      }
    }
    for (Attribute<?> attr : model.attributes()) {
      if (attr instanceof RecordAttribute recordAttr) {
        for (RecordComponentInfo component : recordAttr.components()) {
          if (elementHasJSpecifyAnnotation(component)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private static boolean elementHasJSpecifyAnnotation(AttributedElement element) {
    for (Attribute<?> attr : element.attributes()) {
      switch (attr) {
        case RuntimeVisibleAnnotationsAttribute rva -> {
          for (Annotation ann : rva.annotations()) {
            if (JSPECIFY_DESCRIPTORS.contains(ann.classSymbol().descriptorString())) {
              return true;
            }
          }
        }
        case RuntimeVisibleTypeAnnotationsAttribute rvta -> {
          for (TypeAnnotation ta : rvta.annotations()) {
            if (JSPECIFY_DESCRIPTORS.contains(ta.annotation().classSymbol().descriptorString())) {
              return true;
            }
          }
        }
        case RuntimeVisibleParameterAnnotationsAttribute rvpa -> {
          for (List<Annotation> paramAnns : rvpa.parameterAnnotations()) {
            for (Annotation ann : paramAnns) {
              if (JSPECIFY_DESCRIPTORS.contains(ann.classSymbol().descriptorString())) {
                return true;
              }
            }
          }
        }
        default -> {}
      }
    }
    return false;
  }

  private static boolean hasDeclarationAnnotation(AttributedElement element, String descriptor) {
    for (Attribute<?> attr : element.attributes()) {
      if (attr instanceof RuntimeVisibleAnnotationsAttribute rva) {
        for (Annotation ann : rva.annotations()) {
          if (ann.classSymbol().descriptorString().equals(descriptor)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private static boolean hasTypeAnnotation(AttributedElement element, String descriptor) {
    for (Attribute<?> attr : element.attributes()) {
      if (attr instanceof RuntimeVisibleTypeAnnotationsAttribute rvta) {
        for (TypeAnnotation ta : rvta.annotations()) {
          if (ta.annotation().classSymbol().descriptorString().equals(descriptor)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private static void check(boolean condition, String message) {
    if (!condition) {
      throw new AssertionError(message);
    }
  }

  private HeaderCompileTest() {}
}
