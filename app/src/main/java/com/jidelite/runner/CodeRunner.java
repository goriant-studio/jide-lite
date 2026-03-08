package com.jidelite.runner;

import com.jidelite.model.RunResult;

public interface CodeRunner {

    RunResult runJava(String fileName, String sourceCode);
}
