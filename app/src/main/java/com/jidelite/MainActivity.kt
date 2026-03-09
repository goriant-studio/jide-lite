package com.jidelite

import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.jidelite.editor.JavaSyntaxHighlighter
import com.jidelite.editor.JavaCodeFormatter
import com.jidelite.model.RunResult
import com.jidelite.runner.CodeRunner
import com.jidelite.runner.LocalJavaRunner
import com.jidelite.storage.FileStorageHelper
import com.jidelite.ui.theme.DotMint
import com.jidelite.ui.theme.DotPink
import com.jidelite.ui.theme.DotSand
import com.jidelite.ui.theme.EditorSurface
import com.jidelite.ui.theme.ExplorerSurface
import com.jidelite.ui.theme.JIdeLiteTheme
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
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private lateinit var fileStorageHelper: FileStorageHelper
    private lateinit var codeRunner: CodeRunner
    private lateinit var syntaxHighlighter: JavaSyntaxHighlighter
    private lateinit var javaCodeFormatter: JavaCodeFormatter
    private lateinit var runExecutor: ExecutorService

    private val files = mutableStateListOf<File>()

    private var selectedFileName by mutableStateOf<String?>(null)
    private var editorText by mutableStateOf("")
    private var terminalText by mutableStateOf("")
    private var statusText by mutableStateOf("")
    private var workspacePath by mutableStateOf("")
    private var isDirty by mutableStateOf(false)
    private var isRunning by mutableStateOf(false)
    private var isExplorerCollapsed by mutableStateOf(false)
    private var isTopBarCollapsed by mutableStateOf(true)

    private var suppressEditorCallbacks = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fileStorageHelper = FileStorageHelper(this)
        codeRunner = LocalJavaRunner(this, fileStorageHelper.workspaceDirectory)
        syntaxHighlighter = JavaSyntaxHighlighter(this)
        javaCodeFormatter = JavaCodeFormatter()
        runExecutor = Executors.newSingleThreadExecutor()
        terminalText = getString(R.string.terminal_ready)
        statusText = getString(R.string.status_ready)
        workspacePath = fileStorageHelper.workspaceDirectory.absolutePath

        setContent {
            JIdeLiteTheme {
                JIdeLiteApp(
                    files = files,
                    selectedFileName = selectedFileName,
                    statusText = statusText,
                    editorText = editorText,
                    terminalText = terminalText,
                    workspacePath = workspacePath,
                    isDirty = isDirty,
                    isRunning = isRunning,
                    isExplorerCollapsed = isExplorerCollapsed,
                    isTopBarCollapsed = isTopBarCollapsed,
                    onNewFile = { createNewFile() },
                    onSave = { saveCurrentFile(showToast = true) },
                    onFormat = { formatCurrentFile() },
                    onRun = { runCurrentFile() },
                    onToggleExplorer = { isExplorerCollapsed = !isExplorerCollapsed },
                    onToggleTopBar = { isTopBarCollapsed = !isTopBarCollapsed },
                    onClearTerminal = { terminalText = getString(R.string.terminal_ready) },
                    onOpenFile = { file ->
                        if (saveCurrentFile(showToast = false)) {
                            openFile(file)
                        }
                    },
                    onEditorChanged = { newText ->
                        editorText = newText
                        isDirty = true
                    },
                    syntaxHighlighter = syntaxHighlighter
                )
            }
        }

        loadWorkspace()
    }

    override fun onDestroy() {
        super.onDestroy()
        runExecutor.shutdownNow()
    }

    private fun loadWorkspace() {
        try {
            fileStorageHelper.initializeWorkspace()
            workspacePath = fileStorageHelper.workspaceDirectory.absolutePath
            refreshFiles()
            if (files.isNotEmpty()) {
                openFile(files.first())
            } else {
                statusText = getString(R.string.status_ready)
            }
        } catch (exception: IOException) {
            statusText = "Workspace error"
            terminalText = "Workspace initialization failed.\n\n${exception.message.orEmpty()}"
            showToast("Could not initialize workspace")
        }
    }

    private fun refreshFiles() {
        val listedFiles = fileStorageHelper.listJavaFiles()
        files.clear()
        files.addAll(listedFiles)
    }

    private fun openFile(file: File) {
        try {
            suppressEditorCallbacks = true
            editorText = fileStorageHelper.readFile(file.name)
            suppressEditorCallbacks = false
            selectedFileName = file.name
            isDirty = false
            statusText = "Editing ${file.name}"
        } catch (exception: IOException) {
            suppressEditorCallbacks = false
            terminalText = "Open failed.\n\n${exception.message.orEmpty()}"
            showToast("Could not open ${file.name}")
        }
    }

    private fun createNewFile() {
        if (!saveCurrentFile(showToast = false)) {
            return
        }

        try {
            val createdFile = fileStorageHelper.createNewJavaFile()
            refreshFiles()
            openFile(createdFile)
            statusText = "Created ${createdFile.name}"
            showToast("Created ${createdFile.name}")
        } catch (exception: IOException) {
            terminalText = "New file failed.\n\n${exception.message.orEmpty()}"
            showToast("Could not create file")
        }
    }

    private fun saveCurrentFile(showToast: Boolean): Boolean {
        val fileName = selectedFileName ?: return true

        return try {
            fileStorageHelper.saveFile(fileName, editorText)
            isDirty = false
            statusText = "Saved $fileName"
            if (showToast) {
                showToast("Saved")
            }
            true
        } catch (exception: IOException) {
            terminalText = "Save failed.\n\n${exception.message.orEmpty()}"
            showToast("Save failed")
            false
        }
    }

    private fun runCurrentFile() {
        val fileName = selectedFileName
        if (fileName.isNullOrBlank()) {
            showToast("No file selected")
            return
        }

        if (!saveCurrentFile(showToast = false)) {
            return
        }

        isRunning = true
        statusText = "Running $fileName"
        terminalText = "Running $fileName..."

        runExecutor.execute {
            val result = codeRunner.runJava(fileName, editorText)
            runOnUiThread {
                isRunning = false
                renderRunResult(result)
            }
        }
    }

    private fun formatCurrentFile() {
        val fileName = selectedFileName
        if (fileName.isNullOrBlank()) {
            showToast(getString(R.string.toast_no_file_selected))
            return
        }

        val formattedSource = javaCodeFormatter.format(editorText)
        if (formattedSource == editorText) {
            statusText = getString(R.string.status_already_formatted)
            showToast(getString(R.string.toast_format_no_changes))
            return
        }

        editorText = formattedSource
        isDirty = true
        statusText = getString(R.string.status_formatted, fileName)
        showToast(getString(R.string.toast_formatted))
    }

    private fun renderRunResult(runResult: RunResult) {
        val isSimulated = runResult.exitCode < 0 &&
                (runResult.stdout.contains("Execution mode: simulated", ignoreCase = true) ||
                        runResult.stdout.contains("[placeholder runner]", ignoreCase = true) ||
                        runResult.stderr.contains("placeholder", ignoreCase = true))
        val terminalBuilder = StringBuilder()

        when {
            isSimulated -> {
                terminalBuilder.append("Simulated run")
                statusText = "Placeholder runner"
            }

            runResult.isSuccess -> {
                terminalBuilder.append("Compile success")
                statusText = "Run finished"
            }

            else -> {
                terminalBuilder.append("Run failed")
                statusText = "Run failed"
            }
        }

        if (runResult.stdout.isNotBlank()) {
            terminalBuilder.append("\n\n").append(runResult.stdout.trim())
        }

        if (runResult.stderr.isNotBlank()) {
            terminalBuilder.append("\n\n").append(runResult.stderr.trim())
        }

        if (isSimulated) {
            terminalBuilder.append("\n\nExecution mode: simulated")
        } else if (runResult.exitCode >= 0) {
            terminalBuilder.append("\n\nExit code: ").append(runResult.exitCode)
        }

        terminalText = terminalBuilder.toString()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
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

    @Composable
    private fun JIdeLiteApp(
        files: List<File>,
        selectedFileName: String?,
        statusText: String,
        editorText: String,
        terminalText: String,
        workspacePath: String,
        isDirty: Boolean,
        isRunning: Boolean,
        isExplorerCollapsed: Boolean,
        isTopBarCollapsed: Boolean,
        onNewFile: () -> Unit,
        onSave: () -> Unit,
        onFormat: () -> Unit,
        onRun: () -> Unit,
        onToggleExplorer: () -> Unit,
        onToggleTopBar: () -> Unit,
        onClearTerminal: () -> Unit,
        onOpenFile: (File) -> Unit,
        onEditorChanged: (String) -> Unit,
        syntaxHighlighter: JavaSyntaxHighlighter
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopBar(
                    collapsed = isTopBarCollapsed,
                    statusText = statusText,
                    selectedFileName = selectedFileName,
                    fileCount = files.size,
                    isRunning = isRunning,
                    isExplorerCollapsed = isExplorerCollapsed,
                    onNewFile = onNewFile,
                    onSave = onSave,
                    onFormat = onFormat,
                    onRun = onRun,
                    onToggleExplorer = onToggleExplorer,
                    onToggleCollapse = onToggleTopBar
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Row(modifier = Modifier.fillMaxSize()) {
                    ExplorerPane(
                        modifier = Modifier
                            .width(if (isExplorerCollapsed) 68.dp else 228.dp)
                            .fillMaxHeight(),
                        collapsed = isExplorerCollapsed,
                        files = files,
                        selectedFileName = selectedFileName,
                        onToggleCollapse = onToggleExplorer,
                        onOpenFile = onOpenFile
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        EditorPane(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            fileName = selectedFileName ?: getString(R.string.editor_empty_title),
                            isDirty = isDirty,
                            editorText = editorText,
                            onEditorChanged = {
                                if (!suppressEditorCallbacks) {
                                    onEditorChanged(it)
                                }
                            },
                            syntaxHighlighter = syntaxHighlighter
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        TerminalPane(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (isTopBarCollapsed) 188.dp else 176.dp),
                            terminalText = terminalText,
                            workspacePath = workspacePath,
                            onClearTerminal = onClearTerminal
                        )
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
                    text = getString(R.string.app_name),
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
                    text = getString(R.string.app_version),
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
                        label = "NEW",
                        enabled = !isRunning,
                        highlighted = false,
                        compact = true,
                        onClick = onNewFile
                    )

                    ChromeActionButton(
                        label = "SAVE",
                        enabled = !isRunning,
                        highlighted = false,
                        compact = true,
                        onClick = onSave
                    )

                    ChromeActionButton(
                        label = getString(R.string.action_format_short),
                        enabled = !isRunning,
                        highlighted = false,
                        compact = true,
                        onClick = onFormat
                    )

                    ChromeActionButton(
                        label = "RUN",
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
                        text = "${selectedFileName ?: "No file"}  |  $fileCount files",
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
                            text = getString(R.string.explorer_header),
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
                        text = "\u25BE ${getString(R.string.workspace_label)}",
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
                                text = getString(R.string.explorer_empty),
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
    private fun EditorPane(
        modifier: Modifier,
        fileName: String,
        isDirty: Boolean,
        editorText: String,
        onEditorChanged: (String) -> Unit,
        syntaxHighlighter: JavaSyntaxHighlighter
    ) {
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
                            .widthIn(min = 142.dp)
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
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    val horizontalPadding = with(resources.displayMetrics) { (18f * density).toInt() }
                    val verticalPadding = with(resources.displayMetrics) { (16f * density).toInt() }

                    EditText(context).apply {
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
                        setHorizontallyScrolling(true)
                        overScrollMode = EditText.OVER_SCROLL_IF_CONTENT_SCROLLS
                        inputType = InputType.TYPE_CLASS_TEXT or
                                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                        imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI
                        setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
                        setLineSpacing(with(resources.displayMetrics) { 5f * density }, 1f)
                        setOnKeyListener { _, keyCode, event ->
                            if (keyCode == KeyEvent.KEYCODE_TAB && event.action == KeyEvent.ACTION_DOWN) {
                                insertTwoSpaceIndent(this)
                                true
                            } else {
                                false
                            }
                        }
                        addTextChangedListener(object : TextWatcher {
                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                            }

                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                            }

                            override fun afterTextChanged(s: Editable?) {
                                if (suppressEditorCallbacks) {
                                    return
                                }

                                onEditorChanged(s?.toString().orEmpty())
                                syntaxHighlighter.schedule(this@apply)
                            }
                        })
                    }
                },
                update = { editText ->
                    if (editText.text.toString() != editorText) {
                        suppressEditorCallbacks = true
                        editText.setText(editorText)
                        editText.setSelection(editorText.length)
                        syntaxHighlighter.highlightNow(editText)
                        suppressEditorCallbacks = false
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
        val readyText = getString(R.string.terminal_ready)

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
                        text = getString(R.string.terminal_title).uppercase(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        letterSpacing = 1.6.sp
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = getString(R.string.action_clear).uppercase(),
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
                    text = getString(R.string.terminal_intro),
                    color = TerminalInfo,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${getString(R.string.terminal_workspace_prefix)} $workspacePath",
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
