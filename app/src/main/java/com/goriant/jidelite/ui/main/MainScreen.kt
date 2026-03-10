package com.goriant.jidelite.ui.main

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import com.goriant.jidelite.R
import com.goriant.jidelite.editor.EditorInteractionHelper
import com.goriant.jidelite.editor.JavaSyntaxHighlighter
import com.goriant.jidelite.ui.theme.JIdeLiteColors
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

private const val DEFAULT_EXPLORER_WIDTH = 228f
private const val DEFAULT_TERMINAL_HEIGHT = 188f
private const val EDITOR_INDENT = "    "
private val CollapsedExplorerWidth = 68.dp
private val ExplorerHandleWidth = 12.dp
private val TerminalHandleHeight = 12.dp
private val MinExpandedExplorerWidth = 156.dp
private val MinEditorWidth = 220.dp
private val MinTerminalHeight = 120.dp
private val MinEditorHeight = 180.dp
private val CollapsedTerminalHeight = 38.dp
private val ChromeCompactButtonPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
private val ChromeRegularButtonPadding = PaddingValues(horizontal = 18.dp, vertical = 9.dp)
private val ChromeInlineButtonPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
private val ChromeCompactFieldHeight = 42.dp
private val ChromeFindWidgetFieldHeight = 36.dp
private val ChromeFindWidgetMinWidth = 220.dp
private val ChromeFindWidgetMaxWidth = 420.dp

