package com.jidelite.ui.main

import java.io.File

data class MainUiState(
    val files: List<File> = emptyList(),
    val selectedFileName: String? = null,
    val editorText: String = "",
    val terminalText: String = "",
    val statusText: String = "",
    val workspacePath: String = "",
    val isDirty: Boolean = false,
    val isRunning: Boolean = false
)
