package com.goriant.jidelite.data.dto;

import com.goriant.jidelite.data.enums.ProjectType;

public class ProjectSummaryDto {

    private long projectId;
    private String name;
    private String rootPath;
    private ProjectType type;
    private String buildFilePath;
    private String outputDirPath;
    private int fileCount;

    public ProjectSummaryDto() {
    }

    public ProjectSummaryDto(long projectId, String name, String rootPath, ProjectType type,
                             String buildFilePath, String outputDirPath, int fileCount) {
        this.projectId = projectId;
        this.name = name;
        this.rootPath = rootPath;
        this.type = type;
        this.buildFilePath = buildFilePath;
        this.outputDirPath = outputDirPath;
        this.fileCount = fileCount;
    }

    public long getProjectId() {
        return projectId;
    }

    public void setProjectId(long projectId) {
        this.projectId = projectId;
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

    public int getFileCount() {
        return fileCount;
    }

    public void setFileCount(int fileCount) {
        this.fileCount = fileCount;
    }
}
