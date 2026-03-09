package com.goriant.jidelite.data.entity;

import com.goriant.jidelite.data.enums.ProjectType;

public class ProjectEntity {

    private long id;
    private long workspaceId;
    private String name;
    private String rootPath;
    private ProjectType type;
    private String buildFilePath;
    private String outputDirPath;
    private long createdAt;
    private long lastScannedAt;

    public ProjectEntity() {
    }

    public ProjectEntity(long id, long workspaceId, String name, String rootPath, ProjectType type,
                         String buildFilePath, String outputDirPath, long createdAt, long lastScannedAt) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.name = name;
        this.rootPath = rootPath;
        this.type = type;
        this.buildFilePath = buildFilePath;
        this.outputDirPath = outputDirPath;
        this.createdAt = createdAt;
        this.lastScannedAt = lastScannedAt;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(long workspaceId) {
        this.workspaceId = workspaceId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRootPath() {
        return rootPath;
    }

    public void setRootPath(String rootPath) {
        this.rootPath = rootPath;
    }

    public ProjectType getType() {
        return type;
    }

    public void setType(ProjectType type) {
        this.type = type;
    }

    public String getBuildFilePath() {
        return buildFilePath;
    }

    public void setBuildFilePath(String buildFilePath) {
        this.buildFilePath = buildFilePath;
    }

    public String getOutputDirPath() {
        return outputDirPath;
    }

    public void setOutputDirPath(String outputDirPath) {
        this.outputDirPath = outputDirPath;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getLastScannedAt() {
        return lastScannedAt;
    }

    public void setLastScannedAt(long lastScannedAt) {
        this.lastScannedAt = lastScannedAt;
    }
}
