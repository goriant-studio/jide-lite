package com.jidelite.runner;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
        when(context.getApplicationContext()).thenReturn(applicationContext);
        when(applicationContext.getCacheDir()).thenReturn(cacheDirectory);
        localJavaRunner = new LocalJavaRunner(context, workspaceDirectory);
    }

    @Test
    void runJavaRejectsUnsupportedFileNames() {
        RunResult result = localJavaRunner.runJava("../Main.java", "class Main {}");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getExitCode()).isEqualTo(2);
        assertThat(result.getStdout()).isEmpty();
        assertThat(result.getStderr()).isEqualTo("Unsupported file name: ../Main.java");
        verify(context).getApplicationContext();
        verify(applicationContext).getCacheDir();
    }

    @Test
    void runJavaRejectsEmptySourceCode() {
        RunResult result = localJavaRunner.runJava("Main.java", "   ");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getExitCode()).isEqualTo(2);
        assertThat(result.getStdout()).isEqualTo("$ javac Main.java");
        assertThat(result.getStderr()).isEqualTo("Source code is empty.");
    }

    @Test
    void runJavaRejectsNullFileName() {
        RunResult result = localJavaRunner.runJava(null, "class Main {}");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getExitCode()).isEqualTo(2);
        assertThat(result.getStdout()).isEmpty();
        assertThat(result.getStderr()).isEqualTo("Unsupported file name: ");
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
    void mirrorWorkspaceSourcesCopiesWorkspaceFilesAndOverwritesSelectedFile() throws Exception {
        Files.write(
                new File(workspaceDirectory, "Helper.java").toPath(),
                "class Helper {}\n".getBytes(StandardCharsets.UTF_8)
        );
        Files.write(
                new File(workspaceDirectory, "Main.java").toPath(),
                "class Main { }\n".getBytes(StandardCharsets.UTF_8)
        );
        File sourcesDirectory = new File(rootDirectory, "sources");

        invokePrivate(
                "mirrorWorkspaceSources",
                new Class<?>[]{File.class, String.class, String.class},
                sourcesDirectory,
                "Main.java",
                "class Main {\n    static void run() {}\n}\n"
        );

        String mainSource = new String(
                Files.readAllBytes(new File(sourcesDirectory, "Main.java").toPath()),
                StandardCharsets.UTF_8
        );
        assertThat(new File(sourcesDirectory, "Helper.java")).exists();
        assertThat(mainSource).contains("static void run");
    }

    @Test
    void mirrorWorkspaceSourcesStillWritesSelectedFileWhenWorkspaceDoesNotExist() throws Exception {
        File missingWorkspace = new File(rootDirectory, "missing-workspace");
        LocalJavaRunner runnerWithMissingWorkspace = new LocalJavaRunner(context, missingWorkspace);
        File sourcesDirectory = new File(rootDirectory, "isolated-sources");

        Method method = LocalJavaRunner.class.getDeclaredMethod(
                "mirrorWorkspaceSources",
                File.class,
                String.class,
                String.class
        );
        method.setAccessible(true);
        method.invoke(runnerWithMissingWorkspace, sourcesDirectory, "Solo.java", "class Solo {}\n");

        assertThat(new File(sourcesDirectory, "Solo.java")).exists();
    }

    @Test
    @SuppressWarnings("unchecked")
    void listJavaFilesSortsNamesCaseInsensitively() throws Exception {
        Files.write(new File(rootDirectory, "zeta.java").toPath(), new byte[0]);
        Files.write(new File(rootDirectory, "Alpha.java").toPath(), new byte[0]);
        Files.write(new File(rootDirectory, "middle.txt").toPath(), new byte[0]);

        List<File> javaFiles = (List<File>) invokePrivate(
                "listJavaFiles",
                new Class<?>[]{File.class},
                rootDirectory
        );

        assertThat(javaFiles).extracting(File::getName).containsExactly("Alpha.java", "zeta.java");
    }

    @Test
    @SuppressWarnings("unchecked")
    void listJavaFilesReturnsEmptyWhenDirectoryIsMissing() throws Exception {
        List<File> javaFiles = (List<File>) invokePrivate(
                "listJavaFiles",
                new Class<?>[]{File.class},
                new File(rootDirectory, "not-found")
        );

        assertThat(javaFiles).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void listClassFilesRecursesIntoNestedDirectoriesAndSortsResults() throws Exception {
        File classesRoot = new File(rootDirectory, "classes");
        File nestedDirectory = new File(classesRoot, "nested");
        assertThat(nestedDirectory.mkdirs()).isTrue();
        Files.write(new File(nestedDirectory, "B.class").toPath(), new byte[0]);
        Files.write(new File(classesRoot, "A.class").toPath(), new byte[0]);
        Files.write(new File(classesRoot, "ignore.txt").toPath(), new byte[0]);

        List<File> classFiles = (List<File>) invokePrivate(
                "listClassFiles",
                new Class<?>[]{File.class},
                classesRoot
        );

        assertThat(classFiles).extracting(File::getName).containsExactly("A.class", "B.class");
    }

    private Object invokePrivate(String methodName, Class<?>[] parameterTypes, Object... arguments) throws Exception {
        Method method = LocalJavaRunner.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(localJavaRunner, arguments);
    }
}
