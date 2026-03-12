package com.goriant.jidelite.ui.main

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
private val CollapsedExplorerWidth = 68.dp
private val ExplorerHandleWidth = 12.dp
private val TerminalHandleHeight = 12.dp
private val MinExpandedExplorerWidth = 156.dp
private val MinEditorWidth = 220.dp
private val MinTerminalHeight = 120.dp
private val MinEditorHeight = 180.dp
private val CollapsedTerminalHeight = 38.dp


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

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                if (inputStream != null) {
                    viewModel.onImportProjectRequested(inputStream)
                }
            } catch (e: Exception) {
                viewModel.updateStatus("Import failed", context.getString(R.string.toast_import_failed))
            }
        }
    }

    val importProject = fun() {
        importLauncher.launch("application/zip")
    }

    val exportProject = fun() {
        val file = viewModel.onExportProjectRequested() ?: return
        val fileUri = try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (_: IllegalArgumentException) {
            viewModel.updateStatus("Export failed", context.getString(R.string.toast_export_failed))
            return
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, fileUri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            clipData = ClipData.newUri(context.contentResolver, file.name, fileUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.action_export)))
            viewModel.updateStatus("Exporting project", context.getString(R.string.toast_export_opened))
        } catch (_: ActivityNotFoundException) {
            viewModel.updateStatus("Export failed", context.getString(R.string.toast_export_failed))
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
        onDismissOnboarding = viewModel::dismissOnboarding,
        onToggleWordWrap = viewModel::onToggleWordWrap,
        onAcceptImportSuggestion = viewModel::onAcceptImportSuggestion,
        onSubmitStdin = viewModel::onSubmitStdin,
        onSwitchTab = {},
        onCloseTab = {},
        onImportProject = importProject,
        onExportProject = exportProject
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
    onDismissOnboarding: () -> Unit,
    onToggleWordWrap: () -> Unit,
    onAcceptImportSuggestion: () -> Unit,
    onSubmitStdin: (String) -> Unit,
    onSwitchTab: (Int) -> Unit,
    onCloseTab: (Int) -> Unit,
    onImportProject: () -> Unit,
    onExportProject: () -> Unit
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
    var isShortcutCheatsheetVisible by rememberSaveable { mutableStateOf(false) }
    var isFindReplaceVisible by rememberSaveable { mutableStateOf(false) }
    var isReplaceExpanded by rememberSaveable { mutableStateOf(false) }
    var findQuery by rememberSaveable { mutableStateOf("") }
    var replaceQuery by rememberSaveable { mutableStateOf("") }
    var activeFindMatchIndex by rememberSaveable { mutableIntStateOf(0) }
    val canShareSelectedFile = uiState.selectedFileName?.endsWith(".java", ignoreCase = true) == true

    val findMatches = remember(uiState.editorText, findQuery, isFindReplaceVisible) {
        if (!isFindReplaceVisible || findQuery.isBlank()) {
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

    val closeFindReplace: () -> Unit = {
        isFindReplaceVisible = false
        editorBridge.input?.clearSearchHighlights()
    }

    val jumpToFindMatch: (Int) -> Unit = { index ->
        val match = findMatches.getOrNull(index)
        if (editorBridge.input != null && match != null) {
            editorBridge.input!!.revealRange(match.start, match.endExclusive)
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
        if (editText != null && match != null) {
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
        if (editText != null && findMatches.isNotEmpty()) {
            val editable = editText.text
            if (editable != null) {
                val replacedText = EditorSearchEngine.replaceAll(
                    text = editable.toString(),
                    matches = findMatches,
                    replacement = replaceQuery
                )
                editable.replace(0, editable.length, replacedText)
                val cursor = (findMatches.first().start + replaceQuery.length)
                    .coerceIn(0, replacedText.length)
                editText.revealRange(cursor, cursor)
                currentEditorActions.onStatusChanged("Replaced ${findMatches.size} matches", null)
            }
        } else {
            showToast("No matches")
        }
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
                onShareFile = onShareFile,
                onImportProject = onImportProject,
                onExportProject = onExportProject
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
                            isWordWrapEnabled = uiState.isWordWrapEnabled,
                            isFindReplaceVisible = isFindReplaceVisible,
                            isReplaceExpanded = isReplaceExpanded,
                            findQuery = findQuery,
                            replaceQuery = replaceQuery,
                            findMatches = findMatches,
                            activeFindMatchIndex = activeFindMatchIndex,
                            openTabs = uiState.openTabs,
                            activeTabIndex = uiState.activeTabIndex,
                            onSwitchTab = onSwitchTab,
                            onCloseTab = onCloseTab,
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
                            onCloseFind = closeFindReplace,
                            onToggleReplace = { isReplaceExpanded = !isReplaceExpanded },
                            onFindQueryChanged = { findQuery = it },
                            onReplaceQueryChanged = { replaceQuery = it },
                            onFindPrevious = goToPreviousMatch,
                            onFindNext = goToNextMatch,
                            onReplaceCurrent = replaceCurrentMatch,
                            onReplaceAll = replaceAllMatches,
                            onDecreaseFont = decreaseFontSize,
                            onIncreaseFont = increaseFontSize,
                            onToggleWordWrap = onToggleWordWrap,
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
                                    onShowShortcuts = { isShortcutCheatsheetVisible = true },
                                    onShowOnboarding = onShowOnboarding,
                                    onOpenFind = openFindReplace,
                                    onOpenFindReplace = {
                                        openFindReplace()
                                        isReplaceExpanded = true
                                    }
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
                            isRunning = uiState.isRunning,
                            pendingImportSuggestion = uiState.pendingImportSuggestion,
                            onClearTerminal = onClearTerminal,
                            onToggleCollapse = { isTerminalCollapsed = !isTerminalCollapsed },
                            onSubmitStdin = onSubmitStdin,
                            onAcceptImportSuggestion = onAcceptImportSuggestion
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
