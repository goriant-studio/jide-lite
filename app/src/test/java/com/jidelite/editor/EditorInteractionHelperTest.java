package com.jidelite.editor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class EditorInteractionHelperTest {

    private static final String INDENT = "    ";

    @Test
    void normalizeSelectionSortsAndClampsBounds() {
        EditorInteractionHelper.SelectionBounds bounds =
                EditorInteractionHelper.normalizeSelection(5, 9, -2);

        assertThat(bounds.getStart()).isEqualTo(0);
        assertThat(bounds.getEndExclusive()).isEqualTo(5);
        assertThat(bounds.isEmpty()).isFalse();
    }

    @Test
    void selectedTextReturnsEmptyWhenSelectionIsCollapsed() {
        String selectedText = EditorInteractionHelper.selectedText("sample", 3, 3);

        assertThat(selectedText).isEmpty();
    }

    @Test
    void selectedTextSupportsReverseSelection() {
        String selectedText = EditorInteractionHelper.selectedText("abcdef", 5, 2);

        assertThat(selectedText).isEqualTo("cde");
    }

    @Test
    void replaceSelectionOverwritesSelectedBlockAndMovesCursorToInsertedTextEnd() {
        EditorInteractionHelper.TextMutation mutation =
                EditorInteractionHelper.replaceSelection("hello world", 6, 11, "pad7");

        assertThat(mutation.getText()).isEqualTo("hello pad7");
        assertThat(mutation.getCursorPosition()).isEqualTo(10);
    }

    @Test
    void replaceSelectionInsertsAtCaretWhenNothingIsSelected() {
        EditorInteractionHelper.TextMutation mutation =
                EditorInteractionHelper.replaceSelection("hello", 2, 2, "--");

        assertThat(mutation.getText()).isEqualTo("he--llo");
        assertThat(mutation.getCursorPosition()).isEqualTo(4);
    }

    @Test
    void removeSelectionDeletesSelectedTextAndLeavesCursorAtSelectionStart() {
        EditorInteractionHelper.TextMutation mutation =
                EditorInteractionHelper.removeSelection("hello world", 5, 11);

        assertThat(mutation.getText()).isEqualTo("hello");
        assertThat(mutation.getCursorPosition()).isEqualTo(5);
    }

    @Test
    void insertSmartNewlineKeepsCurrentLineIndent() {
        EditorInteractionHelper.TextMutation mutation =
                EditorInteractionHelper.insertSmartNewline("    int value = 1;", 18, 18, INDENT);

        assertThat(mutation.getText()).isEqualTo("    int value = 1;\n    ");
        assertThat(mutation.getCursorPosition()).isEqualTo(23);
    }

    @Test
    void insertSmartNewlineAddsExtraIndentAfterOpeningBrace() {
        EditorInteractionHelper.TextMutation mutation =
                EditorInteractionHelper.insertSmartNewline("if (ready) {", 12, 12, INDENT);

        assertThat(mutation.getText()).isEqualTo("if (ready) {\n    ");
        assertThat(mutation.getCursorPosition()).isEqualTo(17);
    }

    @Test
    void insertSmartNewlineSplitsBracePairIntoIndentedBlock() {
        EditorInteractionHelper.TextMutation mutation =
                EditorInteractionHelper.insertSmartNewline("if (ready) {}", 12, 12, INDENT);

        assertThat(mutation.getText()).isEqualTo("if (ready) {\n    \n}");
        assertThat(mutation.getCursorPosition()).isEqualTo(17);
    }

    @Test
    void resizeHelpersClampDraggedPaneSizes() {
        float expanded = EditorInteractionHelper.resizeByDelta(228f, 20f, 156f, 260f);
        float clampedExplorer = EditorInteractionHelper.resizeByDelta(228f, 80f, 156f, 260f);
        float clampedTerminal = EditorInteractionHelper.resizeByInvertedDelta(188f, 100f, 120f, 260f);

        assertThat(expanded).isEqualTo(248f);
        assertThat(clampedExplorer).isEqualTo(260f);
        assertThat(clampedTerminal).isEqualTo(120f);
    }
}
