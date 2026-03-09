package com.jidelite.ui.main

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
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.jidelite.R
import com.jidelite.editor.EditorInteractionHelper
import com.jidelite.editor.JavaSyntaxHighlighter
import com.jidelite.ui.theme.DotMint
import com.jidelite.ui.theme.DotPink
import com.jidelite.ui.theme.DotSand
import com.jidelite.ui.theme.EditorSurface
import com.jidelite.ui.theme.ExplorerSurface
import com.jidelite.ui.theme.PrimaryAccent
import com.jidelite.ui.theme.RunButtonColor
import com.jidelite.ui.theme.RunButtonText
import com.jidelite.ui.theme.SecondaryAccent
import com.jidelite.ui.theme.SelectedSurface
import com.jidelite.ui.theme.SurfaceContainer
import com.jidelite.ui.theme.TerminalInfo
import com.jidelite.ui.theme.TerminalReady
import com.jidelite.ui.theme.TerminalSurface
import com.jidelite.ui.theme.TopBarSurface
import java.io.File

private const val DEFAULT_EXPLORER_WIDTH = 228f
private const val DEFAULT_TERMINAL_HEIGHT = 188f
private val CollapsedExplorerWidth = 68.dp
private val ExplorerHandleWidth = 12.dp
private val TerminalHandleHeight = 12.dp
private val MinExpandedExplorerWidth = 156.dp
private val MinEditorWidth = 220.dp
private val MinTerminalHeight = 120.dp
private val MinEditorHeight = 180.dp

@Composable
fun MainRoute(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val uiState = viewModel.uiState
    val context = LocalContext.current
    val syntaxHighlighter = remember(context) { JavaSyntaxHighlighter(context) }
    val pendingToastMessage = viewModel.pendingToastMessage

    LaunchedEffect(pendingToastMessage) {
        val message = pendingToastMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.consumeToast()
    }

    MainScreen(
        modifier = modifier,
        uiState = uiState,
        syntaxHighlighter = syntaxHighlighter,
        onNewFile = viewModel::onNewFileRequested,
        onSave = viewModel::onSaveRequested,
        onFormat = viewModel::onFormatRequested,
        onRun = viewModel::onRunRequested,
        onOpenFile = viewModel::onOpenFileRequested,
        onClearTerminal = viewModel::onClearTerminalRequested,
        onEditorChanged = viewModel::onEditorChanged,
        onStatusChanged = viewModel::updateStatus
    )
}

