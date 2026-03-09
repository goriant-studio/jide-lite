package com.goriant.jidelite.editor;

import java.util.ArrayList;
import java.util.List;

public class JavaCodeFormatter {

    private static final String INDENT = "    ";

    public String format(String sourceCode) {
        String safeSource = normalizeLineEndings(sourceCode == null ? "" : sourceCode);
        if (safeSource.trim().isEmpty()) {
            return "";
        }

        String structuredSource = normalizeStructure(safeSource);
        return indentLines(structuredSource);
    }

    private String normalizeStructure(String sourceCode) {
        StringBuilder builder = new StringBuilder(sourceCode.length() + 32);
        boolean inStringLiteral = false;
        boolean inCharacterLiteral = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        boolean escaping = false;
        boolean lineHasContent = false;
        boolean lineTerminatedByFormatter = false;
        int parenthesisDepth = 0;

        for (int index = 0; index < sourceCode.length(); index++) {
            char current = sourceCode.charAt(index);
            char next = index + 1 < sourceCode.length() ? sourceCode.charAt(index + 1) : '\0';

            if (inLineComment) {
                if (current == '\n') {
                    appendSourceNewline(builder);
                    inLineComment = false;
                    lineHasContent = false;
                    lineTerminatedByFormatter = false;
                } else {
                    builder.append(current);
                    if (!Character.isWhitespace(current)) {
                        lineHasContent = true;
                    }
                    lineTerminatedByFormatter = false;
                }
                continue;
            }

            if (inBlockComment) {
                if (current == '*' && next == '/') {
                    builder.append("*/");
                    index++;
                    inBlockComment = false;
                    lineHasContent = true;
                    continue;
                }
                if (current == '\n') {
                    if (lineTerminatedByFormatter) {
                        lineTerminatedByFormatter = false;
                    } else {
                        appendSourceNewline(builder);
                    }
                    lineHasContent = false;
                } else {
                    builder.append(current);
                    if (!Character.isWhitespace(current)) {
                        lineHasContent = true;
                    }
                    lineTerminatedByFormatter = false;
                }
                continue;
            }

            if (inStringLiteral) {
                builder.append(current);
                if (escaping) {
                    escaping = false;
                } else if (current == '\\') {
                    escaping = true;
                } else if (current == '"') {
                    inStringLiteral = false;
                }
                lineHasContent = true;
                lineTerminatedByFormatter = false;
                continue;
            }

            if (inCharacterLiteral) {
                builder.append(current);
                if (escaping) {
                    escaping = false;
                } else if (current == '\\') {
                    escaping = true;
                } else if (current == '\'') {
                    inCharacterLiteral = false;
                }
                lineHasContent = true;
                lineTerminatedByFormatter = false;
                continue;
            }

            if (current == '/' && next == '/') {
                ensureSpaceBeforeComment(builder, lineHasContent);
                builder.append("//");
                index++;
                inLineComment = true;
                lineHasContent = true;
                lineTerminatedByFormatter = false;
                continue;
            }

            if (current == '/' && next == '*') {
                ensureSpaceBeforeComment(builder, lineHasContent);
                builder.append("/*");
                index++;
                inBlockComment = true;
                lineHasContent = true;
                lineTerminatedByFormatter = false;
                continue;
            }

            if (current == '"') {
                builder.append(current);
                inStringLiteral = true;
                lineHasContent = true;
                lineTerminatedByFormatter = false;
                continue;
            }

            if (current == '\'') {
                builder.append(current);
                inCharacterLiteral = true;
                lineHasContent = true;
                lineTerminatedByFormatter = false;
                continue;
            }

            if (current == '\n') {
                if (lineTerminatedByFormatter) {
                    lineTerminatedByFormatter = false;
                } else {
                    appendSourceNewline(builder);
                }
                lineHasContent = false;
                continue;
            }

            if (Character.isWhitespace(current)) {
                appendSingleSpace(builder, lineHasContent);
                continue;
            }

            switch (current) {
                case '(':
                    builder.append(current);
                    parenthesisDepth++;
                    lineHasContent = true;
                    lineTerminatedByFormatter = false;
                    break;
                case ')':
                    builder.append(current);
                    if (parenthesisDepth > 0) {
                        parenthesisDepth--;
                    }
                    lineHasContent = true;
                    lineTerminatedByFormatter = false;
                    break;
                case '{':
                    trimTrailingSpaces(builder);
                    if (lineHasContent && !endsWithWhitespace(builder)) {
                        builder.append(' ');
                    }
                    builder.append('{');
                    appendStructuralNewline(builder);
                    lineHasContent = false;
                    lineTerminatedByFormatter = true;
                    break;
                case '}':
                    trimTrailingSpaces(builder);
                    if (builder.length() > 0 && builder.charAt(builder.length() - 1) != '\n') {
                        appendStructuralNewline(builder);
                    }
                    builder.append('}');
                    if (findNextSignificantCharacter(sourceCode, index + 1) != ';') {
                        appendStructuralNewline(builder);
                        lineHasContent = false;
                        lineTerminatedByFormatter = true;
                    } else {
                        lineHasContent = true;
                        lineTerminatedByFormatter = false;
                    }
                    break;
                case ';':
                    builder.append(';');
                    if (parenthesisDepth == 0) {
                        appendStructuralNewline(builder);
                        lineHasContent = false;
                        lineTerminatedByFormatter = true;
                    } else {
                        builder.append(' ');
                        lineHasContent = true;
                        lineTerminatedByFormatter = false;
                    }
                    break;
                default:
                    builder.append(current);
                    lineHasContent = true;
                    lineTerminatedByFormatter = false;
                    break;
            }
        }

        trimTrailingBlankSpace(builder);
        return builder.toString();
    }

