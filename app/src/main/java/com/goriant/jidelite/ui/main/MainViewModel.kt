package com.goriant.jidelite.ui.main

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.goriant.jidelite.R
import com.goriant.jidelite.data.db.IdeStateRepository
import com.goriant.jidelite.data.entity.BuildStateEntity
import com.goriant.jidelite.data.entity.OpenEditorEntity
import com.goriant.jidelite.data.entity.ProjectEntity
import com.goriant.jidelite.data.entity.WorkspaceEntity
import com.goriant.jidelite.data.enums.BuildStatus
import com.goriant.jidelite.data.enums.ProjectType
import com.goriant.jidelite.editor.JavaCodeFormatter
import com.goriant.jidelite.runner.CodeRunner
import com.goriant.jidelite.storage.FileStorageHelper
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.LazyThreadSafetyMode
import org.json.JSONArray
import org.json.JSONException

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val APPLICATION_SCOPE = "application"
        private const val LAST_WORKSPACE_ROOT_KEY = "workbench.workspace.lastRootPath"
        private const val ACTIVE_FILE_KEY = "workbench.editor.activeFile"
        private const val SELECTED_ENTRY_KEY = "workbench.explorer.selectedEntry"
        private const val HISTORY_ENTRIES_KEY = "history.entries"
        private const val MAX_HISTORY_ENTRIES = 200
    }

    private val app = application
    private val fileStorageHelper = FileStorageHelper(app)
    private val stateRepository = IdeStateRepository(app)
    private val javaCodeFormatter = JavaCodeFormatter()
    private val runExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var workspaceEntity: WorkspaceEntity? = null
    private var projectEntity: ProjectEntity? = null
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
        stateRepository.close()
    }

    fun onEditorChanged(newText: String) {
        if (newText == uiState.editorText) {
            return
        }
        uiState = uiState.copy(
            editorText = newText,
            isDirty = true,
            editorDiagnostic = null
        )
    }

    fun onNewFileRequested() {
        if (!saveCurrentFile(showToast = false)) {
            return
        }

        try {
            val creationDirectory = resolveCreateTargetDirectory()
            val createdFile = fileStorageHelper.createNewJavaFile(creationDirectory)
            refreshFiles()
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

    fun onNewFolderRequested() {
        if (!saveCurrentFile(showToast = false)) {
            return
        }

        try {
            val creationDirectory = resolveCreateTargetDirectory()
            val createdFolder = fileStorageHelper.createNewFolder(creationDirectory)
            refreshFiles()
            val relativePath = fileStorageHelper.toWorkspaceEntryRelativePath(createdFolder)
            uiState = uiState.copy(
                selectedEntryPath = createdFolder.absolutePath,
                statusText = "Created folder $relativePath"
            )
            persistSelectionState()
            emitToast("Created folder ${createdFolder.name}")
        } catch (exception: IOException) {
            uiState = uiState.copy(
                terminalText = "New folder failed.\n\n${exception.message.orEmpty()}"
            )
            emitToast("Could not create folder")
        }
    }

    fun onDeleteEntryRequested(entry: File) {
        val entryPath = entry.absolutePath
        val entryLabel = entry.name
        val entryType = if (entry.isDirectory) "folder" else "file"

        val openFilePath = uiState.selectedFilePath
        val selectedEntryPath = uiState.selectedEntryPath
        val removesOpenFile = openFilePath != null && isSameOrChildPath(openFilePath, entryPath)
        val removesSelectedEntry = selectedEntryPath != null && isSameOrChildPath(selectedEntryPath, entryPath)

        try {
            fileStorageHelper.deleteEntry(entry)
            val refreshedFiles = refreshFiles()

            if (removesOpenFile) {
                val nextFile = refreshedFiles.firstOrNull { it.isFile }
                if (nextFile != null) {
                    openFile(nextFile)
                } else {
                    uiState = uiState.copy(
                        editorText = "",
                        editorDiagnostic = null,
                        selectedFilePath = null,
                        selectedEntryPath = null,
                        isDirty = false
                    )
                }
            } else if (removesSelectedEntry) {
                uiState = uiState.copy(selectedEntryPath = null)
            }

            uiState = uiState.copy(statusText = "Deleted $entryType $entryLabel")
            persistSelectionState()
            emitToast("Deleted $entryLabel")
        } catch (exception: IOException) {
            uiState = uiState.copy(
                terminalText = "Delete failed.\n\n${exception.message.orEmpty()}"
            )
            emitToast("Could not delete $entryLabel")
        }
    }

    fun onRenameEntryRequested(entry: File, newName: String) {
        if (!saveCurrentFile(showToast = false)) {
            return
        }

        val currentOpenFilePath = uiState.selectedFilePath
        val currentSelectedEntryPath = uiState.selectedEntryPath
        val entryPath = entry.absolutePath
        val entryLabel = entry.name

        try {
            val renamedEntry = fileStorageHelper.renameEntry(entry, newName)
            refreshFiles()

            val updatedOpenFilePath = remapPathAfterEntryRename(
                currentPath = currentOpenFilePath,
                sourcePath = entryPath,
                destinationPath = renamedEntry.absolutePath
            )
            val updatedSelectedEntryPath = remapPathAfterEntryRename(
                currentPath = currentSelectedEntryPath,
                sourcePath = entryPath,
                destinationPath = renamedEntry.absolutePath
            )

            uiState = uiState.copy(
                selectedFilePath = updatedOpenFilePath,
                selectedEntryPath = updatedSelectedEntryPath,
                statusText = "Renamed $entryLabel to ${renamedEntry.name}"
            )
            persistSelectionState()
            if (!updatedOpenFilePath.isNullOrBlank()) {
                persistOpenEditor(updatedOpenFilePath)
            }
            emitToast("Renamed to ${renamedEntry.name}")
        } catch (exception: IOException) {
            uiState = uiState.copy(
                terminalText = "Rename failed.\n\n${exception.message.orEmpty()}"
            )
            emitToast("Could not rename $entryLabel")
        }
    }

    fun onMoveEntryRequested(entry: File, destinationDirectory: File) {
        if (!saveCurrentFile(showToast = false)) {
            return
        }

        val currentOpenFilePath = uiState.selectedFilePath
        val currentSelectedEntryPath = uiState.selectedEntryPath
        val sourcePath = entry.absolutePath

        try {
            val movedEntry = fileStorageHelper.moveEntryToDirectory(entry, destinationDirectory)
            refreshFiles()

            val updatedOpenFilePath = remapPathAfterEntryRename(
                currentPath = currentOpenFilePath,
                sourcePath = sourcePath,
                destinationPath = movedEntry.absolutePath
            )
            val updatedSelectedEntryPath = remapPathAfterEntryRename(
                currentPath = currentSelectedEntryPath,
                sourcePath = sourcePath,
                destinationPath = movedEntry.absolutePath
            )

            uiState = uiState.copy(
                selectedFilePath = updatedOpenFilePath,
                selectedEntryPath = updatedSelectedEntryPath,
                statusText = "Moved ${entry.name}"
            )
            persistSelectionState()
            if (!updatedOpenFilePath.isNullOrBlank()) {
                persistOpenEditor(updatedOpenFilePath)
            }
            emitToast("Moved ${entry.name}")
        } catch (exception: IOException) {
            uiState = uiState.copy(
                terminalText = "Move failed.\n\n${exception.message.orEmpty()}"
            )
            emitToast("Could not move ${entry.name}")
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
            editorDiagnostic = null,
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
            terminalText = "Running $fileName...",
            editorDiagnostic = null
        )
        persistBuildState(
            status = BuildStatus.RUNNING,
            output = uiState.terminalText,
            artifactPath = null
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
            terminalText = "Resolving Maven dependencies...",
            editorDiagnostic = null
        )
        persistBuildState(
            status = BuildStatus.RUNNING,
            output = uiState.terminalText,
            artifactPath = null
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
        if (file.isDirectory) {
            val relativePath = try {
                fileStorageHelper.toWorkspaceEntryRelativePath(file)
            } catch (_: IOException) {
                file.name
            }
            uiState = uiState.copy(
                selectedEntryPath = file.absolutePath,
                statusText = "Selected folder $relativePath"
            )
            persistSelectionState()
            emitToast("Folder: $relativePath")
            return
        }
        if (!saveCurrentFile(showToast = false)) {
            return
        }
        openFile(file)
    }

    fun onClearTerminalRequested() {
        uiState = uiState.copy(
            terminalText = app.getString(R.string.terminal_ready),
            editorDiagnostic = null
        )
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
            initializeDatabaseState(refreshedFiles)
            uiState = uiState.copy(
                files = refreshedFiles,
                workspacePath = fileStorageHelper.workspaceDirectory.absolutePath,
                statusText = app.getString(R.string.status_ready)
            )
            val restoredFile = resolveRestoredOpenFile(refreshedFiles)
            val firstFileToOpen = restoredFile ?: refreshedFiles.firstOrNull { it.isFile }
            if (firstFileToOpen != null) {
                openFile(firstFileToOpen)
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
        val listedFiles = fileStorageHelper.listWorkspaceEntries()
        val currentSelection = uiState.selectedEntryPath
        val hasCurrentSelection = currentSelection != null &&
                listedFiles.any { it.absolutePath == currentSelection }
        uiState = uiState.copy(
            files = listedFiles,
            isMavenProject = listedFiles.any { it.name == "pom.xml" },
            selectedEntryPath = if (hasCurrentSelection) currentSelection else null
        )
        if (projectEntity != null) {
            syncProjectType(listedFiles)
        }
        return listedFiles
    }

    private fun openFile(file: File) {
        try {
            val relativePath = fileStorageHelper.toWorkspaceRelativePath(file)
            uiState = uiState.copy(
                editorText = fileStorageHelper.readFile(file),
                editorDiagnostic = null,
                selectedFilePath = file.absolutePath,
                selectedEntryPath = file.absolutePath,
                isDirty = false,
                statusText = "Editing $relativePath"
            )
            persistSelectionState()
            persistOpenEditor(file.absolutePath)
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
        val diagnostic = resolveEditorDiagnostic(presentation.editorDiagnostic)
        var nextState = uiState.copy(
            isRunning = false,
            isResolvingDependencies = false,
            statusText = presentation.statusText,
            terminalText = presentation.terminalText,
            editorDiagnostic = diagnostic
        )
        val diagnosticFilePath = diagnostic?.filePath

        if (!diagnosticFilePath.isNullOrBlank() && diagnosticFilePath != uiState.selectedFilePath) {
            val file = File(diagnosticFilePath)
            val fileContents = readEditorFile(file)
            if (fileContents != null) {
                nextState = nextState.copy(
                    editorText = fileContents,
                    selectedFilePath = diagnosticFilePath,
                    selectedEntryPath = diagnosticFilePath,
                    isDirty = false
                )
                uiState = nextState
                persistSelectionState()
                persistOpenEditor(diagnosticFilePath)
            } else {
                uiState = nextState.copy(editorDiagnostic = null)
            }
        } else {
            uiState = nextState
        }

        persistBuildState(
            status = inferBuildStatus(presentation),
            output = presentation.terminalText,
            artifactPath = null
        )
    }

    private fun resolveEditorDiagnostic(hint: EditorDiagnosticHint?): EditorDiagnostic? {
        if (hint == null || hint.lineNumber <= 0) {
            return null
        }

        return EditorDiagnostic(
            filePath = resolveDiagnosticFilePath(hint.fileReference),
            lineNumber = hint.lineNumber,
            message = hint.message
        )
    }

    private fun resolveDiagnosticFilePath(fileReference: String?): String? {
        if (fileReference.isNullOrBlank()) {
            return uiState.selectedFilePath
        }

        val currentSelectedPath = uiState.selectedFilePath
        val normalizedReference = fileReference.replace('\\', '/')
        val referenceName = File(fileReference).name

        if (!currentSelectedPath.isNullOrBlank()) {
            val normalizedCurrent = currentSelectedPath.replace('\\', '/')
            if (normalizedCurrent.endsWith(normalizedReference) ||
                (referenceName.isNotBlank() && File(currentSelectedPath).name == referenceName)
            ) {
                return currentSelectedPath
            }
        }

        val absoluteCandidate = File(fileReference)
        if (absoluteCandidate.exists() && absoluteCandidate.isFile) {
            return absoluteCandidate.absolutePath
        }

        val workspaceCandidate = File(fileStorageHelper.workspaceDirectory, fileReference)
        if (workspaceCandidate.exists() && workspaceCandidate.isFile) {
            return workspaceCandidate.absolutePath
        }

        val workspaceFiles = uiState.files.filter { it.isFile }
        val suffixMatches = workspaceFiles.filter { file ->
            file.absolutePath.replace('\\', '/').endsWith(normalizedReference)
        }
        if (suffixMatches.size == 1) {
            return suffixMatches.first().absolutePath
        }

        val nameMatches = workspaceFiles.filter { file ->
            file.name == referenceName
        }
        if (nameMatches.size == 1) {
            return nameMatches.first().absolutePath
        }

        return currentSelectedPath
    }

    private fun readEditorFile(file: File): String? {
        return try {
            fileStorageHelper.readFile(file)
        } catch (_: IOException) {
            null
        }
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
            val runnerClass = Class.forName("com.goriant.jidelite.runner.LocalJavaRunner")
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

    private fun initializeDatabaseState(files: List<File>) {
        val workspaceDirectory = fileStorageHelper.workspaceDirectory
        val workspaceRootPath = workspaceDirectory.absolutePath
        val workspaceName = workspaceDirectory.name.ifBlank { "workspace" }
        val projectType = determineProjectType(files)
        val buildFilePath = files.firstOrNull { it.isFile && it.name == "pom.xml" }?.absolutePath
        val outputDirPath = if (projectType == ProjectType.MAVEN) {
            File(workspaceDirectory, "target").absolutePath
        } else {
            null
        }

        try {
            val workspace = stateRepository.upsertWorkspace(workspaceRootPath, workspaceName)
            val project = stateRepository.upsertProject(
                workspace.id,
                workspaceRootPath,
                projectType,
                buildFilePath,
                outputDirPath
            )
            workspaceEntity = workspace
            projectEntity = project
            stateRepository.putState(APPLICATION_SCOPE, LAST_WORKSPACE_ROOT_KEY, workspaceRootPath)
        } catch (_: Throwable) {
            // Keep state persistence as a non-blocking concern for editor usability.
        }
    }

    private fun syncProjectType(files: List<File>) {
        val workspace = workspaceEntity ?: return
        val currentProject = projectEntity ?: return
        val projectType = determineProjectType(files)
        val buildFilePath = files.firstOrNull { it.isFile && it.name == "pom.xml" }?.absolutePath
        val outputDirPath = if (projectType == ProjectType.MAVEN) {
            File(fileStorageHelper.workspaceDirectory, "target").absolutePath
        } else {
            null
        }

        try {
            projectEntity = stateRepository.upsertProject(
                workspace.id,
                currentProject.rootPath,
                projectType,
                buildFilePath,
                outputDirPath
            )
        } catch (_: Throwable) {
            // Ignore persistence issues to avoid interrupting file operations.
        }
    }

    private fun resolveRestoredOpenFile(files: List<File>): File? {
        val projectId = projectEntity?.id ?: return null
        val scope = workspaceScope(projectId)

        try {
            resolveExistingWorkspaceFile(stateRepository.getState(scope, ACTIVE_FILE_KEY))?.let {
                return it
            }

            stateRepository.listOpenEditors(projectId, 24).forEach { editor ->
                resolveExistingWorkspaceFile(editor.filePath)?.let {
                    return it
                }
            }

            readHistoryEntries(scope).forEach { path ->
                resolveExistingWorkspaceFile(path)?.let {
                    return it
                }
            }
        } catch (_: Throwable) {
            // Fall through to first file in workspace below.
        }

        return files.firstOrNull { it.isFile }
    }

    private fun resolveExistingWorkspaceFile(path: String?): File? {
        if (path.isNullOrBlank()) {
            return null
        }
        val candidate = File(path)
        if (!candidate.exists() || !candidate.isFile) {
            return null
        }
        return try {
            fileStorageHelper.toWorkspaceEntryRelativePath(candidate)
            candidate
        } catch (_: IOException) {
            null
        }
    }

    private fun persistOpenEditor(filePath: String) {
        val projectId = projectEntity?.id ?: return
        val now = System.currentTimeMillis()
        val openEditorEntity = OpenEditorEntity().apply {
            this.projectId = projectId
            this.filePath = filePath
            this.cursorStart = uiState.editorText.length
            this.cursorEnd = uiState.editorText.length
            this.scrollX = 0
            this.scrollY = 0
            this.openedOrder = 0
            this.pinned = false
            this.updatedAt = now
        }
        val scope = workspaceScope(projectId)

        try {
            stateRepository.upsertOpenEditor(openEditorEntity)
            stateRepository.putState(scope, ACTIVE_FILE_KEY, filePath)
            appendHistoryEntry(scope, filePath)
        } catch (_: Throwable) {
            // Ignore persistence failures while editing.
        }
    }

    private fun persistSelectionState() {
        val projectId = projectEntity?.id ?: return
        val scope = workspaceScope(projectId)
        val selectedEntryPath = uiState.selectedEntryPath
        val selectedFilePath = uiState.selectedFilePath

        try {
            if (selectedEntryPath.isNullOrBlank()) {
                stateRepository.removeState(scope, SELECTED_ENTRY_KEY)
            } else {
                stateRepository.putState(scope, SELECTED_ENTRY_KEY, selectedEntryPath)
            }

            if (selectedFilePath.isNullOrBlank()) {
                stateRepository.removeState(scope, ACTIVE_FILE_KEY)
            } else {
                stateRepository.putState(scope, ACTIVE_FILE_KEY, selectedFilePath)
            }
        } catch (_: Throwable) {
            // Ignore persistence failures while editing.
        }
    }

    private fun persistBuildState(status: BuildStatus, output: String, artifactPath: String?) {
        val projectId = projectEntity?.id ?: return
        val buildStateEntity = BuildStateEntity().apply {
            this.projectId = projectId
            this.status = status
            this.lastOutput = output.takeLast(120_000)
            this.lastArtifactPath = artifactPath
            this.lastBuildAt = System.currentTimeMillis()
        }

        try {
            stateRepository.upsertBuildState(buildStateEntity)
        } catch (_: Throwable) {
            // Ignore persistence failures while editing.
        }
    }

    private fun inferBuildStatus(presentation: RunPresentation): BuildStatus {
        val statusText = presentation.statusText.lowercase(Locale.ROOT)
        val terminalText = presentation.terminalText.lowercase(Locale.ROOT)
        return when {
            statusText.contains("fail") || terminalText.contains("failed") || terminalText.contains("error") -> {
                BuildStatus.FAILED
            }

            statusText.contains("success") || terminalText.contains("success") || terminalText.contains("completed") -> {
                BuildStatus.SUCCESS
            }

            else -> BuildStatus.IDLE
        }
    }

    private fun appendHistoryEntry(scope: String, path: String) {
        val entries = readHistoryEntries(scope)
        entries.removeAll { it == path }
        entries.add(0, path)
        if (entries.size > MAX_HISTORY_ENTRIES) {
            entries.subList(MAX_HISTORY_ENTRIES, entries.size).clear()
        }

        val json = JSONArray()
        entries.forEach { entry -> json.put(entry) }
        stateRepository.putState(scope, HISTORY_ENTRIES_KEY, json.toString())
    }

    private fun readHistoryEntries(scope: String): MutableList<String> {
        val entries = mutableListOf<String>()
        val raw = stateRepository.getState(scope, HISTORY_ENTRIES_KEY) ?: return entries
        try {
            val array = JSONArray(raw)
            for (index in 0 until array.length()) {
                val item = array.optString(index)
                if (!item.isNullOrBlank()) {
                    entries.add(item)
                }
            }
        } catch (_: JSONException) {
            return mutableListOf()
        }
        return entries
    }

    private fun workspaceScope(projectId: Long): String {
        return "workspace:$projectId"
    }

    private fun determineProjectType(files: List<File>): ProjectType {
        return when {
            files.any { it.isFile && it.name == "pom.xml" } -> ProjectType.MAVEN
            files.any { it.isFile && it.name == "build.gradle" } -> ProjectType.GRADLE
            else -> ProjectType.PLAIN_JAVA
        }
    }

    private fun isSameOrChildPath(candidatePath: String, rootPath: String): Boolean {
        return try {
            val candidate = File(candidatePath).canonicalPath
            val root = File(rootPath).canonicalPath
            candidate == root || candidate.startsWith(root + File.separator)
        } catch (_: IOException) {
            val normalizedCandidate = candidatePath.lowercase(Locale.ROOT)
            val normalizedRoot = rootPath.lowercase(Locale.ROOT)
            normalizedCandidate == normalizedRoot || normalizedCandidate.startsWith(normalizedRoot + File.separator)
        }
    }

    private fun remapPathAfterEntryRename(
        currentPath: String?,
        sourcePath: String,
        destinationPath: String
    ): String? {
        if (currentPath.isNullOrBlank()) {
            return currentPath
        }

        return try {
            val normalizedCurrent = File(currentPath).canonicalPath
            val normalizedSource = File(sourcePath).canonicalPath
            val normalizedDestination = File(destinationPath).canonicalPath

            when {
                normalizedCurrent == normalizedSource -> normalizedDestination
                normalizedCurrent.startsWith(normalizedSource + File.separator) -> {
                    normalizedDestination + normalizedCurrent.substring(normalizedSource.length)
                }

                else -> currentPath
            }
        } catch (_: IOException) {
            when {
                currentPath == sourcePath -> destinationPath
                currentPath.startsWith(sourcePath + File.separator) -> {
                    destinationPath + currentPath.substring(sourcePath.length)
                }

                else -> currentPath
            }
        }
    }

    private fun resolveCreateTargetDirectory(): File {
        val selectedPath = uiState.selectedEntryPath
        if (selectedPath.isNullOrBlank()) {
            return fileStorageHelper.workspaceDirectory
        }

        val selectedEntry = File(selectedPath)
        return try {
            fileStorageHelper.toWorkspaceEntryRelativePath(selectedEntry)
            when {
                !selectedEntry.exists() -> fileStorageHelper.workspaceDirectory
                selectedEntry.isDirectory -> selectedEntry
                else -> selectedEntry.parentFile ?: fileStorageHelper.workspaceDirectory
            }
        } catch (_: IOException) {
            fileStorageHelper.workspaceDirectory
        }
    }
}
