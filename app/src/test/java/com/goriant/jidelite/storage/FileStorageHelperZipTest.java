package com.goriant.jidelite.storage;

import android.content.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

class FileStorageHelperZipTest {

    @Mock
    private Context mockContext;

    private FileStorageHelper fileStorageHelper;

    @BeforeEach
    void setUp(@TempDir File tempDir) {
        MockitoAnnotations.openMocks(this);
        File appDir = new File(tempDir, "app_dir");
        when(mockContext.getApplicationContext()).thenReturn(mockContext);
        when(mockContext.getFilesDir()).thenReturn(appDir);

        fileStorageHelper = new FileStorageHelper(mockContext);
    }

    @Test
    void exportAndImportRoundTrip(@TempDir File tempDir) throws Exception {
        // 1. Initialize workspace with sample files
        fileStorageHelper.initializeWorkspace();
        fileStorageHelper.saveFile("src/main/java/Main2.java", "public class Main2 {}");
        
        List<File> initialFiles = fileStorageHelper.listWorkspaceFiles();
        assertThat(initialFiles).hasSize(4); // pom.xml, demo/Main.java, demo/Person.java, Main2.java

        // 2. Export to ZIP
        File exportDir = new File(tempDir, "export");
        exportDir.mkdirs();
        File zipFile = fileStorageHelper.exportWorkspaceAsZip(exportDir);
        
        assertThat(zipFile).exists();
        assertThat(zipFile.getName()).endsWith(".zip");

        // 3. Clear workspace manually to simulate a fresh start
        File workspaceDir = fileStorageHelper.getWorkspaceDirectory();
        for (File child : workspaceDir.listFiles()) {
            deleteRecursively(child);
        }
        assertThat(fileStorageHelper.listWorkspaceFiles()).isEmpty();

        // 4. Import from ZIP
        try (FileInputStream fis = new FileInputStream(zipFile)) {
            fileStorageHelper.importWorkspaceFromZip(fis);
        }

        // 5. Verify restored files
        List<File> restoredFiles = fileStorageHelper.listWorkspaceFiles();
        assertThat(restoredFiles).hasSize(4);
        assertThat(restoredFiles).extracting(fileStorageHelper::toWorkspaceRelativePath)
                .containsExactlyInAnyOrder(
                        "pom.xml",
                        "src/main/java/demo/Main.java",
                        "src/main/java/demo/Person.java",
                        "src/main/java/Main2.java"
                );
        
        assertThat(fileStorageHelper.readFile("src/main/java/Main2.java")).isEqualTo("public class Main2 {}");
    }

    @Test
    void importPreventsZipSlipVulnerability() throws Exception {
        fileStorageHelper.initializeWorkspace();
        
        // Create a malicious ZIP file in memory
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // Attempt to write outside the workspace directory
            ZipEntry maliciousEntry = new ZipEntry("../malicious.txt");
            zos.putNextEntry(maliciousEntry);
            zos.write("pwned".getBytes());
            zos.closeEntry();
        }

        ByteArrayInputStream zipStream = new ByteArrayInputStream(baos.toByteArray());

        // Attempting to import should throw an IOException due to path validation
        assertThatThrownBy(() -> fileStorageHelper.importWorkspaceFromZip(zipStream))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("outside of the target dir");
    }

    private void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }
}
