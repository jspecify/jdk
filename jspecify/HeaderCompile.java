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

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.createTempFile;
import static java.nio.file.Files.deleteIfExists;
import static java.nio.file.Files.isDirectory;
import static java.nio.file.Files.list;
import static java.nio.file.Files.newOutputStream;
import static java.nio.file.Files.walk;
import static java.nio.file.Files.write;
import static java.util.zip.Deflater.BEST_COMPRESSION;
import static java.util.zip.ZipEntry.DEFLATED;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Header-compiles the JSpecify-annotated JDK APIs into a jar of stub {@code .class} files using
 * Turbine, omitting classes that contain no nullness annotations.
 *
 * <p>Requires {@code make gensrc} to have been run first so that generated sources under {@code
 * build/<conf>/support/gensrc} are available.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * java jspecify/HeaderCompile.java \
 *   --turbine path/to/turbine-all-deps.jar \
 *   --output path/to/jspecify-jdk-stubs.jar \
 *   [--jdk-src path/to/jdk] \
 *   [--gensrc path/to/build/<conf>/support/gensrc]
 * }</pre>
 */
public final class HeaderCompile {
  private static final String JSPECIFY_PACKAGE_PREFIX = "org/jspecify/annotations/";
  private static final String JSPECIFY_DESCRIPTOR_PREFIX = "Lorg/jspecify/annotations/";

  /** Matches Turbine's normalized zip entry timestamp so builds are byte-for-byte reproducible. */
  private static final LocalDateTime FIXED_TIMESTAMP = LocalDateTime.of(2010, 1, 1, 0, 0, 0);

