package com.jidelite.ui.main

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.jidelite.R
import com.jidelite.editor.JavaCodeFormatter
import com.jidelite.runner.CodeRunner
import com.jidelite.storage.FileStorageHelper
import java.io.File
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.LazyThreadSafetyMode

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val fileStorageHelper = FileStorageHelper(app)
    private val javaCodeFormatter = JavaCodeFormatter()
    private val runExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var runnerInitializationError: Throwable? = null
    private val codeRunner: CodeRunner? by lazy(LazyThreadSafetyMode.NONE) {
        createCodeRunner()
    }

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
        if (!fileName.endsWith(".java")) {
            uiState = uiState.copy(statusText = "Format supports Java files only")
            emitToast("Open a Java file to format")
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
        val selectedPath = uiState.selectedFilePath
        val fileName = uiState.selectedFileName
        if (selectedPath.isNullOrBlank() || fileName.isNullOrBlank()) {
            emitToast(app.getString(R.string.toast_no_file_selected))
            return
        }

        if (!saveCurrentFile(showToast = false)) {
            return
        }
        val runner = obtainCodeRunner() ?: return
        uiState = uiState.copy(
            isRunning = true,
            isResolvingDependencies = false,
            statusText = "Running $fileName",
            terminalText = "Running $fileName..."
        )

        runExecutor.execute {
            val presentation = try {
                RunOutputFormatter.format(runner.run(selectedPath))
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
                applyBackgroundPresentation(presentation)
            }
        }
    }

    fun onResolveDependenciesRequested() {
        if (!uiState.isMavenProject) {
            uiState = uiState.copy(statusText = "No pom.xml in workspace")
            emitToast(app.getString(R.string.toast_no_maven_project))
            return
        }

        if (!saveCurrentFile(showToast = false)) {
            return
        }
        val runner = obtainCodeRunner() ?: return
        uiState = uiState.copy(
            isRunning = false,
            isResolvingDependencies = true,
            statusText = "Resolving Maven dependencies",
            terminalText = "Resolving Maven dependencies..."
        )

        runExecutor.execute {
            val presentation = try {
                RunOutputFormatter.formatDependencyResolution(runner.resolveDependencies())
            } catch (throwable: Throwable) {
                RunPresentation(
                    statusText = "Dependency resolution failed",
                    terminalText = buildString {
                        append("Dependency resolution failed")
                        append("\n\nInternal resolver error.")
                        val details = throwable.message.orEmpty().ifBlank {
                            throwable::class.java.simpleName
                        }
                        append("\n\n").append(details)
                    }
                )
            }
            mainHandler.post {
                applyBackgroundPresentation(presentation)
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
        } catch (throwable: Throwable) {
            uiState = uiState.copy(
                statusText = "Workspace error",
                terminalText = buildString {
                    append("Workspace initialization failed.")
                    append("\n\n")
                    append(formatThrowableSummary(throwable))
                }
            )
            emitToast("Could not initialize workspace")
        }
    }

    private fun refreshFiles(): List<File> {
        val listedFiles = fileStorageHelper.listWorkspaceFiles()
        uiState = uiState.copy(
            files = listedFiles,
            isMavenProject = listedFiles.any { it.name == "pom.xml" }
        )
        return listedFiles
    }

    private fun openFile(file: File) {
        try {
            val relativePath = fileStorageHelper.toWorkspaceRelativePath(file)
            uiState = uiState.copy(
                editorText = fileStorageHelper.readFile(file),
                selectedFilePath = file.absolutePath,
                isDirty = false,
                statusText = "Editing $relativePath"
            )
        } catch (exception: IOException) {
            uiState = uiState.copy(
                terminalText = "Open failed.\n\n${exception.message.orEmpty()}"
            )
            emitToast("Could not open ${file.name}")
        }
    }

    private fun saveCurrentFile(showToast: Boolean): Boolean {
        val selectedPath = uiState.selectedFilePath ?: return true
        val fileName = uiState.selectedFileName ?: File(selectedPath).name

        return try {
            fileStorageHelper.saveFile(File(selectedPath), uiState.editorText)
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

    private fun applyBackgroundPresentation(presentation: RunPresentation) {
        uiState = uiState.copy(
            isRunning = false,
            isResolvingDependencies = false,
            statusText = presentation.statusText,
            terminalText = presentation.terminalText
        )
    }

    private fun emitToast(message: String) {
        pendingToastMessage = message
    }

    private fun obtainCodeRunner(): CodeRunner? {
        val runner = codeRunner
        if (runner != null) {
            return runner
        }

        val failure = runnerInitializationError
        uiState = uiState.copy(
            statusText = "Runner unavailable",
            terminalText = buildString {
                append("Runner initialization failed.")
                if (failure != null) {
                    append("\n\n")
                    append(formatThrowableSummary(failure))
                }
            }
        )
        emitToast("Runner unavailable")
        return null
    }

    private fun createCodeRunner(): CodeRunner? {
        return try {
            val runnerClass = Class.forName("com.jidelite.runner.LocalJavaRunner")
            val constructor = runnerClass.getDeclaredConstructor(Context::class.java, File::class.java)
            val instance = constructor.newInstance(app, fileStorageHelper.workspaceDirectory)
            instance as CodeRunner
        } catch (throwable: Throwable) {
            runnerInitializationError = unwrapReflectionFailure(throwable)
            null
        }
    }

    private fun unwrapReflectionFailure(throwable: Throwable): Throwable {
        var current = throwable
        while (true) {
            val next = current.cause ?: return current
            current = next
        }
    }

    private fun formatThrowableSummary(throwable: Throwable): String {
        val summary = throwable.message.orEmpty().ifBlank {
            throwable::class.java.name
        }
        return "${throwable::class.java.simpleName}: $summary"
    }
}
