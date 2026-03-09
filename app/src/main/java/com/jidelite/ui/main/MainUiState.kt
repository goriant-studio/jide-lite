package com.jidelite.ui.main

import java.io.File

data class MainUiState(
    val files: List<File> = emptyList(),
    val selectedFilePath: String? = null,
    val editorText: String = "",
    val terminalText: String = "",
    val statusText: String = "",
    val workspacePath: String = "",
    val isDirty: Boolean = false,
    val isRunning: Boolean = false,
    val isResolvingDependencies: Boolean = false,
    val isMavenProject: Boolean = false
) {
    val selectedFileName: String?
        get() = selectedFilePath?.let { File(it).name }

    val isBusy: Boolean
        get() = isRunning || isResolvingDependencies
}
