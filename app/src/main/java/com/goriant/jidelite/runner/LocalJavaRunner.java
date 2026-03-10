package com.goriant.jidelite.runner;

import android.content.Context;
import android.os.Build;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.OutputMode;
import com.goriant.jidelite.model.RunResult;

import org.codehaus.commons.compiler.CompileException;
import org.codehaus.commons.compiler.util.resource.PathResourceFinder;
import org.codehaus.janino.ClassLoaderIClassLoader;
import org.codehaus.janino.Compiler;
import org.codehaus.janino.IClassLoader;
import org.codehaus.janino.ResourceFinderIClassLoader;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dalvik.system.DexClassLoader;
import dalvik.system.InMemoryDexClassLoader;

public class LocalJavaRunner implements CodeRunner {

    private static final Pattern PACKAGE_PATTERN =
            Pattern.compile("(?m)^\\s*package\\s+([A-Za-z_$][A-Za-z0-9_$.]*)\\s*;");
    private static final Pattern MAIN_METHOD_PATTERN = Pattern.compile(
            "(?s)\\bpublic\\s+static\\s+void\\s+main\\s*\\(\\s*String\\s*(?:\\[\\s*]|\\.\\.\\.)\\s*[A-Za-z_$][A-Za-z0-9_$]*\\s*\\)"
    );
    private static final Pattern IMPORT_PATTERN = Pattern.compile(
            "(?m)^\\s*import\\s+(?:static\\s+)?([A-Za-z_$][A-Za-z0-9_$.]+)\\s*;"
    );
    private static final int MIN_API_FOR_DEX = 26;
    private static final String RESOLVE_COMMAND = "$ mvn dependency:resolve";

    private final Context appContext;
    private final File workspaceDirectory;
    private final File runnerDirectory;
    private final ProjectDependencyResolver dependencyResolver;

    public LocalJavaRunner(Context context, File workspaceDirectory) {
        this(context, workspaceDirectory, createDefaultResolver(context));
    }

    LocalJavaRunner(Context context, File workspaceDirectory, ProjectDependencyResolver dependencyResolver) {
        this.appContext = context.getApplicationContext();
        this.workspaceDirectory = workspaceDirectory;
        this.runnerDirectory = new File(appContext.getCacheDir(), "local-java-runner");
        this.dependencyResolver = dependencyResolver;
    }

    @Override
    public RunResult run(String selectedPath) {
        File selectedFile;
        try {
            selectedFile = resolveSelectedFile(selectedPath);
        } catch (IOException exception) {
            return new RunResult(false, "", exception.getMessage(), 2);
        }

        List<File> sourceRoots = discoverSourceRoots();
        List<File> sourceFiles = listJavaFiles(sourceRoots);
        if (sourceFiles.isEmpty()) {
            return new RunResult(false, commandTrace(selectedFile), "No Java source files found to compile.", 1);
        }

        EntryPoint entryPoint;
        try {
            entryPoint = selectEntryPoint(selectedFile, sourceFiles);
        } catch (IOException exception) {
            return new RunResult(false, commandTrace(selectedFile), exception.getMessage(), 2);
        }

        DependencyResolutionResult dependencyResolution;
        try {
            dependencyResolution = dependencyResolver.resolve(workspaceDirectory);
        } catch (IOException exception) {
            return new RunResult(
                    false,
                    commandTrace(entryPoint.sourceFile),
                    "Dependency resolution failed.\n\n" + exception.getMessage(),
                    1
            );
        }

        File runRoot = new File(runnerDirectory, "session");
        File classesDir = new File(runRoot, "classes");
        File dexDir = new File(runRoot, "dex");

        try {
            resetDirectory(runRoot);
            ensureDirectory(classesDir);
            ensureDirectory(dexDir);
        } catch (IOException exception) {
            return new RunResult(
                    false,
                    commandTrace(entryPoint.sourceFile),
                    "Could not prepare runner workspace.\n\n" + exception.getMessage(),
                    1
            );
        }

        String compileCommand = sourceFiles.size() > 1
                ? "$ javac " + sourceFiles.size() + " source files"
                : commandTrace(entryPoint.sourceFile);

        try {
<<<<<<< ours
<<<<<<< ours
            compileSources(entryPoint.sourceFile, sourceFiles, classesDir, dependencyResolution.getCompileJars());
=======
            compileSources(sourceFiles, classesDir, dependencyResolution.getCompileJars());
>>>>>>> theirs
=======
            compileSources(sourceFiles, classesDir, dependencyResolution.getCompileJars());
>>>>>>> theirs
        } catch (CompileException exception) {
            String errorText = normalizeCompilerText(exception.getMessage(), "");
            return new RunResult(false, compileCommand, errorText.isEmpty() ? "Compilation failed." : errorText, 1);
        } catch (IOException exception) {
            return new RunResult(
                    false,
                    compileCommand,
                    "Compilation failed.\n\n" + exception.getMessage(),
                    1
            );
        } catch (Throwable throwable) {
            return new RunResult(
                    false,
                    compileCommand,
                    "Compiler backend failed.\n\n" + throwable.getClass().getSimpleName()
                            + ": " + String.valueOf(throwable.getMessage()),
                    1
            );
        }

        List<File> classFiles;
        try {
            classFiles = listClassFiles(classesDir);
        } catch (IOException exception) {
            return new RunResult(
                    false,
                    compileCommand + "\nCompile success.",
                    "Could not inspect compiled classes.\n\n" + exception.getMessage(),
                    1
            );
        }
        if (classFiles.isEmpty()) {
            return new RunResult(false, compileCommand, "Compilation produced no class files.", 1);
        }

        try {
            dexClasses(classFiles, dependencyResolution.getRuntimeJars(), dexDir);
        } catch (CompilationFailedException exception) {
            return new RunResult(
                    false,
                    compileCommand + "\nCompile success.",
                    "Dex conversion failed.\n\n" + exception.getMessage(),
                    1
            );
        }

        return executeMainClass(compileCommand, entryPoint.qualifiedClassName, dexDir);
    }

