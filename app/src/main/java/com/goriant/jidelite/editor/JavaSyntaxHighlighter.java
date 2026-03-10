package com.goriant.jidelite.editor;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.widget.EditText;

import androidx.core.content.ContextCompat;

import com.goriant.jidelite.R;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JavaSyntaxHighlighter {

    private static final Pattern KEYWORD_PATTERN = Pattern.compile(
            "\\b(?:abstract|assert|boolean|break|byte|case|catch|char|class|const|continue|default|do|double|else|enum|extends|final|finally|float|for|if|implements|import|instanceof|int|interface|long|native|new|package|private|protected|public|record|return|short|static|strictfp|super|switch|synchronized|this|throw|throws|transient|try|var|void|volatile|while|true|false|null)\\b"
    );
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\b\\d+(?:\\.\\d+)?(?:[dDfFlL])?\\b");
    private static final Pattern STRING_PATTERN = Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"");
    private static final Pattern COMMENT_PATTERN = Pattern.compile("/\\*(?:.|\\R)*?\\*/|//.*?$", Pattern.MULTILINE);
    private static final Pattern ANNOTATION_PATTERN = Pattern.compile("@[A-Za-z_][A-Za-z0-9_]*");

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int keywordColor;
    private int stringColor;
    private int commentColor;
    private int annotationColor;
    private int numberColor;

    private Runnable pendingHighlight;
    private boolean isApplying;

    public JavaSyntaxHighlighter(Context context) {
        keywordColor = ContextCompat.getColor(context, R.color.syntax_keyword);
        stringColor = ContextCompat.getColor(context, R.color.syntax_string);
        commentColor = ContextCompat.getColor(context, R.color.syntax_comment);
        annotationColor = ContextCompat.getColor(context, R.color.syntax_annotation);
        numberColor = ContextCompat.getColor(context, R.color.syntax_number);
    }

    public void updateColors(
            int keywordColor,
            int stringColor,
            int commentColor,
            int annotationColor,
            int numberColor
    ) {
        this.keywordColor = keywordColor;
        this.stringColor = stringColor;
        this.commentColor = commentColor;
        this.annotationColor = annotationColor;
        this.numberColor = numberColor;
    }

    public void schedule(EditText editText) {
        cancelPending();
        pendingHighlight = new Runnable() {
            @Override
            public void run() {
                apply(editText);
            }
        };
        handler.postDelayed(pendingHighlight, 90L);
    }

    public void highlightNow(EditText editText) {
        cancelPending();
        apply(editText);
    }

    private void cancelPending() {
        if (pendingHighlight != null) {
            handler.removeCallbacks(pendingHighlight);
            pendingHighlight = null;
        }
    }

    private void apply(EditText editText) {
        if (isApplying) {
            return;
        }

        Editable editable = editText.getText();
        if (editable == null) {
            return;
        }

        int selectionStart = editText.getSelectionStart();
        int selectionEnd = editText.getSelectionEnd();
        isApplying = true;
        try {
            clearColorSpans(editable);
            if (editable.length() == 0) {
                return;
            }

            String source = editable.toString();
            applyPattern(editable, source, KEYWORD_PATTERN, keywordColor);
            applyPattern(editable, source, NUMBER_PATTERN, numberColor);
            applyPattern(editable, source, ANNOTATION_PATTERN, annotationColor);
            applyPattern(editable, source, STRING_PATTERN, stringColor);
            applyPattern(editable, source, COMMENT_PATTERN, commentColor);
        } finally {
            isApplying = false;
            int safeStart = Math.max(0, Math.min(selectionStart, editable.length()));
            int safeEnd = Math.max(0, Math.min(selectionEnd, editable.length()));
            editText.setSelection(safeStart, safeEnd);
        }
    }

    private void clearColorSpans(Editable editable) {
        ForegroundColorSpan[] spans = editable.getSpans(0, editable.length(), ForegroundColorSpan.class);
        for (ForegroundColorSpan span : spans) {
            editable.removeSpan(span);
        }
    }

    private void applyPattern(Editable editable, String source, Pattern pattern, int color) {
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            editable.setSpan(
                    new ForegroundColorSpan(color),
                    matcher.start(),
                    matcher.end(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
    }
}
