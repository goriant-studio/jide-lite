package com.goriant.jidelite.runner;

import com.goriant.jidelite.model.RunResult;

public interface CodeRunner {

    RunResult run(String selectedPath);

    RunResult resolveDependencies();
}
