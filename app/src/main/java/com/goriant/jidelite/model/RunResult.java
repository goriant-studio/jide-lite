package com.goriant.jidelite.model;

public class RunResult {

    private final boolean success;
    private final String stdout;
    private final String stderr;
    private final int exitCode;

    public RunResult(boolean success, String stdout, String stderr, int exitCode) {
        this.success = success;
        this.stdout = stdout == null ? "" : stdout;
        this.stderr = stderr == null ? "" : stderr;
        this.exitCode = exitCode;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getStdout() {
        return stdout;
    }

    public String getStderr() {
        return stderr;
    }

    public int getExitCode() {
        return exitCode;
    }
}
