package com.goriant.jidelite.storage;

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
    void initializeWorkspaceCreatesMavenSampleWhenEmpty() throws Exception {
        fileStorageHelper.initializeWorkspace();

        List<File> workspaceFiles = fileStorageHelper.listWorkspaceFiles();

        assertThat(workspaceFiles).extracting(fileStorageHelper::toWorkspaceRelativePath)
                .containsExactly("pom.xml", "src/main/java/demo/Main.java", "src/main/java/demo/Person.java");
        assertThat(fileStorageHelper.readFile("pom.xml")).contains("<artifactId>commons-lang3</artifactId>");
        assertThat(fileStorageHelper.readFile("src/main/java/demo/Main.java"))
                .contains("StringUtils.capitalize")
                .contains("Loaded: ");
        verify(context).getApplicationContext();
        verify(applicationContext).getFilesDir();
    }

    @Test
    void initializeWorkspaceDoesNotOverwriteExistingMainFile() throws Exception {
        fileStorageHelper.saveFile("Main.java", "public class Main { }\n");

        fileStorageHelper.initializeWorkspace();

        assertThat(fileStorageHelper.listWorkspaceFiles()).extracting(fileStorageHelper::toWorkspaceRelativePath)
                .containsExactly("Main.java");
    }

    @Test
    void initializeWorkspaceReplacesOriginalSeededMainWithMavenSample() throws Exception {
        fileStorageHelper.saveFile(
                "Main.java",
                "public class Main {\n"
                        + "    public static void main(String[] args) {\n"
                        + "        System.out.println(\"Hello from J-IDE Lite\");\n"
                        + "    }\n"
                        + "}\n"
        );

        fileStorageHelper.initializeWorkspace();

        assertThat(fileStorageHelper.listWorkspaceFiles()).extracting(fileStorageHelper::toWorkspaceRelativePath)
                .containsExactly("pom.xml", "src/main/java/demo/Main.java", "src/main/java/demo/Person.java");
    }

    @Test
    void initializeWorkspaceLeavesMavenWorkspaceUntouched() throws Exception {
        fileStorageHelper.saveFile("pom.xml", "<project/>");

        fileStorageHelper.initializeWorkspace();

        assertThat(fileStorageHelper.listWorkspaceFiles()).extracting(File::getName).containsExactly("pom.xml");
    }

    @Test
    void createNewJavaFileUsesNextAvailableMainName() throws Exception {
        fileStorageHelper.initializeWorkspace();

        File secondFile = fileStorageHelper.createNewJavaFile();
        File thirdFile = fileStorageHelper.createNewJavaFile();

        List<File> javaFiles = fileStorageHelper.listJavaFiles();
        assertThat(secondFile.getName()).isEqualTo("Main2.java");
        assertThat(thirdFile.getName()).isEqualTo("Main3.java");
        assertThat(javaFiles).extracting(fileStorageHelper::toWorkspaceRelativePath)
                .containsExactly("src/main/java/demo/Main.java", "src/main/java/demo/Person.java", "src/main/java/Main2.java", "src/main/java/Main3.java");
    }

    @Test
    void createNewJavaFileUsesMavenSourceRootWhenPomExists() throws Exception {
        fileStorageHelper.saveFile("pom.xml", "<project/>");

        File createdFile = fileStorageHelper.createNewJavaFile();

        assertThat(fileStorageHelper.toWorkspaceRelativePath(createdFile)).isEqualTo("src/main/java/Main.java");
        assertThat(fileStorageHelper.readFile(createdFile)).contains("Hello from Main");
    }

    @Test
    void createNewFolderUsesNextAvailableNameInSourceRoot() throws Exception {
        fileStorageHelper.saveFile("pom.xml", "<project/>");

        File firstFolder = fileStorageHelper.createNewFolder();
        File secondFolder = fileStorageHelper.createNewFolder();

        assertThat(fileStorageHelper.toWorkspaceEntryRelativePath(firstFolder)).isEqualTo("src/main/java/folder");
        assertThat(fileStorageHelper.toWorkspaceEntryRelativePath(secondFolder)).isEqualTo("src/main/java/folder2");
        assertThat(firstFolder.isDirectory()).isTrue();
        assertThat(secondFolder.isDirectory()).isTrue();
    }

    @Test
    void createNewJavaFileUsesProvidedDirectory() throws Exception {
        fileStorageHelper.saveFile("src/main/Existing.java", "class Existing {}\n");
        File targetDirectory = new File(fileStorageHelper.getWorkspaceDirectory(), "src/main");

        File createdFile = fileStorageHelper.createNewJavaFile(targetDirectory);

        assertThat(fileStorageHelper.toWorkspaceEntryRelativePath(createdFile)).isEqualTo("src/main/Main.java");
    }

    @Test
    void createNewFolderUsesProvidedDirectory() throws Exception {
        File targetDirectory = new File(fileStorageHelper.getWorkspaceDirectory(), "src/main");
        fileStorageHelper.saveFile("src/main/java/demo/App.java", "class App {}\n");

        File createdFolder = fileStorageHelper.createNewFolder(targetDirectory);

        assertThat(fileStorageHelper.toWorkspaceEntryRelativePath(createdFolder)).isEqualTo("src/main/folder");
    }

    @Test
    void saveFileAndReadFileRoundTripContent() throws Exception {
        fileStorageHelper.saveFile("src/main/java/demo/Helper.java", "public class Helper {}\n");

        String content = fileStorageHelper.readFile("src/main/java/demo/Helper.java");

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
                .hasMessageContaining("Invalid workspace file path");
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

    @Test
    void listWorkspaceFilesIncludesPomAndNestedJavaFiles() throws Exception {
        fileStorageHelper.saveFile("pom.xml", "<project/>");
        fileStorageHelper.saveFile("src/main/java/demo/App.java", "class App {}\n");
        fileStorageHelper.saveFile("src/main/java/demo/Helper.java", "class Helper {}\n");

        List<File> files = fileStorageHelper.listWorkspaceFiles();
        List<String> relativePaths = new java.util.ArrayList<>();
        for (File file : files) {
            relativePaths.add(fileStorageHelper.toWorkspaceRelativePath(file));
        }

        assertThat(relativePaths)
                .containsExactly("pom.xml", "src/main/java/demo/App.java", "src/main/java/demo/Helper.java");
    }

    @Test
    void listWorkspaceEntriesIncludesFoldersAndSupportedFiles() throws Exception {
        fileStorageHelper.saveFile("pom.xml", "<project/>");
        fileStorageHelper.saveFile("src/main/java/demo/App.java", "class App {}\n");

        List<File> entries = fileStorageHelper.listWorkspaceEntries();

        assertThat(entries).extracting(fileStorageHelper::toWorkspaceEntryRelativePath).containsExactly(
                "pom.xml",
                "src",
                "src/main",
                "src/main/java",
                "src/main/java/demo",
                "src/main/java/demo/App.java"
        );
    }

    @Test
    void deleteEntryRemovesFileFromWorkspace() throws Exception {
        fileStorageHelper.saveFile("src/main/java/demo/App.java", "class App {}\n");
        File target = new File(fileStorageHelper.getWorkspaceDirectory(), "src/main/java/demo/App.java");

        fileStorageHelper.deleteEntry(target);

        assertThat(fileStorageHelper.listWorkspaceFiles()).isEmpty();
    }

    @Test
    void deleteEntryRemovesDirectoryRecursively() throws Exception {
        fileStorageHelper.saveFile("src/main/java/demo/App.java", "class App {}\n");
        fileStorageHelper.saveFile("src/main/java/demo/Helper.java", "class Helper {}\n");
        File folder = new File(fileStorageHelper.getWorkspaceDirectory(), "src/main/java/demo");

        fileStorageHelper.deleteEntry(folder);

        assertThat(fileStorageHelper.listWorkspaceEntries())
                .extracting(fileStorageHelper::toWorkspaceEntryRelativePath)
                .containsExactly("src", "src/main", "src/main/java");
    }

    @Test
    void renameEntryRenamesJavaFile() throws Exception {
        fileStorageHelper.saveFile("src/main/java/demo/App.java", "class App {}\n");
        File source = new File(fileStorageHelper.getWorkspaceDirectory(), "src/main/java/demo/App.java");

        File renamed = fileStorageHelper.renameEntry(source, "MainApp.java");

        assertThat(fileStorageHelper.toWorkspaceEntryRelativePath(renamed))
                .isEqualTo("src/main/java/demo/MainApp.java");
        assertThat(fileStorageHelper.listWorkspaceEntries())
                .extracting(fileStorageHelper::toWorkspaceEntryRelativePath)
                .contains("src/main/java/demo/MainApp.java");
    }

    @Test
    void renameEntryRenamesFolderAndKeepsChildren() throws Exception {
        fileStorageHelper.saveFile("src/main/java/demo/App.java", "class App {}\n");
        File source = new File(fileStorageHelper.getWorkspaceDirectory(), "src/main/java/demo");

        File renamed = fileStorageHelper.renameEntry(source, "feature");

        assertThat(fileStorageHelper.toWorkspaceEntryRelativePath(renamed))
                .isEqualTo("src/main/java/feature");
        assertThat(fileStorageHelper.listWorkspaceEntries())
                .extracting(fileStorageHelper::toWorkspaceEntryRelativePath)
                .contains("src/main/java/feature", "src/main/java/feature/App.java");
    }

    @Test
    void renameEntryRejectsUnsupportedFileExtension() throws Exception {
        fileStorageHelper.saveFile("Main.java", "class Main {}\n");
        File source = new File(fileStorageHelper.getWorkspaceDirectory(), "Main.java");

        assertThatThrownBy(() -> fileStorageHelper.renameEntry(source, "Main.txt"))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("Invalid file name");
    }

    @Test
    void moveEntryToDirectoryMovesFileAcrossFolders() throws Exception {
        fileStorageHelper.saveFile("src/a/Main.java", "class Main {}\n");
        fileStorageHelper.saveFile("src/b/Target.java", "class Target {}\n");
        File source = new File(fileStorageHelper.getWorkspaceDirectory(), "src/a/Main.java");
        File destinationFolder = new File(fileStorageHelper.getWorkspaceDirectory(), "src/b");

        File moved = fileStorageHelper.moveEntryToDirectory(source, destinationFolder);

        assertThat(fileStorageHelper.toWorkspaceEntryRelativePath(moved)).isEqualTo("src/b/Main.java");
        assertThat(fileStorageHelper.listWorkspaceEntries())
                .extracting(fileStorageHelper::toWorkspaceEntryRelativePath)
                .contains("src/b/Main.java")
                .doesNotContain("src/a/Main.java");
    }

    @Test
    void moveEntryToDirectoryRejectsMovingFolderIntoItsChild() throws Exception {
        fileStorageHelper.saveFile("src/a/Main.java", "class Main {}\n");
        File sourceFolder = new File(fileStorageHelper.getWorkspaceDirectory(), "src");
        File childFolder = new File(fileStorageHelper.getWorkspaceDirectory(), "src/a");

        assertThatThrownBy(() -> fileStorageHelper.moveEntryToDirectory(sourceFolder, childFolder))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("Cannot move a folder into itself");
    }
}
