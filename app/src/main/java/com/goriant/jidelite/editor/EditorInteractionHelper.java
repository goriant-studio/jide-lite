package com.goriant.jidelite.editor;

public final class EditorInteractionHelper {

    private static final String DEFAULT_INDENT = "    ";

    private EditorInteractionHelper() {
    }

    public static SelectionBounds normalizeSelection(int textLength, int rawStart, int rawEnd) {
        int safeLength = Math.max(0, textLength);
        int safeStart = clampInt(rawStart, 0, safeLength);
        int safeEnd = clampInt(rawEnd, 0, safeLength);
        return new SelectionBounds(Math.min(safeStart, safeEnd), Math.max(safeStart, safeEnd));
    }

    public static String selectedText(String text, int rawStart, int rawEnd) {
        String safeText = text == null ? "" : text;
        SelectionBounds selection = normalizeSelection(safeText.length(), rawStart, rawEnd);
        if (selection.isEmpty()) {
            return "";
        }
        return safeText.substring(selection.getStart(), selection.getEndExclusive());
    }

    public static TextMutation replaceSelection(String text, int rawStart, int rawEnd, String replacement) {
        String safeText = text == null ? "" : text;
        String safeReplacement = replacement == null ? "" : replacement;
        SelectionBounds selection = normalizeSelection(safeText.length(), rawStart, rawEnd);
        String updatedText = safeText.substring(0, selection.getStart())
                + safeReplacement
                + safeText.substring(selection.getEndExclusive());
        return new TextMutation(updatedText, selection.getStart() + safeReplacement.length());
    }

    public static TextMutation removeSelection(String text, int rawStart, int rawEnd) {
        String safeText = text == null ? "" : text;
        SelectionBounds selection = normalizeSelection(safeText.length(), rawStart, rawEnd);
        if (selection.isEmpty()) {
            return new TextMutation(safeText, selection.getStart());
        }
        String updatedText = safeText.substring(0, selection.getStart())
                + safeText.substring(selection.getEndExclusive());
        return new TextMutation(updatedText, selection.getStart());
    }

    public static TextMutation insertSmartNewline(String text, int rawStart, int rawEnd, String indentUnit) {
        String safeText = text == null ? "" : text;
        String safeIndent = indentUnit == null || indentUnit.isEmpty() ? DEFAULT_INDENT : indentUnit;
        SelectionBounds selection = normalizeSelection(safeText.length(), rawStart, rawEnd);

        int lineStart = findLineStart(safeText, selection.getStart());
        int lineEnd = findLineEnd(safeText, selection.getEndExclusive());
        String beforeCursorOnLine = safeText.substring(lineStart, selection.getStart());
        String afterCursorOnLine = safeText.substring(selection.getEndExclusive(), lineEnd);
        String leadingIndent = leadingWhitespace(beforeCursorOnLine);
        String beforeTrimmed = beforeCursorOnLine.trim();
        String afterTrimmed = afterCursorOnLine.trim();

        boolean opensBlock = beforeTrimmed.endsWith("{");
        boolean closesBlockImmediately = opensBlock && afterTrimmed.startsWith("}");
        String nextLineIndent = opensBlock ? leadingIndent + safeIndent : leadingIndent;

        StringBuilder insertedText = new StringBuilder();
        insertedText.append('\n').append(nextLineIndent);
        int cursorPosition = selection.getStart() + insertedText.length();

        if (closesBlockImmediately) {
            insertedText.append('\n').append(leadingIndent);
        }

        String updatedText = safeText.substring(0, selection.getStart())
                + insertedText
                + safeText.substring(selection.getEndExclusive());
        return new TextMutation(updatedText, cursorPosition);
    }

    public static float resizeByDelta(float currentSize, float delta, float minSize, float maxSize) {
        return clampFloat(currentSize + delta, minSize, maxSize);
    }

    public static float resizeByInvertedDelta(float currentSize, float delta, float minSize, float maxSize) {
        return clampFloat(currentSize - delta, minSize, maxSize);
    }

    private static int clampInt(int value, int min, int max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(value, max));
    }

    private static float clampFloat(float value, float min, float max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(value, max));
    }

    private static int findLineStart(String text, int index) {
        int safeIndex = clampInt(index, 0, text.length());
        int lastBreak = text.lastIndexOf('\n', Math.max(0, safeIndex - 1));
        return lastBreak < 0 ? 0 : lastBreak + 1;
    }

    private static int findLineEnd(String text, int index) {
        int safeIndex = clampInt(index, 0, text.length());
        int nextBreak = text.indexOf('\n', safeIndex);
        return nextBreak < 0 ? text.length() : nextBreak;
    }

    private static String leadingWhitespace(String text) {
        int index = 0;
        while (index < text.length() && Character.isWhitespace(text.charAt(index)) && text.charAt(index) != '\n') {
            index++;
        }
        return text.substring(0, index);
    }

    public static final class SelectionBounds {
        private final int start;
        private final int endExclusive;

        public SelectionBounds(int start, int endExclusive) {
            this.start = start;
            this.endExclusive = endExclusive;
        }

        public int getStart() {
            return start;
        }

        public int getEndExclusive() {
            return endExclusive;
        }

        public boolean isEmpty() {
            return start >= endExclusive;
        }
    }

    public static final class TextMutation {
        private final String text;
        private final int cursorPosition;

        public TextMutation(String text, int cursorPosition) {
            this.text = text;
            this.cursorPosition = cursorPosition;
        }

        public String getText() {
            return text;
        }

        public int getCursorPosition() {
            return cursorPosition;
        }
    }
}
