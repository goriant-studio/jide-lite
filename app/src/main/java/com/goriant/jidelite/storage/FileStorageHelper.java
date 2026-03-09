package com.goriant.jidelite.storage;

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
    private static final String POM_FILE_NAME = "pom.xml";
    private static final String MAVEN_SOURCE_ROOT = "src/main/java";
    private static final String SAMPLE_GROUP_ID = "com.goriant.jidelite.samples";
    private static final String SAMPLE_ARTIFACT_ID = "apache-commons-demo";
    private static final String SAMPLE_VERSION = "1.0.0";
    private static final String SAMPLE_COMMONS_LANG_VERSION = "3.14.0";
    private static final Pattern SAFE_PATH_SEGMENT = Pattern.compile("^[A-Za-z0-9._-]+$");

    private final File workspaceDirectory;

    public FileStorageHelper(Context context) {
        this.workspaceDirectory = new File(context.getApplicationContext().getFilesDir(), WORKSPACE_DIRECTORY_NAME);
    }

    public File getWorkspaceDirectory() {
        return workspaceDirectory;
    }

    public void initializeWorkspace() throws IOException {
        ensureWorkspaceDirectory();
        if (shouldCreateSampleMavenProject()) {
            createSampleMavenProject();
        }
    }

    public boolean isMavenProject() throws IOException {
        ensureWorkspaceDirectory();
        return getPomFile().exists();
    }

    public File getPomFile() {
        return new File(workspaceDirectory, POM_FILE_NAME);
    }

    public File getPrimarySourceDirectory() throws IOException {
        ensureWorkspaceDirectory();
        return isMavenProject() ? new File(workspaceDirectory, MAVEN_SOURCE_ROOT) : workspaceDirectory;
    }

    public List<File> listWorkspaceFiles() throws IOException {
        ensureWorkspaceDirectory();

        List<File> files = new ArrayList<>();
        collectWorkspaceFiles(workspaceDirectory, files);
        files.sort(new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                try {
                    String leftPath = toWorkspaceRelativePath(left);
                    String rightPath = toWorkspaceRelativePath(right);
                    if (POM_FILE_NAME.equals(leftPath)) {
                        return -1;
                    }
                    if (POM_FILE_NAME.equals(rightPath)) {
                        return 1;
                    }
                    return leftPath.compareToIgnoreCase(rightPath);
                } catch (IOException exception) {
                    return left.getAbsolutePath().compareToIgnoreCase(right.getAbsolutePath());
                }
            }
        });
        return files;
    }

    public List<File> listWorkspaceEntries() throws IOException {
        ensureWorkspaceDirectory();

        List<File> entries = new ArrayList<>();
        collectWorkspaceEntries(workspaceDirectory, entries);
        entries.sort(new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                try {
                    String leftPath = toWorkspaceRelativePathForEntry(left);
                    String rightPath = toWorkspaceRelativePathForEntry(right);
                    if (POM_FILE_NAME.equals(leftPath)) {
                        return -1;
                    }
                    if (POM_FILE_NAME.equals(rightPath)) {
                        return 1;
                    }
                    if (left.isDirectory() != right.isDirectory()) {
                        return left.isDirectory() ? -1 : 1;
                    }
                    return leftPath.compareToIgnoreCase(rightPath);
                } catch (IOException exception) {
                    return left.getAbsolutePath().compareToIgnoreCase(right.getAbsolutePath());
                }
            }
        });
        return entries;
    }

    public List<File> listJavaFiles() throws IOException {
        ensureWorkspaceDirectory();

        List<File> javaFiles = new ArrayList<>();
        collectJavaFiles(workspaceDirectory, javaFiles);
        javaFiles.sort(new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                try {
                    String leftPath = toWorkspaceRelativePath(left);
                    String rightPath = toWorkspaceRelativePath(right);
                    if ("Main.java".equals(leftPath) || (MAVEN_SOURCE_ROOT + "/Main.java").equals(leftPath)) {
                        return -1;
                    }
                    if ("Main.java".equals(rightPath) || (MAVEN_SOURCE_ROOT + "/Main.java").equals(rightPath)) {
                        return 1;
                    }
                    return leftPath.compareToIgnoreCase(rightPath);
                } catch (IOException exception) {
                    return left.getAbsolutePath().compareToIgnoreCase(right.getAbsolutePath());
                }
            }
        });
        return javaFiles;
    }

    public File createNewJavaFile() throws IOException {
        File sourceDirectory = getPrimarySourceDirectory();
        ensureDirectory(sourceDirectory);
        List<File> existingJavaFiles = listJavaFiles();

        int index = 1;
        File candidate;
        String className;
        do {
            className = index == 1 ? "Main" : "Main" + index;
            candidate = new File(sourceDirectory, className + ".java");
            index++;
        } while (candidate.exists() || containsFileName(existingJavaFiles, candidate.getName()));

        saveFile(candidate, buildTemplate(className, "Hello from " + className));
        return candidate;
    }

    public File createNewJavaFile(File directory) throws IOException {
        File targetDirectory = validateWorkspaceDirectory(directory);
        ensureDirectory(targetDirectory);

        int index = 1;
        File candidate;
        String className;
        do {
            className = index == 1 ? "Main" : "Main" + index;
            candidate = new File(targetDirectory, className + ".java");
            index++;
        } while (candidate.exists());

        saveFile(candidate, buildTemplate(className, "Hello from " + className));
        return candidate;
    }

    public File createNewFolder() throws IOException {
        File sourceDirectory = getPrimarySourceDirectory();
        ensureDirectory(sourceDirectory);

        int index = 1;
        File candidate;
        do {
            String folderName = index == 1 ? "folder" : "folder" + index;
            candidate = new File(sourceDirectory, folderName);
            index++;
        } while (candidate.exists());

        ensureDirectory(candidate);
        return candidate;
    }

    public File createNewFolder(File directory) throws IOException {
        File targetDirectory = validateWorkspaceDirectory(directory);
        ensureDirectory(targetDirectory);

        int index = 1;
        File candidate;
        do {
            String folderName = index == 1 ? "folder" : "folder" + index;
            candidate = new File(targetDirectory, folderName);
            index++;
        } while (candidate.exists());

        ensureDirectory(candidate);
        return candidate;
    }

    public String readFile(String filePath) throws IOException {
        return readFile(resolveFile(filePath));
    }

    public String readFile(File file) throws IOException {
        File targetFile = validateWorkspaceFile(file);
        if (!targetFile.exists()) {
            throw new IOException("File does not exist: " + targetFile.getName());
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

    public void saveFile(String filePath, String content) throws IOException {
        saveFile(resolveFile(filePath), content);
    }

    public void saveFile(File file, String content) throws IOException {
        File targetFile = validateWorkspaceFile(file);
        ensureParentDirectory(targetFile);
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(targetFile, false), StandardCharsets.UTF_8))) {
            writer.write(content == null ? "" : content);
        }
    }

    public String toWorkspaceRelativePath(File file) throws IOException {
        File targetFile = validateWorkspaceFile(file);
        return toWorkspaceRelativePathForEntry(targetFile);
    }

    public String toWorkspaceEntryRelativePath(File file) throws IOException {
        return toWorkspaceRelativePathForEntry(file);
    }

    public void deleteEntry(File file) throws IOException {
        File target = validateWorkspaceEntry(file);
        if (!target.exists()) {
            throw new IOException("Entry does not exist: " + target.getName());
        }

        String workspacePath = workspaceDirectory.getCanonicalPath();
        String targetPath = target.getCanonicalPath();
        if (workspacePath.equals(targetPath)) {
            throw new IOException("Cannot delete workspace root.");
        }

        deleteRecursively(target);
    }

    public File renameEntry(File file, String newName) throws IOException {
        File source = validateWorkspaceEntry(file);
        if (!source.exists()) {
            throw new IOException("Entry does not exist: " + source.getName());
        }

        String normalizedName = newName == null ? "" : newName.trim();
        if (normalizedName.isEmpty()) {
            throw new IOException("Invalid file name: " + newName);
        }
        if (!SAFE_PATH_SEGMENT.matcher(normalizedName).matches()) {
            throw new IOException("Invalid file name: " + normalizedName);
        }

        if (source.isFile()) {
            String existingName = source.getName();
            if (POM_FILE_NAME.equals(existingName)) {
                if (!POM_FILE_NAME.equals(normalizedName)) {
                    throw new IOException("pom.xml cannot be renamed.");
                }
            } else if (!normalizedName.endsWith(".java")) {
                throw new IOException("Invalid file name: " + normalizedName);
            }
        }

        File parent = source.getParentFile();
        if (parent == null) {
            throw new IOException("Invalid workspace file path.");
        }
        File destination = new File(parent, normalizedName);
        validateWorkspaceEntry(destination);

        String sourcePath = source.getCanonicalPath();
        String destinationPath = destination.getCanonicalPath();
        if (sourcePath.equals(destinationPath)) {
            return source;
        }
        if (destination.exists()) {
            throw new IOException("Entry already exists: " + normalizedName);
        }
        if (!source.renameTo(destination)) {
            throw new IOException("Could not rename " + source.getName() + " to " + normalizedName);
        }
        return destination;
    }

    public File moveEntryToDirectory(File entry, File destinationDirectory) throws IOException {
        File source = validateWorkspaceEntry(entry);
        File destinationFolder = validateWorkspaceDirectory(destinationDirectory);
        if (!source.exists()) {
            throw new IOException("Entry does not exist: " + source.getName());
        }
        if (!destinationFolder.exists() || !destinationFolder.isDirectory()) {
            throw new IOException("Destination is not a folder.");
        }

        String sourcePath = source.getCanonicalPath();
        String destinationFolderPath = destinationFolder.getCanonicalPath();
        String sourceParentPath = source.getParentFile() == null
                ? ""
                : source.getParentFile().getCanonicalPath();

        if (source.isDirectory()) {
            if (destinationFolderPath.equals(sourcePath)
                    || destinationFolderPath.startsWith(sourcePath + File.separator)) {
                throw new IOException("Cannot move a folder into itself.");
            }
        }

        if (destinationFolderPath.equals(sourceParentPath)) {
            return source;
        }

        File destination = new File(destinationFolder, source.getName());
        validateWorkspaceEntry(destination);
        if (destination.exists()) {
            throw new IOException("Entry already exists: " + destination.getName());
        }
        if (!source.renameTo(destination)) {
            throw new IOException("Could not move " + source.getName() + ".");
        }
        return destination;
    }

    private void collectWorkspaceFiles(File directory, List<File> files) {
        File[] children = directory.listFiles();
        if (children == null) {
            return;
        }

        for (File child : children) {
            if (child.isDirectory()) {
                collectWorkspaceFiles(child, files);
            } else if (isSupportedWorkspaceFile(child)) {
                files.add(child);
            }
        }
    }

    private void collectWorkspaceEntries(File directory, List<File> entries) {
        File[] children = directory.listFiles();
        if (children == null) {
            return;
        }

        for (File child : children) {
            if (child.isDirectory()) {
                entries.add(child);
                collectWorkspaceEntries(child, entries);
            } else if (isSupportedWorkspaceFile(child)) {
                entries.add(child);
            }
        }
    }

    private void collectJavaFiles(File directory, List<File> javaFiles) {
        File[] children = directory.listFiles();
        if (children == null) {
            return;
        }

        for (File child : children) {
            if (child.isDirectory()) {
                collectJavaFiles(child, javaFiles);
            } else if (child.getName().endsWith(".java")) {
                javaFiles.add(child);
            }
        }
    }

    private boolean isSupportedWorkspaceFile(File file) {
        String name = file.getName();
        return name.endsWith(".java") || POM_FILE_NAME.equals(name);
    }

    private void ensureWorkspaceDirectory() throws IOException {
        ensureDirectory(workspaceDirectory);
    }

    private void ensureDirectory(File directory) throws IOException {
        if (directory.exists()) {
            return;
        }

        if (!directory.mkdirs() && !directory.exists()) {
            throw new IOException("Unable to create workspace directory.");
        }
    }

    private void ensureParentDirectory(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent == null) {
            throw new IOException("Invalid workspace file path.");
        }
        ensureDirectory(parent);
    }

    private File resolveFile(String filePath) throws IOException {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IOException("Invalid file name: " + filePath);
        }

        File rawFile = new File(filePath);
        File targetFile = rawFile.isAbsolute() ? rawFile : new File(workspaceDirectory, filePath);
        return validateWorkspaceFile(targetFile);
    }

    private File validateWorkspaceFile(File targetFile) throws IOException {
        ensureWorkspaceDirectory();

        String relativePath = toWorkspaceRelativePathForEntry(targetFile);
        String[] segments = relativePath.split("/");
        String fileName = segments[segments.length - 1];
        if (!fileName.endsWith(".java") && !POM_FILE_NAME.equals(fileName)) {
            throw new IOException("Invalid file name: " + relativePath);
        }
        return targetFile;
    }

    private File validateWorkspaceEntry(File targetFile) throws IOException {
        ensureWorkspaceDirectory();
        toWorkspaceRelativePathForEntry(targetFile);
        return targetFile;
    }

    private File validateWorkspaceDirectory(File targetDirectory) throws IOException {
        File directory = validateWorkspaceEntry(targetDirectory);
        if (directory.exists() && !directory.isDirectory()) {
            throw new IOException("Destination is not a folder.");
        }
        return directory;
    }

    private void deleteRecursively(File target) throws IOException {
        if (target.isDirectory()) {
            File[] children = target.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }

        if (!target.delete() && target.exists()) {
            throw new IOException("Could not delete: " + target.getAbsolutePath());
        }
    }

    private String toWorkspaceRelativePathForEntry(File targetFile) throws IOException {
        String workspacePath = workspaceDirectory.getCanonicalPath();
        String targetPath = targetFile.getCanonicalPath();
        if (!targetPath.startsWith(workspacePath + File.separator)) {
            throw new IOException("Invalid workspace file path.");
        }

        String relativePath = targetPath.substring(workspacePath.length() + 1).replace(File.separatorChar, '/');
        if (relativePath.trim().isEmpty()) {
            throw new IOException("Invalid file name: " + targetFile.getPath());
        }

        String[] segments = relativePath.split("/");
        for (String segment : segments) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)
                    || !SAFE_PATH_SEGMENT.matcher(segment).matches()) {
                throw new IOException("Invalid file name: " + relativePath);
            }
        }
        return relativePath;
    }

    private String buildTemplate(String className, String message) {
        return "public class " + className + " {\n"
                + "    public static void main(String[] args) {\n"
                + "        System.out.println(\"" + message + "\");\n"
                + "    }\n"
                + "}\n";
    }

    private boolean containsFileName(List<File> files, String fileName) {
        for (File file : files) {
            if (fileName.equals(file.getName())) {
                return true;
            }
        }
        return false;
    }

    private boolean shouldCreateSampleMavenProject() throws IOException {
        if (getPomFile().exists()) {
            return false;
        }

        List<File> javaFiles = listJavaFiles();
        if (javaFiles.isEmpty()) {
            return true;
        }

        if (javaFiles.size() != 1) {
            return false;
        }

        File onlyJavaFile = javaFiles.get(0);
        if (!"Main.java".equals(onlyJavaFile.getName())) {
            return false;
        }

        String currentContent = readFile(onlyJavaFile);
        return currentContent.equals(buildTemplate("Main", "Hello from J-IDE Lite").trim());
    }

    private void createSampleMavenProject() throws IOException {
        File legacyMainFile = new File(workspaceDirectory, "Main.java");
        if (legacyMainFile.exists() && !legacyMainFile.delete()) {
            throw new IOException("Could not replace legacy workspace template.");
        }

        saveFile("pom.xml", buildSamplePom());
        saveFile(MAVEN_SOURCE_ROOT + "/demo/Main.java", buildSampleMain());
    }

    private String buildSamplePom() {
        return "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n"
                + "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
                + "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 "
                + "https://maven.apache.org/xsd/maven-4.0.0.xsd\">\n"
                + "    <modelVersion>4.0.0</modelVersion>\n"
                + "    <groupId>" + SAMPLE_GROUP_ID + "</groupId>\n"
                + "    <artifactId>" + SAMPLE_ARTIFACT_ID + "</artifactId>\n"
                + "    <version>" + SAMPLE_VERSION + "</version>\n"
                + "    <properties>\n"
                + "        <maven.compiler.source>8</maven.compiler.source>\n"
                + "        <maven.compiler.target>8</maven.compiler.target>\n"
                + "    </properties>\n"
                + "    <dependencies>\n"
                + "        <dependency>\n"
                + "            <groupId>org.apache.commons</groupId>\n"
                + "            <artifactId>commons-lang3</artifactId>\n"
                + "            <version>" + SAMPLE_COMMONS_LANG_VERSION + "</version>\n"
                + "        </dependency>\n"
                + "    </dependencies>\n"
                + "</project>\n";
    }

    private String buildSampleMain() {
        return "package demo;\n"
                + "\n"
                + "import org.apache.commons.lang3.StringUtils;\n"
                + "\n"
                + "public class Main {\n"
                + "    public static void main(String[] args) {\n"
                + "        String title = StringUtils.capitalize(\"j-ide lite maven sample\");\n"
                + "        String libraryName = StringUtils.join(new String[] {\"Apache\", \"Commons\", \"Lang\"}, ' ');\n"
                + "\n"
                + "        System.out.println(title);\n"
                + "        System.out.println(\"Loaded: \" + libraryName);\n"
                + "    }\n"
                + "}\n";
    }
}
