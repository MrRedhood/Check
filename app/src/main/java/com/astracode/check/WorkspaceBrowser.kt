package com.astracode.check

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption

private const val MAX_WORKSPACE_TEXT_BYTES = 8L * 1024L * 1024L

private data class WorkspaceEntry(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val isHidden: Boolean,
)

@Composable
internal fun WorkspaceBrowserPanel(
    root: File,
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
) {
    var currentDirectory by remember(root) { mutableStateOf("") }
    var entries by remember(root) { mutableStateOf(emptyList<WorkspaceEntry>()) }
    var folderCounts by remember(root) { mutableStateOf(emptyMap<String, Int>()) }
    var refreshKey by remember(root) { mutableStateOf(0) }
    var openedFile by remember(root) { mutableStateOf<String?>(null) }
    var editorText by remember(root) { mutableStateOf("") }
    var savedText by remember(root) { mutableStateOf("") }
    var loading by remember(root) { mutableStateOf(false) }
    var message by remember(root) { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(root, currentDirectory, refreshKey) {
        try {
            val snapshot = withContext(Dispatchers.IO) {
                if (!root.exists() && !root.mkdirs()) throw IOException("Could not create workspace folder.")
                val children = listWorkspaceDirectory(root, currentDirectory)
                val counts = children.asSequence().filter { it.isDirectory }
                    .associate { it.path to workspaceDirectoryItemCount(root, it.path) }
                children to counts
            }
            entries = snapshot.first
            folderCounts = snapshot.second
            message = ""
        } catch (error: Exception) {
            message = error.message ?: "Could not load this folder."
            entries = emptyList()
            folderCounts = emptyMap()
        }
    }

    LaunchedEffect(root, openedFile) {
        val path = openedFile
        if (path == null) {
            editorText = ""
            savedText = ""
            loading = false
            return@LaunchedEffect
        }
        loading = true
        try {
            val text = withContext(Dispatchers.IO) { readWorkspaceText(root, path) }
            editorText = text
            savedText = text
        } catch (error: Exception) {
            message = error.message ?: "Could not open this file."
            openedFile = null
        } finally {
            loading = false
        }
    }

    Column(modifier = modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Workspace files", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose, modifier = Modifier.testTag("close-workspace-browser")) { Text("Close") }
        }
        if (openedFile != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { openedFile = null }, modifier = Modifier.testTag("workspace-editor-back")) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = null)
                    Text("Files")
                }
                Text(openedFile!!.substringAfterLast('/'), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (loading) {
                Text("Opening file…", modifier = Modifier.testTag("workspace-editor-loading"))
            } else {
                OutlinedTextField(
                    value = editorText,
                    onValueChange = { editorText = it },
                    modifier = Modifier.fillMaxWidth().weight(1f).testTag("workspace-editor-input"),
                    label = { Text("File contents") },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val path = openedFile ?: return@Button
                            scope.launch {
                                runCatching { withContext(Dispatchers.IO) { writeWorkspaceText(root, path, editorText) } }
                                    .onSuccess {
                                        savedText = editorText
                                        refreshKey++
                                        message = "Saved " + path.substringAfterLast('/')
                                    }
                                    .onFailure { error -> message = error.message ?: "Could not save this file." }
                            }
                        },
                        enabled = !loading && editorText != savedText,
                        modifier = Modifier.testTag("workspace-save-file"),
                    ) { Text("Save") }
                    TextButton(onClick = { openedFile = null }) { Text("Close file") }
                }
            }
            if (message.isNotBlank()) Text(message, modifier = Modifier.testTag("workspace-message"))
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (currentDirectory.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            currentDirectory = currentDirectory.substringBeforeLast('/', "")
                            refreshKey++
                        },
                        modifier = Modifier.testTag("workspace-go-up"),
                    ) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null)
                        Text("Up")
                    }
                }
                Text(
                    (currentDirectory.substringAfterLast('/').ifEmpty { "Workspace root" }) +
                        " · " + entries.size + if (entries.size == 1) " item" else " items",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("workspace-current-folder-count"),
                )
            }
            if (message.isNotBlank()) Text(message, modifier = Modifier.testTag("workspace-message"))
            if (entries.isEmpty() && message.isBlank()) Text("This folder is empty.", modifier = Modifier.testTag("workspace-empty-folder"))
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(entries, key = { it.path }) { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable {
                                if (entry.isDirectory) {
                                    currentDirectory = entry.path
                                    refreshKey++
                                } else {
                                    openedFile = entry.path
                                }
                            }
                            .padding(horizontal = 6.dp, vertical = 8.dp)
                            .testTag("workspace-entry-" + entry.name),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (entry.isDirectory) Icons.Filled.Folder else Icons.Filled.Description, contentDescription = null)
                        Spacer(Modifier.padding(4.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val detail = when {
                                entry.isDirectory -> {
                                    val count = folderCounts[entry.path] ?: 0
                                    "$count " + if (count == 1) "item" else "items"
                                }
                                entry.isHidden -> "Hidden file · " + entry.sizeBytes + " bytes"
                                else -> entry.sizeBytes.toString() + " bytes"
                            }
                            Text(
                                detail,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = if (entry.isDirectory) Modifier.testTag("workspace-folder-count-" + entry.name)
                                    else Modifier.testTag("workspace-entry-detail-" + entry.name),
                            )
                        }
                    }
                    Divider()
                }
            }
        }
    }
}

