package com.goriant.jidelite.ui.main

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Editable
import android.text.Layout
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.util.AttributeSet
import android.util.TypedValue
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.ContextCompat
import com.goriant.jidelite.R
import com.goriant.jidelite.editor.BracketHelper
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

class CodeEditorEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {

    companion object {
        private const val DEFAULT_FONT_SIZE_SP = 13f
        private const val MIN_FONT_SIZE_SP = 12f
        private const val MAX_FONT_SIZE_SP = 28f
        private const val MIN_LINE_NUMBER_DIGITS = 3
    }

    private var searchMatchColor = ContextCompat.getColor(context, R.color.editor_search_match)
    private var activeSearchMatchColor = ContextCompat.getColor(context, R.color.editor_search_match_active)
    private var gutterColor = ContextCompat.getColor(context, R.color.editor_gutter_bg)
    private var gutterDividerColor = ContextCompat.getColor(context, R.color.editor_gutter_divider)
    private var lineNumberColor = ContextCompat.getColor(context, R.color.editor_line_number)
    private var activeLineNumberColor = ContextCompat.getColor(context, R.color.editor_line_number_active)
    private var errorLineNumberColor = ContextCompat.getColor(context, R.color.editor_line_number_error)
    private var errorLineColor = ContextCompat.getColor(context, R.color.editor_error_line_bg)

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
        textAlign = Paint.Align.RIGHT
        isSubpixelText = true
    }
    private val errorLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = errorLineColor
    }

    private val history = EditorHistory()
    private val scaleDetector = ScaleGestureDetector(context, FontScaleListener())
    private val density = resources.displayMetrics.density
    private val scaledDensity = density * resources.configuration.fontScale
    private val gutterHorizontalPaddingPx = (9f * density).roundToInt()
    private val minimumContentClearancePx = (10f * density).roundToInt()
    private val revealCursorWidthPx = max(2f * density, 1f)

    private var basePaddingLeftPx = 0
    private var basePaddingTopPx = 0
    private var basePaddingRightPx = 0
    private var basePaddingBottomPx = 0
    private var gutterWidthPx = 0
    private var suppressHistoryRecording = false
    private var errorLineNumber: Int? = null

    private var sourceLineCount = 1

    var onFontSizeChanged: ((Float) -> Unit)? = null
    var onGutterWidthChanged: ((Int) -> Unit)? = null
    var onContentStartChanged: ((Int) -> Unit)? = null
    var onSoftEnterKey: (() -> Unit)? = null

    fun setWordWrapEnabled(enabled: Boolean) {
        setHorizontallyScrolling(!enabled)
        invalidate()
        requestLayout()
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val base = super.onCreateInputConnection(outAttrs) ?: return null
        return object : InputConnectionWrapper(base, true) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                if (text == "\n") {
                    val callback = onSoftEnterKey
                    if (callback != null) {
                        callback()
                        return true
                    }
                }
                // Bracket auto-close
                if (text != null && text.length == 1) {
                    val typed = text[0]
                    val editable = this@CodeEditorEditText.text
                    val cursorPos = this@CodeEditorEditText.selectionStart
                    if (editable != null && cursorPos >= 0) {
                        val fullText = editable.toString()
                        if (BracketHelper.shouldAutoClose(fullText, cursorPos, typed)) {
                            val closer = BracketHelper.autoCloseChar(typed)
                            val result = super.commitText(text.toString() + closer, newCursorPosition)
                            // Position cursor between the pair
                            val newPos = this@CodeEditorEditText.selectionStart
                            if (newPos > 0) {
                                this@CodeEditorEditText.setSelection(newPos - 1)
                            }
                            return result
                        }
                    }
                }
                return super.commitText(text, newCursorPosition)
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                // Smart backspace: delete matched pair when cursor is between them
                if (beforeLength == 1 && afterLength == 0) {
                    val editable = this@CodeEditorEditText.text
                    val cursorPos = this@CodeEditorEditText.selectionStart
                    if (editable != null && cursorPos > 0 && cursorPos < editable.length) {
                        if (BracketHelper.isBetweenMatchedPair(editable.toString(), cursorPos)) {
                            return super.deleteSurroundingText(1, 1)
                        }
                    }
                }
                return super.deleteSurroundingText(beforeLength, afterLength)
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN &&
                    (event.keyCode == KeyEvent.KEYCODE_ENTER || event.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)
                ) {
                    val callback = onSoftEnterKey
                    if (callback != null) {
                        callback()
                        return true
                    }
                }
                // Smart backspace via hardware key
                if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_DEL) {
                    val editable = this@CodeEditorEditText.text
                    val cursorPos = this@CodeEditorEditText.selectionStart
                    val selEnd = this@CodeEditorEditText.selectionEnd
                    if (editable != null && cursorPos == selEnd && cursorPos > 0 && cursorPos < editable.length) {
                        if (BracketHelper.isBetweenMatchedPair(editable.toString(), cursorPos)) {
                            editable.delete(cursorPos - 1, cursorPos + 1)
                            return true
                        }
                    }
                }
                return super.sendKeyEvent(event)
            }
        }
    }

    init {
        updateLineNumberPaint()
        setEditorFontSizeSp(DEFAULT_FONT_SIZE_SP)
        addTextChangedListener(
            object : TextWatcher {
                private var removedNewlines = 0

                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                    removedNewlines = 0
                    if (s == null || count <= 0) return
                    for (i in start until (start + count).coerceAtMost(s.length)) {
                        if (s[i] == '\n') removedNewlines++
                    }
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    var addedNewlines = 0
                    if (s != null && count > 0) {
                        for (i in start until (start + count).coerceAtMost(s.length)) {
                            if (s[i] == '\n') addedNewlines++
                        }
                    }
                    sourceLineCount = (sourceLineCount - removedNewlines + addedNewlines).coerceAtLeast(1)
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

    fun applyTheme(
        textColor: Int,
        hintColor: Int,
        gutterColor: Int,
        gutterDividerColor: Int,
        lineNumberColor: Int,
        activeLineNumberColor: Int,
        errorLineNumberColor: Int,
        errorLineColor: Int,
        searchMatchColor: Int,
        activeSearchMatchColor: Int
    ) {
        setTextColor(textColor)
        setHintTextColor(hintColor)
        this.gutterColor = gutterColor
        this.gutterDividerColor = gutterDividerColor
        this.lineNumberColor = lineNumberColor
        this.activeLineNumberColor = activeLineNumberColor
        this.errorLineNumberColor = errorLineNumberColor
        this.errorLineColor = errorLineColor
        this.searchMatchColor = searchMatchColor
        this.activeSearchMatchColor = activeSearchMatchColor
        gutterPaint.color = gutterColor
        gutterDividerPaint.color = gutterDividerColor
        errorLinePaint.color = errorLineColor
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        drawErrorLine(canvas)
        drawGutterBackground(canvas)

        val saveCount = canvas.save()
        val clipLeft = scrollX + contentClipStartPx()
        val clipRight = scrollX + max(contentClipStartPx() + 1, width - contentClipEndPx())
        canvas.clipRect(clipLeft, scrollY, clipRight, scrollY + height)
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
        applySnapshot(snapshot, resetGutter = true)
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
            revealOffsetRange(lineStart, lineStart, anchorOffset = lineStart)
        }
    }

    fun revealRange(start: Int, endExclusive: Int) {
        revealOffsetRange(start, endExclusive, anchorOffset = start)
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

    fun currentGutterWidthPx(): Int = gutterWidthPx

    fun currentContentStartPx(): Int = compoundPaddingLeft

    private fun clearSearchHighlights(editable: Editable) {
        editable.getSpans(0, editable.length, BackgroundColorSpan::class.java).forEach { span ->
            editable.removeSpan(span)
        }
    }

    private fun revealOffsetRange(
        selectionStart: Int,
        selectionEndExclusive: Int,
        anchorOffset: Int
    ) {
        val editable = text ?: return
        val safeStart = selectionStart.coerceIn(0, editable.length)
        val safeEnd = selectionEndExclusive.coerceIn(safeStart, editable.length)
        val safeAnchor = anchorOffset.coerceIn(safeStart, safeEnd)

        post {
            val currentLayout = layout ?: return@post
            if (currentLayout.lineCount <= 0) {
                return@post
            }

            val anchorLine = currentLayout.getLineForOffset(safeAnchor)
            val visibleHeight = height - totalPaddingTop - totalPaddingBottom
            val targetScrollY = (currentLayout.getLineTop(anchorLine) - visibleHeight / 3).coerceAtLeast(0)
            val targetScrollX = calculateHorizontalRevealScroll(
                currentLayout = currentLayout,
                selectionStart = safeStart,
                selectionEndExclusive = safeEnd,
                anchorOffset = safeAnchor
            )

            requestFocus()
            setSelection(safeStart, safeEnd)
            scrollTo(targetScrollX, targetScrollY)
        }
    }

    private fun drawErrorLine(canvas: Canvas) {
        val targetLineNumber = errorLineNumber ?: return
        val currentLayout = layout ?: return
        val textStr = text?.toString() ?: ""

        // Tìm visual lines tương ứng với logical line (để highlight toàn bộ khi có wrap text)
        var currentLogical = 1
        var visualStart = -1
        var visualEnd = -1

        for (lineIndex in 0 until currentLayout.lineCount) {
            val lineStartOffset = currentLayout.getLineStart(lineIndex)
            val isLogicalStart = lineStartOffset == 0 || (lineStartOffset > 0 && textStr[lineStartOffset - 1] == '\n')

            if (isLogicalStart) {
                if (visualStart != -1) break
                if (currentLogical == targetLineNumber) {
                    visualStart = lineIndex
                    visualEnd = lineIndex
                }
                currentLogical++
            } else if (visualStart != -1) {
                visualEnd = lineIndex
            }
        }

        if (visualStart == -1) return

        val top = totalPaddingTop + currentLayout.getLineTop(visualStart)
        val bottom = totalPaddingTop + currentLayout.getLineBottom(visualEnd)

        if (bottom < scrollY || top > scrollY + height) return

        val contentLeft = scrollX + gutterWidthPx.toFloat()
        val saveCount = canvas.save()
        canvas.clipRect(contentLeft, scrollY.toFloat(), scrollX + width.toFloat(), scrollY + height.toFloat())
        canvas.drawRect(contentLeft, top.toFloat(), scrollX + width.toFloat(), bottom.toFloat(), errorLinePaint)
        canvas.restoreToCount(saveCount)
    }

    private fun drawGutterBackground(canvas: Canvas) {
        val gutterRight = scrollX + gutterWidthPx.toFloat()
        val saveCount = canvas.save()

        // Cố định gutter vào lề trái màn hình viewport bằng cách tịnh tiến theo scrollX, scrollY
        canvas.clipRect(scrollX.toFloat(), scrollY.toFloat(), gutterRight, scrollY + height.toFloat())
        canvas.drawRect(scrollX.toFloat(), scrollY.toFloat(), gutterRight, scrollY + height.toFloat(), gutterPaint)
        canvas.drawLine(
            gutterRight - gutterDividerPaint.strokeWidth / 2f,
            scrollY.toFloat(),
            gutterRight - gutterDividerPaint.strokeWidth / 2f,
            scrollY + height.toFloat(),
            gutterDividerPaint
        )
        canvas.restoreToCount(saveCount)
    }

    private fun drawLineNumbers(canvas: Canvas) {
        val currentLayout = layout ?: return
        val textStr = text?.toString() ?: ""
        val saveCount = canvas.save()

        val gutterRight = scrollX + gutterWidthPx.toFloat()
        canvas.clipRect(scrollX.toFloat(), scrollY.toFloat(), gutterRight, scrollY + height.toFloat())

        val xAnchor = gutterRight - gutterHorizontalPaddingPx.toFloat()

        // Tính toán dòng đang được trỏ chuột (logical line)
        var cursorLogicalLine = -1
        if (selectionStart >= 0 && selectionStart <= textStr.length) {
            var newlinesBeforeCursor = 0
            for (i in 0 until selectionStart) {
                if (textStr[i] == '\n') newlinesBeforeCursor++
            }
            cursorLogicalLine = newlinesBeforeCursor + 1
        }

        var logicalLineNumber = 1

        for (lineIndex in 0 until currentLayout.lineCount) {
            val lineStartOffset = currentLayout.getLineStart(lineIndex)
            // Xác định xem dòng hiển thị này có phải là sự bắt đầu của một đoạn code thực tế (không phải từ bị bẻ dòng)
            val isLogicalLineStart = lineStartOffset == 0 || (lineStartOffset > 0 && textStr[lineStartOffset - 1] == '\n')

            val lineTop = totalPaddingTop + currentLayout.getLineTop(lineIndex)
            val lineBottom = totalPaddingTop + currentLayout.getLineBottom(lineIndex)

            // Bỏ qua vẽ nếu nằm ngoài khung nhìn (culling) nhưng vẫn phải đếm số dòng
            if (lineBottom < scrollY || lineTop > scrollY + height) {
                if (isLogicalLineStart) logicalLineNumber++
                continue
            }

            if (isLogicalLineStart) {
                val isCurrentLine = logicalLineNumber == cursorLogicalLine
                val isErrorLine = logicalLineNumber == errorLineNumber

                lineNumberPaint.color = when {
                    isErrorLine -> errorLineNumberColor
                    isCurrentLine -> activeLineNumberColor
                    else -> lineNumberColor
                }
                lineNumberPaint.isFakeBoldText = isCurrentLine

                val label = logicalLineNumber.toString()

                val lineHeight = (lineBottom - lineTop).toFloat()
                val fontMetrics = lineNumberPaint.fontMetrics
                val textHeight = fontMetrics.descent - fontMetrics.ascent
                val baseline = lineTop + (lineHeight - textHeight) / 2f - fontMetrics.ascent

                canvas.drawText(label, xAnchor, baseline, lineNumberPaint)
                logicalLineNumber++
            }
        }
        canvas.restoreToCount(saveCount)
    }

    private fun updateLineNumberPaint() {
        lineNumberPaint.textSize = textSize * 0.78f
        lineNumberPaint.typeface = typeface ?: Typeface.MONOSPACE
        lineNumberPaint.isSubpixelText = true
        lineNumberPaint.textAlign = Paint.Align.RIGHT
    }

    private fun updateGutterPadding(forceReset: Boolean = false) {
        val digits = max(MIN_LINE_NUMBER_DIGITS, sourceLineCount.toString().length)
        val numberWidth = lineNumberPaint.measureText("8".repeat(digits))
        val desiredGutterWidth = (gutterHorizontalPaddingPx * 2 + numberWidth + density).roundToInt()

        val newGutterWidth = if (forceReset) desiredGutterWidth else max(gutterWidthPx, desiredGutterWidth)

        if (newGutterWidth == gutterWidthPx &&
            paddingLeft == basePaddingLeftPx + gutterWidthPx &&
            paddingTop == basePaddingTopPx &&
            paddingRight == basePaddingRightPx &&
            paddingBottom == basePaddingBottomPx
        ) {
            return
        }

        gutterWidthPx = newGutterWidth
        super.setPadding(
            basePaddingLeftPx + gutterWidthPx,
            basePaddingTopPx,
            basePaddingRightPx,
            basePaddingBottomPx
        )
        onGutterWidthChanged?.invoke(gutterWidthPx)
        onContentStartChanged?.invoke(currentContentStartPx())
    }

    private fun contentClipStartPx(): Int = gutterWidthPx + max(basePaddingLeftPx, minimumContentClearancePx)

    private fun contentClipEndPx(): Int = max(basePaddingRightPx, minimumContentClearancePx)

    private fun calculateHorizontalRevealScroll(
        currentLayout: Layout,
        selectionStart: Int,
        selectionEndExclusive: Int,
        anchorOffset: Int
    ): Int {
        val viewportLeft = scrollX + contentClipStartPx()
        val viewportRight = scrollX + width - contentClipEndPx()
        val anchorLine = currentLayout.getLineForOffset(anchorOffset)
        val selectionEndOnAnchorLine = selectionEndExclusive > selectionStart &&
                currentLayout.getLineForOffset((selectionEndExclusive - 1).coerceAtLeast(selectionStart)) == anchorLine

        val anchorLeft = horizontalPositionForOffset(currentLayout, anchorOffset)
        val selectionRight = if (selectionEndOnAnchorLine) {
            horizontalPositionForOffset(currentLayout, selectionEndExclusive)
        } else {
            anchorLeft + revealCursorWidthPx
        }
        val safeRight = max(anchorLeft + revealCursorWidthPx, selectionRight)
        val viewportWidth = max(1, viewportRight - viewportLeft)

        val targetScrollX = when {
            safeRight - anchorLeft >= viewportWidth -> anchorLeft - contentClipStartPx()
            anchorLeft < viewportLeft -> anchorLeft - contentClipStartPx()
            safeRight > viewportRight -> safeRight - (width - contentClipEndPx())
            else -> scrollX.toFloat()
        }

        return targetScrollX.roundToInt().coerceAtLeast(0)
    }

    private fun horizontalPositionForOffset(currentLayout: Layout, offset: Int): Float {
        val safeOffset = offset.coerceIn(0, text?.length ?: 0)
        return compoundPaddingLeft + currentLayout.getPrimaryHorizontal(safeOffset)
    }

    private fun currentSnapshot(): EditorSnapshot {
        val currentText = text?.toString().orEmpty()
        return EditorSnapshot(
            text = currentText,
            selectionStart = selectionStart.coerceIn(0, currentText.length),
            selectionEnd = selectionEnd.coerceIn(0, currentText.length)
        )
    }

    private fun applySnapshot(snapshot: EditorSnapshot, resetGutter: Boolean = false) {
        // Đã xóa phần pre-compute `sourceLineCount` bị lỗi logic khi load dữ liệu,
        // để nhường hoàn toàn cho TextWatcher xử lý increment đúng chuẩn.
        suppressHistoryRecording = true
        setText(snapshot.text)
        setSelection(
            snapshot.selectionStart.coerceIn(0, snapshot.text.length),
            snapshot.selectionEnd.coerceIn(0, snapshot.text.length)
        )
        suppressHistoryRecording = false
        updateGutterPadding(forceReset = resetGutter)
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