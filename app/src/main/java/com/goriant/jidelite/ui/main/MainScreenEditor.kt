package com.goriant.jidelite.ui.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.EditText
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.viewinterop.AndroidView
import com.goriant.jidelite.R
import com.goriant.jidelite.editor.EditorInteractionHelper
import com.goriant.jidelite.editor.JavaSyntaxHighlighter
import com.goriant.jidelite.ui.theme.JIdeLiteColors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.mutableStateOf

@Composable
internal fun EditorPane(
    modifier: Modifier,
    fileName: String,
    isDirty: Boolean,
    selectedFilePath: String?,
    editorText: String,
    editorDiagnostic: EditorDiagnostic?,
    editorBridge: EditorBridgeState,
    syntaxHighlighter: JavaSyntaxHighlighter,
    editorFontSizeSp: Float,
    isWordWrapEnabled: Boolean,
    isFindReplaceVisible: Boolean,
    isReplaceExpanded: Boolean,
    findQuery: String,
    replaceQuery: String,
    findMatches: List<EditorSearchMatch>,
    activeFindMatchIndex: Int,
    openTabs: List<EditorTab>,
    activeTabIndex: Int,
    onSwitchTab: (Int) -> Unit,
    onCloseTab: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onOpenFind: () -> Unit,
    onCloseFind: () -> Unit,
    onToggleReplace: () -> Unit,
    onFindQueryChanged: (String) -> Unit,
    onReplaceQueryChanged: (String) -> Unit,
    onFindPrevious: () -> Unit,
    onFindNext: () -> Unit,
    onReplaceCurrent: () -> Unit,
    onReplaceAll: () -> Unit,
    onDecreaseFont: () -> Unit,
    onIncreaseFont: () -> Unit,
    onToggleWordWrap: () -> Unit,
    onFontSizeChanged: (Float) -> Unit,
    onEditorChanged: (String) -> Unit,
    onHandleShortcut: (EditText, Int, KeyEvent) -> Boolean
) {
    val themeColors = JIdeLiteColors
    val editorThemeSignature = themeColors.hashCode()
    var gutterHeaderWidthPx by remember { mutableIntStateOf(52) }
    var editorContentStartPx by remember { mutableIntStateOf(66) }
    val density = LocalDensity.current
    val gutterHeaderWidth = with(density) { gutterHeaderWidthPx.toDp() }
    val editorContentStartWidth = with(density) { editorContentStartPx.toDp() }
    val currentHandleShortcut by rememberUpdatedState(onHandleShortcut)
    val currentEditorChanged by rememberUpdatedState(onEditorChanged)
    val currentFontSizeChanged by rememberUpdatedState(onFontSizeChanged)

    Column(
        modifier = modifier.background(themeColors.editorSurface)
    ) {
        // Multi-file tab bar
        if (openTabs.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                openTabs.forEachIndexed { index, tab ->
                    val isActive = index == activeTabIndex
                    Box(
                        modifier = Modifier
                            .height(32.dp)
                            .background(
                                if (isActive) themeColors.editorSurface
                                else MaterialTheme.colorScheme.surfaceContainer
                            )
                            .clickable { onSwitchTab(index) }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (isActive) {
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .height(4.dp)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                            Text(
                                text = if (tab.isDirty) "${tab.fileName} ●" else tab.fileName,
                                color = if (isActive) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
                                maxLines = 1
                            )
                            Text(
                                text = "✕",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                modifier = Modifier
                                    .clickable { onCloseTab(index) }
                                    .padding(2.dp)
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(gutterHeaderWidth)
                    .background(themeColors.editorGutterBackground)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(themeColors.editorGutterDivider)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(start = editorContentStartWidth),
                verticalAlignment = Alignment.Bottom
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(min = 134.dp, max = 292.dp)
                        .fillMaxHeight()
                        .background(themeColors.editorSurface)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(MaterialTheme.colorScheme.primary)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(8.dp)
                                .height(8.dp)
                                .background(MaterialTheme.colorScheme.secondary)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Text(
                            text = if (isDirty) "$fileName *" else fileName,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChromeActionButton(label = "FIND", enabled = true, highlighted = false, compact = true, onClick = onOpenFind)
                ChromeActionButton(label = "ALL", enabled = true, highlighted = false, compact = true, onClick = onSelectAll)
                ChromeActionButton(label = "COPY", enabled = true, highlighted = false, compact = true, onClick = onCopy)
                ChromeActionButton(label = "PASTE", enabled = true, highlighted = false, compact = true, onClick = onPaste)
                ChromeActionButton(label = "WRAP", enabled = true, highlighted = isWordWrapEnabled, compact = true, onClick = onToggleWordWrap)
                ChromeActionButton(label = "A-", enabled = true, highlighted = false, compact = true, onClick = onDecreaseFont)
                ChromeActionButton(label = "A+", enabled = true, highlighted = false, compact = true, onClick = onIncreaseFont)

                Text(
                    text = "${editorFontSizeSp.roundToInt()}sp",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    val displayDensity = context.resources.displayMetrics.density
                    val horizontalPadding = (14f * displayDensity).toInt()
                    val verticalPadding = (14f * displayDensity).toInt()

                    CodeEditorEditText(context).apply {
                        editorBridge.input = this
                        editorBridge.documentPath = selectedFilePath
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        background = null
                        hint = context.getString(R.string.editor_hint)
                        gravity = Gravity.TOP or Gravity.START
                        typeface = Typeface.MONOSPACE
                        textSize = 13f
                        includeFontPadding = false
                        isLongClickable = true
                        setHorizontallyScrolling(true)
                        overScrollMode = EditText.OVER_SCROLL_IF_CONTENT_SCROLLS
                        inputType = InputType.TYPE_CLASS_TEXT or
                            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                        imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI
                        setEditorContentPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
                        applyTheme(
                            textColor = themeColors.editorText.toArgb(),
                            hintColor = themeColors.editorHint.toArgb(),
                            gutterColor = themeColors.editorGutterBackground.toArgb(),
                            gutterDividerColor = themeColors.editorGutterDivider.toArgb(),
                            lineNumberColor = themeColors.editorLineNumber.toArgb(),
                            activeLineNumberColor = themeColors.editorLineNumberActive.toArgb(),
                            errorLineNumberColor = themeColors.editorLineNumberError.toArgb(),
                            errorLineColor = themeColors.editorErrorLineBackground.toArgb(),
                            searchMatchColor = themeColors.editorSearchMatch.toArgb(),
                            activeSearchMatchColor = themeColors.editorSearchMatchActive.toArgb()
                        )
                        onGutterWidthChanged = { gutterHeaderWidthPx = it }
                        onContentStartChanged = { editorContentStartPx = it }
                        gutterHeaderWidthPx = currentGutterWidthPx()
                        editorContentStartPx = currentContentStartPx()
                        setEditorFontSizeSp(editorFontSizeSp)
                        setWordWrapEnabled(isWordWrapEnabled)
                        this.onFontSizeChanged = { currentFontSizeChanged(it) }
                        this.onSoftEnterKey = { insertSmartNewline(this) }
                        setOnContextClickListener {
                            requestFocus()
                            performLongClick()
                        }
                        setOnKeyListener { _, keyCode, event ->
                            currentHandleShortcut(this, keyCode, event)
                        }
                        addTextChangedListener(object : TextWatcher {
                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

                            override fun afterTextChanged(s: Editable?) {
                                if (editorBridge.suppressCallbacks) {
                                    return
                                }

                                currentEditorChanged(s?.toString().orEmpty())
                                syntaxHighlighter.schedule(this@apply)
                            }
                        })
                        loadDocument(editorText)
                        syntaxHighlighter.highlightNow(this)
                    }
                },
                update = { editText ->
                    editorBridge.input = editText
                    editText.onFontSizeChanged = { currentFontSizeChanged(it) }
                    editText.onGutterWidthChanged = { gutterHeaderWidthPx = it }
                    editText.onContentStartChanged = { editorContentStartPx = it }
                    editText.onSoftEnterKey = { insertSmartNewline(editText) }
                    gutterHeaderWidthPx = editText.currentGutterWidthPx()
                    editorContentStartPx = editText.currentContentStartPx()
                    editText.applyTheme(
                        textColor = themeColors.editorText.toArgb(),
                        hintColor = themeColors.editorHint.toArgb(),
                        gutterColor = themeColors.editorGutterBackground.toArgb(),
                        gutterDividerColor = themeColors.editorGutterDivider.toArgb(),
                        lineNumberColor = themeColors.editorLineNumber.toArgb(),
                        activeLineNumberColor = themeColors.editorLineNumberActive.toArgb(),
                        errorLineNumberColor = themeColors.editorLineNumberError.toArgb(),
                        errorLineColor = themeColors.editorErrorLineBackground.toArgb(),
                        searchMatchColor = themeColors.editorSearchMatch.toArgb(),
                        activeSearchMatchColor = themeColors.editorSearchMatchActive.toArgb()
                    )
                    if (editorBridge.editorThemeSignature != editorThemeSignature) {
                        syntaxHighlighter.highlightNow(editText)
                        editorBridge.editorThemeSignature = editorThemeSignature
                    }
                    if (abs(editText.currentFontSizeSp() - editorFontSizeSp) >= 0.05f) {
                        editText.setEditorFontSizeSp(editorFontSizeSp)
                    }
                    editText.setWordWrapEnabled(isWordWrapEnabled)
                    if (editorBridge.documentPath != selectedFilePath) {
                        editorBridge.suppressCallbacks = true
                        editText.loadDocument(editorText)
                        syntaxHighlighter.highlightNow(editText)
                        editorBridge.suppressCallbacks = false
                        editorBridge.documentPath = selectedFilePath
                    } else if (editText.text.toString() != editorText) {
                        editorBridge.suppressCallbacks = true
                        editText.applyExternalStateText(editorText)
                        syntaxHighlighter.highlightNow(editText)
                        editorBridge.suppressCallbacks = false
                    }
                    editText.setDiagnosticLine(editorDiagnostic?.lineNumber)
                    if (editorDiagnostic == null) {
                        editorBridge.lastDiagnosticRequestId = null
                    } else if (editorBridge.lastDiagnosticRequestId != editorDiagnostic.requestId) {
                        editText.jumpToLine(editorDiagnostic.lineNumber)
                        editorBridge.lastDiagnosticRequestId = editorDiagnostic.requestId
                    }
                    if (isFindReplaceVisible && findQuery.isNotBlank() && findMatches.isNotEmpty()) {
                        editText.showSearchMatches(findMatches, activeFindMatchIndex.coerceIn(0, findMatches.lastIndex))
                    } else {
                        editText.clearSearchHighlights()
                    }
                }
            )

            if (isFindReplaceVisible) {
                FindReplaceBar(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .widthIn(min = 340.dp, max = 460.dp)
                        .shadow(4.dp),
                    query = findQuery,
                    replacement = replaceQuery,
                    matchCount = findMatches.size,
                    activeMatchIndex = activeFindMatchIndex,
                    isReplaceExpanded = isReplaceExpanded,
                    onQueryChanged = onFindQueryChanged,
                    onReplacementChanged = onReplaceQueryChanged,
                    onPrevious = onFindPrevious,
                    onNext = onFindNext,
                    onReplace = onReplaceCurrent,
                    onReplaceAll = onReplaceAll,
                    onToggleReplace = onToggleReplace,
                    onClose = onCloseFind
                )
            }
        }
    }
}

@Composable
private fun FindReplaceBar(
    modifier: Modifier = Modifier,
    query: String,
    replacement: String,
    matchCount: Int,
    activeMatchIndex: Int,
    isReplaceExpanded: Boolean,
    onQueryChanged: (String) -> Unit,
    onReplacementChanged: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onReplace: () -> Unit,
    onReplaceAll: () -> Unit,
    onToggleReplace: () -> Unit,
    onClose: () -> Unit
) {
    val themeColors = JIdeLiteColors
    val focusRequester = remember { FocusRequester() }
    val matchStatus = if (matchCount == 0) {
        "No results"
    } else {
        "${activeMatchIndex + 1} of $matchCount"
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = modifier
            .background(themeColors.findWidgetBackground)
            .border(
                width = 1.dp,
                color = themeColors.findWidgetBorder,
                shape = RoundedCornerShape(0.dp)
            )
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FindWidgetIconButton(
                symbol = if (isReplaceExpanded) "\u25BE" else "\u25B8",
                onClick = onToggleReplace
            )

            FindWidgetTextField(
                modifier = Modifier
                    .weight(1f),
                value = query,
                onValueChange = onQueryChanged,
                placeholder = stringResource(R.string.find_label),
                focusRequester = focusRequester
            )

            Text(
                text = matchStatus,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                maxLines = 1
            )

            FindWidgetIconButton(symbol = "\u2039", onClick = onPrevious)
            FindWidgetIconButton(symbol = "\u203A", onClick = onNext)
            FindWidgetIconButton(symbol = "\u00D7", onClick = onClose)
        }

        if (isReplaceExpanded) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(22.dp))

                FindWidgetTextField(
                    modifier = Modifier.weight(1f),
                    value = replacement,
                    onValueChange = onReplacementChanged,
                    placeholder = stringResource(R.string.replace_label)
                )

                FindWidgetIconButton(symbol = "\u21C4", onClick = onReplace)
                FindWidgetIconButton(symbol = "\u21BB", onClick = onReplaceAll)
            }
        }
    }
}