private fun listWorkspaceDirectory(root: File, relativePath: String): List<WorkspaceEntry> {
    val directory = resolveWorkspacePath(root, relativePath)
    if (!Files.isDirectory(directory.toPath(), LinkOption.NOFOLLOW_LINKS)) {
        throw IOException("Not a folder: " + relativePath.ifEmpty { "." })
    }
    val files = directory.listFiles() ?: throw IOException("Cannot read folder: $relativePath")
    return files.map { file ->
        val path = if (relativePath.isEmpty()) file.name else "$relativePath/${file.name}"
        val isDirectory = Files.isDirectory(file.toPath(), LinkOption.NOFOLLOW_LINKS)
        WorkspaceEntry(path, file.name, isDirectory, if (isDirectory) 0L else file.length().coerceAtLeast(0L), file.name.startsWith("."))
    }.sortedWith(compareBy<WorkspaceEntry> { !it.isDirectory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
}

private fun workspaceDirectoryItemCount(root: File, relativePath: String): Int = listWorkspaceDirectory(root, relativePath).size

private fun readWorkspaceText(root: File, relativePath: String): String {
    val file = resolveWorkspacePath(root, relativePath)
    if (!Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS)) throw IOException("Not a regular text file.")
    if (Files.size(file.toPath()) > MAX_WORKSPACE_TEXT_BYTES) throw IOException("Files larger than 8 MB are not supported yet.")
    return file.readBytes().toString(Charsets.UTF_8)
}

private fun writeWorkspaceText(root: File, relativePath: String, text: String) {
    val file = resolveWorkspacePath(root, relativePath)
    val bytes = text.toByteArray(Charsets.UTF_8)
    if (bytes.size.toLong() > MAX_WORKSPACE_TEXT_BYTES) throw IOException("Files larger than 8 MB are not supported yet.")
    if (file.exists() && !Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS)) throw IOException("Only regular files can be edited.")
    val temp = File(file.parentFile, ".${file.name}.astracode-tmp")
    try {
        temp.writeBytes(bytes)
        try {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    } finally {
        temp.delete()
    }
}

private fun resolveWorkspacePath(root: File, relativePath: String): File {
    if (relativePath.isNotEmpty() && (relativePath.startsWith("/") || relativePath.contains('\\') || relativePath.indexOf('\u0000') >= 0)) {
        throw SecurityException("Only workspace-relative paths are allowed.")
    }
    val segments = if (relativePath.isEmpty()) emptyList() else relativePath.split('/')
    if (segments.any { it.isEmpty() || it == "." || it == ".." }) throw SecurityException("Invalid workspace path.")
    val canonicalRoot = root.canonicalFile
    val target = if (segments.isEmpty()) canonicalRoot else File(canonicalRoot, segments.joinToString(File.separator)).canonicalFile
    if (target != canonicalRoot && !target.path.startsWith(canonicalRoot.path + File.separator)) throw SecurityException("The path escapes the workspace.")
    var current = canonicalRoot
    for (segment in segments) {
        current = File(current, segment)
        if (Files.isSymbolicLink(current.toPath())) throw SecurityException("Symbolic links are not opened from the workspace.")
    }
    return target
}