    private String indentLines(String sourceCode) {
        String[] rawLines = sourceCode.split("\n", -1);
        List<String> lines = new ArrayList<>();
        BraceState braceState = new BraceState();
        int indentLevel = 0;
        boolean caseBodyActive = false;
        boolean previousLineBlank = false;

        for (String rawLine : rawLines) {
            String line = trimTrailingSpaces(rawLine);
            if (line.trim().isEmpty()) {
                if (!lines.isEmpty() && !previousLineBlank) {
                    lines.add("");
                    previousLineBlank = true;
                }
                continue;
            }

            String content = line.trim();
            LineAnalysis analysis = analyzeLine(content, braceState);
            int effectiveIndent = Math.max(0, indentLevel - analysis.leadingCloseBraces);
            if (caseBodyActive && !analysis.caseLabel && analysis.leadingCloseBraces == 0) {
                effectiveIndent++;
            }

            lines.add(repeatIndent(effectiveIndent) + content);
            indentLevel = Math.max(0, indentLevel + analysis.openBraces - analysis.closeBraces);

            if (analysis.caseLabel) {
                caseBodyActive = !content.contains("{");
            } else if (analysis.leadingCloseBraces > 0) {
                caseBodyActive = false;
            }

            previousLineBlank = false;
        }

        return joinLines(lines);
    }