    @Override
    public RunResult resolveDependencies() {
        File pomFile = new File(workspaceDirectory, "pom.xml");
        if (!pomFile.exists()) {
            return new RunResult(false, RESOLVE_COMMAND, "No pom.xml found in workspace.", 1);
        }

        try {
            DependencyResolutionResult result = dependencyResolver.resolve(workspaceDirectory);
            return new RunResult(true, buildDependencyResolutionOutput(result), "", 0);
        } catch (IOException exception) {
            return new RunResult(
                    false,
                    RESOLVE_COMMAND,
                    "Dependency resolution failed.\n\n" + exception.getMessage(),
                    1
            );
        }
    }

    private static ProjectDependencyResolver createDefaultResolver(Context context) {
        File repositoryDirectory = new File(context.getApplicationContext().getFilesDir(), "m2/repository");
        return new MavenProjectDependencyResolver(
                new MavenPomParser(),
                new HttpMavenRepositoryClient(repositoryDirectory)
        );
    }

    private File resolveSelectedFile(String selectedPath) throws IOException {
        if (selectedPath == null || selectedPath.trim().isEmpty()) {
            throw new IOException("No file selected.");
        }

        File rawFile = new File(selectedPath.trim());
        File targetFile = rawFile.isAbsolute() ? rawFile : new File(workspaceDirectory, selectedPath.trim());
        String workspacePath = workspaceDirectory.getCanonicalPath();
        String targetPath = targetFile.getCanonicalPath();
        if (!targetPath.startsWith(workspacePath + File.separator)) {
            throw new IOException("Unsupported workspace path: " + selectedPath);
        }
        if (!targetFile.exists()) {
            throw new IOException("Selected file does not exist: " + targetFile.getName());
        }
        return targetFile;
    }

    private List<File> discoverSourceRoots() {
        File pomFile = new File(workspaceDirectory, "pom.xml");
        if (pomFile.exists()) {
            return Arrays.asList(new File(workspaceDirectory, "src/main/java"));
        }
        return Arrays.asList(workspaceDirectory);
    }

