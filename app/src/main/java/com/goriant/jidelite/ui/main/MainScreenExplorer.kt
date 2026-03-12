package com.goriant.jidelite.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goriant.jidelite.R
import com.goriant.jidelite.ui.theme.JIdeLiteColors
import java.io.File

@Composable
internal fun ExplorerPane(
    modifier: Modifier,
    collapsed: Boolean,
    workspacePath: String,
    files: List<File>,
    selectedEntryPath: String?,
    isBusy: Boolean,
    onNewFile: () -> Unit,
    onNewFolder: () -> Unit,
    onToggleCollapse: () -> Unit,
    onOpenFile: (File) -> Unit,
    onRenameEntry: (File, String) -> Unit,
    onDeleteEntry: (File) -> Unit,
    onMoveEntry: (File, File) -> Unit
) {
    val themeColors = JIdeLiteColors
    var showNewMenu by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<File?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<File?>(null) }
    val treeRoots = remember(workspacePath, files) { buildExplorerTree(workspacePath, files) }
    val directoryPaths = remember(treeRoots) { collectDirectoryPaths(treeRoots) }
    var hasInitializedExpandedState by remember(workspacePath) { mutableStateOf(false) }
    var expandedDirectoryPaths by remember(workspacePath) { mutableStateOf(emptySet<String>()) }

    LaunchedEffect(directoryPaths) {
        expandedDirectoryPaths = expandedDirectoryPaths.intersect(directoryPaths)
        if (!hasInitializedExpandedState) {
            expandedDirectoryPaths = directoryPaths
            hasInitializedExpandedState = true
        }
    }

    val visibleNodes = remember(treeRoots, expandedDirectoryPaths) {
        flattenExplorerTree(treeRoots, expandedDirectoryPaths)
    }
    val nodeByPath = remember(visibleNodes) { visibleNodes.associate { it.node.path to it.node } }
    val rowBounds = remember { mutableStateMapOf<String, Rect>() }
    var draggingEntryPath by remember { mutableStateOf<String?>(null) }
    var dropTargetDirectoryPath by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(visibleNodes) {
        val visiblePaths = visibleNodes.mapTo(linkedSetOf()) { it.node.path }
        val stalePaths = rowBounds.keys.filterNot { visiblePaths.contains(it) }
        for (path in stalePaths) {
            rowBounds.remove(path)
        }
        if (draggingEntryPath != null && !visiblePaths.contains(draggingEntryPath)) {
            draggingEntryPath = null
            dropTargetDirectoryPath = null
        } else if (dropTargetDirectoryPath != null && !visiblePaths.contains(dropTargetDirectoryPath)) {
            dropTargetDirectoryPath = null
        }
    }

    Box(
        modifier = modifier.background(themeColors.explorerSurface)
    ) {
        if (collapsed) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ChromeActionButton(
                    label = "▶",
                    enabled = true,
                    highlighted = false,
                    compact = true,
                    onClick = onToggleCollapse
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "EX",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    letterSpacing = 1.2.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Box {
                    ChromeActionButton(
                        label = "+",
                        enabled = !isBusy,
                        highlighted = false,
                        compact = true,
                        testTag = "explorer-new",
                        onClick = { showNewMenu = true }
                    )
                    DropdownMenu(
                        expanded = showNewMenu,
                        onDismissRequest = { showNewMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(text = "Folder") },
                            onClick = {
                                showNewMenu = false
                                onNewFolder()
                            },
                            enabled = !isBusy
                        )
                        DropdownMenuItem(
                            text = { Text(text = "File") },
                            onClick = {
                                showNewMenu = false
                                onNewFile()
                            },
                            enabled = !isBusy
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                if (files.isEmpty()) {
                    Spacer(modifier = Modifier.weight(1f))
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        items(files, key = { workspaceRelativePath(workspacePath, it) }) { file ->
                            CollapsedExplorerFilePill(
                                label = if (file.isDirectory) "DIR" else compactFileLabel(file.name),
                                selected = file.absolutePath == selectedEntryPath,
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
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.explorer_header),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.4.sp,
                        modifier = Modifier.weight(1f)
                    )

                    Box {
                        ChromeActionButton(
                            label = "➕",
                            enabled = !isBusy,
                            highlighted = false,
                            compact = true,
                            testTag = "explorer-new",
                            onClick = { showNewMenu = true }
                        )
                        DropdownMenu(
                            expanded = showNewMenu,
                            onDismissRequest = { showNewMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(text = "Folder") },
                                onClick = {
                                    showNewMenu = false
                                    onNewFolder()
                                },
                                enabled = !isBusy
                            )
                            DropdownMenuItem(
                                text = { Text(text = "File") },
                                onClick = {
                                    showNewMenu = false
                                    onNewFile()
                                },
                                enabled = !isBusy
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    ChromeActionButton(
                        label = "◀",
                        enabled = true,
                        highlighted = false,
                        compact = true,
                        onClick = onToggleCollapse
                    )
                }

                Text(
                    text = "\u25BE ${stringResource(R.string.workspace_label)}",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                if (files.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.explorer_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        items(visibleNodes, key = { it.node.path }) { visibleNode ->
                            ExplorerTreeRow(
                                node = visibleNode.node,
                                depth = visibleNode.depth,
                                selected = visibleNode.node.file.absolutePath == selectedEntryPath,
                                expanded = visibleNode.node.isDirectory &&
                                    expandedDirectoryPaths.contains(visibleNode.node.path),
                                isDragging = draggingEntryPath == visibleNode.node.path,
                                isDropTarget = dropTargetDirectoryPath == visibleNode.node.path,
                                isBusy = isBusy,
                                onToggleExpand = { path ->
                                    expandedDirectoryPaths = if (expandedDirectoryPaths.contains(path)) {
                                        expandedDirectoryPaths - path
                                    } else {
                                        expandedDirectoryPaths + path
                                    }
                                },
                                onSelect = onOpenFile,
                                onRename = { file ->
                                    renameTarget = file
                                    renameValue = file.name
                                },
                                onDelete = { file ->
                                    deleteTarget = file
                                },
                                onPositioned = { path, bounds ->
                                    rowBounds[path] = bounds
                                },
                                onDragStart = { path ->
                                    draggingEntryPath = path
                                    dropTargetDirectoryPath = null
                                },
                                onDragMove = { path, pointerInWindow ->
                                    if (draggingEntryPath == path) {
                                        dropTargetDirectoryPath = findHoveredDirectoryPath(
                                            pointerInWindow = pointerInWindow,
                                            rowBounds = rowBounds,
                                            nodeByPath = nodeByPath,
                                            draggedPath = path
                                        )
                                    }
                                },
                                onDragEnd = { path ->
                                    val draggedPath = draggingEntryPath
                                    val destinationPath = dropTargetDirectoryPath
                                    val sourceNode = draggedPath?.let { nodeByPath[it] }
                                    val destinationNode = destinationPath?.let { nodeByPath[it] }
                                    if (draggedPath == path &&
                                        sourceNode != null &&
                                        destinationNode != null &&
                                        destinationNode.isDirectory &&
                                        destinationNode.path != sourceNode.path
                                    ) {
                                        onMoveEntry(sourceNode.file, destinationNode.file)
                                    }
                                    draggingEntryPath = null
                                    dropTargetDirectoryPath = null
                                },
                                onDragCancel = { path ->
                                    if (draggingEntryPath == path) {
                                        draggingEntryPath = null
                                        dropTargetDirectoryPath = null
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        val activeRenameTarget = renameTarget
        if (activeRenameTarget != null) {
            RenameEntryDialog(
                value = renameValue,
                onValueChange = { renameValue = it },
                onDismiss = { renameTarget = null },
                onConfirm = {
                    val requestedName = renameValue.trim()
                    if (requestedName.isNotEmpty()) {
                        onRenameEntry(activeRenameTarget, requestedName)
                    }
                    renameTarget = null
                },
                isBusy = isBusy
            )
        }

        val activeDeleteTarget = deleteTarget
        if (activeDeleteTarget != null) {
            DeleteEntryDialog(
                entry = activeDeleteTarget,
                onDismiss = { deleteTarget = null },
                onConfirm = {
                    onDeleteEntry(activeDeleteTarget)
                    deleteTarget = null
                },
                isBusy = isBusy
            )
        }
    }
}

@Composable
private fun CollapsedExplorerFilePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val themeColors = JIdeLiteColors
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (selected) themeColors.selectedSurface else Color.Transparent)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(7.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun ExplorerTreeRow(
    node: ExplorerNode,
    depth: Int,
    selected: Boolean,
    expanded: Boolean,
    isDragging: Boolean,
    isDropTarget: Boolean,
    isBusy: Boolean,
    onToggleExpand: (String) -> Unit,
    onSelect: (File) -> Unit,
    onRename: (File) -> Unit,
    onDelete: (File) -> Unit,
    onPositioned: (String, Rect) -> Unit,
    onDragStart: (String) -> Unit,
    onDragMove: (String, Offset) -> Unit,
    onDragEnd: (String) -> Unit,
    onDragCancel: (String) -> Unit
) {
    val themeColors = JIdeLiteColors
    var rowBounds by remember(node.path) { mutableStateOf<Rect?>(null) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                when {
                    selected -> themeColors.selectedSurface
                    isDropTarget -> MaterialTheme.colorScheme.surfaceContainer
                    else -> Color.Transparent
                }
            )
            .alpha(if (isDragging) 0.55f else 1f)
            .onGloballyPositioned { coordinates ->
                val origin = coordinates.positionInWindow()
                val bounds = Rect(
                    left = origin.x,
                    top = origin.y,
                    right = origin.x + coordinates.size.width,
                    bottom = origin.y + coordinates.size.height
                )
                rowBounds = bounds
                onPositioned(node.path, bounds)
            }
            .pointerInput(node.path, isBusy, node.isDirectory) {
                if (isBusy || node.isDirectory) {
                    return@pointerInput
                }
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        onDragStart(node.path)
                        val bounds = rowBounds
                        if (bounds != null) {
                            onDragMove(
                                node.path,
                                Offset(
                                    x = bounds.left + it.x,
                                    y = bounds.top + it.y
                                )
                            )
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val bounds = rowBounds
                        if (bounds != null) {
                            onDragMove(
                                node.path,
                                Offset(
                                    x = bounds.left + change.position.x,
                                    y = bounds.top + change.position.y
                                )
                            )
                        }
                    },
                    onDragEnd = {
                        onDragEnd(node.path)
                    },
                    onDragCancel = {
                        onDragCancel(node.path)
                    }
                )
            }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable { onSelect(node.file) }
                .padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width((depth * 12).dp))

            Text(
                text = when {
                    !node.isDirectory -> " "
                    expanded -> "\u25BE"
                    else -> "\u25B8"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier
                    .width(12.dp)
                    .clickable(enabled = node.isDirectory) {
                        if (node.isDirectory) {
                            onToggleExpand(node.path)
                        }
                    }
            )

            Spacer(modifier = Modifier.width(6.dp))

            Box(
                modifier = Modifier
                    .width(7.dp)
                    .height(14.dp)
                    .background(
                        when {
                            selected -> MaterialTheme.colorScheme.primary
                            node.isDirectory -> MaterialTheme.colorScheme.secondary
                            else -> themeColors.dotMint
                        }
                    )
            )

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = if (node.isDirectory) "${node.name}/" else node.name,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = "\u270E",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier
                .alpha(if (isBusy) 0.45f else 1f)
                .clickable(enabled = !isBusy) { onRename(node.file) }
                .padding(horizontal = 4.dp, vertical = 2.dp)
        )

        Text(
            text = "\u2715",
            color = themeColors.dotPink,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier
                .alpha(if (isBusy) 0.45f else 1f)
                .clickable(enabled = !isBusy) { onDelete(node.file) }
                .padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun RenameEntryDialog(
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    isBusy: Boolean
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Rename",
                fontFamily = FontFamily.Monospace
            )
        },
        text = {
            CompactOutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = "Name"
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isBusy && value.trim().isNotEmpty()
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isBusy
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun DeleteEntryDialog(
    entry: File,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    isBusy: Boolean
) {
    val entryLabel = entry.name.ifBlank { entry.absolutePath }
    val entryType = if (entry.isDirectory) "folder" else "file"
    val message = if (entry.isDirectory) {
        "Delete \"$entryLabel\" and all contents? This action cannot be undone."
    } else {
        "Delete \"$entryLabel\"? This action cannot be undone."
    }

    AlertDialog(
        modifier = Modifier.testTag("delete-entry-dialog"),
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Delete $entryType",
                fontFamily = FontFamily.Monospace
            )
        },
        text = {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isBusy,
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                    disabledContentColor = MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
                )
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isBusy
            ) {
                Text("Cancel")
            }
        }
    )
}
