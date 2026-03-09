package com.jidelite.data.entity;

import com.jidelite.data.enums.BuildStatus;

public class BuildStateEntity {

    private long id;
    private long projectId;
    private BuildStatus status;
    private String lastOutput;
    private String lastArtifactPath;
    private long lastBuildAt;

    public BuildStateEntity() {
    }

    public BuildStateEntity(long id, long projectId, BuildStatus status, String lastOutput,
                            String lastArtifactPath, long lastBuildAt) {
        this.id = id;
        this.projectId = projectId;
        this.status = status;
        this.lastOutput = lastOutput;
        this.lastArtifactPath = lastArtifactPath;
        this.lastBuildAt = lastBuildAt;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getProjectId() {
        return projectId;
    }

    public void setProjectId(long projectId) {
        this.projectId = projectId;
    }

    public BuildStatus getStatus() {
        return status;
    }

    public void setStatus(BuildStatus status) {
        this.status = status;
    }

    public String getLastOutput() {
        return lastOutput;
    }

    public void setLastOutput(String lastOutput) {
        this.lastOutput = lastOutput;
    }

    public String getLastArtifactPath() {
        return lastArtifactPath;
    }

    public void setLastArtifactPath(String lastArtifactPath) {
        this.lastArtifactPath = lastArtifactPath;
    }

    public long getLastBuildAt() {
        return lastBuildAt;
    }

    public void setLastBuildAt(long lastBuildAt) {
        this.lastBuildAt = lastBuildAt;
    }
}