    private EntryPoint selectEntryPoint(File selectedFile, List<File> sourceFiles) throws IOException {
        if (selectedFile.getName().endsWith(".java")) {
            for (File sourceFile : sourceFiles) {
                if (sourceFile.getCanonicalPath().equals(selectedFile.getCanonicalPath())) {
                    String source = readUtf8(sourceFile);
                    if (!declaresMainMethod(source)) {
                        throw new IOException("Selected file does not define main(String[] args): " + selectedFile.getName());
                    }
                    return new EntryPoint(sourceFile, qualifyClassName(source, classNameFor(sourceFile)));
                }
            }
            throw new IOException("Selected Java file is outside the active source roots: " + selectedFile.getName());
        }

        List<EntryPoint> mainCandidates = new ArrayList<>();
        for (File sourceFile : sourceFiles) {
            String source = readUtf8(sourceFile);
            if (declaresMainMethod(source)) {
                mainCandidates.add(new EntryPoint(sourceFile, qualifyClassName(source, classNameFor(sourceFile))));
            }
        }

        if (mainCandidates.isEmpty()) {
            throw new IOException("No runnable main(String[] args) method found in the project.");
        }
        if (mainCandidates.size() == 1) {
            return mainCandidates.get(0);
        }

        for (EntryPoint candidate : mainCandidates) {
            if ("Main.java".equals(candidate.sourceFile.getName())) {
                return candidate;
            }
        }
        throw new IOException("Multiple runnable classes found. Open a Java file with main(String[] args) before running.");
    }

    private boolean declaresMainMethod(String sourceCode) {
        return MAIN_METHOD_PATTERN.matcher(sourceCode).find();
    }

    private String classNameFor(File sourceFile) {
        String name = sourceFile.getName();
        return name.endsWith(".java") ? name.substring(0, name.length() - 5) : name;
    }

