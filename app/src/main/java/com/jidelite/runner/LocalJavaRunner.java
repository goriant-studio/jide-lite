package com.jidelite.runner;

import android.content.Context;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.OutputMode;
import com.jidelite.model.RunResult;

import org.eclipse.jdt.core.compiler.batch.BatchCompiler;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
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

    private static final Pattern SAFE_FILE_NAME = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*\\.java$");
    private static final Pattern PACKAGE_PATTERN =
            Pattern.compile("(?m)^\\s*package\\s+([A-Za-z_$][A-Za-z0-9_$.]*)\\s*;");
    private static final int MIN_API_FOR_DEX = 26;

    private final Context appContext;
    private final File workspaceDirectory;
    private final File runnerDirectory;

    public LocalJavaRunner(Context context, File workspaceDirectory) {
        this.appContext = context.getApplicationContext();
        this.workspaceDirectory = workspaceDirectory;
        this.runnerDirectory = new File(appContext.getCacheDir(), "local-java-runner");
    }

    @Override
    public RunResult runJava(String fileName, String sourceCode) {
        String safeFileName = fileName == null ? "" : fileName.trim();
        String safeSourceCode = sourceCode == null ? "" : sourceCode;

        if (!SAFE_FILE_NAME.matcher(safeFileName).matches()) {
            return new RunResult(false, "", "Unsupported file name: " + safeFileName, 2);
        }

        if (safeSourceCode.trim().isEmpty()) {
            return new RunResult(false, commandTrace(safeFileName), "Source code is empty.", 2);
        }

        File runRoot = new File(runnerDirectory, "session");
        File sourcesDir = new File(runRoot, "sources");
        File classesDir = new File(runRoot, "classes");
        File dexDir = new File(runRoot, "dex");

        try {
            resetDirectory(runRoot);
            ensureDirectory(sourcesDir);
            ensureDirectory(classesDir);
            ensureDirectory(dexDir);
            mirrorWorkspaceSources(sourcesDir, safeFileName, safeSourceCode);
        } catch (IOException exception) {
            return new RunResult(
                    false,
                    commandTrace(safeFileName),
                    "Could not prepare runner workspace.\n\n" + exception.getMessage(),
                    1
            );
        }

        List<File> sourceFiles = listJavaFiles(sourcesDir);
        if (sourceFiles.isEmpty()) {
            return new RunResult(false, commandTrace(safeFileName), "No Java source files found to compile.", 1);
        }

        StringWriter compilerOutWriter = new StringWriter();
        StringWriter compilerErrWriter = new StringWriter();
        boolean compileSuccess = BatchCompiler.compile(
                buildCompilerArguments(sourceFiles, classesDir),
                new PrintWriter(compilerOutWriter),
                new PrintWriter(compilerErrWriter),
                null
        );

        String compileOutput = normalizeCompilerText(compilerOutWriter.toString(), compilerErrWriter.toString());
        String compileCommand = sourceFiles.size() > 1 ? "$ javac *.java" : commandTrace(safeFileName);

        if (!compileSuccess) {
            String errorText = compileOutput.isEmpty() ? "Compilation failed." : compileOutput;
            return new RunResult(false, compileCommand, errorText, 1);
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
            dexClasses(classFiles, dexDir);
        } catch (CompilationFailedException exception) {
            return new RunResult(
                    false,
                    compileCommand + "\nCompile success.",
                    "Dex conversion failed.\n\n" + exception.getMessage(),
                    1
            );
        }

        String className = safeFileName.substring(0, safeFileName.length() - 5);
        String qualifiedMainClass = qualifyClassName(safeSourceCode, className);

        return executeMainClass(compileCommand, qualifiedMainClass, dexDir);
    }

    private String[] buildCompilerArguments(List<File> sourceFiles, File classesDir) {
        List<String> arguments = new ArrayList<>();
        arguments.add("-proc:none");
        arguments.add("-source");
        arguments.add("1.8");
        arguments.add("-target");
        arguments.add("1.8");
        arguments.add("-encoding");
        arguments.add("UTF-8");
        arguments.add("-d");
        arguments.add(classesDir.getAbsolutePath());

        String classpath = System.getProperty("java.class.path");
        if (classpath != null && !classpath.trim().isEmpty()) {
            arguments.add("-classpath");
            arguments.add(classpath);
        }

        for (File sourceFile : sourceFiles) {
            arguments.add(sourceFile.getAbsolutePath());
        }
        return arguments.toArray(new String[0]);
    }

    private void dexClasses(List<File> classFiles, File dexDir) throws CompilationFailedException {
        D8Command.Builder builder = D8Command.builder()
                .setMinApiLevel(MIN_API_FOR_DEX)
                .setDisableDesugaring(true)
                .setOutput(dexDir.toPath(), OutputMode.DexIndexed);

        for (File classFile : classFiles) {
            builder.addProgramFiles(classFile.toPath());
        }

        D8.run(builder.build());
    }

    private RunResult executeMainClass(String compileCommand, String mainClassName, File dexDir) {
        File dexFile = new File(dexDir, "classes.dex");
        if (!dexFile.exists()) {
            return new RunResult(
                    false,
                    compileCommand + "\nCompile success.",
                    "Dex conversion completed but classes.dex was not created.",
                    1
            );
        }

        ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;

        try {
            byte[] dexBytes = Files.readAllBytes(dexFile.toPath());
            InMemoryDexClassLoader classLoader =
                    new InMemoryDexClassLoader(ByteBuffer.wrap(dexBytes), appContext.getClassLoader());
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

    private void mirrorWorkspaceSources(File sourcesDir, String selectedFileName, String selectedSource) throws IOException {
        ensureDirectory(sourcesDir);

        if (workspaceDirectory != null && workspaceDirectory.exists()) {
            List<File> workspaceFiles = listJavaFiles(workspaceDirectory);
            for (File workspaceFile : workspaceFiles) {
                byte[] bytes = Files.readAllBytes(workspaceFile.toPath());
                Files.write(new File(sourcesDir, workspaceFile.getName()).toPath(), bytes);
            }
        }

        Files.write(
                new File(sourcesDir, selectedFileName).toPath(),
                selectedSource.getBytes(StandardCharsets.UTF_8)
        );
    }

    private List<File> listJavaFiles(File directory) {
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".java"));
        if (files == null) {
            return new ArrayList<>();
        }

        List<File> javaFiles = new ArrayList<>(Arrays.asList(files));
        javaFiles.sort(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        return javaFiles;
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

    private String commandTrace(String fileName) {
        return "$ javac " + fileName;
    }
}
