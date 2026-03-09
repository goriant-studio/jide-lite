package com.goriant.jidelite.data.entity;

public class ProjectConfigEntity {

    private long id;
    private long projectId;
    private String sourceRootsJson;
    private String resourceRootsJson;
    private String testRootsJson;
    private String excludeRootsJson;
    private String rawBuildMetaJson;
    private long updatedAt;

    public ProjectConfigEntity() {
    }

    public ProjectConfigEntity(long id, long projectId, String sourceRootsJson, String resourceRootsJson,
                               String testRootsJson, String excludeRootsJson, String rawBuildMetaJson,
                               long updatedAt) {
        this.id = id;
        this.projectId = projectId;
        this.sourceRootsJson = sourceRootsJson;
        this.resourceRootsJson = resourceRootsJson;
        this.testRootsJson = testRootsJson;
        this.excludeRootsJson = excludeRootsJson;
        this.rawBuildMetaJson = rawBuildMetaJson;
        this.updatedAt = updatedAt;
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

    public String getSourceRootsJson() {
        return sourceRootsJson;
    }

    public void setSourceRootsJson(String sourceRootsJson) {
        this.sourceRootsJson = sourceRootsJson;
    }

    public String getResourceRootsJson() {
        return resourceRootsJson;
    }

    public void setResourceRootsJson(String resourceRootsJson) {
        this.resourceRootsJson = resourceRootsJson;
    }

    public String getTestRootsJson() {
        return testRootsJson;
    }

    public void setTestRootsJson(String testRootsJson) {
        this.testRootsJson = testRootsJson;
    }

    public String getExcludeRootsJson() {
        return excludeRootsJson;
    }

    public void setExcludeRootsJson(String excludeRootsJson) {
        this.excludeRootsJson = excludeRootsJson;
    }

    public String getRawBuildMetaJson() {
        return rawBuildMetaJson;
    }

    public void setRawBuildMetaJson(String rawBuildMetaJson) {
        this.rawBuildMetaJson = rawBuildMetaJson;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