    private String readUtf8(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    /**
     * Compiles workspace source files in correct dependency order.
     *
     * Algorithm:
     *  1. Build a map of qualifiedClassName -> File for every source file in the workspace.
     *  2. Starting from the entry point, walk the import graph depth-first (DFS).
     *     Same-package files are treated as implicit dependencies.
     *  3. Files emerge from the DFS in post-order, i.e. every dependency appears before
     *     the file that depends on it.
     *  4. Compile each file in that order using a fresh Janino Compiler.
     *     The IClassLoader always points at classesDir, which accumulates compiled
     *     .class files after each step — so later files can resolve earlier ones.
     *
     * This avoids all Janino setSourcePath / setIClassLoader ordering pitfalls and
     * mirrors the way javac resolves multi-file projects.
     */
    private void compileSources(
<<<<<<< ours
<<<<<<< ours
            File entryPointFile,
            List<File> allSourceFiles,
            File classesDir,
            List<File> compileJars
    ) throws CompileException, IOException {

        // Step 1: build qualifiedClassName -> File map
        Map<String, File> classToFile = new LinkedHashMap<>();
        for (File f : allSourceFiles) {
            String src = readUtf8(f);
            String qualifiedName = qualifyClassName(src, classNameFor(f));
            classToFile.put(qualifiedName, f);
        }

        // Step 2 & 3: DFS from entry point -> post-order compilation list
        List<File> compilationOrder = new ArrayList<>();
        Set<File> visited = new LinkedHashSet<>();
        resolveCompilationOrder(entryPointFile, classToFile, visited, compilationOrder);

        // Step 4: compile each file in dependency order
        for (File sourceFile : compilationOrder) {
            Compiler compiler = new Compiler();
            compiler.setSourceVersion(8);
            compiler.setTargetVersion(8);
            compiler.setEncoding(StandardCharsets.UTF_8);
            compiler.setDestinationDirectory(classesDir, false);
            // classesDir already contains .class files from previous iterations,
            // so the next file can resolve types compiled in earlier steps.
            compiler.setIClassLoader(buildCompilerClassLoader(compileJars, classesDir));
            compiler.compile(new File[]{sourceFile});
        }
=======
=======
>>>>>>> theirs
            List<File> sourceFiles,
            File classesDir,
            List<File> compileJars
    ) throws CompileException, IOException {
        Compiler compiler = new Compiler();
        compiler.setSourceVersion(8);
        compiler.setTargetVersion(8);
        compiler.setEncoding(StandardCharsets.UTF_8);
        compiler.setDestinationDirectory(classesDir, false);

<<<<<<< ours
        // Janino 3.1.12 local-project compilation strategy:
        // - Compile the full workspace source set in one compiler invocation.
        // - Keep only the dependency class loader layer (JARs + app runtime).
        //
        // This mirrors javac-style project compilation and avoids Janino source-path
        // lookup edge cases that can fail to resolve simple same-package types
        // (e.g. Main.java referencing Person from Person.java).
        compiler.setIClassLoader(buildCompilerClassLoader(compileJars));

        compiler.compile(sourceFiles.toArray(new File[0]));
>>>>>>> theirs
=======
        // Compile all workspace sources in a single pass. This ensures simple type names
        // (for example, `Person` referenced from `Main`) are resolved from peer source
        // units without relying on Janino's source-path lazy lookup behavior.
        compiler.setIClassLoader(buildCompilerClassLoader(compileJars));
        compiler.compile(sourceFiles.toArray(new File[0]));
>>>>>>> theirs
    }

    /**
     * DFS traversal that produces a post-order list of files to compile.
     * A file is appended only after all its own dependencies are appended first.
     */
    private void resolveCompilationOrder(
            File file,
            Map<String, File> classToFile,
            Set<File> visited,
            List<File> ordered
    ) throws IOException {
        if (visited.contains(file)) {
            return;
        }
        visited.add(file); // mark before recursing to handle circular refs gracefully

        String source = readUtf8(file);
        String pkg = extractPackageName(source);

        // Explicit imports that map to workspace source files
        Matcher importMatcher = IMPORT_PATTERN.matcher(source);
        while (importMatcher.find()) {
            String importedName = importMatcher.group(1);
            File dep = classToFile.get(importedName);
            if (dep != null) {
                resolveCompilationOrder(dep, classToFile, visited, ordered);
            }
        }

        // Same-package files referenced without an explicit import statement
        if (!pkg.isEmpty()) {
            String pkgPrefix = pkg + ".";
            for (Map.Entry<String, File> entry : classToFile.entrySet()) {
                if (entry.getKey().startsWith(pkgPrefix) && !entry.getValue().equals(file)) {
                    resolveCompilationOrder(entry.getValue(), classToFile, visited, ordered);
                }
            }
        }

        ordered.add(file); // post-order: this file goes AFTER all its dependencies
    }

    private String extractPackageName(String source) {
        Matcher m = PACKAGE_PATTERN.matcher(source);
        return m.find() ? m.group(1) : "";
    }

    /**
     * Builds an IClassLoader chain that resolves (in lookup order):
     *   1. Android/JDK classes via the app ClassLoader
     *   2. Maven JAR dependencies via PathResourceFinder
     *   3. Previously compiled .class files in classesDir via PathResourceFinder
     *      (classesDir grows after each compile step, so later files resolve earlier ones)
     *
     * URLClassLoader is intentionally avoided — it throws UnsupportedOperationException
     * on Android/Dalvik when Janino tries to use it for class resolution.
     */
    private IClassLoader buildCompilerClassLoader(List<File> compileJars, File classesDir) {
        ClassLoader parentClassLoader = appContext.getClassLoader();
        if (parentClassLoader == null) {
            parentClassLoader = LocalJavaRunner.class.getClassLoader();
        }

        // Layer 1: Android/JDK classes (java.lang.*, android.*, etc.)
        IClassLoader base = new ClassLoaderIClassLoader(parentClassLoader);

<<<<<<< ours
        // Layer 2: Maven JAR dependencies
        IClassLoader withJars = base;
        if (compileJars != null && !compileJars.isEmpty()) {
            List<File> existingJars = new ArrayList<>();
            for (File jar : compileJars) {
                if (jar.exists()) existingJars.add(jar);
            }
            if (!existingJars.isEmpty()) {
                withJars = new ResourceFinderIClassLoader(
                        new PathResourceFinder(existingJars.toArray(new File[0])), base);
            }
=======
        // Layer 2 - Maven JAR dependencies used during compilation.
        if (compileJars == null || compileJars.isEmpty()) {
            return base;
>>>>>>> theirs
        }

        // Layer 3: Previously compiled .class files from classesDir.
        // PathResourceFinder resolves "demo/Person.class" inside a directory tree,
        // which is exactly what Janino needs when compiling the next file in the chain.
        if (classesDir != null && classesDir.exists()) {
            return new ResourceFinderIClassLoader(
                    new PathResourceFinder(new File[]{classesDir}), withJars);
        }

        return withJars;
    }

    private void dexClasses(List<File> classFiles, List<File> runtimeJars, File dexDir) throws CompilationFailedException {
        D8Command.Builder builder = D8Command.builder()
                .setMinApiLevel(MIN_API_FOR_DEX)
                .setOutput(dexDir.toPath(), OutputMode.DexIndexed);

        for (File classFile : classFiles) {
            builder.addProgramFiles(classFile.toPath());
        }
        for (File jarFile : runtimeJars) {
            builder.addProgramFiles(jarFile.toPath());
        }

        D8.run(builder.build());
    }

    private RunResult executeMainClass(String compileCommand, String mainClassName, File dexDir) {
        List<File> dexFiles = listDexFiles(dexDir);
        if (dexFiles.isEmpty()) {
            return new RunResult(
                    false,
                    compileCommand + "\nCompile success.",
                    "Dex conversion completed but no dex files were created.",
                    1
            );
        }

        ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;

        try {
            ByteBuffer[] dexBuffers = new ByteBuffer[dexFiles.size()];
            for (int index = 0; index < dexFiles.size(); index++) {
                dexBuffers[index] = ByteBuffer.wrap(Files.readAllBytes(dexFiles.get(index).toPath()));
            }

            ClassLoader parentClassLoader = appContext.getClassLoader();
            if (parentClassLoader == null) {
                parentClassLoader = LocalJavaRunner.class.getClassLoader();
            }
            ClassLoader classLoader = createDexClassLoader(dexFiles, dexBuffers, parentClassLoader);
            Class<?> mainClass = classLoader.loadClass(mainClassName);
            Method mainMethod = mainClass.getMethod("main", String[].class);

            if (!Modifier.isStatic(mainMethod.getModifiers()) || mainMethod.getReturnType() != Void.TYPE) {
                return new RunResult(
                        false,
                        compileCommand + "\nCompile success.",
                        "Runtime failed.\nmain(String[] args) must be static and return void.",
                        1
                );
            }

            PrintStream runtimeOut = new PrintStream(stdoutBuffer, true, StandardCharsets.UTF_8.name());
            PrintStream runtimeErr = new PrintStream(stderrBuffer, true, StandardCharsets.UTF_8.name());
            System.setOut(runtimeOut);
            System.setErr(runtimeErr);

            try {
                mainMethod.invoke(null, (Object) new String[0]);
            } catch (InvocationTargetException invocationTargetException) {
                Throwable cause = invocationTargetException.getCause();
                if (cause != null) {
                    cause.printStackTrace(runtimeErr);
                } else {
                    invocationTargetException.printStackTrace(runtimeErr);
                }
            }

            runtimeOut.flush();
            runtimeErr.flush();
        } catch (NoSuchMethodException exception) {
            return new RunResult(
                    false,
                    compileCommand + "\nCompile success.",
                    "Runtime failed.\nMain method not found in class " + mainClassName + ".",
                    1
            );
        } catch (ClassNotFoundException exception) {
            return new RunResult(
                    false,
                    compileCommand + "\nCompile success.",
                    "Runtime failed.\nCould not load compiled class " + mainClassName + ".",
                    1
            );
        } catch (Exception exception) {
            return new RunResult(
                    false,
                    compileCommand + "\nCompile success.",
                    "Runtime failed.\n\n" + exception.getMessage(),
                    1
            );
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }

        String runtimeStdout = new String(stdoutBuffer.toByteArray(), StandardCharsets.UTF_8).trim();
        String runtimeStderr = new String(stderrBuffer.toByteArray(), StandardCharsets.UTF_8).trim();

        StringBuilder stdout = new StringBuilder();
        stdout.append(compileCommand).append('\n');
        stdout.append("Compile success.").append('\n');
        stdout.append("$ java ").append(mainClassName);
        if (!runtimeStdout.isEmpty()) {
            stdout.append("\n\n").append(runtimeStdout);
        } else {
            stdout.append("\n\nProgram finished with no console output.");
        }

        boolean success = runtimeStderr.isEmpty();
        return new RunResult(success, stdout.toString(), runtimeStderr, success ? 0 : 1);
    }

    private ClassLoader createDexClassLoader(List<File> dexFiles, ByteBuffer[] dexBuffers, ClassLoader parentClassLoader) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            return new InMemoryDexClassLoader(dexBuffers, parentClassLoader);
        }

        StringBuilder dexPathBuilder = new StringBuilder();
        for (int index = 0; index < dexFiles.size(); index++) {
            if (index > 0) {
                dexPathBuilder.append(File.pathSeparatorChar);
            }
            dexPathBuilder.append(dexFiles.get(index).getAbsolutePath());
        }

        File optimizedDexDirectory = new File(runnerDirectory, "optimized-dex");
        ensureDirectory(optimizedDexDirectory);
        return new DexClassLoader(
                dexPathBuilder.toString(),
                optimizedDexDirectory.getAbsolutePath(),
                null,
                parentClassLoader
        );
    }