@Composable
fun MainRoute(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val uiState = viewModel.uiState
    val context = LocalContext.current
    val syntaxHighlighter = remember(context) { JavaSyntaxHighlighter(context) }
    val pendingToastMessage = viewModel.pendingToastMessage
    val shareChooserTitle = stringResource(R.string.share_chooser_title)

    LaunchedEffect(pendingToastMessage) {
        val message = pendingToastMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.consumeToast()
    }

    val shareCurrentFile = fun() {
        val file = viewModel.prepareSelectedJavaFileForShare() ?: return
        val fileUri = try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (_: IllegalArgumentException) {
            viewModel.updateStatus("Share failed", context.getString(R.string.toast_share_failed))
            return
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/x-java-source"
            putExtra(Intent.EXTRA_STREAM, fileUri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            clipData = ClipData.newUri(context.contentResolver, file.name, fileUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            context.startActivity(Intent.createChooser(shareIntent, shareChooserTitle))
            viewModel.updateStatus("Sharing ${file.name}", context.getString(R.string.toast_share_opened))
        } catch (_: ActivityNotFoundException) {
            viewModel.updateStatus("Share failed", context.getString(R.string.toast_share_failed))
        }
    }

    MainScreen(
        modifier = modifier,
        uiState = uiState,
        syntaxHighlighter = syntaxHighlighter,
        onNewFile = viewModel::onNewFileRequested,
        onNewFolder = viewModel::onNewFolderRequested,
        onResolveDependencies = viewModel::onResolveDependenciesRequested,
        onSave = viewModel::onSaveRequested,
        onFormat = viewModel::onFormatRequested,
        onRun = viewModel::onRunRequested,
        onOpenFile = viewModel::onOpenFileRequested,
        onRenameEntry = viewModel::onRenameEntryRequested,
        onDeleteEntry = viewModel::onDeleteEntryRequested,
        onMoveEntry = viewModel::onMoveEntryRequested,
        onClearTerminal = viewModel::onClearTerminalRequested,
        onEditorChanged = viewModel::onEditorChanged,
        onStatusChanged = viewModel::updateStatus,
        onToggleTheme = viewModel::onToggleThemeRequested,
        onShareFile = shareCurrentFile,
        onShowOnboarding = viewModel::showOnboarding,
        onDismissOnboarding = viewModel::dismissOnboarding
    )
}

@Composable
private fun MainScreen(
    modifier: Modifier = Modifier,
    uiState: MainUiState,
    syntaxHighlighter: JavaSyntaxHighlighter,
    onNewFile: () -> Unit,
    onNewFolder: () -> Unit,
    onResolveDependencies: () -> Unit,
    onSave: () -> Unit,
    onFormat: () -> Unit,
    onRun: () -> Unit,
    onOpenFile: (File) -> Unit,
    onRenameEntry: (File, String) -> Unit,
    onDeleteEntry: (File) -> Unit,
    onMoveEntry: (File, File) -> Unit,
    onClearTerminal: () -> Unit,
    onEditorChanged: (String) -> Unit,
    onStatusChanged: (String, String?) -> Unit,
    onToggleTheme: () -> Unit,
    onShareFile: () -> Unit,
    onShowOnboarding: () -> Unit,
    onDismissOnboarding: () -> Unit
) {
    val context = LocalContext.current
    val themeColors = JIdeLiteColors
    val appNameShort = stringResource(R.string.app_name_short)
    val editorBridge = remember { EditorBridgeState() }
    var isExplorerCollapsed by rememberSaveable { mutableStateOf(false) }
    var isTopBarCollapsed by rememberSaveable { mutableStateOf(true) }
    var isTerminalCollapsed by rememberSaveable { mutableStateOf(false) }
    var explorerPaneWidthValue by rememberSaveable { mutableFloatStateOf(DEFAULT_EXPLORER_WIDTH) }
    var terminalPaneHeightValue by rememberSaveable { mutableFloatStateOf(DEFAULT_TERMINAL_HEIGHT) }
    var editorFontSizeSp by rememberSaveable { mutableFloatStateOf(13f) }
    var isFindReplaceVisible by rememberSaveable { mutableStateOf(false) }
    var findQuery by rememberSaveable { mutableStateOf("") }
    var replaceQuery by rememberSaveable { mutableStateOf("") }
    var activeFindMatchIndex by rememberSaveable { mutableStateOf(0) }
    var isShortcutCheatsheetVisible by rememberSaveable { mutableStateOf(false) }
    val canShareSelectedFile = uiState.selectedFileName?.endsWith(".java", ignoreCase = true) == true

    DisposableEffect(Unit) {
        onDispose {
            editorBridge.clear()
        }
    }

    SideEffect {
        syntaxHighlighter.updateColors(
            themeColors.syntaxKeyword.toArgb(),
            themeColors.syntaxString.toArgb(),
            themeColors.syntaxComment.toArgb(),
            themeColors.syntaxAnnotation.toArgb(),
            themeColors.syntaxNumber.toArgb()
        )
    }

    val showToast: (String) -> Unit = remember(context) {
        { message -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    }
    val editorActions = remember(
        context,
        appNameShort,
        onStatusChanged,
        showToast,
        onNewFile,
        onNewFolder,
        onResolveDependencies,
        onSave,
        onFormat,
        onRun,
        uiState.selectedFileName,
        uiState.isBusy,
        uiState.isMavenProject
    ) {
        EditorActionContext(
            context = context,
            selectedFileName = uiState.selectedFileName,
            appNameShort = appNameShort,
            onStatusChanged = onStatusChanged,
            showToast = showToast,
            isBusy = uiState.isBusy,
            isMavenProject = uiState.isMavenProject,
            onNewFile = onNewFile,
            onNewFolder = onNewFolder,
            onResolveDependencies = onResolveDependencies,
            onSave = onSave,
            onFormat = onFormat,
            onRun = onRun
        )
    }

    val currentEditorActions by rememberUpdatedState(editorActions)
    val currentEditorChanged by rememberUpdatedState(onEditorChanged)
    val findMatches = remember(uiState.editorText, findQuery, isFindReplaceVisible) {
        if (!isFindReplaceVisible) {
            emptyList()
        } else {
            EditorSearchEngine.findMatches(uiState.editorText, findQuery)
        }
    }

    LaunchedEffect(findMatches.size, findQuery, isFindReplaceVisible, uiState.selectedFilePath) {
        activeFindMatchIndex = when {
            !isFindReplaceVisible || findQuery.isBlank() || findMatches.isEmpty() -> 0
            else -> activeFindMatchIndex.coerceIn(0, findMatches.lastIndex)
        }
    }

    LaunchedEffect(isFindReplaceVisible, findQuery, findMatches, activeFindMatchIndex, uiState.editorText) {
        editorBridge.input?.let { input ->
            if (isFindReplaceVisible && findQuery.isNotBlank() && findMatches.isNotEmpty()) {
                input.showSearchMatches(findMatches, activeFindMatchIndex.coerceIn(0, findMatches.lastIndex))
            } else {
                input.clearSearchHighlights()
            }
        }
    }

    val openFindReplace: () -> Unit = {
        val selectedText = EditorInteractionHelper.selectedText(
            editorBridge.input?.text?.toString(),
            editorBridge.input?.selectionStart ?: 0,
            editorBridge.input?.selectionEnd ?: 0
        )
        if (selectedText.isNotBlank()) {
            findQuery = selectedText
        }
        isFindReplaceVisible = true
        activeFindMatchIndex = 0
    }
    val jumpToFindMatch: (Int) -> Unit = { index ->
        val editText = editorBridge.input
        val match = findMatches.getOrNull(index)
        if (editText != null && match != null) {
            editText.revealRange(match.start, match.endExclusive)
        }
    }
    val goToPreviousMatch: () -> Unit = {
        if (findMatches.isEmpty()) {
            showToast("No matches")
        } else {
            activeFindMatchIndex = if (activeFindMatchIndex <= 0) {
                findMatches.lastIndex
            } else {
                activeFindMatchIndex - 1
            }
            jumpToFindMatch(activeFindMatchIndex)
        }
    }
    val goToNextMatch: () -> Unit = {
        if (findMatches.isEmpty()) {
            showToast("No matches")
        } else {
            activeFindMatchIndex = if (activeFindMatchIndex >= findMatches.lastIndex) {
                0
            } else {
                activeFindMatchIndex + 1
            }
            jumpToFindMatch(activeFindMatchIndex)
        }
    }
    val replaceCurrentMatch: () -> Unit = {
        val editText = activeEditor(editorBridge, currentEditorActions)
        val match = findMatches.getOrNull(activeFindMatchIndex)
        if (editText == null) {
            Unit
        } else if (match == null) {
            currentEditorActions.showToast("No matches")
        } else {
            val editable = editText.text
            if (editable != null) {
                editable.replace(match.start, match.endExclusive, replaceQuery)
                val cursor = (match.start + replaceQuery.length).coerceIn(0, editable.length)
                editText.revealRange(cursor, cursor)
                currentEditorActions.onStatusChanged("Replaced match", null)
            }
        }
    }
    val replaceAllMatches: () -> Unit = {
        val editText = activeEditor(editorBridge, currentEditorActions)
        if (editText == null) {
            Unit
        } else if (findMatches.isEmpty()) {
            currentEditorActions.showToast("No matches")
        } else {
            val editable = editText.text
            if (editable != null) {
                val replacedText = EditorSearchEngine.replaceAll(
                    text = editable.toString(),
                    matches = findMatches,
                    replacement = replaceQuery
                )
                editable.replace(0, editable.length, replacedText)
                val cursor = (findMatches.first().start + replaceQuery.length).coerceIn(0, replacedText.length)
                editText.revealRange(cursor, cursor)
                currentEditorActions.onStatusChanged("Replaced ${findMatches.size} matches", null)
            }
        }
    }
    val decreaseFontSize: () -> Unit = {
        val nextSize = (editorFontSizeSp - 1f).coerceAtLeast(12f)
        editorFontSizeSp = nextSize
        editorBridge.input?.setEditorFontSizeSp(nextSize)
        currentEditorActions.onStatusChanged("Font ${nextSize.roundToInt()}sp", null)
    }
    val increaseFontSize: () -> Unit = {
        val nextSize = (editorFontSizeSp + 1f).coerceAtMost(28f)
        editorFontSizeSp = nextSize
        editorBridge.input?.setEditorFontSizeSp(nextSize)
        currentEditorActions.onStatusChanged("Font ${nextSize.roundToInt()}sp", null)
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(
                collapsed = isTopBarCollapsed,
                statusText = uiState.statusText,
                selectedFileName = uiState.selectedFileName,
                fileCount = uiState.files.count { it.isFile },
                isBusy = uiState.isBusy,
                canResolveDependencies = uiState.isMavenProject,
                isDarkTheme = uiState.themeMode.isDarkTheme,
                isShortcutCheatsheetVisible = isShortcutCheatsheetVisible,
                canShareSelectedFile = canShareSelectedFile,
                onResolveDependencies = onResolveDependencies,
                onSave = onSave,
                onFormat = onFormat,
                onRun = onRun,
                onToggleCollapse = { isTopBarCollapsed = !isTopBarCollapsed },
                onToggleTheme = onToggleTheme,
                onShowShortcuts = { isShortcutCheatsheetVisible = true },
                onShareFile = onShareFile
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
            ) {
                val density = LocalDensity.current
                val availableWidth = this@BoxWithConstraints.maxWidth
                val availableHeight = this@BoxWithConstraints.maxHeight
                val maxExplorerWidthCandidate = availableWidth - MinEditorWidth - ExplorerHandleWidth
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

                val maxTerminalHeightCandidate = availableHeight - MinEditorHeight - TerminalHandleHeight
                val maxTerminalHeight = if (maxTerminalHeightCandidate > MinTerminalHeight) {
                    maxTerminalHeightCandidate
                } else {
                    MinTerminalHeight
                }
                val effectiveMinTerminalHeight = minOf(MinTerminalHeight, maxTerminalHeight)
                val expandedTerminalHeight = terminalPaneHeightValue.dp.coerceIn(
                    effectiveMinTerminalHeight,
                    maxTerminalHeight
                )
                val effectiveTerminalHeight = if (isTerminalCollapsed) {
                    CollapsedTerminalHeight
                } else {
                    expandedTerminalHeight
                }

                Row(modifier = Modifier.fillMaxSize()) {
                    ExplorerPane(
                        modifier = Modifier
                            .width(effectiveExplorerWidth)
                            .fillMaxHeight(),
                        collapsed = isExplorerCollapsed,
                        workspacePath = uiState.workspacePath,
                        files = uiState.files,
                        selectedEntryPath = uiState.selectedEntryPath,
                        isBusy = uiState.isBusy,
                        onNewFile = onNewFile,
                        onNewFolder = onNewFolder,
                        onToggleCollapse = { isExplorerCollapsed = !isExplorerCollapsed },
                        onOpenFile = onOpenFile,
                        onRenameEntry = onRenameEntry,
                        onDeleteEntry = onDeleteEntry,
                        onMoveEntry = onMoveEntry
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
                            selectedFilePath = uiState.selectedFilePath,
                            editorText = uiState.editorText,
                            editorDiagnostic = uiState.editorDiagnostic,
                            editorBridge = editorBridge,
                            syntaxHighlighter = syntaxHighlighter,
                            editorFontSizeSp = editorFontSizeSp,
                            isFindReplaceVisible = isFindReplaceVisible,
                            findQuery = findQuery,
                            replaceQuery = replaceQuery,
                            findMatches = findMatches,
                            activeFindMatchIndex = activeFindMatchIndex,
                            onSelectAll = {
                                selectAllInEditor(editorBridge, currentEditorActions)
                            },
                            onCopy = {
                                copySelectionFromEditor(editorBridge, currentEditorActions)
                            },
                            onPaste = {
                                pasteIntoEditor(editorBridge, currentEditorActions)
                            },
                            onOpenFind = openFindReplace,
                            onCloseFind = { isFindReplaceVisible = false },
                            onFindQueryChanged = { findQuery = it },
                            onReplaceQueryChanged = { replaceQuery = it },
                            onFindPrevious = goToPreviousMatch,
                            onFindNext = goToNextMatch,
                            onReplaceCurrent = replaceCurrentMatch,
                            onReplaceAll = replaceAllMatches,
                            onDecreaseFont = decreaseFontSize,
                            onIncreaseFont = increaseFontSize,
                            onFontSizeChanged = { newSize ->
                                if (abs(editorFontSizeSp - newSize) >= 0.05f) {
                                    editorFontSizeSp = newSize
                                }
                            },
                            onEditorChanged = currentEditorChanged,
                            onHandleShortcut = { editText, keyCode, event ->
                                handleEditorShortcut(
                                    editText = editText,
                                    keyCode = keyCode,
                                    event = event,
                                    editorBridge = editorBridge,
                                    actionContext = currentEditorActions,
                                    onUndo = {
                                        undoInEditor(editorBridge, currentEditorActions, syntaxHighlighter)
                                    },
                                    onRedo = {
                                        redoInEditor(editorBridge, currentEditorActions, syntaxHighlighter)
                                    },
                                    onFind = openFindReplace,
                                    onShowShortcuts = { isShortcutCheatsheetVisible = true },
                                    onShowOnboarding = onShowOnboarding
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
                            collapsed = isTerminalCollapsed,
                            terminalText = uiState.terminalText,
                            workspacePath = uiState.workspacePath,
                            onClearTerminal = onClearTerminal,
                            onToggleCollapse = { isTerminalCollapsed = !isTerminalCollapsed }
                        )
                    }
                }
            }
        }

        if (isShortcutCheatsheetVisible) {
            ShortcutCheatsheetDialog(
                onDismiss = { isShortcutCheatsheetVisible = false }
            )
        }

        if (uiState.isOnboardingVisible) {
            OnboardingDialog(
                onDismiss = onDismissOnboarding
            )
        }
    }
}

@Composable
private fun TopBar(
    collapsed: Boolean,
    statusText: String,
    selectedFileName: String?,
    fileCount: Int,
    isBusy: Boolean,
    canResolveDependencies: Boolean,
    isDarkTheme: Boolean,
    isShortcutCheatsheetVisible: Boolean,
    canShareSelectedFile: Boolean,
    onResolveDependencies: () -> Unit,
    onSave: () -> Unit,
    onFormat: () -> Unit,
    onRun: () -> Unit,
    onToggleCollapse: () -> Unit,
    onToggleTheme: () -> Unit,
    onShowShortcuts: () -> Unit,
    onShareFile: () -> Unit
) {
    val themeColors = JIdeLiteColors
    val selectedFileLabel = selectedFileName ?: stringResource(R.string.editor_empty_title)
    val fileCountLabel = rememberFileCountLabel(fileCount)
    val actionRowScrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .background(themeColors.topBarSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (collapsed) 44.dp else 48.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(MaterialTheme.colorScheme.primary)
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

            ChromeActionButton(
                label = if (collapsed) "🔽" else "🔼",
                enabled = true,
                highlighted = false,
                compact = true,
                testTag = "topbar-toggle",
                onClick = onToggleCollapse
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = stringResource(R.string.app_version),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(actionRowScrollState),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChromeActionButton(
                    label = stringResource(R.string.action_shortcuts).uppercase(),
                    enabled = true,
                    highlighted = isShortcutCheatsheetVisible,
                    compact = true,
                    testTag = "topbar-shortcuts",
                    onClick = onShowShortcuts
                )

                ChromeActionButton(
                    label = stringResource(R.string.action_share).uppercase(),
                    enabled = canShareSelectedFile,
                    highlighted = false,
                    compact = true,
                    testTag = "topbar-share",
                    onClick = onShareFile
                )

                ChromeActionButton(
                    label = if (isDarkTheme) {
                        stringResource(R.string.action_theme_light).uppercase()
                    } else {
                        stringResource(R.string.action_theme_dark).uppercase()
                    },
                    enabled = true,
                    highlighted = false,
                    compact = true,
                    testTag = "topbar-theme",
                    onClick = onToggleTheme
                )

                ChromeActionButton(
                    label = stringResource(R.string.action_resolve_short).uppercase(),
                    enabled = !isBusy && canResolveDependencies,
                    highlighted = false,
                    compact = true,
                    testTag = "topbar-resolve",
                    onClick = onResolveDependencies
                )

                ChromeActionButton(
                    label = stringResource(R.string.action_save).uppercase(),
                    enabled = !isBusy,
                    highlighted = false,
                    compact = true,
                    testTag = "topbar-save",
                    onClick = onSave
                )

                ChromeActionButton(
                    label = stringResource(R.string.action_format_short).uppercase(),
                    enabled = !isBusy,
                    highlighted = false,
                    compact = true,
                    testTag = "topbar-format",
                    onClick = onFormat
                )

                ChromeActionButton(
                    label = stringResource(R.string.action_run).uppercase(),
                    enabled = !isBusy,
                    highlighted = true,
                    compact = true,
                    testTag = "topbar-run",
                    onClick = onRun
                )
            }
        }

        if (!collapsed) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
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

                Spacer(modifier = Modifier.width(10.dp))

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
    testTag: String? = null,
    onClick: () -> Unit
) {
    val themeColors = JIdeLiteColors
    val shape = RoundedCornerShape(7.dp)
    val containerColor = if (highlighted) {
        themeColors.runButtonColor
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val textColor = if (highlighted) {
        themeColors.runButtonText
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.55f)
            .clip(shape)
            .background(containerColor)
            .then(if (testTag == null) Modifier else Modifier.testTag(testTag))
            .then(
                if (highlighted) {
                    Modifier
                } else {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                }
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(if (compact) ChromeCompactButtonPadding else ChromeRegularButtonPadding),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (highlighted) "\u25B6 $label" else label,
            color = textColor,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            fontSize = if (compact) 10.sp else 13.sp,
            letterSpacing = if (compact) 0.2.sp else 0.45.sp
        )
    }
}

@Composable
private fun ChromeInlineTextButton(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        contentPadding = ChromeInlineButtonPadding,
        colors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun CompactOutlinedTextField(
    modifier: Modifier = Modifier,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    fieldHeight: androidx.compose.ui.unit.Dp = ChromeCompactFieldHeight,
    textFontSize: TextUnit = 13.sp,
    placeholderFontSize: TextUnit = 12.sp,
    containerColor: Color = MaterialTheme.colorScheme.surface
) {
    OutlinedTextField(
        modifier = modifier.height(fieldHeight),
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        shape = RoundedCornerShape(10.dp),
        textStyle = TextStyle(
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = FontFamily.Monospace,
            fontSize = textFontSize
        ),
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedContainerColor = containerColor,
            unfocusedContainerColor = containerColor,
            disabledContainerColor = containerColor
        ),
        placeholder = {
            Text(
                text = placeholder,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                fontSize = placeholderFontSize
            )
        }
    )
}

@Composable
private fun ExplorerPane(
    modifier: Modifier,
    collapsed: Boolean,
    workspacePath: String,
    files: List<File>,
    selectedEntryPath: String?,
    isBusy: Boolean,
    onNewFile: () -> Unit,
    onNewFolder: () -> Unit,
    onToggleCollapse: () -> Unit,
    onOpenFile: (File) -> Unit,
    onRenameEntry: (File, String) -> Unit,
    onDeleteEntry: (File) -> Unit,
    onMoveEntry: (File, File) -> Unit
) {
    val themeColors = JIdeLiteColors
    var showNewMenu by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<File?>(null) }
    var renameValue by remember { mutableStateOf("") }
    val treeRoots = remember(workspacePath, files) { buildExplorerTree(workspacePath, files) }
    val directoryPaths = remember(treeRoots) { collectDirectoryPaths(treeRoots) }
    var hasInitializedExpandedState by remember(workspacePath) { mutableStateOf(false) }
    var expandedDirectoryPaths by remember(workspacePath) { mutableStateOf(emptySet<String>()) }

    LaunchedEffect(directoryPaths) {
        expandedDirectoryPaths = expandedDirectoryPaths.intersect(directoryPaths)
        if (!hasInitializedExpandedState) {
            expandedDirectoryPaths = directoryPaths
            hasInitializedExpandedState = true
        }
    }

    val visibleNodes = remember(treeRoots, expandedDirectoryPaths) {
        flattenExplorerTree(treeRoots, expandedDirectoryPaths)
    }
    val nodeByPath = remember(visibleNodes) { visibleNodes.associate { it.node.path to it.node } }
    val rowBounds = remember { mutableStateMapOf<String, Rect>() }
    var draggingEntryPath by remember { mutableStateOf<String?>(null) }
    var dropTargetDirectoryPath by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(visibleNodes) {
        val visiblePaths = visibleNodes.mapTo(linkedSetOf()) { it.node.path }
        val stalePaths = rowBounds.keys.filterNot { visiblePaths.contains(it) }
        for (path in stalePaths) {
            rowBounds.remove(path)
        }
        if (draggingEntryPath != null && !visiblePaths.contains(draggingEntryPath)) {
            draggingEntryPath = null
            dropTargetDirectoryPath = null
        } else if (dropTargetDirectoryPath != null && !visiblePaths.contains(dropTargetDirectoryPath)) {
            dropTargetDirectoryPath = null
        }
    }

    Box(
        modifier = modifier.background(themeColors.explorerSurface)
    ) {
        if (collapsed) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ChromeActionButton(
                    label = "▶",
                    enabled = true,
                    highlighted = false,
                    compact = true,
                    onClick = onToggleCollapse
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "EX",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    letterSpacing = 1.2.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Box {
                    ChromeActionButton(
                        label = "+",
                        enabled = !isBusy,
                        highlighted = false,
                        compact = true,
                        testTag = "explorer-new",
                        onClick = { showNewMenu = true }
                    )
                    DropdownMenu(
                        expanded = showNewMenu,
                        onDismissRequest = { showNewMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(text = "Folder") },
                            onClick = {
                                showNewMenu = false
                                onNewFolder()
                            },
                            enabled = !isBusy
                        )
                        DropdownMenuItem(
                            text = { Text(text = "File") },
                            onClick = {
                                showNewMenu = false
                                onNewFile()
                            },
                            enabled = !isBusy
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                if (files.isEmpty()) {
                    Spacer(modifier = Modifier.weight(1f))
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        items(files, key = { workspaceRelativePath(workspacePath, it) }) { file ->
                            CollapsedExplorerFilePill(
                                label = if (file.isDirectory) "DIR" else compactFileLabel(file.name),
                                selected = file.absolutePath == selectedEntryPath,
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
                        .padding(horizontal = 12.dp, vertical = 10.dp),
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

                    Box {
                        ChromeActionButton(
                            label = "➕",
                            enabled = !isBusy,
                            highlighted = false,
                            compact = true,
                            testTag = "explorer-new",
                            onClick = { showNewMenu = true }
                        )
                        DropdownMenu(
                            expanded = showNewMenu,
                            onDismissRequest = { showNewMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(text = "Folder") },
                                onClick = {
                                    showNewMenu = false
                                    onNewFolder()
                                },
                                enabled = !isBusy
                            )
                            DropdownMenuItem(
                                text = { Text(text = "File") },
                                onClick = {
                                    showNewMenu = false
                                    onNewFile()
                                },
                                enabled = !isBusy
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    ChromeActionButton(
                        label = "◀",
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
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
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
                            .padding(top = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        items(visibleNodes, key = { it.node.path }) { visibleNode ->
                            ExplorerTreeRow(
                                node = visibleNode.node,
                                depth = visibleNode.depth,
                                selected = visibleNode.node.file.absolutePath == selectedEntryPath,
                                expanded = visibleNode.node.isDirectory &&
                                        expandedDirectoryPaths.contains(visibleNode.node.path),
                                isDragging = draggingEntryPath == visibleNode.node.path,
                                isDropTarget = dropTargetDirectoryPath == visibleNode.node.path,
                                isBusy = isBusy,
                                onToggleExpand = { path ->
                                    expandedDirectoryPaths = if (expandedDirectoryPaths.contains(path)) {
                                        expandedDirectoryPaths - path
                                    } else {
                                        expandedDirectoryPaths + path
                                    }
                                },
                                onSelect = onOpenFile,
                                onRename = { file ->
                                    renameTarget = file
                                    renameValue = file.name
                                },
                                onDelete = onDeleteEntry,
                                onPositioned = { path, bounds ->
                                    rowBounds[path] = bounds
                                },
                                onDragStart = { path ->
                                    draggingEntryPath = path
                                    dropTargetDirectoryPath = null
                                },
                                onDragMove = { path, pointerInWindow ->
                                    if (draggingEntryPath == path) {
                                        val hoveredPath = findHoveredDirectoryPath(
                                            pointerInWindow = pointerInWindow,
                                            rowBounds = rowBounds,
                                            nodeByPath = nodeByPath,
                                            draggedPath = path
                                        )
                                        dropTargetDirectoryPath = hoveredPath
                                    }
                                },
                                onDragEnd = { path ->
                                    val draggedPath = draggingEntryPath
                                    val destinationPath = dropTargetDirectoryPath
                                    val sourceNode = draggedPath?.let { nodeByPath[it] }
                                    val destinationNode = destinationPath?.let { nodeByPath[it] }
                                    if (draggedPath == path &&
                                        sourceNode != null &&
                                        destinationNode != null &&
                                        destinationNode.isDirectory &&
                                        destinationNode.path != sourceNode.path
                                    ) {
                                        onMoveEntry(sourceNode.file, destinationNode.file)
                                    }
                                    draggingEntryPath = null
                                    dropTargetDirectoryPath = null
                                },
                                onDragCancel = { path ->
                                    if (draggingEntryPath == path) {
                                        draggingEntryPath = null
                                        dropTargetDirectoryPath = null
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        val activeRenameTarget = renameTarget
        if (activeRenameTarget != null) {
            RenameEntryDialog(
                value = renameValue,
                onValueChange = { renameValue = it },
                onDismiss = { renameTarget = null },
                onConfirm = {
                    val requestedName = renameValue.trim()
                    if (requestedName.isNotEmpty()) {
                        onRenameEntry(activeRenameTarget, requestedName)
                    }
                    renameTarget = null
                },
                isBusy = isBusy
            )
        }
    }
}

@Composable
private fun CollapsedExplorerFilePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val themeColors = JIdeLiteColors
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (selected) themeColors.selectedSurface else Color.Transparent)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(7.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun ExplorerTreeRow(
    node: ExplorerNode,
    depth: Int,
    selected: Boolean,
    expanded: Boolean,
    isDragging: Boolean,
    isDropTarget: Boolean,
    isBusy: Boolean,
    onToggleExpand: (String) -> Unit,
    onSelect: (File) -> Unit,
    onRename: (File) -> Unit,
    onDelete: (File) -> Unit,
    onPositioned: (String, Rect) -> Unit,
    onDragStart: (String) -> Unit,
    onDragMove: (String, Offset) -> Unit,
    onDragEnd: (String) -> Unit,
    onDragCancel: (String) -> Unit
) {
    val themeColors = JIdeLiteColors
    var rowBounds by remember(node.path) { mutableStateOf<Rect?>(null) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                when {
                    selected -> themeColors.selectedSurface
                    isDropTarget -> MaterialTheme.colorScheme.surfaceContainer
                    else -> Color.Transparent
                }
            )
            .alpha(if (isDragging) 0.55f else 1f)
            .onGloballyPositioned { coordinates ->
                val origin = coordinates.positionInWindow()
                val bounds = Rect(
                    left = origin.x,
                    top = origin.y,
                    right = origin.x + coordinates.size.width,
                    bottom = origin.y + coordinates.size.height
                )
                rowBounds = bounds
                onPositioned(node.path, bounds)
            }
            .pointerInput(node.path, isBusy, node.isDirectory) {
                if (isBusy || node.isDirectory) {
                    return@pointerInput
                }
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        onDragStart(node.path)
                        val bounds = rowBounds
                        if (bounds != null) {
                            onDragMove(
                                node.path,
                                Offset(
                                    x = bounds.left + it.x,
                                    y = bounds.top + it.y
                                )
                            )
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val bounds = rowBounds
                        if (bounds != null) {
                            onDragMove(
                                node.path,
                                Offset(
                                    x = bounds.left + change.position.x,
                                    y = bounds.top + change.position.y
                                )
                            )
                        }
                    },
                    onDragEnd = {
                        onDragEnd(node.path)
                    },
                    onDragCancel = {
                        onDragCancel(node.path)
                    }
                )
            }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable { onSelect(node.file) }
                .padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width((depth * 12).dp))

            Text(
                text = when {
                    !node.isDirectory -> " "
                    expanded -> "\u25BE"
                    else -> "\u25B8"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier
                    .width(12.dp)
                    .clickable(enabled = node.isDirectory) {
                        if (node.isDirectory) {
                            onToggleExpand(node.path)
                        }
                    }
            )

            Spacer(modifier = Modifier.width(6.dp))

            Box(
                modifier = Modifier
                    .width(7.dp)
                    .height(14.dp)
                    .background(
                        when {
                            selected -> MaterialTheme.colorScheme.primary
                            node.isDirectory -> MaterialTheme.colorScheme.secondary
                            else -> themeColors.dotMint
                        }
                    )
            )

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = if (node.isDirectory) "${node.name}/" else node.name,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = "\u270E",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier
                .alpha(if (isBusy) 0.45f else 1f)
                .clickable(enabled = !isBusy) { onRename(node.file) }
                .padding(horizontal = 4.dp, vertical = 2.dp)
        )

        Text(
            text = "\u2715",
            color = themeColors.dotPink,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier
                .alpha(if (isBusy) 0.45f else 1f)
                .clickable(enabled = !isBusy) { onDelete(node.file) }
                .padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun RenameEntryDialog(
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    isBusy: Boolean
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Rename",
                fontFamily = FontFamily.Monospace
            )
        },
        text = {
            CompactOutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = "Name"
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isBusy && value.trim().isNotEmpty()
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isBusy
            ) {
                Text("Cancel")
            }
        }
    )
}

private data class ExplorerNode(
    val path: String,
    val name: String,
    val file: File,
    val isDirectory: Boolean,
    val children: MutableList<ExplorerNode> = mutableListOf()
)

private data class VisibleExplorerNode(
    val node: ExplorerNode,
    val depth: Int
)

private fun buildExplorerTree(workspacePath: String, files: List<File>): List<ExplorerNode> {
    val sortedFiles = files.sortedWith(
        compareBy<File>({ !it.isDirectory }, { workspaceRelativePath(workspacePath, it).lowercase() })
    )
    val nodeByPath = LinkedHashMap<String, ExplorerNode>()
    for (file in sortedFiles) {
        val relativePath = workspaceRelativePath(workspacePath, file)
        if (relativePath.isBlank()) {
            continue
        }
        nodeByPath[relativePath] = ExplorerNode(
            path = relativePath,
            name = relativePath.substringAfterLast('/'),
            file = file,
            isDirectory = file.isDirectory
        )
    }

    val roots = mutableListOf<ExplorerNode>()
    for ((path, node) in nodeByPath) {
        val parentPath = path.substringBeforeLast('/', "")
        val parentNode = nodeByPath[parentPath]
        if (parentNode != null && parentNode.isDirectory) {
            parentNode.children.add(node)
        } else {
            roots.add(node)
        }
    }

    sortExplorerNodes(roots)
    return roots
}

private fun sortExplorerNodes(nodes: MutableList<ExplorerNode>) {
    nodes.sortWith(compareBy<ExplorerNode>({ !it.isDirectory }, { it.name.lowercase() }))
    for (node in nodes) {
        if (node.children.isNotEmpty()) {
            sortExplorerNodes(node.children)
        }
    }
}

private fun collectDirectoryPaths(nodes: List<ExplorerNode>): Set<String> {
    val directoryPaths = linkedSetOf<String>()

    fun visit(node: ExplorerNode) {
        if (node.isDirectory) {
            directoryPaths.add(node.path)
        }
        for (child in node.children) {
            visit(child)
        }
    }

    for (node in nodes) {
        visit(node)
    }
    return directoryPaths
}

private fun flattenExplorerTree(
    roots: List<ExplorerNode>,
    expandedDirectoryPaths: Set<String>
): List<VisibleExplorerNode> {
    val output = mutableListOf<VisibleExplorerNode>()
    for (root in roots) {
        appendVisibleNode(output, root, 0, expandedDirectoryPaths)
    }
    return output
}

private fun appendVisibleNode(
    output: MutableList<VisibleExplorerNode>,
    node: ExplorerNode,
    depth: Int,
    expandedDirectoryPaths: Set<String>
) {
    output.add(VisibleExplorerNode(node = node, depth = depth))
    if (node.isDirectory && expandedDirectoryPaths.contains(node.path)) {
        for (child in node.children) {
            appendVisibleNode(output, child, depth + 1, expandedDirectoryPaths)
        }
    }
}

private fun findHoveredDirectoryPath(
    pointerInWindow: Offset,
    rowBounds: Map<String, Rect>,
    nodeByPath: Map<String, ExplorerNode>,
    draggedPath: String
): String? {
    for ((path, bounds) in rowBounds) {
        val node = nodeByPath[path] ?: continue
        if (!node.isDirectory) {
            continue
        }
        if (path == draggedPath || path.startsWith("$draggedPath/")) {
            continue
        }
        if (bounds.contains(pointerInWindow)) {
            return path
        }
    }
    return null
}

private fun workspaceRelativePath(workspacePath: String, file: File): String {
    if (workspacePath.isBlank()) {
        return file.name
    }

    return try {
        file.relativeTo(File(workspacePath)).invariantSeparatorsPath
    } catch (_: IllegalArgumentException) {
        file.name
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
            .background(MaterialTheme.colorScheme.surfaceContainer)
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
                .padding(vertical = 8.dp)
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
            .background(MaterialTheme.colorScheme.surfaceContainer)
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
                .padding(horizontal = 22.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
    }
}

@Composable
private fun EditorPane(
    modifier: Modifier,
    fileName: String,
    isDirty: Boolean,
    selectedFilePath: String?,
    editorText: String,
    editorDiagnostic: EditorDiagnostic?,
    editorBridge: EditorBridgeState,
    syntaxHighlighter: JavaSyntaxHighlighter,
    editorFontSizeSp: Float,
    isFindReplaceVisible: Boolean,
    findQuery: String,
    replaceQuery: String,
    findMatches: List<EditorSearchMatch>,
    activeFindMatchIndex: Int,
    onSelectAll: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onOpenFind: () -> Unit,
    onCloseFind: () -> Unit,
    onFindQueryChanged: (String) -> Unit,
    onReplaceQueryChanged: (String) -> Unit,
    onFindPrevious: () -> Unit,
    onFindNext: () -> Unit,
    onReplaceCurrent: () -> Unit,
    onReplaceAll: () -> Unit,
    onDecreaseFont: () -> Unit,
    onIncreaseFont: () -> Unit,
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
                                .size(8.dp)
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

                ChromeActionButton(
                    label = "FIND",
                    enabled = true,
                    highlighted = isFindReplaceVisible,
                    compact = true,
                    onClick = onOpenFind
                )

                ChromeActionButton(
                    label = "A-",
                    enabled = true,
                    highlighted = false,
                    compact = true,
                    onClick = onDecreaseFont
                )

                ChromeActionButton(
                    label = "A+",
                    enabled = true,
                    highlighted = false,
                    compact = true,
                    onClick = onIncreaseFont
                )

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
                    val density = context.resources.displayMetrics.density
                    val horizontalPadding = (14f * density).toInt()
                    val verticalPadding = (14f * density).toInt()

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
                    this.onFontSizeChanged = { currentFontSizeChanged(it) }
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
                        loadDocument(editorText)
                        syntaxHighlighter.highlightNow(this)
                    }
                },
            update = { editText ->
                editorBridge.input = editText
                editText.onFontSizeChanged = { currentFontSizeChanged(it) }
                editText.onGutterWidthChanged = { gutterHeaderWidthPx = it }
                editText.onContentStartChanged = { editorContentStartPx = it }
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
                        .padding(top = 10.dp, end = 12.dp)
                        .fillMaxWidth(0.74f)
                        .widthIn(min = ChromeFindWidgetMinWidth, max = ChromeFindWidgetMaxWidth),
                    query = findQuery,
                    replacement = replaceQuery,
                    matchCount = findMatches.size,
                    activeMatchIndex = activeFindMatchIndex,
                    onQueryChanged = onFindQueryChanged,
                    onReplacementChanged = onReplaceQueryChanged,
                    onPrevious = onFindPrevious,
                    onNext = onFindNext,
                    onReplace = onReplaceCurrent,
                    onReplaceAll = onReplaceAll,
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
    onQueryChanged: (String) -> Unit,
    onReplacementChanged: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onReplace: () -> Unit,
    onReplaceAll: () -> Unit,
    onClose: () -> Unit
) {
    val themeColors = JIdeLiteColors
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(themeColors.topBarSurface.copy(alpha = 0.98f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompactOutlinedTextField(
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                value = query,
                onValueChange = onQueryChanged,
                placeholder = stringResource(R.string.find_label),
                fieldHeight = ChromeFindWidgetFieldHeight,
                textFontSize = 12.sp,
                placeholderFontSize = 11.sp,
                containerColor = themeColors.editorSurface.copy(alpha = 0.96f)
            )

            MatchCountPill(matchCount = matchCount, activeMatchIndex = activeMatchIndex)

            ChromeInlineTextButton(label = stringResource(R.string.find_prev), onClick = onPrevious)

            ChromeInlineTextButton(label = stringResource(R.string.find_next), onClick = onNext)

            ChromeInlineTextButton(label = "X", onClick = onClose)
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompactOutlinedTextField(
                modifier = Modifier.weight(1f),
                value = replacement,
                onValueChange = onReplacementChanged,
                placeholder = stringResource(R.string.replace_label),
                fieldHeight = ChromeFindWidgetFieldHeight,
                textFontSize = 12.sp,
                placeholderFontSize = 11.sp,
                containerColor = themeColors.editorSurface.copy(alpha = 0.96f)
            )

            ChromeInlineTextButton(label = stringResource(R.string.replace_action), onClick = onReplace)

            ChromeInlineTextButton(label = stringResource(R.string.replace_all_action), onClick = onReplaceAll)
        }
    }
}

@Composable
private fun MatchCountPill(
    matchCount: Int,
    activeMatchIndex: Int
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (matchCount == 0) "0/0" else "${activeMatchIndex + 1}/$matchCount",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp
        )
    }
}

private data class ShortcutBinding(
    val keys: String,
    val action: String
)

private val EditorShortcutBindings = listOf(
    ShortcutBinding("Tab", "Indent current line or selection"),
    ShortcutBinding("Enter", "Insert smart newline"),
    ShortcutBinding("Ctrl+Shift+?", "Reopen onboarding guide"),
    ShortcutBinding("Ctrl+/", "Open keyboard shortcuts"),
    ShortcutBinding("Ctrl+N", "Create a new Java file"),
    ShortcutBinding("Ctrl+Shift+N", "Create a new folder"),
    ShortcutBinding("Ctrl+S", "Save current file"),
    ShortcutBinding("Ctrl+R", "Run current file"),
    ShortcutBinding("Ctrl+Shift+F", "Format current Java file"),
    ShortcutBinding("Ctrl+Shift+D", "Resolve Maven dependencies"),
    ShortcutBinding("Ctrl+F", "Open find and replace"),
    ShortcutBinding("Ctrl+A", "Select all"),
    ShortcutBinding("Ctrl+C", "Copy selection"),
    ShortcutBinding("Ctrl+X", "Cut selection"),
    ShortcutBinding("Ctrl+V", "Paste clipboard"),
    ShortcutBinding("Ctrl+Z", "Undo"),
    ShortcutBinding("Ctrl+Shift+Z / Ctrl+Y", "Redo")
)

@Composable
private fun ShortcutCheatsheetDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        modifier = Modifier.testTag("shortcuts-dialog"),
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.shortcuts_title),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.shortcuts_subtitle),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )

                EditorShortcutBindings.forEach { binding ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = binding.keys,
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            modifier = Modifier.width(124.dp)
                        )

                        Text(
                            text = binding.action,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.shortcuts_close))
            }
        }
    )
}

@Composable
private fun OnboardingDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        modifier = Modifier.testTag("onboarding-dialog"),
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.onboarding_title),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.onboarding_subtitle),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )

                OnboardingCard(
                    title = stringResource(R.string.onboarding_runner_title),
                    body = stringResource(R.string.onboarding_runner_body)
                )

                OnboardingCard(
                    title = stringResource(R.string.onboarding_maven_title),
                    body = stringResource(R.string.onboarding_maven_body)
                )

                OnboardingCard(
                    title = stringResource(R.string.onboarding_share_title),
                    body = stringResource(R.string.onboarding_share_body)
                )
            }
        },
        confirmButton = {
            TextButton(
                modifier = Modifier.testTag("onboarding-dismiss"),
                onClick = onDismiss
            ) {
                Text(text = stringResource(R.string.onboarding_cta))
            }
        }
    )
}

