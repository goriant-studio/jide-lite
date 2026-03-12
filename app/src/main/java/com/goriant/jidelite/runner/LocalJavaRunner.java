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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dalvik.system.DexClassLoader;
import dalvik.system.InMemoryDexClassLoader;

public class LocalJavaRunner implements CodeRunner {

    private static final Pattern PACKAGE_PATTERN =
            Pattern.compile("(?m)^\\s*package\\s+([A-Za-z_$][A-Za-z0-9_$.]*)\\s*;");
    private static final Pattern IMPORT_PATTERN =
            Pattern.compile("(?m)^\\s*import\\s+(static\\s+)?([A-Za-z_$][A-Za-z0-9_$.]*\\*?)\\s*;");
    private static final Pattern TYPE_DECLARATION_PATTERN = Pattern.compile(
            "(?m)^\\s*(?:public\\s+|protected\\s+|private\\s+|abstract\\s+|final\\s+|static\\s+|sealed\\s+|non-sealed\\s+)*"
                    + "(class|interface|enum|record)\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\b"
    );
    private static final Pattern SIMPLE_TYPE_REFERENCE_PATTERN =
            Pattern.compile("\\b([A-Z][A-Za-z0-9_$]*)\\b");
    private static final Pattern QUALIFIED_TYPE_REFERENCE_PATTERN =
            Pattern.compile("\\b([a-z_$][A-Za-z0-9_$]*(?:\\.[a-z_$][A-Za-z0-9_$]*)*\\.[A-Z][A-Za-z0-9_$]*)\\b");
    private static final Pattern MAIN_METHOD_PATTERN = Pattern.compile(
            "(?s)\\bpublic\\s+static\\s+void\\s+main\\s*\\(\\s*String\\s*(?:\\[\\s*]|\\.\\.\\.)\\s*[A-Za-z_$][A-Za-z0-9_$]*\\s*\\)"
    );
    private static final int MIN_API_FOR_DEX = 26;
    private static final int RUN_TIMEOUT_EXIT_CODE = 124;
    private static final long RUN_TIMEOUT_MILLIS = 10_000L;
    private static final long BUILD_CACHE_VERSION = 1L;
    private static final String RESOLVE_COMMAND = "$ mvn dependency:resolve";
    private static final String BUILD_CACHE_FILE_NAME = "build-cache.bin";
    private static final Object SYSTEM_STREAM_LOCK = new Object();

    private final Context appContext;
    private final File workspaceDirectory;
    private final File runnerDirectory;
    private final ProjectDependencyResolver dependencyResolver;
    private final long runTimeoutMillis;
    private volatile InteractiveInputStream activeStdin = null;
    private volatile boolean executing = false;

    public LocalJavaRunner(Context context, File workspaceDirectory) {
        this(context, workspaceDirectory, createDefaultResolver(context));
    }

    LocalJavaRunner(Context context, File workspaceDirectory, ProjectDependencyResolver dependencyResolver) {
        this(context, workspaceDirectory, dependencyResolver, RUN_TIMEOUT_MILLIS);
    }