@Composable
private fun MainScreen(
    modifier: Modifier = Modifier,
    uiState: MainUiState,
    syntaxHighlighter: JavaSyntaxHighlighter,
    onNewFile: () -> Unit,
    onSave: () -> Unit,
    onFormat: () -> Unit,
    onRun: () -> Unit,
    onOpenFile: (File) -> Unit,
    onClearTerminal: () -> Unit,
    onEditorChanged: (String) -> Unit,
    onStatusChanged: (String, String?) -> Unit
) {
    val context = LocalContext.current
    val appNameShort = stringResource(R.string.app_name_short)
    val editorBridge = remember { EditorBridgeState() }
    var isExplorerCollapsed by rememberSaveable { mutableStateOf(false) }
    var isTopBarCollapsed by rememberSaveable { mutableStateOf(true) }
    var explorerPaneWidthValue by rememberSaveable { mutableFloatStateOf(DEFAULT_EXPLORER_WIDTH) }
    var terminalPaneHeightValue by rememberSaveable { mutableFloatStateOf(DEFAULT_TERMINAL_HEIGHT) }

    DisposableEffect(Unit) {
        onDispose {
            editorBridge.clear()
        }
    }

    val showToast: (String) -> Unit = remember(context) {
        { message -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    }
    val editorActions = remember(context, appNameShort, onStatusChanged, showToast, uiState.selectedFileName) {
        EditorActionContext(
            context = context,
            selectedFileName = uiState.selectedFileName,
            appNameShort = appNameShort,
            onStatusChanged = onStatusChanged,
            showToast = showToast
        )
    }

    val currentEditorActions by rememberUpdatedState(editorActions)
    val currentEditorChanged by rememberUpdatedState(onEditorChanged)

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(
                collapsed = isTopBarCollapsed,
                statusText = uiState.statusText,
                selectedFileName = uiState.selectedFileName,
                fileCount = uiState.files.size,
                isRunning = uiState.isRunning,
                isExplorerCollapsed = isExplorerCollapsed,
                onNewFile = onNewFile,
                onSave = onSave,
                onFormat = onFormat,
                onRun = onRun,
                onToggleExplorer = { isExplorerCollapsed = !isExplorerCollapsed },
                onToggleCollapse = { isTopBarCollapsed = !isTopBarCollapsed }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val maxExplorerWidthCandidate = maxWidth - MinEditorWidth - ExplorerHandleWidth
                val maxExplorerWidth = if (maxExplorerWidthCandidate > CollapsedExplorerWidth) {
                    maxExplorerWidthCandidate
                } else {
                    CollapsedExplorerWidth
                }
                val minExplorerWidth = minOf(MinExpandedExplorerWidth, maxExplorerWidth)
                val effectiveExplorerWidth = if (isExplorerCollapsed) {
                    CollapsedExplorerWidth
                } else {
                    explorerPaneWidthValue.dp.coerceIn(minExplorerWidth, maxExplorerWidth)
                }

                val maxTerminalHeightCandidate = maxHeight - MinEditorHeight - TerminalHandleHeight
                val maxTerminalHeight = if (maxTerminalHeightCandidate > MinTerminalHeight) {
                    maxTerminalHeightCandidate
                } else {
                    MinTerminalHeight
                }
                val effectiveMinTerminalHeight = minOf(MinTerminalHeight, maxTerminalHeight)
                val effectiveTerminalHeight = terminalPaneHeightValue.dp.coerceIn(
                    effectiveMinTerminalHeight,
                    maxTerminalHeight
                )

                Row(modifier = Modifier.fillMaxSize()) {
                    ExplorerPane(
                        modifier = Modifier
                            .width(effectiveExplorerWidth)
                            .fillMaxHeight(),
                        collapsed = isExplorerCollapsed,
                        files = uiState.files,
                        selectedFileName = uiState.selectedFileName,
                        onToggleCollapse = { isExplorerCollapsed = !isExplorerCollapsed },
                        onOpenFile = onOpenFile
                    )

                    if (isExplorerCollapsed) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                    } else {
                        VerticalResizeHandle(
                            modifier = Modifier.fillMaxHeight(),
                            onDrag = { delta ->
                                explorerPaneWidthValue = EditorInteractionHelper.resizeByDelta(
                                    explorerPaneWidthValue,
                                    with(density) { delta.toDp().value },
                                    minExplorerWidth.value,
                                    maxExplorerWidth.value
                                )
                            }
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        EditorPane(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            fileName = uiState.selectedFileName ?: stringResource(R.string.editor_empty_title),
                            isDirty = uiState.isDirty,
                            editorText = uiState.editorText,
                            editorBridge = editorBridge,
                            syntaxHighlighter = syntaxHighlighter,
                            onSelectAll = {
                                selectAllInEditor(editorBridge, currentEditorActions)
                            },
                            onCopy = {
                                copySelectionFromEditor(editorBridge, currentEditorActions)
                            },
                            onPaste = {
                                pasteIntoEditor(editorBridge, currentEditorActions)
                            },
                            onEditorChanged = currentEditorChanged,
                            onHandleShortcut = { editText, keyCode, event ->
                                handleEditorShortcut(
                                    editText = editText,
                                    keyCode = keyCode,
                                    event = event,
                                    editorBridge = editorBridge,
                                    actionContext = currentEditorActions
                                )
                            }
                        )

                        HorizontalResizeHandle(
                            modifier = Modifier.fillMaxWidth(),
                            onDrag = { delta ->
                                terminalPaneHeightValue = EditorInteractionHelper.resizeByInvertedDelta(
                                    terminalPaneHeightValue,
                                    with(density) { delta.toDp().value },
                                    effectiveMinTerminalHeight.value,
                                    maxTerminalHeight.value
                                )
                            }
                        )

                        TerminalPane(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(effectiveTerminalHeight),
                            terminalText = uiState.terminalText,
                            workspacePath = uiState.workspacePath,
                            onClearTerminal = onClearTerminal
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    collapsed: Boolean,
    statusText: String,
    selectedFileName: String?,
    fileCount: Int,
    isRunning: Boolean,
    isExplorerCollapsed: Boolean,
    onNewFile: () -> Unit,
    onSave: () -> Unit,
    onFormat: () -> Unit,
    onRun: () -> Unit,
    onToggleExplorer: () -> Unit,
    onToggleCollapse: () -> Unit
) {
    val selectedFileLabel = selectedFileName ?: stringResource(R.string.editor_empty_title)
    val fileCountLabel = rememberFileCountLabel(fileCount)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TopBarSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (collapsed) 46.dp else 52.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(PrimaryAccent)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = stringResource(R.string.app_name),
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                fontSize = if (collapsed) 16.sp else 17.sp,
                letterSpacing = 0.2.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = stringResource(R.string.app_version),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.weight(1f))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChromeActionButton(
                    label = if (collapsed) "TOP+" else "TOP-",
                    enabled = true,
                    highlighted = false,
                    compact = true,
                    onClick = onToggleCollapse
                )

                ChromeActionButton(
                    label = if (isExplorerCollapsed) "SIDE+" else "SIDE-",
                    enabled = true,
                    highlighted = false,
                    compact = true,
                    onClick = onToggleExplorer
                )

                ChromeActionButton(
                    label = stringResource(R.string.action_new_short).uppercase(),
                    enabled = !isRunning,
                    highlighted = false,
                    compact = true,
                    onClick = onNewFile
                )

                ChromeActionButton(
                    label = stringResource(R.string.action_save).uppercase(),
                    enabled = !isRunning,
                    highlighted = false,
                    compact = true,
                    onClick = onSave
                )

                ChromeActionButton(
                    label = stringResource(R.string.action_format_short).uppercase(),
                    enabled = !isRunning,
                    highlighted = false,
                    compact = true,
                    onClick = onFormat
                )

                ChromeActionButton(
                    label = stringResource(R.string.action_run).uppercase(),
                    enabled = !isRunning,
                    highlighted = true,
                    compact = true,
                    onClick = onRun
                )
            }
        }

        if (!collapsed) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = statusText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = "$selectedFileLabel  |  $fileCountLabel",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ChromeActionButton(
    label: String,
    enabled: Boolean,
    highlighted: Boolean,
    compact: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(8.dp)
    val containerColor = if (highlighted) RunButtonColor else SurfaceContainer
    val textColor = if (highlighted) RunButtonText else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.55f)
            .clip(shape)
            .background(containerColor)
            .then(
                if (highlighted) {
                    Modifier
                } else {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                }
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(
                horizontal = if (compact) 12.dp else 24.dp,
                vertical = if (compact) 8.dp else 12.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (highlighted) "\u25B6 $label" else label,
            color = textColor,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            fontSize = if (compact) 11.sp else 14.sp,
            letterSpacing = if (compact) 0.3.sp else 0.7.sp
        )
    }
}

@Composable
private fun ExplorerPane(
    modifier: Modifier,
    collapsed: Boolean,
    files: List<File>,
    selectedFileName: String?,
    onToggleCollapse: () -> Unit,
    onOpenFile: (File) -> Unit
) {
    Box(
        modifier = modifier.background(ExplorerSurface)
    ) {
        if (collapsed) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ChromeActionButton(
                    label = ">>",
                    enabled = true,
                    highlighted = false,
                    compact = true,
                    onClick = onToggleCollapse
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "EX",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    letterSpacing = 1.2.sp
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                if (files.isEmpty()) {
                    Spacer(modifier = Modifier.weight(1f))
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        items(files, key = { it.name }) { file ->
                            CollapsedExplorerFilePill(
                                label = compactFileLabel(file.name),
                                selected = file.name == selectedFileName,
                                onClick = { onOpenFile(file) }
                            )
                        }
                    }
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.explorer_header),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.4.sp,
                        modifier = Modifier.weight(1f)
                    )

                    ChromeActionButton(
                        label = "<<",
                        enabled = true,
                        highlighted = false,
                        compact = true,
                        onClick = onToggleCollapse
                    )
                }

                Text(
                    text = "\u25BE ${stringResource(R.string.workspace_label)}",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                if (files.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.explorer_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        items(files, key = { it.name }) { file ->
                            ExplorerFileRow(
                                file = file,
                                selected = file.name == selectedFileName,
                                onClick = { onOpenFile(file) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CollapsedExplorerFilePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) SelectedSurface else Color.Transparent)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) PrimaryAccent else MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun ExplorerFileRow(
    file: File,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) SelectedSurface else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(8.dp)
                .height(18.dp)
                .background(if (selected) PrimaryAccent else SecondaryAccent)
        )

        Spacer(modifier = Modifier.width(14.dp))

        Text(
            text = file.name,
            color = if (selected) PrimaryAccent else MaterialTheme.colorScheme.onSurface,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun VerticalResizeHandle(
    modifier: Modifier = Modifier,
    onDrag: (Float) -> Unit
) {
    Box(
        modifier = modifier
            .width(ExplorerHandleWidth)
            .background(SurfaceContainer)
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState(onDrag)
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(2.dp)
                .padding(vertical = 10.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
    }
}

@Composable
private fun HorizontalResizeHandle(
    modifier: Modifier = Modifier,
    onDrag: (Float) -> Unit
) {
    Box(
        modifier = modifier
            .height(TerminalHandleHeight)
            .background(SurfaceContainer)
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState(onDrag)
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .padding(horizontal = 28.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
    }
}

@Composable
private fun EditorPane(
    modifier: Modifier,
    fileName: String,
    isDirty: Boolean,
    editorText: String,
    editorBridge: EditorBridgeState,
    syntaxHighlighter: JavaSyntaxHighlighter,
    onSelectAll: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onEditorChanged: (String) -> Unit,
    onHandleShortcut: (EditText, Int, KeyEvent) -> Boolean
) {
    val currentHandleShortcut by rememberUpdatedState(onHandleShortcut)
    val currentEditorChanged by rememberUpdatedState(onEditorChanged)

    Column(
        modifier = modifier.background(EditorSurface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(SurfaceContainer)
        ) {
            Row(
                modifier = Modifier.fillMaxHeight(),
                verticalAlignment = Alignment.Bottom
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(min = 142.dp, max = 320.dp)
                        .fillMaxHeight()
                        .background(EditorSurface)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(PrimaryAccent)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(SecondaryAccent)
                        )

                        Spacer(modifier = Modifier.width(14.dp))

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
                    .padding(end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChromeActionButton(
                    label = "ALL",
                    enabled = true,
                    highlighted = false,
                    compact = true,
                    onClick = onSelectAll
                )

                ChromeActionButton(
                    label = "COPY",
                    enabled = true,
                    highlighted = false,
                    compact = true,
                    onClick = onCopy
                )

                ChromeActionButton(
                    label = "PASTE",
                    enabled = true,
                    highlighted = false,
                    compact = true,
                    onClick = onPaste
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                val density = context.resources.displayMetrics.density
                val horizontalPadding = (18f * density).toInt()
                val verticalPadding = (16f * density).toInt()

                EditText(context).apply {
                    editorBridge.input = this
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    background = null
                    setTextColor(context.getColor(R.color.text_primary))
                    setHintTextColor(context.getColor(R.color.text_hint))
                    hint = context.getString(R.string.editor_hint)
                    gravity = Gravity.TOP or Gravity.START
                    typeface = Typeface.MONOSPACE
                    textSize = 15f
                    includeFontPadding = false
                    isLongClickable = true
                    setHorizontallyScrolling(true)
                    overScrollMode = EditText.OVER_SCROLL_IF_CONTENT_SCROLLS
                    inputType = InputType.TYPE_CLASS_TEXT or
                            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                    imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI
                    setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
                    setLineSpacing(5f * density, 1f)
                    setOnContextClickListener {
                        requestFocus()
                        performLongClick()
                    }
                    setOnKeyListener { _, keyCode, event ->
                        currentHandleShortcut(this, keyCode, event)
                    }
                    addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                        }

                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        }

                        override fun afterTextChanged(s: Editable?) {
                            if (editorBridge.suppressCallbacks) {
                                return
                            }

                            currentEditorChanged(s?.toString().orEmpty())
                            syntaxHighlighter.schedule(this@apply)
                        }
                    })
                }
            },
            update = { editText ->
                editorBridge.input = editText
                if (editText.text.toString() != editorText) {
                    editorBridge.suppressCallbacks = true
                    editText.setText(editorText)
                    editText.setSelection(editorText.length)
                    syntaxHighlighter.highlightNow(editText)
                    editorBridge.suppressCallbacks = false
                }
            }
        )
    }
}

@Composable
private fun TerminalPane(
    modifier: Modifier,
    terminalText: String,
    workspacePath: String,
    onClearTerminal: () -> Unit
) {
    val scrollState = rememberScrollState()
    val readyText = stringResource(R.string.terminal_ready)

    LaunchedEffect(terminalText) {
        scrollState.scrollTo(scrollState.maxValue)
    }

    val terminalColor = when {
        terminalText.startsWith(readyText) -> TerminalReady
        terminalText.contains("failed", ignoreCase = true) -> DotPink
        terminalText.contains("simulated", ignoreCase = true) -> SecondaryAccent
        else -> MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = modifier.background(TerminalSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .background(DotPink)
                )
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .background(DotSand)
                )
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .background(DotMint)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = stringResource(R.string.terminal_title).uppercase(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    letterSpacing = 1.6.sp
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = stringResource(R.string.action_clear).uppercase(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                letterSpacing = 1.2.sp,
                modifier = Modifier.clickable(onClick = onClearTerminal)
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            Text(
                text = stringResource(R.string.terminal_intro),
                color = TerminalInfo,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${stringResource(R.string.terminal_workspace_prefix)} $workspacePath",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = terminalText,
                color = terminalColor,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun rememberFileCountLabel(fileCount: Int): String {
    return when (fileCount) {
        0 -> stringResource(R.string.file_count_zero)
        1 -> stringResource(R.string.file_count_one)
        else -> stringResource(R.string.file_count_many, fileCount)
    }
}

private class EditorBridgeState {
    var input: EditText? = null
    var suppressCallbacks: Boolean = false

    fun clear() {
        input = null
        suppressCallbacks = false
    }
}

private data class EditorActionContext(
    val context: Context,
    val selectedFileName: String?,
    val appNameShort: String,
    val onStatusChanged: (String, String?) -> Unit,
    val showToast: (String) -> Unit
)

private fun activeEditor(
    editorBridge: EditorBridgeState,
    actionContext: EditorActionContext,
    showMissingEditorMessage: Boolean = true
): EditText? {
    val editText = editorBridge.input
    if (editText == null) {
        if (showMissingEditorMessage) {
            actionContext.showToast("Editor unavailable")
        }
        return null
    }

    editText.requestFocus()
    return editText
}

private fun copySelectionFromEditor(
    editorBridge: EditorBridgeState,
    actionContext: EditorActionContext
) {
    activeEditor(editorBridge, actionContext)?.let { editText ->
        copySelectionFromEditor(editText, actionContext)
    }
}

private fun copySelectionFromEditor(
    editText: EditText,
    actionContext: EditorActionContext
) {
    val selectedText = EditorInteractionHelper.selectedText(
        editText.text?.toString(),
        editText.selectionStart,
        editText.selectionEnd
    )
    if (selectedText.isEmpty()) {
        actionContext.showToast("Select text first")
        return
    }

    val clipboardManager = actionContext.context.getSystemService(ClipboardManager::class.java)
    clipboardManager.setPrimaryClip(
        ClipData.newPlainText(actionContext.selectedFileName ?: actionContext.appNameShort, selectedText)
    )
    actionContext.onStatusChanged("Copied selection", "Copied")
}

private fun cutSelectionFromEditor(
    editText: EditText,
    actionContext: EditorActionContext
) {
    val sourceText = editText.text?.toString().orEmpty()
    val selectedText = EditorInteractionHelper.selectedText(
        sourceText,
        editText.selectionStart,
        editText.selectionEnd
    )
    if (selectedText.isEmpty()) {
        actionContext.showToast("Select text first")
        return
    }

    val editable = editText.text ?: return
    val mutation = EditorInteractionHelper.removeSelection(
        sourceText,
        editText.selectionStart,
        editText.selectionEnd
    )
    val clipboardManager = actionContext.context.getSystemService(ClipboardManager::class.java)
    clipboardManager.setPrimaryClip(
        ClipData.newPlainText(actionContext.selectedFileName ?: actionContext.appNameShort, selectedText)
    )
    editable.replace(0, editable.length, mutation.text)
    editText.setSelection(mutation.cursorPosition)
    actionContext.onStatusChanged("Cut selection", null)
}

private fun pasteIntoEditor(
    editorBridge: EditorBridgeState,
    actionContext: EditorActionContext
) {
    activeEditor(editorBridge, actionContext)?.let { editText ->
        pasteIntoEditor(editText, actionContext)
    }
}

private fun pasteIntoEditor(
    editText: EditText,
    actionContext: EditorActionContext
) {
    val clipboardManager = actionContext.context.getSystemService(ClipboardManager::class.java)
    val primaryClip = clipboardManager.primaryClip
    if (!clipboardManager.hasPrimaryClip() || primaryClip == null || primaryClip.itemCount == 0) {
        actionContext.showToast("Clipboard is empty")
        return
    }

    val clipboardText = primaryClip.getItemAt(0).coerceToText(actionContext.context)?.toString().orEmpty()
    if (clipboardText.isEmpty()) {
        actionContext.showToast("Clipboard is empty")
        return
    }

    val editable = editText.text ?: return
    val mutation = EditorInteractionHelper.replaceSelection(
        editable.toString(),
        editText.selectionStart,
        editText.selectionEnd,
        clipboardText
    )
    editable.replace(0, editable.length, mutation.text)
    editText.setSelection(mutation.cursorPosition)
    actionContext.onStatusChanged("Pasted from clipboard", null)
}

private fun selectAllInEditor(
    editorBridge: EditorBridgeState,
    actionContext: EditorActionContext
) {
    activeEditor(editorBridge, actionContext)?.let { editText ->
        val editable = editText.text ?: return@let
        if (editable.isEmpty()) {
            actionContext.showToast("Nothing to select")
            return@let
        }

        editText.setSelection(0, editable.length)
        actionContext.onStatusChanged("Selected all", null)
    }
}

private fun handleEditorShortcut(
    editText: EditText,
    keyCode: Int,
    event: KeyEvent,
    editorBridge: EditorBridgeState,
    actionContext: EditorActionContext
): Boolean {
    if (event.action != KeyEvent.ACTION_DOWN) {
        return false
    }

    return when {
        keyCode == KeyEvent.KEYCODE_TAB -> {
            insertTwoSpaceIndent(editText)
            true
        }

        event.isCtrlPressed && keyCode == KeyEvent.KEYCODE_A -> {
            selectAllInEditor(editorBridge, actionContext)
            true
        }

        event.isCtrlPressed && keyCode == KeyEvent.KEYCODE_C -> {
            copySelectionFromEditor(editText, actionContext)
            true
        }

        event.isCtrlPressed && keyCode == KeyEvent.KEYCODE_X -> {
            cutSelectionFromEditor(editText, actionContext)
            true
        }

        event.isCtrlPressed && keyCode == KeyEvent.KEYCODE_V -> {
            pasteIntoEditor(editText, actionContext)
            true
        }

        else -> false
    }
}

private fun compactFileLabel(fileName: String): String {
    val baseName = fileName.removeSuffix(".java")
    if (baseName.isEmpty()) {
        return "J"
    }

    val digits = baseName.takeLastWhile { it.isDigit() }
    return if (digits.isNotEmpty()) {
        "${baseName.first().uppercaseChar()}$digits".take(3)
    } else {
        baseName.take(2).uppercase()
    }
}

private fun insertTwoSpaceIndent(editText: EditText) {
    val editable = editText.text ?: return
    val selectionStart = editText.selectionStart.coerceAtLeast(0)
    val selectionEnd = editText.selectionEnd.coerceAtLeast(0)
    val start = minOf(selectionStart, selectionEnd)
    val end = maxOf(selectionStart, selectionEnd)

    if (start == end) {
        editable.insert(start, "  ")
        editText.setSelection(start + 2)
        return
    }

    val currentText = editable.toString()
    val firstLineStart = currentText.lastIndexOf('\n', start - 1).let { index ->
        if (index == -1) 0 else index + 1
    }
    val selectedBlock = currentText.substring(firstLineStart, end)
    val indentedBlock = "  " + selectedBlock.replace("\n", "\n  ")
    val insertedSpaces = indentedBlock.length - selectedBlock.length

    editable.replace(firstLineStart, end, indentedBlock)
    editText.setSelection(start + 2, end + insertedSpaces)
}