@Composable
private fun OnboardingCard(
    title: String,
    body: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.primary,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp
        )

        Text(
            text = body,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun TerminalPane(
    modifier: Modifier,
    collapsed: Boolean,
    terminalText: String,
    workspacePath: String,
    onClearTerminal: () -> Unit,
    onToggleCollapse: () -> Unit
) {
    val themeColors = JIdeLiteColors
    val scrollState = rememberScrollState()
    val readyText = stringResource(R.string.terminal_ready)

    LaunchedEffect(terminalText) {
        scrollState.scrollTo(scrollState.maxValue)
    }

    val terminalColor = when {
        terminalText.startsWith(readyText) -> themeColors.terminalReady
        terminalText.contains("failed", ignoreCase = true) -> themeColors.dotPink
        terminalText.contains("simulated", ignoreCase = true) -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = modifier.background(themeColors.terminalSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(themeColors.dotPink)
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(themeColors.dotSand)
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(themeColors.dotMint)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = stringResource(R.string.terminal_title).uppercase(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp,
                    letterSpacing = 1.2.sp
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.action_clear).uppercase(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                    modifier = Modifier.clickable(onClick = onClearTerminal)
                )

                ChromeActionButton(
                    label = if (!collapsed) "🔽" else "🔼",
                    enabled = true,
                    highlighted = false,
                    compact = true,
                    testTag = "terminal-toggle",
                    onClick = onToggleCollapse
                )
            }
        }

        if (!collapsed) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            SelectionContainer {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.terminal_intro),
                        color = themeColors.terminalInfo,
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
    }
}

