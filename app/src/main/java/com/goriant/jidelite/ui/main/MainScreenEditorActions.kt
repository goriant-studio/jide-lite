package com.goriant.jidelite.ui.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.EditText
import com.goriant.jidelite.editor.EditorInteractionHelper
import com.goriant.jidelite.editor.JavaSyntaxHighlighter
import kotlin.math.max
import kotlin.math.min

private const val EDITOR_INDENT = "    "

internal class EditorBridgeState {
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

internal data class EditorActionContext(
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

internal fun activeEditor(
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

internal fun copySelectionFromEditor(
    editorBridge: EditorBridgeState,
    actionContext: EditorActionContext
) {
    activeEditor(editorBridge, actionContext)?.let { editText ->
        copySelectionFromEditor(editText, actionContext)
    }
}

internal fun copySelectionFromEditor(
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

internal fun cutSelectionFromEditor(
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

internal fun pasteIntoEditor(
    editorBridge: EditorBridgeState,
    actionContext: EditorActionContext
) {
    activeEditor(editorBridge, actionContext)?.let { editText ->
        pasteIntoEditor(editText, actionContext)
    }
}

internal fun pasteIntoEditor(
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

internal fun selectAllInEditor(
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

internal fun undoInEditor(
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

internal fun redoInEditor(
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

internal fun runEditorCommandIfAllowed(
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

internal fun handleEditorShortcut(
    editText: EditText,
    keyCode: Int,
    event: android.view.KeyEvent,
    editorBridge: EditorBridgeState,
    actionContext: EditorActionContext,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onShowShortcuts: () -> Unit,
    onShowOnboarding: () -> Unit,
    onOpenFind: () -> Unit,
    onOpenFindReplace: () -> Unit
): Boolean {
    if (event.action != android.view.KeyEvent.ACTION_DOWN) {
        return false
    }

    return when {
        keyCode == android.view.KeyEvent.KEYCODE_TAB -> {
            insertIndentUnit(editText)
            true
        }

        keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
            keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> {
            insertSmartNewline(editText)
            true
        }

        event.isCtrlPressed && event.isShiftPressed &&
            keyCode == android.view.KeyEvent.KEYCODE_SLASH -> {
            onShowOnboarding()
            true
        }

        event.isCtrlPressed && keyCode == android.view.KeyEvent.KEYCODE_SLASH -> {
            onShowShortcuts()
            true
        }

        event.isCtrlPressed && event.isShiftPressed &&
            keyCode == android.view.KeyEvent.KEYCODE_N -> {
            runEditorCommandIfAllowed(actionContext) {
                actionContext.onNewFolder()
            }
            true
        }

        event.isCtrlPressed && keyCode == android.view.KeyEvent.KEYCODE_N -> {
            runEditorCommandIfAllowed(actionContext) {
                actionContext.onNewFile()
            }
            true
        }

        event.isCtrlPressed && keyCode == android.view.KeyEvent.KEYCODE_S -> {
            runEditorCommandIfAllowed(actionContext) {
                actionContext.onSave()
            }
            true
        }

        event.isCtrlPressed && event.isShiftPressed &&
            keyCode == android.view.KeyEvent.KEYCODE_Z -> {
            onRedo()
            true
        }

        event.isCtrlPressed && keyCode == android.view.KeyEvent.KEYCODE_Z -> {
            onUndo()
            true
        }

        event.isCtrlPressed && keyCode == android.view.KeyEvent.KEYCODE_Y -> {
            onRedo()
            true
        }

        event.isCtrlPressed && keyCode == android.view.KeyEvent.KEYCODE_R -> {
            runEditorCommandIfAllowed(actionContext) {
                actionContext.onRun()
            }
            true
        }

        event.isCtrlPressed && !event.isShiftPressed && keyCode == android.view.KeyEvent.KEYCODE_F -> {
            onOpenFind()
            true
        }

        event.isCtrlPressed && keyCode == android.view.KeyEvent.KEYCODE_H -> {
            onOpenFindReplace()
            true
        }

        event.isCtrlPressed && event.isShiftPressed &&
            keyCode == android.view.KeyEvent.KEYCODE_F -> {
            runEditorCommandIfAllowed(actionContext) {
                actionContext.onFormat()
            }
            true
        }

        event.isCtrlPressed && event.isShiftPressed &&
            keyCode == android.view.KeyEvent.KEYCODE_D -> {
            runEditorCommandIfAllowed(actionContext, requiresMavenProject = true) {
                actionContext.onResolveDependencies()
            }
            true
        }

        event.isCtrlPressed && keyCode == android.view.KeyEvent.KEYCODE_A -> {
            selectAllInEditor(editorBridge, actionContext)
            true
        }

        event.isCtrlPressed && keyCode == android.view.KeyEvent.KEYCODE_C -> {
            copySelectionFromEditor(editText, actionContext)
            true
        }

        event.isCtrlPressed && keyCode == android.view.KeyEvent.KEYCODE_X -> {
            cutSelectionFromEditor(editText, actionContext)
            true
        }

        event.isCtrlPressed && keyCode == android.view.KeyEvent.KEYCODE_V -> {
            pasteIntoEditor(editText, actionContext)
            true
        }

        event.isShiftPressed && keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
            moveSelectionLeft(editText, true)
            true
        }

        event.isShiftPressed && keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
            moveSelectionRight(editText, true)
            true
        }

        event.isShiftPressed && keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP -> {
            moveSelectionUp(editText, true)
            true
        }

        event.isShiftPressed && keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
            moveSelectionDown(editText, true)
            true
        }

        else -> false
    }
}

internal fun moveSelectionLeft(editText: EditText, isShiftPressed: Boolean) {
    val editable = editText.text ?: return
    val end = android.text.Selection.getSelectionEnd(editable)
    val newEnd = (end - 1).coerceAtLeast(0)
    if (isShiftPressed) {
        android.text.Selection.extendSelection(editable, newEnd)
    } else {
        android.text.Selection.setSelection(editable, newEnd)
    }
}

internal fun moveSelectionRight(editText: EditText, isShiftPressed: Boolean) {
    val editable = editText.text ?: return
    val end = android.text.Selection.getSelectionEnd(editable)
    val newEnd = (end + 1).coerceAtMost(editable.length)
    if (isShiftPressed) {
        android.text.Selection.extendSelection(editable, newEnd)
    } else {
        android.text.Selection.setSelection(editable, newEnd)
    }
}

internal fun moveSelectionUp(editText: EditText, isShiftPressed: Boolean) {
    val layout = editText.layout ?: return
    val editable = editText.text ?: return
    val end = android.text.Selection.getSelectionEnd(editable)
    val line = layout.getLineForOffset(end)
    if (line > 0) {
        val x = layout.getPrimaryHorizontal(end)
        val newEnd = layout.getOffsetForHorizontal(line - 1, x)
        if (isShiftPressed) {
            android.text.Selection.extendSelection(editable, newEnd)
        } else {
            android.text.Selection.setSelection(editable, newEnd)
        }
    } else if (!isShiftPressed) {
        android.text.Selection.setSelection(editable, 0)
    } else {
        android.text.Selection.extendSelection(editable, 0)
    }
}

internal fun moveSelectionDown(editText: EditText, isShiftPressed: Boolean) {
    val layout = editText.layout ?: return
    val editable = editText.text ?: return
    val end = android.text.Selection.getSelectionEnd(editable)
    val line = layout.getLineForOffset(end)
    if (line < layout.lineCount - 1) {
        val x = layout.getPrimaryHorizontal(end)
        val newEnd = layout.getOffsetForHorizontal(line + 1, x)
        if (isShiftPressed) {
            android.text.Selection.extendSelection(editable, newEnd)
        } else {
            android.text.Selection.setSelection(editable, newEnd)
        }
    } else if (!isShiftPressed) {
        android.text.Selection.setSelection(editable, editable.length)
    } else {
        android.text.Selection.extendSelection(editable, editable.length)
    }
}

internal fun insertIndentUnit(editText: EditText) {
    val editable = editText.text ?: return
    val selectionStart = editText.selectionStart.coerceAtLeast(0)
    val selectionEnd = editText.selectionEnd.coerceAtLeast(0)
    val start = min(selectionStart, selectionEnd)
    val end = max(selectionStart, selectionEnd)
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

internal fun insertSmartNewline(editText: EditText) {
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
