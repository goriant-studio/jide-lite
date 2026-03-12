package com.goriant.jidelite.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;

import com.goriant.jidelite.model.RunResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@ExtendWith(MockitoExtension.class)
public class LocalJavaRunnerTest {

    @TempDir
    Path tempDir;

    @Mock
    private Context context;

    @Mock
    private Context applicationContext;

    private File rootDirectory;
    private File workspaceDirectory;
    private File cacheDirectory;
    private LocalJavaRunner localJavaRunner;

    @BeforeEach
    void setUp() {
        rootDirectory = tempDir.toFile();
        workspaceDirectory = new File(rootDirectory, "workspace");
        cacheDirectory = new File(rootDirectory, "cache");
        assertThat(workspaceDirectory.mkdirs()).isTrue();
        assertThat(cacheDirectory.mkdirs()).isTrue();
        lenient().when(context.getApplicationContext()).thenReturn(applicationContext);
        lenient().when(applicationContext.getCacheDir()).thenReturn(cacheDirectory);
        lenient().when(applicationContext.getClassLoader()).thenReturn(LocalJavaRunnerTest.class.getClassLoader());
        localJavaRunner = new LocalJavaRunner(context, workspaceDirectory, new EmptyDependencyResolver());
    }

    @Test
    void runRejectsUnsupportedWorkspacePaths() {
        RunResult result = localJavaRunner.run("../Main.java");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getExitCode()).isEqualTo(2);
        assertThat(result.getStdout()).isEmpty();
        assertThat(result.getStderr()).isEqualTo("Unsupported workspace path: ../Main.java");
        verify(context).getApplicationContext();
        verify(applicationContext).getCacheDir();
    }

    @Test
    void runRejectsNullSelection() {
        RunResult result = localJavaRunner.run(null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getExitCode()).isEqualTo(2);
        assertThat(result.getStdout()).isEmpty();
        assertThat(result.getStderr()).isEqualTo("No file selected.");
    }

    @Test
    void qualifyClassNamePrefixesPackageName() throws Exception {
        String qualifiedName = (String) invokePrivate(
                "qualifyClassName",
                new Class<?>[]{String.class, String.class},
                "package demo.sample;\npublic class Main {}\n",
                "Main"
        );

        assertThat(qualifiedName).isEqualTo("demo.sample.Main");
    }

    @Test
    void qualifyClassNameReturnsSimpleNameWhenPackageIsMissing() throws Exception {
        String qualifiedName = (String) invokePrivate(
                "qualifyClassName",
                new Class<?>[]{String.class, String.class},
                "public class Main {}\n",
                "Main"
        );

        assertThat(qualifiedName).isEqualTo("Main");
    }

    @Test
    void normalizeCompilerTextMergesStreamsInStableOrder() throws Exception {
        String compilerText = (String) invokePrivate(
                "normalizeCompilerText",
                new Class<?>[]{String.class, String.class},
                " note: extra output  ",
                " error: compile failed  "
        );

        assertThat(compilerText).isEqualTo("error: compile failed\n\nnote: extra output");
    }

    @Test
    void normalizeCompilerTextReturnsEmptyWhenBothInputsBlank() throws Exception {
        String compilerText = (String) invokePrivate(
                "normalizeCompilerText",
                new Class<?>[]{String.class, String.class},
                "  ",
                null
        );

        assertThat(compilerText).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void discoverSourceRootsUsesMavenLayoutWhenPomExists() throws Exception {
        Files.write(new File(workspaceDirectory, "pom.xml").toPath(), "<project/>".getBytes(StandardCharsets.UTF_8));

        List<File> sourceRoots = (List<File>) invokePrivate("discoverSourceRoots", new Class<?>[0]);

        assertThat(sourceRoots).containsExactly(new File(workspaceDirectory, "src/main/java"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listJavaFilesRecursesIntoNestedDirectories() throws Exception {
        File sourceRoot = new File(workspaceDirectory, "src/main/java");
        assertThat(new File(sourceRoot, "demo").mkdirs()).isTrue();
        Files.write(new File(sourceRoot, "demo/App.java").toPath(), new byte[0]);
        Files.write(new File(sourceRoot, "demo/Helper.java").toPath(), new byte[0]);

        List<File> javaFiles = (List<File>) invokePrivate(
                "listJavaFiles",
                new Class<?>[]{List.class},
                Collections.singletonList(sourceRoot)
        );

        assertThat(javaFiles).extracting(File::getName).containsExactly("App.java", "Helper.java");
    }

    @Test
    void selectEntryPointUsesSelectedJavaFileWithMain() throws Exception {
        File mainFile = writeSource(
                "src/main/java/demo/App.java",
                "package demo;\npublic class App { public static void main(String[] args) {} }\n"
        );

        Object entryPoint = invokePrivate(
                "selectEntryPoint",
                new Class<?>[]{File.class, List.class},
                mainFile,
                Collections.singletonList(mainFile)
        );

        assertThat(readField(entryPoint, "sourceFile")).isEqualTo(mainFile);
        assertThat(readField(entryPoint, "qualifiedClassName")).isEqualTo("demo.App");
    }

    @Test
    void selectEntryPointFallsBackToSingleMainWhenPomIsSelected() throws Exception {
        File pomFile = writeSource("pom.xml", "<project/>");
        File mainFile = writeSource(
                "src/main/java/demo/Main.java",
                "package demo;\npublic class Main { public static void main(String[] args) {} }\n"
        );

        Object entryPoint = invokePrivate(
                "selectEntryPoint",
                new Class<?>[]{File.class, List.class},
                pomFile,
                Collections.singletonList(mainFile)
        );

        assertThat(readField(entryPoint, "sourceFile")).isEqualTo(mainFile);
        assertThat(readField(entryPoint, "qualifiedClassName")).isEqualTo("demo.Main");
    }

    @Test
    void selectEntryPointRejectsSelectedJavaFileWithoutMain() {
        File helperFile = writeSourceUnchecked(
                "src/main/java/demo/Helper.java",
                "package demo;\npublic class Helper { void run() {} }\n"
        );

        Throwable throwable = catchThrowable(() -> invokePrivate(
                "selectEntryPoint",
                new Class<?>[]{File.class, List.class},
                helperFile,
                Collections.singletonList(helperFile)
        ));

        assertThat(throwable).hasRootCauseInstanceOf(IOException.class);
        assertThat(throwable.getCause().getMessage())
                .contains("Selected file does not define main(String[] args)");
    }

    @Test
    void selectEntryPointRejectsAmbiguousPomSelection() {
        File pomFile = writeSourceUnchecked("pom.xml", "<project/>");
        File firstMain = writeSourceUnchecked(
                "src/main/java/demo/First.java",
                "package demo;\npublic class First { public static void main(String[] args) {} }\n"
        );
        File secondMain = writeSourceUnchecked(
                "src/main/java/demo/Second.java",
                "package demo;\npublic class Second { public static void main(String[] args) {} }\n"
        );

        Throwable throwable = catchThrowable(() -> invokePrivate(
                "selectEntryPoint",
                new Class<?>[]{File.class, List.class},
                pomFile,
                Arrays.asList(firstMain, secondMain)
        ));

        assertThat(throwable).hasRootCauseInstanceOf(IOException.class);
        assertThat(throwable.getCause().getMessage())
                .contains("Multiple runnable classes found");
    }

    @Test
    void resolveDependenciesRejectsWorkspaceWithoutPom() {
        RunResult result = localJavaRunner.resolveDependencies();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getStdout()).isEqualTo("$ mvn dependency:resolve");
        assertThat(result.getStderr()).isEqualTo("No pom.xml found in workspace.");
        assertThat(result.getExitCode()).isEqualTo(1);
    }

    @Test
    void resolveDependenciesReportsResolvedArtifacts() throws Exception {
        Files.write(new File(workspaceDirectory, "pom.xml").toPath(), "<project/>".getBytes(StandardCharsets.UTF_8));
        File compileJar = new File(rootDirectory, "compile.jar");
        File runtimeJar = new File(rootDirectory, "runtime.jar");
        Files.write(compileJar.toPath(), new byte[0]);
        Files.write(runtimeJar.toPath(), new byte[0]);

        LocalJavaRunner runner = new LocalJavaRunner(
                context,
                workspaceDirectory,
                new FixedDependencyResolver(new DependencyResolutionResult(
                        Collections.singletonList(compileJar),
                        Collections.singletonList(runtimeJar)
                ))
        );

        RunResult result = runner.resolveDependencies();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getExitCode()).isEqualTo(0);
        assertThat(result.getStdout()).contains("$ mvn dependency:resolve");
        assertThat(result.getStdout()).contains("Compile jars: 1");
        assertThat(result.getStdout()).contains("Runtime jars: 1");
        assertThat(result.getStdout()).contains("- runtime.jar");
        assertThat(result.getStderr()).isEmpty();
    }

    @Test
    void dexClassesSupportsCommonsLangRuntimeJar() throws Exception {
        File lambdaJar = findCommonsLangJar();
        File dexDir = new File(rootDirectory, "dex-output");
        assertThat(dexDir.mkdirs()).isTrue();

        invokePrivate(
                "dexClasses",
                new Class<?>[]{List.class, List.class, File.class},
                Collections.emptyList(),
                Collections.singletonList(lambdaJar),
                dexDir
        );

        assertThat(dexDir.listFiles((dir, name) -> name.endsWith(".dex"))).isNotEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void compileSourcesCompilesPeerTypesReferencedBySimpleName() throws Exception {
        File mainFile = writeSource(
                "src/main/java/demo/Main.java",
                "package demo;\n"
                        + "public class Main {\n"
                        + "  public static void main(String[] args) {\n"
                        + "    Person person = new Person(\"Ava\");\n"
                        + "    System.out.println(person.name());\n"
                        + "  }\n"
                        + "}\n"
        );
        File personFile = writeSource(
                "src/main/java/demo/Person.java",
                "package demo;\n"
                        + "public class Person {\n"
                        + "  private final String name;\n"
                        + "  public Person(String name) { this.name = name; }\n"
                        + "  public String name() { return this.name; }\n"
                        + "}\n"
        );
        File classesDir = new File(rootDirectory, "classes");
        assertThat(classesDir.mkdirs()).isTrue();

        invokePrivate(
                "compileSources",
                new Class<?>[]{List.class, File.class, List.class},
                Arrays.asList(mainFile, personFile),
                classesDir,
                Collections.emptyList()
        );

        assertThat(new File(classesDir, "demo/Main.class")).exists();
        assertThat(new File(classesDir, "demo/Person.class")).exists();
    }

    @Test
    void buildCompilationPlanUsesCacheHitWhenSourcesAreUnchanged() throws Exception {
        File mainFile = writeSource(
                "src/main/java/demo/Main.java",
                "package demo;\n"
                        + "public class Main {\n"
                        + "  public static void main(String[] args) {\n"
                        + "    System.out.println(new Person().name());\n"
                        + "  }\n"
                        + "}\n"
        );
        File personFile = writeSource(
                "src/main/java/demo/Person.java",
                "package demo;\n"
                        + "public class Person {\n"
                        + "  String name() { return \"Ava\"; }\n"
                        + "}\n"
        );
        File classesDir = new File(rootDirectory, "incremental-classes");
        assertThat(classesDir.mkdirs()).isTrue();

        Object emptyCache = loadBuildCache(new File(rootDirectory, "missing-cache.bin"));
        Object initialPlan = buildCompilationPlan(
                Arrays.asList(mainFile, personFile),
                emptyCache,
                classesDir,
                "deps-v1"
        );
        Object cachedBuild = readField(initialPlan, "nextCache");
        materializeCompiledOutputs(cachedBuild, classesDir);

        Object cachedPlan = buildCompilationPlan(
                Arrays.asList(mainFile, personFile),
                cachedBuild,
                classesDir,
                "deps-v1"
        );

        assertThat(readField(cachedPlan, "fullRebuild")).isEqualTo(false);
        assertThat(compilationTargetNames(cachedPlan)).isEmpty();
    }

    @Test
    void buildCompilationPlanRecompilesChangedSourceAndDependentsOnly() throws Exception {
        File mainFile = writeSource(
                "src/main/java/demo/Main.java",
                "package demo;\n"
                        + "public class Main {\n"
                        + "  public static void main(String[] args) {\n"
                        + "    System.out.println(new Person().name());\n"
                        + "  }\n"
                        + "}\n"
        );
        File personFile = writeSource(
                "src/main/java/demo/Person.java",
                "package demo;\n"
                        + "public class Person {\n"
                        + "  String name() { return \"Ava\"; }\n"
                        + "}\n"
        );
        File utilFile = writeSource(
                "src/main/java/demo/Util.java",
                "package demo;\n"
                        + "public class Util {\n"
                        + "  static int value() { return 1; }\n"
                        + "}\n"
        );
        File classesDir = new File(rootDirectory, "incremental-classes");
        assertThat(classesDir.mkdirs()).isTrue();

        Object emptyCache = loadBuildCache(new File(rootDirectory, "missing-cache.bin"));
        Object initialPlan = buildCompilationPlan(
                Arrays.asList(mainFile, personFile, utilFile),
                emptyCache,
                classesDir,
                "deps-v1"
        );
        Object cachedBuild = readField(initialPlan, "nextCache");
        materializeCompiledOutputs(cachedBuild, classesDir);

        Files.write(
                personFile.toPath(),
                ("package demo;\n"
                        + "public class Person {\n"
                        + "  String name() { return \"Nova\"; }\n"
                        + "}\n").getBytes(StandardCharsets.UTF_8)
        );
        assertThat(personFile.setLastModified(personFile.lastModified() + 2_000L)).isTrue();

        Object incrementalPlan = buildCompilationPlan(
                Arrays.asList(mainFile, personFile, utilFile),
                cachedBuild,
                classesDir,
                "deps-v1"
        );

        assertThat(readField(incrementalPlan, "fullRebuild")).isEqualTo(false);
        assertThat(compilationTargetNames(incrementalPlan))
                .containsExactlyInAnyOrder("Main.java", "Person.java");
    }

    @Test
    void invokeMainMethodWithTimeoutStopsLongRunningProgram() throws Exception {
        LocalJavaRunner runner = new LocalJavaRunner(context, workspaceDirectory, new EmptyDependencyResolver(), 50L);

        Object outcome = invokePrivateOn(
                runner,
                "invokeMainMethodWithTimeout",
                new Class<?>[]{Method.class},
                LongSleepingProgram.class.getDeclaredMethod("main", String[].class)
        );

        assertThat(readField(outcome, "timedOut")).isEqualTo(true);
        assertThat((String) readField(outcome, "stderr"))
                .contains("Runtime timed out after 50ms and was terminated.");
    }

    private File writeSource(String relativePath, String content) throws Exception {
        File file = new File(workspaceDirectory, relativePath);
        File parent = file.getParentFile();
        if (parent != null) {
            assertThat(parent.mkdirs() || parent.exists()).isTrue();
        }
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private File writeSourceUnchecked(String relativePath, String content) {
        try {
            return writeSource(relativePath, content);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private Throwable catchThrowable(ThrowingRunnable runnable) {
        try {
            runnable.run();
            return null;
        } catch (Throwable throwable) {
            return throwable;
        }
    }

    private Object invokePrivate(String methodName, Class<?>[] parameterTypes, Object... arguments) throws Exception {
        return invokePrivateOn(localJavaRunner, methodName, parameterTypes, arguments);
    }

    private Object invokePrivateOn(Object target, String methodName, Class<?>[] parameterTypes, Object... arguments) throws Exception {
        Method method = LocalJavaRunner.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, arguments);
    }

    private Object readField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private Object loadBuildCache(File metadataFile) throws Exception {
        return invokePrivate("loadBuildCache", new Class<?>[]{File.class}, metadataFile);
    }

    private Object buildCompilationPlan(
            List<File> sourceFiles,
            Object buildCache,
            File classesDir,
            String dependencyFingerprint
    ) throws Exception {
        return invokePrivate(
                "buildCompilationPlan",
                new Class<?>[]{List.class, innerClass("BuildCache"), File.class, String.class},
                sourceFiles,
                buildCache,
                classesDir,
                dependencyFingerprint
        );
    }

    @SuppressWarnings("unchecked")
    private List<String> compilationTargetNames(Object compilationPlan) throws Exception {
        List<File> sourceFiles = (List<File>) readField(compilationPlan, "sourcesToCompile");
        return sourceFiles.stream().map(File::getName).toList();
    }

    @SuppressWarnings("unchecked")
    private void materializeCompiledOutputs(Object buildCache, File classesDir) throws Exception {
        Map<String, Object> sourcesByPath = (Map<String, Object>) readField(buildCache, "sourcesByPath");
        for (Object cachedSourceState : sourcesByPath.values()) {
            String packageName = (String) readField(cachedSourceState, "packageName");
            List<String> typeNames = (List<String>) readField(cachedSourceState, "typeNames");
            File packageDirectory = packageName.isEmpty()
                    ? classesDir
                    : new File(classesDir, packageName.replace('.', File.separatorChar));
            assertThat(packageDirectory.mkdirs() || packageDirectory.exists()).isTrue();
            for (String typeName : typeNames) {
                Files.write(new File(packageDirectory, typeName + ".class").toPath(), new byte[0]);
            }
        }
    }

    private Class<?> innerClass(String simpleName) {
        for (Class<?> candidate : LocalJavaRunner.class.getDeclaredClasses()) {
            if (candidate.getSimpleName().equals(simpleName)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Missing inner class " + simpleName);
    }

    private File findCommonsLangJar() throws Exception {
        Path gradleHome = new File(System.getProperty("user.home"), ".gradle").toPath();
        try (Stream<Path> candidates = Files.walk(gradleHome)) {
            Path jarPath = candidates
                    .filter(Files::isRegularFile)
                    .filter(path -> "commons-lang3-3.14.0.jar".equals(path.getFileName().toString()))
                    .findFirst()
                    .orElseThrow(() -> new IOException("Could not locate commons-lang3-3.14.0.jar under ~/.gradle"));
            return jarPath.toFile();
        }
    }

    private interface ThrowingRunnable {
        void run() throws Throwable;
    }

    private static final class EmptyDependencyResolver implements ProjectDependencyResolver {
        @Override
        public DependencyResolutionResult resolve(File workspaceDirectory) {
            return DependencyResolutionResult.empty();
        }
    }

    private static final class FixedDependencyResolver implements ProjectDependencyResolver {
        private final DependencyResolutionResult result;

        private FixedDependencyResolver(DependencyResolutionResult result) {
            this.result = result;
        }

        @Override
        public DependencyResolutionResult resolve(File workspaceDirectory) {
            return result;
        }
    }

    @SuppressWarnings("unused")
    private static final class LongSleepingProgram {
        public static void main(String[] args) throws Exception {
            Thread.sleep(Long.MAX_VALUE);
        }
    }
}
