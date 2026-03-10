package com.goriant.jidelite.ui.main

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.Editable
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.ContextCompat
import com.goriant.jidelite.R
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

class CodeEditorEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {

    companion object {
        private const val DEFAULT_FONT_SIZE_SP = 15f
        private const val MIN_FONT_SIZE_SP = 12f
        private const val MAX_FONT_SIZE_SP = 28f
        private const val MIN_LINE_NUMBER_DIGITS = 2
    }

    private val searchMatchColor = ContextCompat.getColor(context, R.color.editor_search_match)
    private val activeSearchMatchColor = ContextCompat.getColor(context, R.color.editor_search_match_active)
    private val gutterColor = ContextCompat.getColor(context, R.color.editor_gutter_bg)
    private val gutterDividerColor = ContextCompat.getColor(context, R.color.editor_gutter_divider)
    private val lineNumberColor = ContextCompat.getColor(context, R.color.editor_line_number)
    private val activeLineNumberColor = ContextCompat.getColor(context, R.color.editor_line_number_active)
    private val errorLineNumberColor = ContextCompat.getColor(context, R.color.editor_line_number_error)
    private val errorLineColor = ContextCompat.getColor(context, R.color.editor_error_line_bg)

    private val gutterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = gutterColor
    }
    private val gutterDividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density
        color = gutterDividerColor
    }
    private val lineNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
    }
    private val errorLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = errorLineColor
    }

    private val history = EditorHistory()
    private val scaleDetector = ScaleGestureDetector(context, FontScaleListener())
    private val density = resources.displayMetrics.density
    private val scaledDensity = density * resources.configuration.fontScale
    private val gutterHorizontalPaddingPx = (10f * density).roundToInt()

    private var basePaddingLeftPx = 0
    private var basePaddingTopPx = 0
    private var basePaddingRightPx = 0
    private var basePaddingBottomPx = 0
    private var gutterWidthPx = 0
    private var suppressHistoryRecording = false
    private var errorLineNumber: Int? = null

    var onFontSizeChanged: ((Float) -> Unit)? = null

    init {
        updateLineNumberPaint()
        setEditorFontSizeSp(DEFAULT_FONT_SIZE_SP)
        addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                }

                override fun afterTextChanged(s: Editable?) {
                    updateGutterPadding()
                    if (!suppressHistoryRecording) {
                        history.record(currentSnapshot())
                    }
                    invalidate()
                }
            }
        )
        history.reset(currentSnapshot())
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        drawErrorLine(canvas)
        drawGutterBackground(canvas)

        val saveCount = canvas.save()
        canvas.clipRect(scrollX + gutterWidthPx, scrollY, scrollX + width, scrollY + height)
        super.onDraw(canvas)
        canvas.restoreToCount(saveCount)

        drawLineNumbers(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        if (scaleDetector.isInProgress) {
            parent?.requestDisallowInterceptTouchEvent(true)
            return true
        }
        return super.onTouchEvent(event)
    }

    fun setEditorContentPadding(left: Int, top: Int, right: Int, bottom: Int) {
        basePaddingLeftPx = left
        basePaddingTopPx = top
        basePaddingRightPx = right
        basePaddingBottomPx = bottom
        updateGutterPadding()
    }

    fun loadDocument(text: String, selectionStart: Int = text.length, selectionEnd: Int = selectionStart) {
        val snapshot = EditorSnapshot(
            text = text,
            selectionStart = selectionStart.coerceIn(0, text.length),
            selectionEnd = selectionEnd.coerceIn(0, text.length)
        )
        applySnapshot(snapshot)
        history.reset(snapshot)
    }

    fun applyExternalStateText(
        text: String,
        selectionStart: Int = text.length,
        selectionEnd: Int = selectionStart
    ) {
        val snapshot = EditorSnapshot(
            text = text,
            selectionStart = selectionStart.coerceIn(0, text.length),
            selectionEnd = selectionEnd.coerceIn(0, text.length)
        )
        if (snapshot == history.current()) {
            return
        }
        applySnapshot(snapshot)
        history.record(snapshot)
    }

    fun undoTextChange(): Boolean {
        val snapshot = history.undo() ?: return false
        applySnapshot(snapshot)
        return true
    }

    fun redoTextChange(): Boolean {
        val snapshot = history.redo() ?: return false
        applySnapshot(snapshot)
        return true
    }

    fun hasUndoHistory(): Boolean = history.hasUndo()

    fun hasRedoHistory(): Boolean = history.hasRedo()

    fun setDiagnosticLine(lineNumber: Int?) {
        errorLineNumber = lineNumber?.takeIf { it > 0 }
        invalidate()
    }

    fun jumpToLine(lineNumber: Int) {
        if (lineNumber <= 0) {
            return
        }

        post {
            val currentLayout = layout ?: return@post
            if (currentLayout.lineCount <= 0) {
                return@post
            }

            val lineIndex = (lineNumber - 1).coerceIn(0, currentLayout.lineCount - 1)
            val lineStart = currentLayout.getLineStart(lineIndex)
            val visibleHeight = height - totalPaddingTop - totalPaddingBottom
            val targetScrollY = (currentLayout.getLineTop(lineIndex) - visibleHeight / 3).coerceAtLeast(0)
            requestFocus()
            setSelection(lineStart)
            scrollTo(scrollX, targetScrollY)
        }
    }

    fun revealRange(start: Int, endExclusive: Int) {
        val editable = text ?: return
        val safeStart = start.coerceIn(0, editable.length)
        val safeEnd = endExclusive.coerceIn(safeStart, editable.length)

        post {
            val currentLayout = layout ?: return@post
            val lineIndex = currentLayout.getLineForOffset(safeStart)
            val visibleHeight = height - totalPaddingTop - totalPaddingBottom
            val targetScrollY = (currentLayout.getLineTop(lineIndex) - visibleHeight / 3).coerceAtLeast(0)
            requestFocus()
            setSelection(safeStart, safeEnd)
            scrollTo(scrollX, targetScrollY)
        }
    }

    fun showSearchMatches(matches: List<EditorSearchMatch>, activeMatchIndex: Int) {
        val editable = text ?: return
        clearSearchHighlights(editable)
        matches.forEachIndexed { index, match ->
            if (match.start >= match.endExclusive || match.endExclusive > editable.length) {
                return@forEachIndexed
            }
            editable.setSpan(
                BackgroundColorSpan(if (index == activeMatchIndex) activeSearchMatchColor else searchMatchColor),
                match.start,
                match.endExclusive,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    fun clearSearchHighlights() {
        text?.let(::clearSearchHighlights)
    }

    fun setEditorFontSizeSp(sizeSp: Float) {
        val clampedSize = sizeSp.coerceIn(MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP)
        if (abs(currentFontSizeSp() - clampedSize) < 0.05f) {
            return
        }

        super.setTextSize(TypedValue.COMPLEX_UNIT_SP, clampedSize)
        setLineSpacing((5f * density) * (clampedSize / DEFAULT_FONT_SIZE_SP), 1f)
        updateLineNumberPaint()
        updateGutterPadding()
        onFontSizeChanged?.invoke(clampedSize)
    }

    fun currentFontSizeSp(): Float = textSize / scaledDensity

    private fun clearSearchHighlights(editable: Editable) {
        editable.getSpans(0, editable.length, BackgroundColorSpan::class.java).forEach { span ->
            editable.removeSpan(span)
        }
    }

    private fun drawErrorLine(canvas: Canvas) {
        val targetLineNumber = errorLineNumber ?: return
        val currentLayout = layout ?: return
        if (currentLayout.lineCount <= 0) {
            return
        }

        val lineIndex = (targetLineNumber - 1).coerceIn(0, currentLayout.lineCount - 1)
        val top = totalPaddingTop + currentLayout.getLineTop(lineIndex) - scrollY
        val bottom = totalPaddingTop + currentLayout.getLineBottom(lineIndex) - scrollY
        if (bottom < 0 || top > height) {
            return
        }

        val fixedLeft = scrollX.toFloat()
        canvas.drawRect(fixedLeft, top.toFloat(), fixedLeft + width, bottom.toFloat(), errorLinePaint)
    }

    private fun drawGutterBackground(canvas: Canvas) {
        val fixedLeft = scrollX.toFloat()
        canvas.drawRect(fixedLeft, 0f, fixedLeft + gutterWidthPx, height.toFloat(), gutterPaint)
        canvas.drawLine(
            fixedLeft + gutterWidthPx - gutterDividerPaint.strokeWidth / 2f,
            0f,
            fixedLeft + gutterWidthPx - gutterDividerPaint.strokeWidth / 2f,
            height.toFloat(),
            gutterDividerPaint
        )
    }

    private fun drawLineNumbers(canvas: Canvas) {
        val currentLayout = layout ?: return
        val fixedLeft = scrollX.toFloat()
        val currentLine = selectionStart.takeIf { it >= 0 }?.let(currentLayout::getLineForOffset) ?: -1

        for (lineIndex in 0 until currentLayout.lineCount) {
            val lineTop = totalPaddingTop + currentLayout.getLineTop(lineIndex) - scrollY
            val lineBottom = totalPaddingTop + currentLayout.getLineBottom(lineIndex) - scrollY
            if (lineBottom < 0 || lineTop > height) {
                continue
            }

            lineNumberPaint.color = when (lineIndex + 1) {
                errorLineNumber -> errorLineNumberColor
                currentLine + 1 -> activeLineNumberColor
                else -> lineNumberColor
            }

            val label = (lineIndex + 1).toString()
            val baseline = (totalPaddingTop + currentLayout.getLineBaseline(lineIndex) - scrollY).toFloat()
            val x = fixedLeft + gutterWidthPx - gutterHorizontalPaddingPx - lineNumberPaint.measureText(label)
            canvas.drawText(label, x, baseline, lineNumberPaint)
        }
    }

    private fun updateLineNumberPaint() {
        lineNumberPaint.textSize = textSize * 0.78f
    }

    private fun updateGutterPadding() {
        val digits = max(MIN_LINE_NUMBER_DIGITS, max(lineCount, 1).toString().length)
        val desiredGutterWidth = (gutterHorizontalPaddingPx * 2 + lineNumberPaint.measureText("8".repeat(digits))).roundToInt()
        if (desiredGutterWidth == gutterWidthPx &&
            paddingLeft == basePaddingLeftPx + gutterWidthPx &&
            paddingTop == basePaddingTopPx &&
            paddingRight == basePaddingRightPx &&
            paddingBottom == basePaddingBottomPx
        ) {
            return
        }

        gutterWidthPx = desiredGutterWidth
        super.setPadding(
            basePaddingLeftPx + gutterWidthPx,
            basePaddingTopPx,
            basePaddingRightPx,
            basePaddingBottomPx
        )
    }

    private fun currentSnapshot(): EditorSnapshot {
        val currentText = text?.toString().orEmpty()
        return EditorSnapshot(
            text = currentText,
            selectionStart = selectionStart.coerceIn(0, currentText.length),
            selectionEnd = selectionEnd.coerceIn(0, currentText.length)
        )
    }

    private fun applySnapshot(snapshot: EditorSnapshot) {
        suppressHistoryRecording = true
        setText(snapshot.text)
        setSelection(
            snapshot.selectionStart.coerceIn(0, snapshot.text.length),
            snapshot.selectionEnd.coerceIn(0, snapshot.text.length)
        )
        suppressHistoryRecording = false
        updateGutterPadding()
        invalidate()
    }

    private inner class FontScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val nextSize = currentFontSizeSp() * detector.scaleFactor
            setEditorFontSizeSp(nextSize)
            return true
        }
    }

    private data class EditorSnapshot(
        val text: String,
        val selectionStart: Int,
        val selectionEnd: Int
    )

    private class EditorHistory(private val maxEntries: Int = 200) {
        private val undoStack = ArrayDeque<EditorSnapshot>()
        private val redoStack = ArrayDeque<EditorSnapshot>()
        private var current = EditorSnapshot("", 0, 0)

        fun current(): EditorSnapshot = current

        fun reset(snapshot: EditorSnapshot) {
            undoStack.clear()
            redoStack.clear()
            current = snapshot
        }

        fun record(snapshot: EditorSnapshot) {
            if (snapshot == current) {
                return
            }
            undoStack.addLast(current)
            while (undoStack.size > maxEntries) {
                undoStack.removeFirst()
            }
            current = snapshot
            redoStack.clear()
        }

        fun undo(): EditorSnapshot? {
            if (undoStack.isEmpty()) {
                return null
            }
            redoStack.addLast(current)
            current = undoStack.removeLast()
            return current
        }

        fun redo(): EditorSnapshot? {
            if (redoStack.isEmpty()) {
                return null
            }
            undoStack.addLast(current)
            current = redoStack.removeLast()
            return current
        }

        fun hasUndo(): Boolean = undoStack.isNotEmpty()

        fun hasRedo(): Boolean = redoStack.isNotEmpty()
    }
}
