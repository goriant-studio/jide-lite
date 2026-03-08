package com.jidelite.runner;

import com.jidelite.model.RunResult;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LocalJavaRunner implements CodeRunner {

    private static final Pattern FILE_NAME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*\\.java$");
    private static final Pattern PUBLIC_CLASS_PATTERN =
            Pattern.compile("\\bpublic\\s+class\\s+([A-Za-z_$][A-Za-z0-9_$]*)");
    private static final Pattern MAIN_METHOD_PATTERN =
            Pattern.compile("\\bpublic\\s+static\\s+void\\s+main\\s*\\(");
    private static final Pattern PRINT_PATTERN =
            Pattern.compile("System\\.out\\.(?:println|print)\\s*\\(\\s*\"((?:\\\\.|[^\"\\\\])*)\"\\s*\\)");

    @Override
    public RunResult runJava(String fileName, String sourceCode) {
        String safeFileName = fileName == null ? "" : fileName.trim();
        String safeSource = sourceCode == null ? "" : sourceCode;

        if (!FILE_NAME_PATTERN.matcher(safeFileName).matches()) {
            return new RunResult(false, "", "Compile failed.\nUnsupported file name: " + safeFileName, 2);
        }

        if (safeSource.trim().isEmpty()) {
            return new RunResult(false, commandTrace(safeFileName), "Compile failed.\nSource code is empty.", 2);
        }

        String expectedClassName = safeFileName.substring(0, safeFileName.length() - 5);
        Matcher classMatcher = PUBLIC_CLASS_PATTERN.matcher(safeSource);
        if (classMatcher.find() && !expectedClassName.equals(classMatcher.group(1))) {
            return new RunResult(
                    false,
                    commandTrace(safeFileName),
                    "Compile failed.\npublic class " + classMatcher.group(1)
                            + " must be declared in " + expectedClassName + ".java",
                    1
            );
        }

        if (!hasBalancedBraces(safeSource)) {
            return new RunResult(false, commandTrace(safeFileName), "Compile failed.\nUnmatched braces detected.", 1);
        }

        StringBuilder stdoutBuilder = new StringBuilder();
        stdoutBuilder.append("[placeholder runner]").append('\n');
        stdoutBuilder.append("Validated file structure and simulated console output.").append('\n');
        stdoutBuilder.append("Real local javac/java execution is not wired in this MVP.").append('\n');
        stdoutBuilder.append('\n');
        stdoutBuilder.append(commandTrace(safeFileName)).append('\n');
        stdoutBuilder.append("Compile success.").append('\n');
        stdoutBuilder.append("$ java ").append(expectedClassName);

        // TODO: Replace this placeholder with real on-device execution.
        // A real implementation can write the source into the workspace, then invoke:
        //   javac <fileName>
        //   java <className>
        // via an embedded OpenJDK distribution or a Termux-backed bridge.
        if (!MAIN_METHOD_PATTERN.matcher(safeSource).find()) {
            return new RunResult(
                    false,
                    stdoutBuilder.toString(),
                    "Runtime failed.\nMain method not found in class " + expectedClassName + ".",
                    1
            );
        }

        List<String> printedLines = extractPrintedLines(safeSource);
        if (printedLines.isEmpty()) {
            stdoutBuilder.append("\n\nProgram finished with no console output.");
        } else {
            stdoutBuilder.append("\n\n");
            for (int index = 0; index < printedLines.size(); index++) {
                stdoutBuilder.append(printedLines.get(index));
                if (index < printedLines.size() - 1) {
                    stdoutBuilder.append('\n');
                }
            }
        }

        return new RunResult(true, stdoutBuilder.toString(), "", -1);
    }

    private String commandTrace(String fileName) {
        return "$ javac " + fileName;
    }

    private boolean hasBalancedBraces(String sourceCode) {
        int balance = 0;
        for (int index = 0; index < sourceCode.length(); index++) {
            char current = sourceCode.charAt(index);
            if (current == '{') {
                balance++;
            } else if (current == '}') {
                balance--;
                if (balance < 0) {
                    return false;
                }
            }
        }
        return balance == 0;
    }

    private List<String> extractPrintedLines(String sourceCode) {
        List<String> lines = new ArrayList<>();
        Matcher matcher = PRINT_PATTERN.matcher(sourceCode);
        while (matcher.find()) {
            lines.add(unescapeJavaString(matcher.group(1)));
        }
        return lines;
    }

    private String unescapeJavaString(String rawValue) {
        String value = rawValue;
        value = value.replace("\\n", "\n");
        value = value.replace("\\t", "\t");
        value = value.replace("\\r", "\r");
        value = value.replace("\\\"", "\"");
        value = value.replace("\\\\", "\\");
        return value;
    }
}
