package com.goriant.jidelite.ui.main

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import java.io.File

internal data class ExplorerNode(
    val path: String,
    val name: String,
    val file: File,
    val isDirectory: Boolean,
    val children: MutableList<ExplorerNode> = mutableListOf()
)

internal data class VisibleExplorerNode(
    val node: ExplorerNode,
    val depth: Int
)

internal fun buildExplorerTree(workspacePath: String, files: List<File>): List<ExplorerNode> {
    val sortedFiles = files.sortedWith(
        compareBy<File>({ !it.isDirectory }, { workspaceRelativePath(workspacePath, it).lowercase() })
    )
    val nodeByPath = LinkedHashMap<String, ExplorerNode>()
    for (file in sortedFiles) {
        val relativePath = workspaceRelativePath(workspacePath, file)
        if (relativePath.isBlank()) {
            continue
        }
        nodeByPath[relativePath] = ExplorerNode(
            path = relativePath,
            name = relativePath.substringAfterLast('/'),
            file = file,
            isDirectory = file.isDirectory
        )
    }

    val roots = mutableListOf<ExplorerNode>()
    for ((path, node) in nodeByPath) {
        val parentPath = path.substringBeforeLast('/', "")
        val parentNode = nodeByPath[parentPath]
        if (parentNode != null && parentNode.isDirectory) {
            parentNode.children.add(node)
        } else {
            roots.add(node)
        }
    }

    sortExplorerNodes(roots)
    return roots
}

internal fun sortExplorerNodes(nodes: MutableList<ExplorerNode>) {
    nodes.sortWith(compareBy<ExplorerNode>({ !it.isDirectory }, { it.name.lowercase() }))
    for (node in nodes) {
        if (node.children.isNotEmpty()) {
            sortExplorerNodes(node.children)
        }
    }
}

internal fun collectDirectoryPaths(nodes: List<ExplorerNode>): Set<String> {
    val directoryPaths = linkedSetOf<String>()

    fun visit(node: ExplorerNode) {
        if (node.isDirectory) {
            directoryPaths.add(node.path)
        }
        for (child in node.children) {
            visit(child)
        }
    }

    for (node in nodes) {
        visit(node)
    }
    return directoryPaths
}

internal fun flattenExplorerTree(
    roots: List<ExplorerNode>,
    expandedDirectoryPaths: Set<String>
): List<VisibleExplorerNode> {
    val output = mutableListOf<VisibleExplorerNode>()
    for (root in roots) {
        appendVisibleNode(output, root, 0, expandedDirectoryPaths)
    }
    return output
}

internal fun appendVisibleNode(
    output: MutableList<VisibleExplorerNode>,
    node: ExplorerNode,
    depth: Int,
    expandedDirectoryPaths: Set<String>
) {
    output.add(VisibleExplorerNode(node = node, depth = depth))
    if (node.isDirectory && expandedDirectoryPaths.contains(node.path)) {
        for (child in node.children) {
            appendVisibleNode(output, child, depth + 1, expandedDirectoryPaths)
        }
    }
}

internal fun findHoveredDirectoryPath(
    pointerInWindow: Offset,
    rowBounds: Map<String, Rect>,
    nodeByPath: Map<String, ExplorerNode>,
    draggedPath: String
): String? {
    for ((path, bounds) in rowBounds) {
        val node = nodeByPath[path] ?: continue
        if (!node.isDirectory) {
            continue
        }
        if (path == draggedPath || path.startsWith("$draggedPath/")) {
            continue
        }
        if (bounds.contains(pointerInWindow)) {
            return path
        }
    }
    return null
}

internal fun workspaceRelativePath(workspacePath: String, file: File): String {
    if (workspacePath.isBlank()) {
        return file.name
    }

    return try {
        file.relativeTo(File(workspacePath)).invariantSeparatorsPath
    } catch (_: IllegalArgumentException) {
        file.name
    }
}

internal fun compactFileLabel(fileName: String): String {
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
