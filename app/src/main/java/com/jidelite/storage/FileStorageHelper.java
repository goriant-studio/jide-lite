package com.jidelite.storage;

import android.content.Context;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

public class FileStorageHelper {

    private static final String WORKSPACE_DIRECTORY_NAME = "workspace";
    private static final Pattern SAFE_FILE_NAME = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*\\.java$");

    private final File workspaceDirectory;

    public FileStorageHelper(Context context) {
        this.workspaceDirectory = new File(context.getApplicationContext().getFilesDir(), WORKSPACE_DIRECTORY_NAME);
    }

    public File getWorkspaceDirectory() {
        return workspaceDirectory;
    }

    public void initializeWorkspace() throws IOException {
        ensureWorkspaceDirectory();
        if (listJavaFiles().isEmpty()) {
            saveFile("Main.java", buildTemplate("Main", "Hello from J-IDE Lite"));
        }
    }

    public List<File> listJavaFiles() throws IOException {
        ensureWorkspaceDirectory();
        File[] files = workspaceDirectory.listFiles((directory, name) -> name.endsWith(".java"));
        List<File> javaFiles = new ArrayList<>();
        if (files != null) {
            for (File file : files) {
                javaFiles.add(file);
            }
        }

        javaFiles.sort(new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                if ("Main.java".equals(left.getName())) {
                    return -1;
                }
                if ("Main.java".equals(right.getName())) {
                    return 1;
                }
                return left.getName().compareToIgnoreCase(right.getName());
            }
        });
        return javaFiles;
    }

    public File createNewJavaFile() throws IOException {
        ensureWorkspaceDirectory();
        int index = 1;
        File candidate;
        String className;
        do {
            className = index == 1 ? "Main" : "Main" + index;
            candidate = new File(workspaceDirectory, className + ".java");
            index++;
        } while (candidate.exists());

        saveFile(candidate.getName(), buildTemplate(className, "Hello from " + className));
        return candidate;
    }

    public String readFile(String fileName) throws IOException {
        File targetFile = resolveFile(fileName);
        if (!targetFile.exists()) {
            throw new IOException("File does not exist: " + fileName);
        }

        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(targetFile), StandardCharsets.UTF_8))) {
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                if (!firstLine) {
                    content.append('\n');
                }
                content.append(line);
                firstLine = false;
            }
        }
        return content.toString();
    }

    public void saveFile(String fileName, String content) throws IOException {
        File targetFile = resolveFile(fileName);
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(targetFile, false), StandardCharsets.UTF_8))) {
            writer.write(content == null ? "" : content);
        }
    }

    private void ensureWorkspaceDirectory() throws IOException {
        if (workspaceDirectory.exists()) {
            return;
        }

        if (!workspaceDirectory.mkdirs() && !workspaceDirectory.exists()) {
            throw new IOException("Unable to create workspace directory.");
        }
    }

    private File resolveFile(String fileName) throws IOException {
        if (fileName == null || fileName.trim().isEmpty() || !SAFE_FILE_NAME.matcher(fileName).matches()) {
            throw new IOException("Invalid file name: " + fileName);
        }

        ensureWorkspaceDirectory();
        File targetFile = new File(workspaceDirectory, fileName);
        String workspacePath = workspaceDirectory.getCanonicalPath();
        String targetPath = targetFile.getCanonicalPath();
        if (!targetPath.equals(workspacePath)
                && !targetPath.startsWith(workspacePath + File.separator)) {
            throw new IOException("Invalid workspace file path.");
        }
        return targetFile;
    }

    private String buildTemplate(String className, String message) {
        return "public class " + className + " {\n"
                + "    public static void main(String[] args) {\n"
                + "        System.out.println(\"" + message + "\");\n"
                + "    }\n"
                + "}\n";
    }
}
