package com.jidelite.runner;

import com.jidelite.model.RunResult;

public class TermuxJavaRunner implements CodeRunner {

    @Override
    public RunResult runJava(String fileName, String sourceCode) {
        String safeFileName = fileName == null ? "Main.java" : fileName.trim();
        String className = safeFileName.endsWith(".java")
                ? safeFileName.substring(0, safeFileName.length() - 5)
                : "Main";

        String stdout = "$ javac " + safeFileName + "\n$ java " + className;
        String stderr = "Termux runner is disabled. J-IDE Lite now uses the local embedded runner instead.";
        return new RunResult(false, stdout, stderr, 126);
    }
}
