package com.jidelite.runner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class PomModel {

    private final String groupId;
    private final String artifactId;
    private final String version;
    private final String packaging;
    private final List<String> repositories;
    private final List<PomDependency> dependencies;

    PomModel(
            String groupId,
            String artifactId,
            String version,
            String packaging,
            List<String> repositories,
            List<PomDependency> dependencies
    ) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.packaging = packaging == null || packaging.trim().isEmpty() ? "jar" : packaging.trim();
        this.repositories = new ArrayList<>(repositories);
        this.dependencies = new ArrayList<>(dependencies);
    }

    String getGroupId() {
        return groupId;
    }

    String getArtifactId() {
        return artifactId;
    }

    String getVersion() {
        return version;
    }

    String getPackaging() {
        return packaging;
    }

    List<String> getRepositories() {
        return Collections.unmodifiableList(repositories);
    }

    List<PomDependency> getDependencies() {
        return Collections.unmodifiableList(dependencies);
    }
}
