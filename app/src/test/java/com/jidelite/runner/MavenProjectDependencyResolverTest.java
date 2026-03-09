package com.jidelite.runner;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class MavenProjectDependencyResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void resolveBuildsCompileAndRuntimeClasspathFromPomGraph() throws Exception {
        File workspace = tempDir.resolve("workspace").toFile();
        assertThat(workspace.mkdirs()).isTrue();
        Files.write(
                new File(workspace, "pom.xml").toPath(),
                ("<project>"
                        + "<modelVersion>4.0.0</modelVersion>"
                        + "<groupId>demo</groupId>"
                        + "<artifactId>app</artifactId>"
                        + "<version>1.0.0</version>"
                        + "<dependencies>"
                        + "<dependency><groupId>com.example</groupId><artifactId>alpha</artifactId><version>1.0</version></dependency>"
                        + "<dependency><groupId>com.example</groupId><artifactId>gamma</artifactId><version>1.0</version><scope>runtime</scope></dependency>"
                        + "<dependency><groupId>com.example</groupId><artifactId>ignored-test</artifactId><version>1.0</version><scope>test</scope></dependency>"
                        + "<dependency><groupId>com.example</groupId><artifactId>ignored-optional</artifactId><version>1.0</version><optional>true</optional></dependency>"
                        + "</dependencies>"
                        + "</project>")
                        .getBytes(StandardCharsets.UTF_8)
        );

        FakeMavenRepositoryClient repositoryClient = new FakeMavenRepositoryClient(tempDir.resolve("repo").toFile());
        repositoryClient.register(
                "com.example:alpha:1.0",
                "<project><modelVersion>4.0.0</modelVersion><groupId>com.example</groupId><artifactId>alpha</artifactId><version>1.0</version>"
                        + "<dependencies>"
                        + "<dependency><groupId>com.example</groupId><artifactId>beta</artifactId><version>1.0</version><scope>runtime</scope></dependency>"
                        + "<dependency><groupId>com.example</groupId><artifactId>delta</artifactId><version>1.0</version></dependency>"
                        + "<dependency><groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter</artifactId><scope>test</scope></dependency>"
                        + "</dependencies>"
                        + "</project>"
        );
        repositoryClient.register(
                "com.example:beta:1.0",
                "<project><modelVersion>4.0.0</modelVersion><groupId>com.example</groupId><artifactId>beta</artifactId><version>1.0</version></project>"
        );
        repositoryClient.register(
                "com.example:delta:1.0",
                "<project><modelVersion>4.0.0</modelVersion><groupId>com.example</groupId><artifactId>delta</artifactId><version>1.0</version></project>"
        );
        repositoryClient.register(
                "com.example:gamma:1.0",
                "<project><modelVersion>4.0.0</modelVersion><groupId>com.example</groupId><artifactId>gamma</artifactId><version>1.0</version>"
                        + "<dependencies>"
                        + "<dependency><groupId>com.example</groupId><artifactId>epsilon</artifactId><version>1.0</version></dependency>"
                        + "</dependencies>"
                        + "</project>"
        );
        repositoryClient.register(
                "com.example:epsilon:1.0",
                "<project><modelVersion>4.0.0</modelVersion><groupId>com.example</groupId><artifactId>epsilon</artifactId><version>1.0</version></project>"
        );

        MavenProjectDependencyResolver resolver = new MavenProjectDependencyResolver(
                new MavenPomParser(),
                repositoryClient
        );

        DependencyResolutionResult result = resolver.resolve(workspace);

        assertThat(relativeNames(result.getCompileJars()))
                .containsExactly("alpha-1.0.jar", "delta-1.0.jar");
        assertThat(relativeNames(result.getRuntimeJars()))
                .containsExactly("alpha-1.0.jar", "gamma-1.0.jar", "beta-1.0.jar", "delta-1.0.jar", "epsilon-1.0.jar");
    }

    private List<String> relativeNames(List<File> files) {
        List<String> names = new ArrayList<>();
        for (File file : files) {
            names.add(file.getName());
        }
        return names;
    }

    private static final class FakeMavenRepositoryClient implements MavenRepositoryClient {
        private final File repositoryDirectory;
        private final Map<String, File> pomFiles = new HashMap<>();
        private final Map<String, File> jarFiles = new HashMap<>();

        private FakeMavenRepositoryClient(File repositoryDirectory) {
            this.repositoryDirectory = repositoryDirectory;
            assertThat(repositoryDirectory.mkdirs() || repositoryDirectory.exists()).isTrue();
        }

        private void register(String coordinateKey, String pomContent) throws Exception {
            String[] parts = coordinateKey.split(":");
            MavenCoordinate coordinate = new MavenCoordinate(parts[0], parts[1], parts[2], "jar");
            File pomFile = new File(repositoryDirectory, coordinate.getArtifactId() + "-" + coordinate.getVersion() + ".pom");
            File jarFile = new File(repositoryDirectory, coordinate.getArtifactId() + "-" + coordinate.getVersion() + ".jar");
            Files.write(pomFile.toPath(), pomContent.getBytes(StandardCharsets.UTF_8));
            Files.write(jarFile.toPath(), new byte[0]);
            pomFiles.put(coordinateKey, pomFile);
            jarFiles.put(coordinateKey, jarFile);
        }

        @Override
        public File fetchPom(MavenCoordinate coordinate, List<String> repositories) {
            return pomFiles.get(coordinate.asKey());
        }

        @Override
        public File fetchJar(MavenCoordinate coordinate, List<String> repositories) {
            return jarFiles.get(coordinate.asKey());
        }
    }
}
