package com.goriant.jidelite.storage;

import java.io.File;
import java.io.IOException;

/**
 * Shared file-system utilities for directory creation and recursive deletion.
 * <p>
 * Extracted to eliminate duplication across {@code FileStorageHelper},
 * {@code LocalJavaRunner}, and {@code HttpMavenRepositoryClient}.
 */
public final class FileUtils {

    private FileUtils() {
    }

    /**
     * Ensures the given directory exists, creating it (and parents) if needed.
     * Does nothing if the directory already exists.
     *
     * @throws IOException if the directory could not be created
     */
    public static void ensureDirectory(File directory) throws IOException {
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

    /**
     * Ensures the parent directory of the given file exists.
     *
     * @throws IOException if the parent directory could not be created
     */
    public static void ensureParentDirectory(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent == null) {
            throw new IOException("Invalid file path: no parent directory.");
        }
        ensureDirectory(parent);
    }

    /**
     * Recursively deletes a file or directory. Does nothing if the target
     * does not exist or is {@code null}.
     *
     * @throws IOException if any file or directory could not be deleted
     */
    public static void deleteRecursively(File file) throws IOException {
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

    /**
     * Resets a directory by deleting it recursively and recreating it empty.
     *
     * @throws IOException if the directory could not be reset
     */
    public static void resetDirectory(File directory) throws IOException {
        deleteRecursively(directory);
        ensureDirectory(directory);
    }
}
