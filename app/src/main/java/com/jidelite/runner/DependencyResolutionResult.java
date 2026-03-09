package com.jidelite.runner;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DependencyResolutionResult {

    private final List<File> compileJars;
    private final List<File> runtimeJars;

    public DependencyResolutionResult(List<File> compileJars, List<File> runtimeJars) {
        this.compileJars = new ArrayList<>(compileJars);
        this.runtimeJars = new ArrayList<>(runtimeJars);
    }

    public static DependencyResolutionResult empty() {
        return new DependencyResolutionResult(Collections.<File>emptyList(), Collections.<File>emptyList());
    }

    public List<File> getCompileJars() {
        return Collections.unmodifiableList(compileJars);
    }

    public List<File> getRuntimeJars() {
        return Collections.unmodifiableList(runtimeJars);
    }
}
