package com.jidelite.runner;

import java.io.File;
import java.io.IOException;

public interface ProjectDependencyResolver {

    DependencyResolutionResult resolve(File workspaceDirectory) throws IOException;
}
