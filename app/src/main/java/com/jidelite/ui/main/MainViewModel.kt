package com.jidelite.ui.main

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.jidelite.R
import com.jidelite.editor.JavaCodeFormatter
import com.jidelite.runner.CodeRunner
import com.jidelite.runner.LocalJavaRunner
import com.jidelite.storage.FileStorageHelper
import java.io.File
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val fileStorageHelper = FileStorageHelper(app)
    private val codeRunner: CodeRunner = LocalJavaRunner(app, fileStorageHelper.workspaceDirectory)
    private val javaCodeFormatter = JavaCodeFormatter()
    private val runExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    var uiState by mutableStateOf(
        MainUiState(
            terminalText = app.getString(R.string.terminal_ready),
            statusText = app.getString(R.string.status_ready),
            workspacePath = fileStorageHelper.workspaceDirectory.absolutePath
        )
    )
        private set

    var pendingToastMessage by mutableStateOf<String?>(null)
        private set

    init {
        loadWorkspace()
    }

    override fun onCleared() {
        super.onCleared()
        runExecutor.shutdownNow()
    }

    fun onEditorChanged(newText: String) {
        if (newText == uiState.editorText) {
            return
        }
        uiState = uiState.copy(editorText = newText, isDirty = true)
    }

    fun onNewFileRequested() {
        if (!saveCurrentFile(showToast = false)) {
            return
        }

        try {
            val createdFile = fileStorageHelper.createNewJavaFile()
            val refreshedFiles = refreshFiles()
            uiState = uiState.copy(files = refreshedFiles)
            openFile(createdFile)
            uiState = uiState.copy(statusText = "Created ${createdFile.name}")
            emitToast("Created ${createdFile.name}")
        } catch (exception: IOException) {
            uiState = uiState.copy(
                terminalText = "New file failed.\n\n${exception.message.orEmpty()}"
            )
            emitToast("Could not create file")
        }
    }

    fun onSaveRequested() {
        saveCurrentFile(showToast = true)
    }

    fun onFormatRequested() {
        val fileName = uiState.selectedFileName
        if (fileName.isNullOrBlank()) {
            emitToast(app.getString(R.string.toast_no_file_selected))
            return
        }

        val formattedSource = javaCodeFormatter.format(uiState.editorText)
        if (formattedSource == uiState.editorText) {
            uiState = uiState.copy(statusText = app.getString(R.string.status_already_formatted))
            emitToast(app.getString(R.string.toast_format_no_changes))
            return
        }

        uiState = uiState.copy(
            editorText = formattedSource,
            isDirty = true,
            statusText = app.getString(R.string.status_formatted, fileName)
        )
        emitToast(app.getString(R.string.toast_formatted))
    }

    fun onRunRequested() {
        val fileName = uiState.selectedFileName
        if (fileName.isNullOrBlank()) {
            emitToast(app.getString(R.string.toast_no_file_selected))
            return
        }

        if (!saveCurrentFile(showToast = false)) {
            return
        }

        val sourceSnapshot = uiState.editorText
        uiState = uiState.copy(
            isRunning = true,
            statusText = "Running $fileName",
            terminalText = "Running $fileName..."
        )

        runExecutor.execute {
            val presentation = try {
                RunOutputFormatter.format(codeRunner.runJava(fileName, sourceSnapshot))
            } catch (throwable: Throwable) {
                RunPresentation(
                    statusText = "Run failed",
                    terminalText = buildString {
                        append("Run failed")
                        append("\n\nInternal runner error.")
                        val details = throwable.message.orEmpty().ifBlank {
                            throwable::class.java.simpleName
                        }
                        append("\n\n").append(details)
                    }
                )
            }
            mainHandler.post {
                applyRunPresentation(presentation)
            }
        }
    }

    fun onOpenFileRequested(file: File) {
        if (!saveCurrentFile(showToast = false)) {
            return
        }
        openFile(file)
    }

    fun onClearTerminalRequested() {
        uiState = uiState.copy(terminalText = app.getString(R.string.terminal_ready))
    }

    fun updateStatus(statusText: String, toastMessage: String? = null) {
        uiState = uiState.copy(statusText = statusText)
        if (!toastMessage.isNullOrBlank()) {
            emitToast(toastMessage)
        }
    }

    fun consumeToast() {
        pendingToastMessage = null
    }

    private fun loadWorkspace() {
        try {
            fileStorageHelper.initializeWorkspace()
            val refreshedFiles = refreshFiles()
            uiState = uiState.copy(
                files = refreshedFiles,
                workspacePath = fileStorageHelper.workspaceDirectory.absolutePath,
                statusText = app.getString(R.string.status_ready)
            )
            if (refreshedFiles.isNotEmpty()) {
                openFile(refreshedFiles.first())
            }
        } catch (exception: IOException) {
            uiState = uiState.copy(
                statusText = "Workspace error",
                terminalText = "Workspace initialization failed.\n\n${exception.message.orEmpty()}"
            )
            emitToast("Could not initialize workspace")
        }
    }

    private fun refreshFiles(): List<File> {
        val listedFiles = fileStorageHelper.listJavaFiles()
        uiState = uiState.copy(files = listedFiles)
        return listedFiles
    }

    private fun openFile(file: File) {
        try {
            uiState = uiState.copy(
                editorText = fileStorageHelper.readFile(file.name),
                selectedFileName = file.name,
                isDirty = false,
                statusText = "Editing ${file.name}"
            )
        } catch (exception: IOException) {
            uiState = uiState.copy(
                terminalText = "Open failed.\n\n${exception.message.orEmpty()}"
            )
            emitToast("Could not open ${file.name}")
        }
    }

    private fun saveCurrentFile(showToast: Boolean): Boolean {
        val fileName = uiState.selectedFileName ?: return true

        return try {
            fileStorageHelper.saveFile(fileName, uiState.editorText)
            uiState = uiState.copy(
                isDirty = false,
                statusText = "Saved $fileName"
            )
            if (showToast) {
                emitToast("Saved")
            }
            true
        } catch (exception: IOException) {
            uiState = uiState.copy(
                terminalText = "Save failed.\n\n${exception.message.orEmpty()}"
            )
            emitToast("Save failed")
            false
        }
    }

    private fun applyRunPresentation(presentation: RunPresentation) {
        uiState = uiState.copy(
            isRunning = false,
            statusText = presentation.statusText,
            terminalText = presentation.terminalText
        )
    }

    private fun emitToast(message: String) {
        pendingToastMessage = message
    }
}