    private LineAnalysis analyzeLine(String line, BraceState braceState) {
        LineAnalysis analysis = new LineAnalysis();
        boolean inStringLiteral = false;
        boolean inCharacterLiteral = false;
        boolean escaping = false;
        boolean seenCode = false;

        analysis.caseLabel = line.startsWith("case ") || line.startsWith("default:");

        for (int index = 0; index < line.length(); index++) {
            char current = line.charAt(index);
            char next = index + 1 < line.length() ? line.charAt(index + 1) : '\0';

            if (braceState.inBlockComment) {
                if (current == '*' && next == '/') {
                    braceState.inBlockComment = false;
                    index++;
                }
                continue;
            }

            if (inStringLiteral) {
                if (escaping) {
                    escaping = false;
                } else if (current == '\\') {
                    escaping = true;
                } else if (current == '"') {
                    inStringLiteral = false;
                }
                continue;
            }

            if (inCharacterLiteral) {
                if (escaping) {
                    escaping = false;
                } else if (current == '\\') {
                    escaping = true;
                } else if (current == '\'') {
                    inCharacterLiteral = false;
                }
                continue;
            }

            if (current == '/' && next == '/') {
                break;
            }

            if (current == '/' && next == '*') {
                braceState.inBlockComment = true;
                index++;
                continue;
            }

            if (Character.isWhitespace(current)) {
                continue;
            }

            if (current == '"') {
                inStringLiteral = true;
                seenCode = true;
                continue;
            }

            if (current == '\'') {
                inCharacterLiteral = true;
                seenCode = true;
                continue;
            }

            if (current == '{') {
                analysis.openBraces++;
                seenCode = true;
                continue;
            }

            if (current == '}') {
                analysis.closeBraces++;
                if (!seenCode) {
                    analysis.leadingCloseBraces++;
                }
                seenCode = true;
                continue;
            }

            seenCode = true;
        }

        return analysis;
    }

    private void ensureSpaceBeforeComment(StringBuilder builder, boolean lineHasContent) {
        if (lineHasContent && !endsWithWhitespace(builder)) {
            builder.append(' ');
        }
    }

    private void appendSingleSpace(StringBuilder builder, boolean lineHasContent) {
        if (lineHasContent && builder.length() > 0 && !endsWithWhitespace(builder)) {
            builder.append(' ');
        }
    }

    private void appendSourceNewline(StringBuilder builder) {
        trimTrailingSpaces(builder);
        builder.append('\n');
    }

    private void appendStructuralNewline(StringBuilder builder) {
        trimTrailingSpaces(builder);
        if (builder.length() == 0 || builder.charAt(builder.length() - 1) != '\n') {
            builder.append('\n');
        }
    }

    private void trimTrailingBlankSpace(StringBuilder builder) {
        while (builder.length() > 0) {
            char current = builder.charAt(builder.length() - 1);
            if (current != '\n' && current != ' ' && current != '\t') {
                break;
            }
            builder.deleteCharAt(builder.length() - 1);
        }
    }

    private void trimTrailingSpaces(StringBuilder builder) {
        while (builder.length() > 0) {
            char current = builder.charAt(builder.length() - 1);
            if (current != ' ' && current != '\t') {
                break;
            }
            builder.deleteCharAt(builder.length() - 1);
        }
    }

    private String trimTrailingSpaces(String line) {
        int end = line.length();
        while (end > 0) {
            char current = line.charAt(end - 1);
            if (current != ' ' && current != '\t') {
                break;
            }
            end--;
        }
        return line.substring(0, end);
    }

    private boolean endsWithWhitespace(StringBuilder builder) {
        if (builder.length() == 0) {
            return false;
        }
        char current = builder.charAt(builder.length() - 1);
        return current == ' ' || current == '\n' || current == '\t';
    }

    private char findNextSignificantCharacter(String sourceCode, int startIndex) {
        for (int index = startIndex; index < sourceCode.length(); index++) {
            char current = sourceCode.charAt(index);
            if (!Character.isWhitespace(current)) {
                return current;
            }
        }
        return '\0';
    }

    private String normalizeLineEndings(String sourceCode) {
        return sourceCode.replace("\r\n", "\n").replace('\r', '\n');
    }

    private String repeatIndent(int count) {
        StringBuilder builder = new StringBuilder(count * INDENT.length());
        for (int index = 0; index < count; index++) {
            builder.append(INDENT);
        }
        return builder.toString();
    }

    private String joinLines(List<String> lines) {
        if (lines.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < lines.size(); index++) {
            if (index > 0) {
                builder.append('\n');
            }
            builder.append(lines.get(index));
        }
        return builder.toString();
    }

    private static final class BraceState {
        private boolean inBlockComment;
    }

    private static final class LineAnalysis {
        private int leadingCloseBraces;
        private int openBraces;
        private int closeBraces;
        private boolean caseLabel;
    }
}