  public static void main(String[] args) throws Exception {
    Path turbineJar = null;
    Path outputJar = null;
    var jdkSrc = Path.of(".");
    Path gensrcDir = null;

    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--turbine" -> turbineJar = Path.of(requireArg(args, ++i, "--turbine"));
        case "--output" -> outputJar = Path.of(requireArg(args, ++i, "--output"));
        case "--jdk-src" -> jdkSrc = Path.of(requireArg(args, ++i, "--jdk-src"));
        case "--gensrc" -> gensrcDir = Path.of(requireArg(args, ++i, "--gensrc"));
        default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
      }
    }
    if (turbineJar == null || outputJar == null) {
      throw new IllegalArgumentException(
          "Usage: java HeaderCompile.java --turbine <turbine.jar> --output <out.jar>"
              + " [--jdk-src <dir>] [--gensrc <dir>]");
    }
    if (gensrcDir == null) {
      gensrcDir = findGensrcDir(jdkSrc);
    }

    var sources = collectSources(jdkSrc.resolve("src"), gensrcDir);
    var rawJar = createTempFile("jspecify-jdk-raw-", ".jar");
    var paramsFile = createTempFile("jspecify-jdk-turbine-", ".params");
    try {
      runTurbine(turbineJar, sources, rawJar, paramsFile);
      filterJar(rawJar, outputJar.toAbsolutePath());
    } finally {
      deleteIfExists(rawJar);
      deleteIfExists(paramsFile);
    }
  }

  private static String requireArg(String[] args, int index, String flag) {
    if (index >= args.length) {
      throw new IllegalArgumentException("Missing value for " + flag);
    }
    return args[index];
  }

  private static Path findGensrcDir(Path jdkSrc) throws IOException {
    var buildDir = jdkSrc.resolve("build");
    if (isDirectory(buildDir)) {
      try (var configs = list(buildDir)) {
        var candidates =
            configs
                .sorted()
                .map(c -> c.resolve("support/gensrc"))
                .filter(d -> isDirectory(d))
                .toList();
        if (candidates.size() == 1) {
          return candidates.getFirst();
        }
        if (candidates.size() > 1) {
          throw new IllegalStateException(
              "Multiple build configurations found (" + candidates + "); pass --gensrc explicitly");
        }
      }
    }
    throw new IllegalStateException(
        "No support/gensrc directory found under "
            + buildDir
            + "; run 'bash ./configure && make gensrc' first or pass --gensrc");
  }

  private static List<Path> collectSources(Path srcDir, Path gensrcDir) throws IOException {
    var sources = new ArrayList<Path>();
    try (var moduleDirs = list(srcDir)) {
      for (var moduleDir : moduleDirs.sorted().toList()) {
        if (!isDirectory(moduleDir.resolve("share/classes"))) {
          continue;
        }
        var seenInModule = new HashSet<Path>();
        for (var root :
            List.of(
                moduleDir.resolve("linux/classes"),
                moduleDir.resolve("unix/classes"),
                moduleDir.resolve("share/classes"),
                gensrcDir.resolve(moduleDir.getFileName()))) {
          if (!isDirectory(root)) {
            continue;
          }
          try (var files = walk(root)) {
            for (var file : files.sorted().toList()) {
              var rel = root.relativize(file);
              if (file.toString().endsWith(".java")
                  && !rel.getFileName().toString().equals("module-info.java")
                  && seenInModule.add(rel)) {
                sources.add(file);
              }
            }
          }
        }
      }
    }
    return sources;
  }

  private static void runTurbine(Path turbineJar, List<Path> sources, Path rawJar, Path paramsFile)
      throws IOException, InterruptedException {
    var params = new ArrayList<String>(sources.size() + 8);
    params.add("--output");
    params.add(rawJar.toString());
    // Target Java 21 bytecode so PermittedSubclasses and NestHost/NestMembers are emitted while
    // keeping the resulting class files readable by tools running on JDK 21+.
    params.add("--javacopts");
    params.add("--target");
    params.add("21");
    params.add("--");
    params.add("--sources");
    for (var source : sources) {
      params.add(source.toString());
    }
    write(paramsFile, params, UTF_8);

    var javaBin = ProcessHandle.current().info().command().orElse("java");
    var exitCode =
        new ProcessBuilder(
                javaBin,
                "-cp",
                turbineJar.toString(),
                "com.google.turbine.main.Main",
                "@" + paramsFile)
            .inheritIO()
            .start()
            .waitFor();
    if (exitCode != 0) {
      throw new RuntimeException("Turbine exited with status " + exitCode);
    }
  }

  private static void filterJar(Path rawJar, Path outputJar) throws IOException {
    var parentDir = outputJar.getParent();
    if (parentDir != null) {
      createDirectories(parentDir);
    }
    int keptCount = 0;
    try (var jar = new JarFile(rawJar.toFile());
        var fileOut = newOutputStream(outputJar);
        var bufferedOut = new BufferedOutputStream(fileOut);
        var zipOut = new ZipOutputStream(bufferedOut)) {
      zipOut.setMethod(DEFLATED);
      zipOut.setLevel(BEST_COMPRESSION);
      var entries = jar.entries();
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        var name = entry.getName();
        if (!name.endsWith(".class") || name.startsWith(JSPECIFY_PACKAGE_PREFIX)) {
          continue;
        }
        byte[] bytes;
        try (var in = jar.getInputStream(entry)) {
          bytes = in.readAllBytes();
        }
        if (!new String(bytes, ISO_8859_1).contains(JSPECIFY_DESCRIPTOR_PREFIX)) {
          continue;
        }
        var outEntry = new ZipEntry(name);
        outEntry.setTimeLocal(FIXED_TIMESTAMP);
        zipOut.putNextEntry(outEntry);
        zipOut.write(bytes);
        zipOut.closeEntry();
        keptCount++;
      }
    }
    if (keptCount == 0) {
      deleteIfExists(outputJar);
      throw new IllegalStateException("Filtered jar contained no annotated classes");
    }
    System.out.printf("Wrote %d annotated class files to %s%n", keptCount, outputJar);
  }

  private HeaderCompile() {}
}
