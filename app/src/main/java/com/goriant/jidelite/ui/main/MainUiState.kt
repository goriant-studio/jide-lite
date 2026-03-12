package com.goriant.jidelite.ui.main

import com.goriant.jidelite.ui.theme.ThemeMode
import java.io.File

data class ImportSuggestion(
    val qualifiedName: String,
    val importStatement: String,
    val simpleName: String
)

data class EditorTab(
    val filePath: String,
    val fileName: String,
    val isDirty: Boolean = false,
    val editorText: String = "",
    val cursorStart: Int = 0,
    val cursorEnd: Int = 0,
    val scrollX: Int = 0,
    val scrollY: Int = 0
)

data class MainUiState(
    val files: List<File> = emptyList(),
    val selectedFilePath: String? = null,
    val selectedEntryPath: String? = null,
    val editorText: String = "",
    val editorDiagnostic: EditorDiagnostic? = null,
    val terminalText: String = "",
    val statusText: String = "",
    val workspacePath: String = "",
    val isDirty: Boolean = false,
    val isRunning: Boolean = false,
    val isResolvingDependencies: Boolean = false,
    val isMavenProject: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val isOnboardingVisible: Boolean = false,
    val isWordWrapEnabled: Boolean = false,
    val pendingImportSuggestion: ImportSuggestion? = null,
    val openTabs: List<EditorTab> = emptyList(),
    val activeTabIndex: Int = -1
) {
    val selectedFileName: String?
        get() = selectedFilePath?.let { File(it).name }

    val isBusy: Boolean
        get() = isRunning || isResolvingDependencies
}
