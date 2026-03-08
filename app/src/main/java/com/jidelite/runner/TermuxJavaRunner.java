package com.jidelite.runner;

import com.jidelite.model.RunResult;

public class TermuxJavaRunner implements CodeRunner {

    @Override
    public RunResult runJava(String fileName, String sourceCode) {
        String safeFileName = fileName == null ? "Main.java" : fileName;
        String className = !safeFileName.endsWith(".java")
                ? "Main"
                : safeFileName.substring(0, safeFileName.length() - 5);

        // TODO: When Termux integration is ready, replace this placeholder by executing:
        //   javac <fileName>
        //   java <className>
        // inside a workspace directory shared with the app.
        String stdout = "$ javac " + safeFileName + "\n$ java " + className;
        String stderr = "Termux runner is not connected in this MVP yet.";
        return new RunResult(false, stdout, stderr, -1);
    }
}
