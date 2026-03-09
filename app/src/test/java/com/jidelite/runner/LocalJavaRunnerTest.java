package com.jidelite.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;

import com.jidelite.model.RunResult;

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
        Method method = LocalJavaRunner.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(localJavaRunner, arguments);
    }

    private Object readField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
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
}
