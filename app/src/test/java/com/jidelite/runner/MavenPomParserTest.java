package com.jidelite.runner;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

class MavenPomParserTest {

    @TempDir
    Path tempDir;

    private final MavenPomParser parser = new MavenPomParser();

    @Test
    void parseResolvesProjectPropertiesDependenciesAndRepositories() throws Exception {
        File pomFile = tempDir.resolve("pom.xml").toFile();
        Files.write(
                pomFile.toPath(),
                ("<project>"
                        + "<modelVersion>4.0.0</modelVersion>"
                        + "<groupId>demo.app</groupId>"
                        + "<artifactId>sample</artifactId>"
                        + "<version>1.0.0</version>"
                        + "<properties>"
                        + "<lib.version>2.3.4</lib.version>"
                        + "</properties>"
                        + "<repositories>"
                        + "<repository><id>jitpack</id><url>https://jitpack.io</url></repository>"
                        + "</repositories>"
                        + "<dependencies>"
                        + "<dependency>"
                        + "<groupId>org.example</groupId>"
                        + "<artifactId>core</artifactId>"
                        + "<version>${lib.version}</version>"
                        + "</dependency>"
                        + "</dependencies>"
                        + "</project>")
                        .getBytes(StandardCharsets.UTF_8)
        );

        PomModel model = parser.parse(pomFile);

        assertThat(model.getGroupId()).isEqualTo("demo.app");
        assertThat(model.getArtifactId()).isEqualTo("sample");
        assertThat(model.getVersion()).isEqualTo("1.0.0");
        assertThat(model.getRepositories()).containsExactly("https://jitpack.io");
        assertThat(model.getDependencies()).hasSize(1);
        assertThat(model.getDependencies().get(0).getCoordinate().asKey()).isEqualTo("org.example:core:2.3.4");
    }

    @Test
    void parseFallsBackToParentGroupAndVersion() throws Exception {
        File pomFile = tempDir.resolve("pom.xml").toFile();
        Files.write(
                pomFile.toPath(),
                ("<project>"
                        + "<modelVersion>4.0.0</modelVersion>"
                        + "<parent>"
                        + "<groupId>demo.parent</groupId>"
                        + "<artifactId>parent</artifactId>"
                        + "<version>9.1.0</version>"
                        + "</parent>"
                        + "<artifactId>child</artifactId>"
                        + "</project>")
                        .getBytes(StandardCharsets.UTF_8)
        );

        PomModel model = parser.parse(pomFile);

        assertThat(model.getGroupId()).isEqualTo("demo.parent");
        assertThat(model.getArtifactId()).isEqualTo("child");
        assertThat(model.getVersion()).isEqualTo("9.1.0");
    }

    @Test
    void resolvePlaceholdersHandlesMultipleExpressionsWithoutRegex() throws Exception {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("project.groupId", "demo.app");
        properties.put("project.version", "1.0.0");

        String resolved = parser.resolvePlaceholders(
                "${project.groupId}:${project.version}:${missing.property}",
                properties
        );

        assertThat(resolved).isEqualTo("demo.app:1.0.0:${missing.property}");
    }

    @Test
    void parseIgnoresNonRuntimeDependenciesWithoutVersion() throws Exception {
        File pomFile = tempDir.resolve("pom.xml").toFile();
        Files.write(
                pomFile.toPath(),
                ("<project>"
                        + "<modelVersion>4.0.0</modelVersion>"
                        + "<groupId>demo.app</groupId>"
                        + "<artifactId>sample</artifactId>"
                        + "<version>1.0.0</version>"
                        + "<dependencies>"
                        + "<dependency>"
                        + "<groupId>org.example</groupId>"
                        + "<artifactId>runtime-lib</artifactId>"
                        + "<version>2.0.0</version>"
                        + "</dependency>"
                        + "<dependency>"
                        + "<groupId>org.junit.jupiter</groupId>"
                        + "<artifactId>junit-jupiter</artifactId>"
                        + "<scope>test</scope>"
                        + "</dependency>"
                        + "</dependencies>"
                        + "</project>")
                        .getBytes(StandardCharsets.UTF_8)
        );

        PomModel model = parser.parse(pomFile);

        assertThat(model.getDependencies()).hasSize(1);
        assertThat(model.getDependencies().get(0).getCoordinate().asKey()).isEqualTo("org.example:runtime-lib:2.0.0");
    }
}
