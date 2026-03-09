package com.jidelite.runner;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class HttpMavenRepositoryClient implements MavenRepositoryClient {

    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 15000;
    private static final String DEFAULT_REPOSITORY = "https://repo1.maven.org/maven2/";

    private final File repositoryDirectory;

    HttpMavenRepositoryClient(File repositoryDirectory) {
        this.repositoryDirectory = repositoryDirectory;
    }

    @Override
    public File fetchPom(MavenCoordinate coordinate, List<String> repositories) throws IOException {
        return fetchArtifact(coordinate, "pom", repositories);
    }

    @Override
    public File fetchJar(MavenCoordinate coordinate, List<String> repositories) throws IOException {
        if (!"jar".equals(coordinate.getPackaging())) {
            throw new IOException("Unsupported Maven packaging for runtime: " + coordinate.getPackaging()
                    + " (" + coordinate + ")");
        }
        return fetchArtifact(coordinate, "jar", repositories);
    }

    private File fetchArtifact(MavenCoordinate coordinate, String extension, List<String> repositories) throws IOException {
        File target = new File(repositoryDirectory, coordinate.toRepositoryPath(extension));
        if (target.exists()) {
            return target;
        }

        ensureParentDirectory(target);
        List<String> attemptedUrls = new ArrayList<>();
        for (String repository : buildRepositoryList(repositories)) {
            String artifactUrl = normalizeRepositoryUrl(repository) + coordinate.toRepositoryPath(extension);
            attemptedUrls.add(artifactUrl);
            if (downloadFile(artifactUrl, target)) {
                return target;
            }
        }

        throw new IOException("Could not resolve " + extension + " for " + coordinate
                + " from repositories: " + attemptedUrls);
    }

    private List<String> buildRepositoryList(List<String> repositories) {
        Set<String> ordered = new LinkedHashSet<>();
        if (repositories != null) {
            ordered.addAll(repositories);
        }
        ordered.add(DEFAULT_REPOSITORY);
        return new ArrayList<>(ordered);
    }

    private boolean downloadFile(String artifactUrl, File target) throws IOException {
        HttpURLConnection connection = null;
        File tempFile = new File(target.getParentFile(), target.getName() + ".part");
        try {
            connection = (HttpURLConnection) new URL(artifactUrl).openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "J-IDE-Lite/1.0");
            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_NOT_FOUND) {
                return false;
            }
            if (status < 200 || status >= 300) {
                throw new IOException("Repository request failed with HTTP " + status + " for " + artifactUrl);
            }

            try (InputStream input = connection.getInputStream();
                 FileOutputStream output = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
            }

            if (!tempFile.renameTo(target)) {
                throw new IOException("Could not move downloaded file into cache: " + target.getAbsolutePath());
            }
            return true;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            if (tempFile.exists() && !target.exists()) {
                tempFile.delete();
            }
        }
    }

    private void ensureParentDirectory(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.exists()) {
            throw new IOException("Could not create Maven cache directory: " + parent.getAbsolutePath());
        }
    }

    private String normalizeRepositoryUrl(String repository) {
        return repository.endsWith("/") ? repository : repository + "/";
    }
}
