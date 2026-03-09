package com.goriant.jidelite.runner;

import java.util.Objects;

final class MavenCoordinate {

    private final String groupId;
    private final String artifactId;
    private final String version;
    private final String packaging;

    MavenCoordinate(String groupId, String artifactId, String version, String packaging) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.packaging = packaging == null || packaging.trim().isEmpty() ? "jar" : packaging.trim();
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

    String toRepositoryPath(String extension) {
        return groupId.replace('.', '/')
                + "/" + artifactId
                + "/" + version
                + "/" + artifactId + "-" + version + "." + extension;
    }

    String asKey() {
        return groupId + ":" + artifactId + ":" + version;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MavenCoordinate)) {
            return false;
        }
        MavenCoordinate that = (MavenCoordinate) other;
        return Objects.equals(groupId, that.groupId)
                && Objects.equals(artifactId, that.artifactId)
                && Objects.equals(version, that.version)
                && Objects.equals(packaging, that.packaging);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, artifactId, version, packaging);
    }

    @Override
    public String toString() {
        return asKey();
    }
}
