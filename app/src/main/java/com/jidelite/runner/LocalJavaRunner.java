package com.jidelite.runner;

import android.content.Context;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.OutputMode;
import com.jidelite.model.RunResult;

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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dalvik.system.InMemoryDexClassLoader;

public class LocalJavaRunner implements CodeRunner {

    private static final Pattern PACKAGE_PATTERN =
            Pattern.compile("(?m)^\\s*package\\s+([A-Za-z_$][A-Za-z0-9_$.]*)\\s*;");
    private static final Pattern MAIN_METHOD_PATTERN = Pattern.compile(
            "(?s)\\bpublic\\s+static\\s+void\\s+main\\s*\\(\\s*String\\s*(?:\\[\\s*]|\\.\\.\\.)\\s*[A-Za-z_$][A-Za-z0-9_$]*\\s*\\)"
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
            compileSources(sourceFiles, sourceRoots, classesDir, dependencyResolution.getCompileJars());
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

    private void compileSources(
            List<File> sourceFiles,
            List<File> sourceRoots,
            File classesDir,
            List<File> compileJars
    ) throws CompileException, IOException {
        Compiler compiler = new Compiler();
        compiler.setSourceVersion(8);
        compiler.setTargetVersion(8);
        compiler.setEncoding(StandardCharsets.UTF_8);
        compiler.setSourcePath(sourceRoots.toArray(new File[0]));
        compiler.setDestinationDirectory(classesDir, false);
        compiler.setIClassLoader(buildCompilerClassLoader(compileJars));
        compiler.compile(sourceFiles.toArray(new File[0]));
    }

    private IClassLoader buildCompilerClassLoader(List<File> compileJars) {
        ClassLoader parentClassLoader = appContext.getClassLoader();
        if (parentClassLoader == null) {
            parentClassLoader = LocalJavaRunner.class.getClassLoader();
        }
        IClassLoader parent = new ClassLoaderIClassLoader(parentClassLoader);
        if (compileJars == null || compileJars.isEmpty()) {
            return parent;
        }
        return new ResourceFinderIClassLoader(new PathResourceFinder(compileJars.toArray(new File[0])), parent);
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
            InMemoryDexClassLoader classLoader = new InMemoryDexClassLoader(dexBuffers, parentClassLoader);
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