    LocalJavaRunner(
            Context context,
            File workspaceDirectory,
            ProjectDependencyResolver dependencyResolver,
            long runTimeoutMillis
    ) {
        this.appContext = context.getApplicationContext();
        this.workspaceDirectory = workspaceDirectory;
        this.runnerDirectory = new File(
                new File(appContext.getCacheDir(), "local-java-runner"),
                workspaceSessionId(workspaceDirectory)
        );
        this.dependencyResolver = dependencyResolver;
        this.runTimeoutMillis = runTimeoutMillis;
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

        File classesDir = new File(runnerDirectory, "classes");
        File dexDir = new File(runnerDirectory, "dex");
        File metadataFile = new File(runnerDirectory, BUILD_CACHE_FILE_NAME);

        try {
            ensureDirectory(runnerDirectory);
            ensureDirectory(classesDir);
            resetDirectory(dexDir);
        } catch (IOException exception) {
            return new RunResult(
                    false,
                    commandTrace(entryPoint.sourceFile),
                    "Could not prepare runner workspace.\n\n" + exception.getMessage(),
                    1
            );
        }

        BuildCache cachedBuild = loadBuildCache(metadataFile);
        String dependencyFingerprint = buildDependencyFingerprint(dependencyResolution);
        CompilationPlan compilationPlan;
        try {
            compilationPlan = buildCompilationPlan(sourceFiles, cachedBuild, classesDir, dependencyFingerprint);
        } catch (IOException exception) {
            return new RunResult(
                    false,
                    commandTrace(entryPoint.sourceFile),
                    "Could not inspect project sources.\n\n" + exception.getMessage(),
                    1
            );
        }

        String compileCommand = compilationPlan.describeCompileCommand(entryPoint.sourceFile);

        try {
            if (compilationPlan.fullRebuild) {
                resetDirectory(classesDir);
                ensureDirectory(classesDir);
            } else {
                deleteCompiledOutputs(compilationPlan.staleSourcePaths, cachedBuild, classesDir);
            }

            if (!compilationPlan.sourcesToCompile.isEmpty()) {
                compileSources(compilationPlan.sourcesToCompile, classesDir, dependencyResolution.getCompileJars());
            }
            saveBuildCache(metadataFile, compilationPlan.nextCache);
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

    private static String workspaceSessionId(File workspaceDirectory) {
        String workspacePath = workspaceDirectory.getAbsolutePath();
        try {
            workspacePath = workspaceDirectory.getCanonicalPath();
        } catch (IOException ignored) {
            // Fall back to the absolute path when canonical resolution fails.
        }
        return UUID.nameUUIDFromBytes(workspacePath.getBytes(StandardCharsets.UTF_8)).toString();
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

    private CompilationPlan buildCompilationPlan(
            List<File> sourceFiles,
            BuildCache cachedBuild,
            File classesDir,
            String dependencyFingerprint
    ) throws IOException {
        Map<String, SourceSnapshot> currentSnapshots = buildSourceSnapshots(sourceFiles);
        BuildCache nextCache = buildBuildCache(currentSnapshots, dependencyFingerprint);

        if (cachedBuild.version != BUILD_CACHE_VERSION
                || !dependencyFingerprint.equals(cachedBuild.dependencyFingerprint)) {
            return new CompilationPlan(
                    true,
                    sortedSourceFiles(currentSnapshots.values()),
                    Collections.emptySet(),
                    nextCache,
                    currentSnapshots.size()
            );
        }

        Set<String> deletedSourcePaths = new LinkedHashSet<>(cachedBuild.sourcesByPath.keySet());
        deletedSourcePaths.removeAll(currentSnapshots.keySet());

        Set<String> dirtySourcePaths = new LinkedHashSet<>();
        for (SourceSnapshot snapshot : currentSnapshots.values()) {
            CachedSourceState cachedState = cachedBuild.sourcesByPath.get(snapshot.canonicalPath);
            if (cachedState == null
                    || snapshot.lastModified != cachedState.lastModified
                    || snapshot.fileLength != cachedState.fileLength
                    || !snapshot.packageName.equals(cachedState.packageName)
                    || !snapshot.typeNames.equals(cachedState.typeNames)
                    || !hasCompiledOutputs(classesDir, cachedState)) {
                dirtySourcePaths.add(snapshot.canonicalPath);
            }
        }

        if (dirtySourcePaths.isEmpty() && deletedSourcePaths.isEmpty()) {
            return new CompilationPlan(
                    false,
                    Collections.emptyList(),
                    Collections.emptySet(),
                    nextCache,
                    currentSnapshots.size()
            );
        }

        Set<String> impactedSourcePaths = collectImpactedSourcePaths(
                currentSnapshots,
                cachedBuild,
                dirtySourcePaths,
                deletedSourcePaths
        );
        Set<String> staleSourcePaths = new LinkedHashSet<>(deletedSourcePaths);
        staleSourcePaths.addAll(impactedSourcePaths);

        List<File> sourcesToCompile = new ArrayList<>();
        for (String sourcePath : impactedSourcePaths) {
            SourceSnapshot snapshot = currentSnapshots.get(sourcePath);
            if (snapshot != null) {
                sourcesToCompile.add(snapshot.sourceFile);
            }
        }
        sourcesToCompile.sort(Comparator.comparing(File::getAbsolutePath));

        return new CompilationPlan(
                false,
                sourcesToCompile,
                staleSourcePaths,
                nextCache,
                currentSnapshots.size()
        );
    }

    private Map<String, SourceSnapshot> buildSourceSnapshots(List<File> sourceFiles) throws IOException {
        Map<String, SourceSnapshot> snapshots = new LinkedHashMap<>();
        for (File sourceFile : sourceFiles) {
            String sourceCode = readUtf8(sourceFile);
            String sanitizedSource = sanitizeJavaSource(sourceCode);
            String canonicalPath = sourceFile.getCanonicalPath();
            SourceSnapshot snapshot = new SourceSnapshot(
                    sourceFile,
                    canonicalPath,
                    sourceFile.lastModified(),
                    sourceFile.length(),
                    packageNameFor(sourceCode),
                    extractTopLevelTypeNames(sanitizedSource, classNameFor(sourceFile)),
                    sanitizedSource
            );
            snapshots.put(canonicalPath, snapshot);
        }

        Map<String, Set<String>> qualifiedOwners = new HashMap<>();
        Map<String, Set<String>> packageOwners = new HashMap<>();
        for (SourceSnapshot snapshot : snapshots.values()) {
            for (String typeName : snapshot.typeNames) {
                addOwner(qualifiedOwners, qualifyTypeName(snapshot.packageName, typeName), snapshot.canonicalPath);
                addOwner(packageOwners, packageTypeKey(snapshot.packageName, typeName), snapshot.canonicalPath);
            }
        }

        for (SourceSnapshot snapshot : snapshots.values()) {
            snapshot.dependencyPaths.addAll(resolveSourceDependencies(snapshot, qualifiedOwners, packageOwners));
            snapshot.dependencyPaths.remove(snapshot.canonicalPath);
        }

        return snapshots;
    }

    private Set<String> resolveSourceDependencies(
            SourceSnapshot snapshot,
            Map<String, Set<String>> qualifiedOwners,
            Map<String, Set<String>> packageOwners
    ) {
        Set<String> dependencies = new LinkedHashSet<>();
        Map<String, Set<String>> explicitImportsBySimpleName = new HashMap<>();
        Set<String> wildcardImportPackages = new LinkedHashSet<>();

        for (ImportReference importReference : extractImports(snapshot.sanitizedSource)) {
            if (importReference.staticImport) {
                String ownerType = importReference.wildcard
                        ? importReference.qualifiedName
                        : ownerTypeForStaticImport(importReference.qualifiedName);
                if (ownerType != null) {
                    addAll(dependencies, qualifiedOwners.get(ownerType));
                }
                continue;
            }

            if (importReference.wildcard) {
                wildcardImportPackages.add(importReference.qualifiedName);
                continue;
            }

            Set<String> importedSources = qualifiedOwners.get(importReference.qualifiedName);
            addAll(dependencies, importedSources);
            if (importedSources != null && !importedSources.isEmpty()) {
                explicitImportsBySimpleName
                        .computeIfAbsent(simpleNameOf(importReference.qualifiedName), ignored -> new LinkedHashSet<>())
                        .addAll(importedSources);
            }
        }

        Matcher qualifiedMatcher = QUALIFIED_TYPE_REFERENCE_PATTERN.matcher(snapshot.sanitizedSource);
        while (qualifiedMatcher.find()) {
            addAll(dependencies, qualifiedOwners.get(qualifiedMatcher.group(1)));
        }

        Matcher simpleMatcher = SIMPLE_TYPE_REFERENCE_PATTERN.matcher(snapshot.sanitizedSource);
        while (simpleMatcher.find()) {
            String simpleName = simpleMatcher.group(1);
            if (snapshot.typeNames.contains(simpleName)) {
                continue;
            }

            addAll(dependencies, explicitImportsBySimpleName.get(simpleName));
            addAll(dependencies, packageOwners.get(packageTypeKey(snapshot.packageName, simpleName)));
            for (String importedPackage : wildcardImportPackages) {
                addAll(dependencies, packageOwners.get(packageTypeKey(importedPackage, simpleName)));
            }
        }

        return dependencies;
    }

    private List<ImportReference> extractImports(String sanitizedSource) {
        List<ImportReference> imports = new ArrayList<>();
        Matcher matcher = IMPORT_PATTERN.matcher(sanitizedSource);
        while (matcher.find()) {
            String importedName = matcher.group(2).trim();
            boolean wildcard = importedName.endsWith(".*");
            if (wildcard) {
                importedName = importedName.substring(0, importedName.length() - 2);
            }
            imports.add(new ImportReference(importedName, wildcard, matcher.group(1) != null));
        }
        return imports;
    }

    private String sanitizeJavaSource(String sourceCode) {
        StringBuilder sanitized = new StringBuilder(sourceCode.length());
        boolean inLineComment = false;
        boolean inBlockComment = false;
        boolean inString = false;
        boolean inCharacter = false;
        boolean escaped = false;

        for (int index = 0; index < sourceCode.length(); index++) {
            char current = sourceCode.charAt(index);
            char next = index + 1 < sourceCode.length() ? sourceCode.charAt(index + 1) : '\0';

            if (inLineComment) {
                if (current == '\n' || current == '\r') {
                    inLineComment = false;
                    sanitized.append(current);
                } else {
                    sanitized.append(' ');
                }
                continue;
            }

            if (inBlockComment) {
                if (current == '*' && next == '/') {
                    inBlockComment = false;
                    sanitized.append("  ");
                    index++;
                } else {
                    sanitized.append((current == '\n' || current == '\r') ? current : ' ');
                }
                continue;
            }

            if (inString) {
                if (current == '\\' && !escaped) {
                    escaped = true;
                    sanitized.append(' ');
                    continue;
                }
                if (current == '"' && !escaped) {
                    inString = false;
                    sanitized.append(' ');
                    continue;
                }
                escaped = false;
                sanitized.append((current == '\n' || current == '\r') ? current : ' ');
                continue;
            }

            if (inCharacter) {
                if (current == '\\' && !escaped) {
                    escaped = true;
                    sanitized.append(' ');
                    continue;
                }
                if (current == '\'' && !escaped) {
                    inCharacter = false;
                    sanitized.append(' ');
                    continue;
                }
                escaped = false;
                sanitized.append((current == '\n' || current == '\r') ? current : ' ');
                continue;
            }

            if (current == '/' && next == '/') {
                inLineComment = true;
                sanitized.append("  ");
                index++;
                continue;
            }
            if (current == '/' && next == '*') {
                inBlockComment = true;
                sanitized.append("  ");
                index++;
                continue;
            }
            if (current == '"') {
                inString = true;
                escaped = false;
                sanitized.append(' ');
                continue;
            }
            if (current == '\'') {
                inCharacter = true;
                escaped = false;
                sanitized.append(' ');
                continue;
            }

            sanitized.append(current);
        }

        return sanitized.toString();
    }

    private List<String> extractTopLevelTypeNames(String sanitizedSource, String fallbackTypeName) {
        List<String> typeNames = new ArrayList<>();
        Matcher matcher = TYPE_DECLARATION_PATTERN.matcher(sanitizedSource);
        int braceDepth = 0;
        int scanIndex = 0;

        while (matcher.find()) {
            braceDepth = advanceBraceDepth(sanitizedSource, scanIndex, matcher.start(), braceDepth);
            scanIndex = matcher.start();
            if (braceDepth == 0) {
                String typeName = matcher.group(2);
                if (!typeNames.contains(typeName)) {
                    typeNames.add(typeName);
                }
            }
        }

        if (typeNames.isEmpty()) {
            typeNames.add(fallbackTypeName);
        }
        return typeNames;
    }

    private int advanceBraceDepth(String sourceCode, int start, int end, int braceDepth) {
        int nextDepth = braceDepth;
        for (int index = start; index < end; index++) {
            char current = sourceCode.charAt(index);
            if (current == '{') {
                nextDepth++;
            } else if (current == '}' && nextDepth > 0) {
                nextDepth--;
            }
        }
        return nextDepth;
    }

    private Set<String> collectImpactedSourcePaths(
            Map<String, SourceSnapshot> currentSnapshots,
            BuildCache cachedBuild,
            Set<String> dirtySourcePaths,
            Set<String> deletedSourcePaths
    ) {
        Map<String, Set<String>> reverseDependencies = new HashMap<>();
        for (SourceSnapshot snapshot : currentSnapshots.values()) {
            for (String dependencyPath : snapshot.dependencyPaths) {
                reverseDependencies
                        .computeIfAbsent(dependencyPath, ignored -> new LinkedHashSet<>())
                        .add(snapshot.canonicalPath);
            }
        }
        for (Map.Entry<String, CachedSourceState> entry : cachedBuild.sourcesByPath.entrySet()) {
            for (String dependencyPath : entry.getValue().dependencyPaths) {
                reverseDependencies
                        .computeIfAbsent(dependencyPath, ignored -> new LinkedHashSet<>())
                        .add(entry.getKey());
            }
        }

        Set<String> impacted = new LinkedHashSet<>();
        Queue<String> pending = new ArrayDeque<>();
        pending.addAll(dirtySourcePaths);
        pending.addAll(deletedSourcePaths);

        while (!pending.isEmpty()) {
            String sourcePath = pending.remove();
            if (!impacted.add(sourcePath)) {
                continue;
            }
            Set<String> dependents = reverseDependencies.get(sourcePath);
            if (dependents != null) {
                pending.addAll(dependents);
            }
        }

        return impacted;
    }

    private BuildCache buildBuildCache(Map<String, SourceSnapshot> currentSnapshots, String dependencyFingerprint) {
        Map<String, CachedSourceState> sourcesByPath = new LinkedHashMap<>();
        List<String> sourcePaths = new ArrayList<>(currentSnapshots.keySet());
        Collections.sort(sourcePaths);
        for (String sourcePath : sourcePaths) {
            SourceSnapshot snapshot = currentSnapshots.get(sourcePath);
            sourcesByPath.put(
                    sourcePath,
                    new CachedSourceState(
                            snapshot.lastModified,
                            snapshot.fileLength,
                            snapshot.packageName,
                            snapshot.typeNames,
                            snapshot.dependencyPaths
                    )
            );
        }
        return new BuildCache(BUILD_CACHE_VERSION, dependencyFingerprint, sourcesByPath);
    }

    private boolean hasCompiledOutputs(File classesDir, CachedSourceState cachedState) {
        for (String typeName : cachedState.typeNames) {
            if (!expectedClassFile(classesDir, cachedState.packageName, typeName).exists()) {
                return false;
            }
        }
        return true;
    }

    private File expectedClassFile(File classesDir, String packageName, String typeName) {
        return new File(resolvePackageDirectory(classesDir, packageName), typeName + ".class");
    }

    private void deleteCompiledOutputs(Set<String> sourcePaths, BuildCache cachedBuild, File classesDir) throws IOException {
        for (String sourcePath : sourcePaths) {
            CachedSourceState cachedState = cachedBuild.sourcesByPath.get(sourcePath);
            if (cachedState == null) {
                continue;
            }
            deleteCompiledOutputs(classesDir, cachedState);
        }
    }

    private void deleteCompiledOutputs(File classesDir, CachedSourceState cachedState) throws IOException {
        File packageDirectory = resolvePackageDirectory(classesDir, cachedState.packageName);
        if (!packageDirectory.exists()) {
            return;
        }

        for (String typeName : cachedState.typeNames) {
            File[] compiledOutputs = packageDirectory.listFiles((directory, name) ->
                    name.equals(typeName + ".class")
                            || (name.startsWith(typeName + "$") && name.endsWith(".class"))
            );
            if (compiledOutputs == null) {
                continue;
            }
            for (File compiledOutput : compiledOutputs) {
                deleteRecursively(compiledOutput);
            }
        }
    }

    private File resolvePackageDirectory(File classesDir, String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return classesDir;
        }
        return new File(classesDir, packageName.replace('.', File.separatorChar));
    }

    private String buildDependencyFingerprint(DependencyResolutionResult dependencyResolution) {
        List<File> jars = new ArrayList<>();
        jars.addAll(dependencyResolution.getCompileJars());
        jars.addAll(dependencyResolution.getRuntimeJars());
        jars.sort(Comparator.comparing(File::getAbsolutePath));

        StringBuilder builder = new StringBuilder();
        for (File jar : jars) {
            builder.append(jar.getAbsolutePath())
                    .append(':')
                    .append(jar.length())
                    .append(':')
                    .append(jar.lastModified())
                    .append('\n');
        }
        return builder.toString();
    }

    private BuildCache loadBuildCache(File metadataFile) {
        if (!metadataFile.exists()) {
            return BuildCache.empty();
        }

        try (ObjectInputStream inputStream = new ObjectInputStream(new FileInputStream(metadataFile))) {
            Object value = inputStream.readObject();
            if (value instanceof BuildCache) {
                BuildCache cachedBuild = (BuildCache) value;
                if (cachedBuild.version == BUILD_CACHE_VERSION) {
                    return cachedBuild;
                }
            }
        } catch (IOException | ClassNotFoundException ignored) {
            // Ignore stale build cache files and fall back to a full rebuild.
        }

        return BuildCache.empty();
    }

    private void saveBuildCache(File metadataFile, BuildCache buildCache) throws IOException {
        ensureDirectory(metadataFile.getParentFile());
        try (ObjectOutputStream outputStream = new ObjectOutputStream(new FileOutputStream(metadataFile))) {
            outputStream.writeObject(buildCache);
        }
    }

    private List<File> sortedSourceFiles(Iterable<SourceSnapshot> snapshots) {
        List<File> sourceFiles = new ArrayList<>();
        for (SourceSnapshot snapshot : snapshots) {
            sourceFiles.add(snapshot.sourceFile);
        }
        sourceFiles.sort(Comparator.comparing(File::getAbsolutePath));
        return sourceFiles;
    }

    private void compileSources(
            List<File> sourceFiles,
            File classesDir,
            List<File> compileJars
    ) throws CompileException, IOException {
        Compiler compiler = new Compiler();
        compiler.setSourceVersion(8);
        compiler.setTargetVersion(8);
        compiler.setEncoding(StandardCharsets.UTF_8);
        compiler.setDestinationDirectory(classesDir, false);
        compiler.setIClassLoader(buildCompilerClassLoader(classesDir, compileJars));
        compiler.compile(sourceFiles.toArray(new File[0]));
    }

    private IClassLoader buildCompilerClassLoader(File classesDir, List<File> compileJars) {
        ClassLoader parentClassLoader = appContext.getClassLoader();
        if (parentClassLoader == null) {
            parentClassLoader = LocalJavaRunner.class.getClassLoader();
        }

        IClassLoader base = new ClassLoaderIClassLoader(parentClassLoader);
        List<File> classPathEntries = new ArrayList<>();
        if (classesDir.exists()) {
            classPathEntries.add(classesDir);
        }
        if (compileJars != null) {
            classPathEntries.addAll(compileJars);
        }
        if (classPathEntries.isEmpty()) {
            return base;
        }

        return new ResourceFinderIClassLoader(
                new PathResourceFinder(classPathEntries.toArray(new File[0])),
                base
        );
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

            ExecutionOutcome outcome = invokeMainMethodWithTimeout(mainMethod);
            return buildExecutionResult(compileCommand, mainClassName, outcome);
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
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new RunResult(
                    false,
                    compileCommand + "\nCompile success.",
                    "Runtime interrupted while waiting for program output.",
                    130
            );
        } catch (Exception exception) {
            return new RunResult(
                    false,
                    compileCommand + "\nCompile success.",
                    "Runtime failed.\n\n" + exception.getMessage(),
                    1
            );
        }
    }

    private RunResult buildExecutionResult(String compileCommand, String mainClassName, ExecutionOutcome outcome) {
        String runtimeStdout = outcome.stdout.trim();
        String runtimeStderr = outcome.stderr.trim();

        StringBuilder stdout = new StringBuilder();
        stdout.append(compileCommand).append('\n');
        stdout.append("Compile success.").append('\n');
        stdout.append("$ java ").append(mainClassName);
        if (!runtimeStdout.isEmpty()) {
            stdout.append("\n\n").append(runtimeStdout);
        } else {
            stdout.append("\n\nProgram finished with no console output.");
        }

        boolean success = !outcome.timedOut && runtimeStderr.isEmpty();
        int exitCode = outcome.timedOut ? RUN_TIMEOUT_EXIT_CODE : (success ? 0 : 1);
        return new RunResult(success, stdout.toString(), runtimeStderr, exitCode);
    }

    private ExecutionOutcome invokeMainMethodWithTimeout(Method mainMethod) throws InterruptedException, IOException {
        ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();
        PrintStream runtimeOut = new PrintStream(stdoutBuffer, true, StandardCharsets.UTF_8.name());
        PrintStream runtimeErr = new PrintStream(stderrBuffer, true, StandardCharsets.UTF_8.name());
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        InputStream originalIn = System.in;
        InteractiveInputStream stdinStream = new InteractiveInputStream();
        activeStdin = stdinStream;
        executing = true;
        ThreadGroup executionGroup = new ThreadGroup("local-java-runner");

        Thread executionThread = new Thread(executionGroup, () -> {
            try {
                mainMethod.invoke(null, (Object) new String[0]);
            } catch (InvocationTargetException invocationTargetException) {
                Throwable cause = invocationTargetException.getCause();
                if (cause instanceof ThreadDeath) {
                    throw (ThreadDeath) cause;
                }
                if (cause != null) {
                    cause.printStackTrace(runtimeErr);
                } else {
                    invocationTargetException.printStackTrace(runtimeErr);
                }
            } catch (ThreadDeath threadDeath) {
                throw threadDeath;
            } catch (Throwable throwable) {
                throwable.printStackTrace(runtimeErr);
            }
        }, "local-java-runner-main");
        executionThread.setDaemon(true);
        executionThread.setContextClassLoader(mainMethod.getDeclaringClass().getClassLoader());

        boolean timedOut = false;
        synchronized (SYSTEM_STREAM_LOCK) {
            System.setOut(runtimeOut);
            System.setErr(runtimeErr);
            System.setIn(stdinStream);
            try {
                executionThread.start();
                executionThread.join(runTimeoutMillis);
                if (executionThread.isAlive()) {
                    timedOut = true;
                    runtimeErr.println("Runtime timed out after " + formatTimeoutMillis(runTimeoutMillis) + " and was terminated.");
                    terminateExecutionGroup(executionGroup);
                }
            } finally {
                stdinStream.close();
                activeStdin = null;
                executing = false;
                runtimeOut.flush();
                runtimeErr.flush();
                System.setOut(originalOut);
                System.setErr(originalErr);
                System.setIn(originalIn);
            }
        }

        return new ExecutionOutcome(
                new String(stdoutBuffer.toByteArray(), StandardCharsets.UTF_8),
                new String(stderrBuffer.toByteArray(), StandardCharsets.UTF_8),
                timedOut
        );
    }

    private String formatTimeoutMillis(long timeoutMillis) {
        if (timeoutMillis % 1000L == 0L) {
            return (timeoutMillis / 1000L) + "s";
        }
        return timeoutMillis + "ms";
    }

    @SuppressWarnings("deprecation")
    private void terminateExecutionGroup(ThreadGroup executionGroup) {
        executionGroup.interrupt();

        Thread[] liveThreads = enumerateThreads(executionGroup);
        for (Thread thread : liveThreads) {
            if (thread == null || !thread.isAlive()) {
                continue;
            }
            try {
                thread.join(100L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        liveThreads = enumerateThreads(executionGroup);
        for (Thread thread : liveThreads) {
            if (thread != null && thread.isAlive()) {
                try {
                    thread.stop();
                } catch (UnsupportedOperationException ignored) {
                    // Host-side unit tests on newer JDKs can reject Thread.stop().
                    // The thread remains daemonized and interrupted, so the run still unblocks.
                }
            }
        }
    }

    private Thread[] enumerateThreads(ThreadGroup threadGroup) {
        int estimatedSize = Math.max(4, threadGroup.activeCount() * 2 + 2);
        Thread[] threads = new Thread[estimatedSize];
        int count = threadGroup.enumerate(threads, true);
        return Arrays.copyOf(threads, count);
    }

    @Override
    public void submitStdin(String input) {
        InteractiveInputStream stream = activeStdin;
        if (stream != null && input != null) {
            stream.submitLine(input);
        }
    }

    @Override
    public boolean isExecuting() {
        return executing;
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
        if (directory == null) {
            return;
        }
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
        return qualifyTypeName(packageNameFor(sourceCode), className);
    }

    private String packageNameFor(String sourceCode) {
        Matcher matcher = PACKAGE_PATTERN.matcher(sourceCode);
        if (!matcher.find()) {
            return "";
        }
        return matcher.group(1);
    }

    private String qualifyTypeName(String packageName, String className) {
        if (packageName == null || packageName.isEmpty()) {
            return className;
        }
        return packageName + "." + className;
    }

    private String packageTypeKey(String packageName, String typeName) {
        return (packageName == null ? "" : packageName) + "#" + typeName;
    }

    private String simpleNameOf(String qualifiedName) {
        int separatorIndex = qualifiedName.lastIndexOf('.');
        return separatorIndex >= 0 ? qualifiedName.substring(separatorIndex + 1) : qualifiedName;
    }

    private void addOwner(Map<String, Set<String>> ownerMap, String key, String sourcePath) {
        ownerMap.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(sourcePath);
    }

    private void addAll(Set<String> target, Set<String> values) {
        if (values != null) {
            target.addAll(values);
        }
    }

    private String ownerTypeForStaticImport(String importedName) {
        int separatorIndex = importedName.lastIndexOf('.');
        if (separatorIndex <= 0) {
            return null;
        }
        return importedName.substring(0, separatorIndex);
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

    private static final class CompilationPlan {
        private final boolean fullRebuild;
        private final List<File> sourcesToCompile;
        private final Set<String> staleSourcePaths;
        private final BuildCache nextCache;
        private final int totalSourceCount;

        private CompilationPlan(
                boolean fullRebuild,
                List<File> sourcesToCompile,
                Set<String> staleSourcePaths,
                BuildCache nextCache,
                int totalSourceCount
        ) {
            this.fullRebuild = fullRebuild;
            this.sourcesToCompile = sourcesToCompile;
            this.staleSourcePaths = staleSourcePaths;
            this.nextCache = nextCache;
            this.totalSourceCount = totalSourceCount;
        }

        private String describeCompileCommand(File entryPointSource) {
            if (fullRebuild) {
                if (totalSourceCount == 1) {
                    return "$ javac " + entryPointSource.getName();
                }
                return "$ javac full rebuild (" + totalSourceCount + " source files)";
            }
            if (sourcesToCompile.isEmpty()) {
                return "$ javac incremental cache hit";
            }
            return "$ javac incremental " + sourcesToCompile.size() + "/" + totalSourceCount + " source files";
        }
    }

    private static final class SourceSnapshot {
        private final File sourceFile;
        private final String canonicalPath;
        private final long lastModified;
        private final long fileLength;
        private final String packageName;
        private final List<String> typeNames;
        private final String sanitizedSource;
        private final Set<String> dependencyPaths = new LinkedHashSet<>();

        private SourceSnapshot(
                File sourceFile,
                String canonicalPath,
                long lastModified,
                long fileLength,
                String packageName,
                List<String> typeNames,
                String sanitizedSource
        ) {
            this.sourceFile = sourceFile;
            this.canonicalPath = canonicalPath;
            this.lastModified = lastModified;
            this.fileLength = fileLength;
            this.packageName = packageName;
            this.typeNames = new ArrayList<>(typeNames);
            this.sanitizedSource = sanitizedSource;
        }
    }

    private static final class ImportReference {
        private final String qualifiedName;
        private final boolean wildcard;
        private final boolean staticImport;

        private ImportReference(String qualifiedName, boolean wildcard, boolean staticImport) {
            this.qualifiedName = qualifiedName;
            this.wildcard = wildcard;
            this.staticImport = staticImport;
        }
    }

    private static final class ExecutionOutcome {
        private final String stdout;
        private final String stderr;
        private final boolean timedOut;

        private ExecutionOutcome(String stdout, String stderr, boolean timedOut) {
            this.stdout = stdout;
            this.stderr = stderr;
            this.timedOut = timedOut;
        }
    }

    private static final class BuildCache implements Serializable {
        private static final long serialVersionUID = 1L;

        private final long version;
        private final String dependencyFingerprint;
        private final Map<String, CachedSourceState> sourcesByPath;

        private BuildCache(long version, String dependencyFingerprint, Map<String, CachedSourceState> sourcesByPath) {
            this.version = version;
            this.dependencyFingerprint = dependencyFingerprint == null ? "" : dependencyFingerprint;
            this.sourcesByPath = new LinkedHashMap<>(sourcesByPath);
        }

        private static BuildCache empty() {
            return new BuildCache(BUILD_CACHE_VERSION, "", Collections.emptyMap());
        }
    }

    private static final class CachedSourceState implements Serializable {
        private static final long serialVersionUID = 1L;

        private final long lastModified;
        private final long fileLength;
        private final String packageName;
        private final List<String> typeNames;
        private final Set<String> dependencyPaths;

        private CachedSourceState(
                long lastModified,
                long fileLength,
                String packageName,
                List<String> typeNames,
                Set<String> dependencyPaths
        ) {
            this.lastModified = lastModified;
            this.fileLength = fileLength;
            this.packageName = packageName == null ? "" : packageName;
            this.typeNames = new ArrayList<>(typeNames);
            this.dependencyPaths = new LinkedHashSet<>(dependencyPaths);
        }
    }
}
