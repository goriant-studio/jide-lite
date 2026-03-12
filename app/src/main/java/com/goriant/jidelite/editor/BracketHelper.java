package com.goriant.jidelite.editor;

/**
 * Utility for bracket auto-closing and matching-bracket finding.
 */
public final class BracketHelper {

    private BracketHelper() {
    }

    /**
     * Returns the closing character that should be auto-inserted when {@code typed}
     * is entered, or {@code '\0'} if no auto-close is appropriate.
     */
    public static char autoCloseChar(char typed) {
        switch (typed) {
            case '{': return '}';
            case '(': return ')';
            case '[': return ']';
            case '"': return '"';
            case '\'': return '\'';
            default: return '\0';
        }
    }

    /**
     * Determines whether auto-close should fire when {@code typed} is entered at
     * {@code cursorPos} within {@code text}.
     */
    public static boolean shouldAutoClose(String text, int cursorPos, char typed) {
        char closer = autoCloseChar(typed);
        if (closer == '\0') {
            return false;
        }

        // For quotes, do not auto-close if already inside a string of the same kind.
        if (typed == '"' || typed == '\'') {
            if (isInsideQuote(text, cursorPos, typed)) {
                return false;
            }
        }

        // Do not auto-close if the next character is an alphanumeric character.
        if (cursorPos < text.length()) {
            char next = text.charAt(cursorPos);
            if (Character.isLetterOrDigit(next)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Returns {@code true} when the cursor sits between a matched pair of brackets/quotes
     * such as {@code {|}} or {@code (|)} where backspace should remove both characters.
     */
    public static boolean isBetweenMatchedPair(String text, int cursorPos) {
        if (cursorPos <= 0 || cursorPos >= text.length()) {
            return false;
        }
        char before = text.charAt(cursorPos - 1);
        char after = text.charAt(cursorPos);
        return (before == '{' && after == '}')
                || (before == '(' && after == ')')
                || (before == '[' && after == ']')
                || (before == '"' && after == '"')
                || (before == '\'' && after == '\'');
    }

    /**
     * Finds the position of the bracket that matches the one at {@code bracketPos},
     * or {@code -1} if no match is found. Handles nesting and skips string/char literals.
     */
    public static int findMatchingBracket(String text, int bracketPos) {
        if (bracketPos < 0 || bracketPos >= text.length()) {
            return -1;
        }

        char bracket = text.charAt(bracketPos);
        char target;
        int direction;

        switch (bracket) {
            case '{': target = '}'; direction = 1; break;
            case '}': target = '{'; direction = -1; break;
            case '(': target = ')'; direction = 1; break;
            case ')': target = '('; direction = -1; break;
            case '[': target = ']'; direction = 1; break;
            case ']': target = '['; direction = -1; break;
            default: return -1;
        }

        int depth = 0;
        int length = text.length();
        int pos = bracketPos;
        boolean inString = false;
        boolean inChar = false;
        boolean escaped = false;

        while (true) {
            pos += direction;
            if (pos < 0 || pos >= length) {
                return -1;
            }

            char ch = text.charAt(pos);

            if (escaped) {
                escaped = false;
                continue;
            }
            if (ch == '\\') {
                escaped = true;
                continue;
            }
            if (ch == '"' && !inChar) {
                inString = !inString;
                continue;
            }
            if (ch == '\'' && !inString) {
                inChar = !inChar;
                continue;
            }
            if (inString || inChar) {
                continue;
            }

            if (ch == bracket) {
                depth++;
            } else if (ch == target) {
                if (depth == 0) {
                    return pos;
                }
                depth--;
            }
        }
    }

    private static boolean isInsideQuote(String text, int cursorPos, char quoteChar) {
        int count = 0;
        boolean escaped = false;
        for (int i = 0; i < cursorPos && i < text.length(); i++) {
            char ch = text.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (ch == '\\') {
                escaped = true;
                continue;
            }
            if (ch == quoteChar) {
                count++;
            }
        }
        return count % 2 != 0;
    }
}