    private List<File> listJavaFiles(List<File> sourceRoots) {
        List<File> javaFiles = new ArrayList<>();
        for (File sourceRoot : sourceRoots) {
            collectJavaFiles(sourceRoot, javaFiles);
        }
        javaFiles.sort(Comparator.comparing(File::getAbsolutePath));
        return javaFiles;
    }

    private void collectJavaFiles(File directory, List<File> javaFiles) {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                collectJavaFiles(file, javaFiles);
            } else if (file.getName().endsWith(".java")) {
                javaFiles.add(file);
            }
        }
    }

    private List<File> listClassFiles(File directory) throws IOException {
        List<File> classFiles = new ArrayList<>();
        File[] files = directory.listFiles();
        if (files == null) {
            return classFiles;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                classFiles.addAll(listClassFiles(file));
            } else if (file.getName().endsWith(".class")) {
                classFiles.add(file);
            }
        }

        classFiles.sort(Comparator.comparing(File::getAbsolutePath));
        return classFiles;
    }

    private List<File> listDexFiles(File directory) {
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".dex"));
        if (files == null) {
            return new ArrayList<>();
        }
        List<File> dexFiles = new ArrayList<>(Arrays.asList(files));
        dexFiles.sort(Comparator.comparing(File::getName));
        return dexFiles;
    }

    private void resetDirectory(File directory) throws IOException {
        deleteRecursively(directory);
        ensureDirectory(directory);
    }

    private void ensureDirectory(File directory) throws IOException {
        if (directory.exists()) {
            return;
        }
        if (!directory.mkdirs() && !directory.exists()) {
            throw new IOException("Could not create directory: " + directory.getAbsolutePath());
        }
    }

    private void deleteRecursively(File file) throws IOException {
        if (file == null || !file.exists()) {
            return;
        }

        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }

        if (!file.delete() && file.exists()) {
            throw new IOException("Could not delete: " + file.getAbsolutePath());
        }
    }

    private String qualifyClassName(String sourceCode, String className) {
        Matcher matcher = PACKAGE_PATTERN.matcher(sourceCode);
        if (!matcher.find()) {
            return className;
        }
        return matcher.group(1) + "." + className;
    }

    private String normalizeCompilerText(String compilerOut, String compilerErr) {
        StringBuilder builder = new StringBuilder();
        if (compilerErr != null && !compilerErr.trim().isEmpty()) {
            builder.append(compilerErr.trim());
        }
        if (compilerOut != null && !compilerOut.trim().isEmpty()) {
            if (builder.length() > 0) {
                builder.append('\n').append('\n');
            }
            builder.append(compilerOut.trim());
        }
        return builder.toString().trim();
    }

    private String commandTrace(File sourceFile) {
        return "$ javac " + sourceFile.getName();
    }

    private String buildDependencyResolutionOutput(DependencyResolutionResult result) {
        StringBuilder builder = new StringBuilder();
        builder.append(RESOLVE_COMMAND);
        builder.append("\nResolve success.");
        builder.append("\nCompile jars: ").append(result.getCompileJars().size());
        builder.append("\nRuntime jars: ").append(result.getRuntimeJars().size());

        if (!result.getRuntimeJars().isEmpty()) {
            builder.append("\n\nRuntime artifacts:");
            for (File jarFile : result.getRuntimeJars()) {
                builder.append("\n- ").append(jarFile.getName());
            }
        }

        return builder.toString();
    }

    private static final class EntryPoint {
        private final File sourceFile;
        private final String qualifiedClassName;

        private EntryPoint(File sourceFile, String qualifiedClassName) {
            this.sourceFile = sourceFile;
            this.qualifiedClassName = qualifiedClassName;
        }
    }
}
