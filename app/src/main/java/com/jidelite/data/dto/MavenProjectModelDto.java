package com.jidelite.data.dto;

import java.util.ArrayList;
import java.util.List;

public class MavenProjectModelDto {

    private String groupId;
    private String artifactId;
    private String version;
    private String pomPath;
    private List<String> sourceRoots = new ArrayList<>();
    private List<String> resourceRoots = new ArrayList<>();
    private List<String> testRoots = new ArrayList<>();
    private List<String> excludeRoots = new ArrayList<>();
    private String outputDir;

    public MavenProjectModelDto() {
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getPomPath() {
        return pomPath;
    }

    public void setPomPath(String pomPath) {
        this.pomPath = pomPath;
    }

    public List<String> getSourceRoots() {
        return sourceRoots;
    }

    public void setSourceRoots(List<String> sourceRoots) {
        this.sourceRoots = sourceRoots;
    }

    public List<String> getResourceRoots() {
        return resourceRoots;
    }

    public void setResourceRoots(List<String> resourceRoots) {
        this.resourceRoots = resourceRoots;
    }

    public List<String> getTestRoots() {
        return testRoots;
    }

    public void setTestRoots(List<String> testRoots) {
        this.testRoots = testRoots;
    }

    public List<String> getExcludeRoots() {
        return excludeRoots;
    }

    public void setExcludeRoots(List<String> excludeRoots) {
        this.excludeRoots = excludeRoots;
    }

    public String getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(String outputDir) {
        this.outputDir = outputDir;
    }
}
