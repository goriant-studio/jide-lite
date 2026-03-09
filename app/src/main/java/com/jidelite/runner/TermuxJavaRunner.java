package com.jidelite.runner;

import com.jidelite.model.RunResult;

public class TermuxJavaRunner implements CodeRunner {

    private static final String TERMUX_DISABLED_MESSAGE =
            "Termux runner is disabled. J-IDE Lite now uses the local embedded runner instead.";

    @Override
    public RunResult run(String selectedPath) {
        String safePath = selectedPath == null ? "Main.java" : selectedPath.trim();
        String fileName = new java.io.File(safePath).getName();
        String className = fileName.endsWith(".java")
                ? fileName.substring(0, fileName.length() - 5)
                : "Main";

        String stdout = "$ javac " + fileName + "\n$ java " + className;
        return new RunResult(false, stdout, TERMUX_DISABLED_MESSAGE, 126);
    }

    @Override
    public RunResult resolveDependencies() {
        return new RunResult(
                false,
                "$ mvn dependency:resolve",
                TERMUX_DISABLED_MESSAGE,
                126
        );
    }
}
