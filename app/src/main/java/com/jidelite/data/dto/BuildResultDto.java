package com.jidelite.data.dto;

import com.jidelite.data.enums.BuildStatus;

import java.util.ArrayList;
import java.util.List;

public class BuildResultDto {

    private BuildStatus status;
    private String output;
    private int exitCode;
    private List<String> artifactPaths = new ArrayList<>();

    public BuildResultDto() {
    }

    public BuildStatus getStatus() {
        return status;
    }

    public void setStatus(BuildStatus status) {
        this.status = status;
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    public int getExitCode() {
        return exitCode;
    }

    public void setExitCode(int exitCode) {
        this.exitCode = exitCode;
    }

    public List<String> getArtifactPaths() {
        return artifactPaths;
    }

    public void setArtifactPaths(List<String> artifactPaths) {
        this.artifactPaths = artifactPaths;
    }
}
