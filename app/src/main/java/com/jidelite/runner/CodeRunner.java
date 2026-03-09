package com.jidelite.runner;

import com.jidelite.model.RunResult;

public interface CodeRunner {

    RunResult run(String selectedPath);

    RunResult resolveDependencies();
}
