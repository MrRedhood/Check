package com.astracode.check

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files

private data class WorkspaceTreeEntry(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
)

private data class VisibleWorkspaceTreeEntry(
    val entry: WorkspaceTreeEntry,
    val depth: Int,
    val connectorHasNext: List<Boolean>,
)

@Composable
internal fun WorkspaceTreeDialog(
    root: File,
    currentDirectory: String,
    openedFile: String?,
    onNavigateToDirectory: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var childrenByDirectory by remember(root) {
        mutableStateOf(emptyMap<String, List<WorkspaceTreeEntry>>())
    }
    var expandedDirectories by remember(root) { mutableStateOf(setOf("")) }
    var loadingDirectories by remember(root) { mutableStateOf(emptySet<String>()) }
    var loadErrors by remember(root) { mutableStateOf(emptyMap<String, String>()) }
    val scope = rememberCoroutineScope()

    fun requestChildren(directory: String) {
        if (directory in childrenByDirectory || directory in loadingDirectories) return
        loadingDirectories = loadingDirectories + directory
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val canonicalRoot = root.canonicalFile
                    val folder = if (directory.isBlank()) canonicalRoot else File(canonicalRoot, directory)
                    val canonicalFolder = folder.canonicalFile
                    if (!canonicalFolder.toPath().startsWith(canonicalRoot.toPath())) {
                        throw IOException("This folder is outside the workspace.")
                    }
                    val files = folder.listFiles()
                        ?: if (!folder.exists()) emptyArray() else throw IOException("Could not list this folder.")
                    files.map { file ->
                        val isSafeDirectory = file.isDirectory && !Files.isSymbolicLink(file.toPath())
                        WorkspaceTreeEntry(
                            path = if (directory.isBlank()) file.name else "$directory/${file.name}",
                            name = file.name,
                            isDirectory = isSafeDirectory,
                        )
                    }.sortedWith(compareBy<WorkspaceTreeEntry>({ !it.isDirectory }, { it.name.lowercase() }))
                }
            }
            result.onSuccess { entries ->
                childrenByDirectory = childrenByDirectory + (directory to entries)
                loadErrors = loadErrors - directory
            }.onFailure { error ->
                loadErrors = loadErrors + (directory to (error.message ?: "Could not load folder."))
            }
            loadingDirectories = loadingDirectories - directory
        }
    }

    fun toggleDirectory(path: String) {
        if (path in expandedDirectories) {
            expandedDirectories = expandedDirectories - path
        } else {
            expandedDirectories = expandedDirectories + path
            requestChildren(path)
        }
    }

    LaunchedEffect(root) {
        requestChildren("")
    }

    val visibleEntries = remember(childrenByDirectory, expandedDirectories) {
        buildList {
            fun appendChildren(directory: String, depth: Int, connectors: List<Boolean>, ancestry: Set<String>) {
                val entries = childrenByDirectory[directory].orEmpty()
                entries.forEachIndexed { index, entry ->
                    add(VisibleWorkspaceTreeEntry(entry, depth, connectors))
                    if (entry.isDirectory && entry.path in expandedDirectories && entry.path !in ancestry) {
                        appendChildren(
                            entry.path,
                            depth + 1,
                            connectors + (index < entries.lastIndex),
                            ancestry + entry.path,
                        )
                    }
                }
            }
            if ("" in expandedDirectories) {
                appendChildren("", 1, listOf(true), setOf(""))
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.48f)),
        ) {
            Row(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .width(320.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Workspace",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.testTag("workspace-tree-title"),
                            )
                            Text(
                                root.name.ifBlank { "Workspace root" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.testTag("workspace-tree-close")) {
                            Icon(Icons.Filled.Close, contentDescription = "Close workspace tree")
                        }
                    }
                    Divider()
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().testTag("workspace-tree-list"),
                    ) {
                        item(key = "workspace-root") {
                            WorkspaceTreeRow(
                                path = "",
                                name = "Workspace",
                                isDirectory = true,
                                depth = 0,
                                connectorHasNext = emptyList(),
                                expanded = "" in expandedDirectories,
                                selected = currentDirectory.isBlank() && openedFile == null,
                                isRoot = true,
                                onToggleExpansion = { toggleDirectory("") },
                                onSelect = { onNavigateToDirectory("") },
                            )
                        }
                        items(visibleEntries, key = { it.entry.path }) { visible ->
                            val entry = visible.entry
                            WorkspaceTreeRow(
                                path = entry.path,
                                name = entry.name,
                                isDirectory = entry.isDirectory,
                                depth = visible.depth,
                                connectorHasNext = visible.connectorHasNext,
                                expanded = entry.path in expandedDirectories,
                                selected = entry.path == currentDirectory || entry.path == openedFile,
                                onToggleExpansion = { toggleDirectory(entry.path) },
                                onSelect = {
                                    if (entry.isDirectory) onNavigateToDirectory(entry.path)
                                    else onOpenFile(entry.path)
                                },
                            )
                        }
                    }
                    val currentError = loadErrors.values.firstOrNull()
                    if (currentError != null) {
                        Text(
                            currentError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    if (loadingDirectories.isNotEmpty()) {
                        Text(
                            "Loading folders…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 6.dp).testTag("workspace-tree-loading"),
                        )
                    }
                }
                Spacer(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(onClick = onDismiss),
                )
            }
        }
    }
}

@Composable
private fun WorkspaceTreeRow(
    path: String,
    name: String,
    isDirectory: Boolean,
    depth: Int,
    connectorHasNext: List<Boolean>,
    expanded: Boolean,
    selected: Boolean,
    isRoot: Boolean = false,
    onToggleExpansion: () -> Unit,
    onSelect: () -> Unit,
) {
    val selectedBackground = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    } else {
        Color.Transparent
    }
    val connectorColor = MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .background(selectedBackground)
            .clickable(onClick = onSelect)
            .testTag(
                if (isRoot) "workspace-tree-root"
                else "workspace-tree-${if (isDirectory) "folder" else "file"}-${path.replace("/", "-")}",
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (depth > 0) {
            Canvas(
                modifier = Modifier
                    .width((depth * 16).dp)
                    .height(42.dp),
            ) {
                val step = 16.dp.toPx()
                val centerY = size.height / 2f
                val stroke = 1.dp.toPx()
                connectorHasNext.forEachIndexed { index, hasNext ->
                    val x = (index + 0.5f) * step
                    val endY = if (hasNext || index < connectorHasNext.lastIndex) size.height else centerY
                    drawLine(
                        color = connectorColor,
                        start = Offset(x, 0f),
                        end = Offset(x, endY),
                        strokeWidth = stroke,
                    )
                }
                drawLine(
                    color = connectorColor,
                    start = Offset((depth - 0.5f) * step, centerY),
                    end = Offset(depth * step, centerY),
                    strokeWidth = stroke,
                )
            }
        }
        if (isDirectory) {
            IconButton(
                onClick = onToggleExpansion,
                modifier = Modifier.size(32.dp).testTag(
                    "workspace-tree-expand-${if (isRoot) "root" else path.replace("/", "-")}",
                ),
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight,
                    contentDescription = if (expanded) "Collapse $name" else "Expand $name",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Spacer(Modifier.width(32.dp))
        }
        Icon(
            imageVector = when {
                !isDirectory -> Icons.Filled.Description
                expanded -> Icons.Filled.FolderOpen
                else -> Icons.Filled.Folder
            },
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 9.dp, end = 6.dp).weight(1f),
        )
    }
}
