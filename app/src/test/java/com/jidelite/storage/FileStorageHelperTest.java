package com.jidelite.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

@ExtendWith(MockitoExtension.class)
public class FileStorageHelperTest {

    @TempDir
    Path tempDir;

    @Mock
    private Context context;

    @Mock
    private Context applicationContext;

    private File filesDirectory;
    private FileStorageHelper fileStorageHelper;

    @BeforeEach
    void setUp() {
        filesDirectory = tempDir.resolve("files").toFile();
        assertThat(filesDirectory.mkdirs()).isTrue();
        when(context.getApplicationContext()).thenReturn(applicationContext);
        when(applicationContext.getFilesDir()).thenReturn(filesDirectory);
        fileStorageHelper = new FileStorageHelper(context);
    }

    @Test
    void initializeWorkspaceCreatesMainTemplateWhenEmpty() throws Exception {
        fileStorageHelper.initializeWorkspace();

        List<File> javaFiles = fileStorageHelper.listJavaFiles();

        assertThat(javaFiles).extracting(File::getName).containsExactly("Main.java");
        assertThat(fileStorageHelper.readFile("Main.java")).contains("Hello from J-IDE Lite");
        verify(context).getApplicationContext();
        verify(applicationContext).getFilesDir();
    }

    @Test
    void initializeWorkspaceDoesNotOverwriteExistingMainFile() throws Exception {
        fileStorageHelper.saveFile("Main.java", "public class Main { }\n");

        fileStorageHelper.initializeWorkspace();

        assertThat(fileStorageHelper.readFile("Main.java")).isEqualTo("public class Main { }");
    }

    @Test
    void createNewJavaFileUsesNextAvailableMainName() throws Exception {
        fileStorageHelper.initializeWorkspace();

        File secondFile = fileStorageHelper.createNewJavaFile();
        File thirdFile = fileStorageHelper.createNewJavaFile();

        List<File> javaFiles = fileStorageHelper.listJavaFiles();
        assertThat(secondFile.getName()).isEqualTo("Main2.java");
        assertThat(thirdFile.getName()).isEqualTo("Main3.java");
        assertThat(javaFiles).extracting(File::getName).containsExactly("Main.java", "Main2.java", "Main3.java");
    }

    @Test
    void saveFileAndReadFileRoundTripContent() throws Exception {
        fileStorageHelper.saveFile("Helper.java", "public class Helper {}\n");

        String content = fileStorageHelper.readFile("Helper.java");

        assertThat(content).isEqualTo("public class Helper {}");
    }

    @Test
    void saveFileTreatsNullContentAsEmptyString() throws Exception {
        fileStorageHelper.saveFile("Empty.java", null);

        assertThat(fileStorageHelper.readFile("Empty.java")).isEmpty();
    }

    @Test
    void saveFileRejectsInvalidNames() {
        assertThatThrownBy(() -> fileStorageHelper.saveFile("../Main.java", "class Main {}"))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("Invalid file name");
    }

    @Test
    void readFileFailsWhenTargetDoesNotExist() {
        assertThatThrownBy(() -> fileStorageHelper.readFile("Missing.java"))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("File does not exist: Missing.java");
    }

    @Test
    void listJavaFilesKeepsMainFirstThenSortsRemainingFiles() throws Exception {
        fileStorageHelper.saveFile("Zoo.java", "class Zoo {}\n");
        fileStorageHelper.saveFile("alpha.java", "class alpha {}\n");
        fileStorageHelper.saveFile("Main.java", "class Main {}\n");

        List<File> javaFiles = fileStorageHelper.listJavaFiles();

        assertThat(javaFiles).extracting(File::getName).containsExactly("Main.java", "alpha.java", "Zoo.java");
    }
}