@Composable
private fun rememberFileCountLabel(fileCount: Int): String {
    return pluralStringResource(R.plurals.file_count, fileCount, fileCount)
}

private class EditorBridgeState {
    var input: CodeEditorEditText? = null
    var documentPath: String? = null
    var lastDiagnosticRequestId: Long? = null
    var editorThemeSignature: Int? = null
    var suppressCallbacks: Boolean = false

    fun clear() {
        input = null
        documentPath = null
        lastDiagnosticRequestId = null
        editorThemeSignature = null
        suppressCallbacks = false
    }
}

private data class EditorActionContext(
    val context: Context,
    val selectedFileName: String?,
    val appNameShort: String,
    val onStatusChanged: (String, String?) -> Unit,
    val showToast: (String) -> Unit,
    val isBusy: Boolean,
    val isMavenProject: Boolean,
    val onNewFile: () -> Unit,
    val onNewFolder: () -> Unit,
    val onResolveDependencies: () -> Unit,
    val onSave: () -> Unit,
    val onFormat: () -> Unit,
    val onRun: () -> Unit
)

private fun activeEditor(
    editorBridge: EditorBridgeState,
    actionContext: EditorActionContext,
    showMissingEditorMessage: Boolean = true
): CodeEditorEditText? {
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

private fun undoInEditor(
    editorBridge: EditorBridgeState,
    actionContext: EditorActionContext,
    syntaxHighlighter: JavaSyntaxHighlighter
) {
    val editText = activeEditor(editorBridge, actionContext) ?: return
    if (!editText.undoTextChange()) {
        actionContext.showToast("Nothing to undo")
        return
    }
    syntaxHighlighter.highlightNow(editText)
    actionContext.onStatusChanged("Undo", null)
}

private fun redoInEditor(
    editorBridge: EditorBridgeState,
    actionContext: EditorActionContext,
    syntaxHighlighter: JavaSyntaxHighlighter
) {
    val editText = activeEditor(editorBridge, actionContext) ?: return
    if (!editText.redoTextChange()) {
        actionContext.showToast("Nothing to redo")
        return
    }
    syntaxHighlighter.highlightNow(editText)
    actionContext.onStatusChanged("Redo", null)
}

private fun runEditorCommandIfAllowed(
    actionContext: EditorActionContext,
    requiresMavenProject: Boolean = false,
    command: () -> Unit
) {
    if (actionContext.isBusy) {
        actionContext.showToast("Please wait for current task to finish")
        return
    }
    if (requiresMavenProject && !actionContext.isMavenProject) {
        actionContext.showToast("No pom.xml in workspace")
        return
    }
    command()
}

private fun handleEditorShortcut(
    editText: EditText,
    keyCode: Int,
    event: KeyEvent,
    editorBridge: EditorBridgeState,
    actionContext: EditorActionContext,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onFind: () -> Unit,
    onShowShortcuts: () -> Unit,
    onShowOnboarding: () -> Unit
): Boolean {
    if (event.action != KeyEvent.ACTION_DOWN) {
        return false
    }

    return when {
        keyCode == KeyEvent.KEYCODE_TAB -> {
            insertIndentUnit(editText)
            true
        }

        keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER -> {
            insertSmartNewline(editText)
            true
        }

        event.isCtrlPressed && event.isShiftPressed && keyCode == KeyEvent.KEYCODE_SLASH -> {
            onShowOnboarding()
            true
        }

        event.isCtrlPressed && keyCode == KeyEvent.KEYCODE_SLASH -> {
            onShowShortcuts()
            true
        }

        event.isCtrlPressed && event.isShiftPressed && keyCode == KeyEvent.KEYCODE_N -> {
            runEditorCommandIfAllowed(actionContext) {
                actionContext.onNewFolder()
            }
            true
        }

        event.isCtrlPressed && keyCode == KeyEvent.KEYCODE_N -> {
            runEditorCommandIfAllowed(actionContext) {
                actionContext.onNewFile()
            }
            true
        }

        event.isCtrlPressed && keyCode == KeyEvent.KEYCODE_S -> {
            runEditorCommandIfAllowed(actionContext) {
                actionContext.onSave()
            }
            true
        }

        event.isCtrlPressed && event.isShiftPressed && keyCode == KeyEvent.KEYCODE_Z -> {
            onRedo()
            true
        }

        event.isCtrlPressed && keyCode == KeyEvent.KEYCODE_Z -> {
            onUndo()
            true
        }

        event.isCtrlPressed && keyCode == KeyEvent.KEYCODE_Y -> {
            onRedo()
            true
        }

        event.isCtrlPressed && keyCode == KeyEvent.KEYCODE_R -> {
            runEditorCommandIfAllowed(actionContext) {
                actionContext.onRun()
            }
            true
        }

        event.isCtrlPressed && event.isShiftPressed && keyCode == KeyEvent.KEYCODE_F -> {
            runEditorCommandIfAllowed(actionContext) {
                actionContext.onFormat()
            }
            true
        }

        event.isCtrlPressed && event.isShiftPressed && keyCode == KeyEvent.KEYCODE_D -> {
            runEditorCommandIfAllowed(actionContext, requiresMavenProject = true) {
                actionContext.onResolveDependencies()
            }
            true
        }

        event.isCtrlPressed && keyCode == KeyEvent.KEYCODE_F -> {
            onFind()
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

private fun insertIndentUnit(editText: EditText) {
    val editable = editText.text ?: return
    val selectionStart = editText.selectionStart.coerceAtLeast(0)
    val selectionEnd = editText.selectionEnd.coerceAtLeast(0)
    val start = minOf(selectionStart, selectionEnd)
    val end = maxOf(selectionStart, selectionEnd)
    val indentWidth = EDITOR_INDENT.length

    if (start == end) {
        editable.insert(start, EDITOR_INDENT)
        editText.setSelection(start + indentWidth)
        return
    }

    val currentText = editable.toString()
    val firstLineStart = currentText.lastIndexOf('\n', start - 1).let { index ->
        if (index == -1) 0 else index + 1
    }
    val selectedBlock = currentText.substring(firstLineStart, end)
    val indentedBlock = EDITOR_INDENT + selectedBlock.replace("\n", "\n$EDITOR_INDENT")
    val insertedSpaces = indentedBlock.length - selectedBlock.length

    editable.replace(firstLineStart, end, indentedBlock)
    editText.setSelection(start + indentWidth, end + insertedSpaces)
}

private fun insertSmartNewline(editText: EditText) {
    val editable = editText.text ?: return
    val mutation = EditorInteractionHelper.insertSmartNewline(
        editable.toString(),
        editText.selectionStart,
        editText.selectionEnd,
        EDITOR_INDENT
    )
    editable.replace(0, editable.length, mutation.text)
    editText.setSelection(mutation.cursorPosition)
}
