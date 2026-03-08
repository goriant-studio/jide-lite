package com.jidelite

import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.jidelite.editor.JavaSyntaxHighlighter
import com.jidelite.model.RunResult
import com.jidelite.runner.CodeRunner
import com.jidelite.runner.LocalJavaRunner
import com.jidelite.storage.FileStorageHelper
import com.jidelite.ui.theme.EditorSurface
import com.jidelite.ui.theme.ExplorerSurface
import com.jidelite.ui.theme.JIdeLiteTheme
import com.jidelite.ui.theme.RunButtonColor
import com.jidelite.ui.theme.SelectedSurface
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
    private lateinit var runExecutor: ExecutorService

    private val files = mutableStateListOf<File>()

    private var selectedFileName by mutableStateOf<String?>(null)
    private var editorText by mutableStateOf("")
    private var terminalText by mutableStateOf("")
    private var statusText by mutableStateOf("")
    private var isDirty by mutableStateOf(false)
    private var isRunning by mutableStateOf(false)

    private var suppressEditorCallbacks = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fileStorageHelper = FileStorageHelper(this)
        codeRunner = LocalJavaRunner()
        syntaxHighlighter = JavaSyntaxHighlighter(this)
        runExecutor = Executors.newSingleThreadExecutor()
        terminalText = getString(R.string.terminal_ready)
        statusText = getString(R.string.status_ready)

        setContent {
            JIdeLiteTheme {
                JIdeLiteApp(
                    files = files,
                    selectedFileName = selectedFileName,
                    statusText = statusText,
                    editorText = editorText,
                    terminalText = terminalText,
                    isDirty = isDirty,
                    isRunning = isRunning,
                    onNewFile = { createNewFile() },
                    onSave = { saveCurrentFile(showToast = true) },
                    onRun = { runCurrentFile() },
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

    private fun renderRunResult(runResult: RunResult) {
        val isSimulated = runResult.exitCode < 0
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
        } else {
            terminalBuilder.append("\n\nExit code: ").append(runResult.exitCode)
        }

        terminalText = terminalBuilder.toString()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    @Composable
    private fun JIdeLiteApp(
        files: List<File>,
        selectedFileName: String?,
        statusText: String,
        editorText: String,
        terminalText: String,
        isDirty: Boolean,
        isRunning: Boolean,
        onNewFile: () -> Unit,
        onSave: () -> Unit,
        onRun: () -> Unit,
        onClearTerminal: () -> Unit,
        onOpenFile: (File) -> Unit,
        onEditorChanged: (String) -> Unit,
        syntaxHighlighter: JavaSyntaxHighlighter
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 14.dp)
            ) {
                TopBar(
                    statusText = statusText,
                    isRunning = isRunning,
                    onNewFile = onNewFile,
                    onSave = onSave,
                    onRun = onRun
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    ExplorerPane(
                        modifier = Modifier.weight(0.2f),
                        files = files,
                        selectedFileName = selectedFileName,
                        onOpenFile = onOpenFile
                    )

                    EditorPane(
                        modifier = Modifier.weight(0.8f),
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
                }

                Spacer(modifier = Modifier.height(14.dp))

                TerminalPane(
                    terminalText = terminalText,
                    onClearTerminal = onClearTerminal
                )
            }
        }
    }

    @Composable
    private fun TopBar(
        statusText: String,
        isRunning: Boolean,
        onNewFile: () -> Unit,
        onSave: () -> Unit,
        onRun: () -> Unit
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = TopBarSurface,
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = getString(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)
                    )

                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                FilledTonalButton(
                    onClick = onNewFile,
                    enabled = !isRunning,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text(text = getString(R.string.action_new_file))
                }

                Spacer(modifier = Modifier.width(8.dp))

                FilledTonalButton(
                    onClick = onSave,
                    enabled = !isRunning,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text(text = getString(R.string.action_save))
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onRun,
                    enabled = !isRunning,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RunButtonColor,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(text = getString(R.string.action_run))
                }
            }
        }
    }

    @Composable
    private fun ExplorerPane(
        modifier: Modifier,
        files: List<File>,
        selectedFileName: String?,
        onOpenFile: (File) -> Unit
    ) {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = ExplorerSurface,
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = getString(R.string.explorer_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.weight(1f)
                    )

                    val fileCountText = when (files.size) {
                        0 -> getString(R.string.file_count_zero)
                        1 -> getString(R.string.file_count_one)
                        else -> getString(R.string.file_count_many, files.size)
                    }
                    Text(
                        text = fileCountText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(top = 16.dp, bottom = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                if (files.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = getString(R.string.explorer_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
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

    @Composable
    private fun ExplorerFileRow(
        file: File,
        selected: Boolean,
        onClick: () -> Unit
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            color = if (selected) SelectedSurface else Color.Transparent,
            shape = RoundedCornerShape(18.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                else Color.Transparent
            )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (selected) getString(R.string.file_meta_active) else getString(R.string.file_meta),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
        Surface(
            modifier = modifier.fillMaxSize(),
            color = EditorSurface,
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = fileName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (isDirty) getString(R.string.editor_meta_unsaved) else getString(R.string.editor_meta_saved),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(top = 14.dp, bottom = 10.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        val horizontalPadding = with(resources.displayMetrics) { (20f * density).toInt() }
                        val verticalPadding = with(resources.displayMetrics) { (14f * density).toInt() }

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
                            setLineSpacing(with(resources.displayMetrics) { 4f * density }, 1f)
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
    }

    @Composable
    private fun TerminalPane(
        terminalText: String,
        onClearTerminal: () -> Unit
    ) {
        val scrollState = rememberScrollState()

        LaunchedEffect(terminalText) {
            scrollState.scrollTo(scrollState.maxValue)
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp),
            color = TerminalSurface,
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = getString(R.string.terminal_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.weight(1f)
                    )

                    TextButton(onClick = onClearTerminal) {
                        Text(text = getString(R.string.action_clear))
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(top = 12.dp, bottom = 10.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent)
                        .verticalScroll(scrollState)
                ) {
                    Text(
                        text = terminalText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    )
                }
            }
        }
    }
}
