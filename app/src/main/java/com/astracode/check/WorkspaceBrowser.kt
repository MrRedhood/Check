package com.astracode.check

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalContext
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

@OptIn(ExperimentalFoundationApi::class)
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
    var actionEntry by remember(root) { mutableStateOf<WorkspaceEntry?>(null) }
    var pendingDeleteEntry by remember(root) { mutableStateOf<WorkspaceEntry?>(null) }
    var operationEntry by remember(root) { mutableStateOf<WorkspaceEntry?>(null) }
    var operationKind by remember(root) { mutableStateOf<String?>(null) }
    var operationInput by remember(root) { mutableStateOf("") }
    var syncEntry by remember(root) { mutableStateOf<WorkspaceEntry?>(null) }
    val context = LocalContext.current
    val externalRoot = remember(root) { File(root.parentFile, "external-workspace") }
    var syncedPaths by remember(root) { mutableStateOf(loadSyncedPaths(context, root)) }
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
                                        syncLinkedWorkspacePaths(root, externalRoot, syncedPaths, path)
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
                            .combinedClickable(
                                onClick = {
                                    if (entry.isDirectory) {
                                        currentDirectory = entry.path
                                        refreshKey++
                                    } else openedFile = entry.path
                                },
                                onLongClick = { actionEntry = entry },
                            )
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

    actionEntry?.let { entry ->
        AlertDialog(onDismissRequest = { actionEntry = null },
            title = { Text(entry.name + " options") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TextButton(onClick = { syncEntry = entry; actionEntry = null },
                    modifier = Modifier.fillMaxWidth().testTag("workspace-action-sync")) { Text("Sync") }
                TextButton(onClick = { operationEntry = entry; operationKind = "rename"; operationInput = entry.name; actionEntry = null },
                    modifier = Modifier.fillMaxWidth().testTag("workspace-action-rename")) { Text("Rename") }
                TextButton(onClick = { operationEntry = entry; operationKind = "copy"; operationInput = "copy-" + entry.name; actionEntry = null },
                    modifier = Modifier.fillMaxWidth().testTag("workspace-action-copy")) { Text("Copy") }
                TextButton(onClick = { operationEntry = entry; operationKind = "move"; operationInput = entry.path; actionEntry = null },
                    modifier = Modifier.fillMaxWidth().testTag("workspace-action-move")) { Text("Move") }
                TextButton(onClick = { pendingDeleteEntry = entry; actionEntry = null },
                    modifier = Modifier.fillMaxWidth().testTag("workspace-action-delete")) { Text("Delete") }
            } },
            confirmButton = { TextButton(onClick = { actionEntry = null }) { Text("Cancel") } })
    }

    pendingDeleteEntry?.let { entry ->
        AlertDialog(onDismissRequest = { pendingDeleteEntry = null },
            title = { Text("Delete " + entry.name + "?") },
            text = { Text("Delete this item and everything inside its folder, if it is a folder.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val result = runCatching {
                            withContext(Dispatchers.IO) {
                                val target = resolveWorkspacePath(root, entry.path)
                                if (!target.deleteRecursively()) throw IOException("Could not delete " + entry.name)
                                updateSyncedPathsAfterDelete(context, root, externalRoot, syncedPaths, entry.path)
                            }
                        }
                        result.onSuccess { updatedLinks ->
                            syncedPaths = updatedLinks
                            pendingDeleteEntry = null
                            refreshKey++
                            message = "Deleted " + entry.name
                        }.onFailure { error ->
                            pendingDeleteEntry = null
                            message = error.message ?: "Could not delete item."
                        }
                    }
                }, modifier = Modifier.testTag("confirm-workspace-delete")) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteEntry = null }) { Text("Cancel") } })
    }

    operationEntry?.let { entry ->
        val kind = operationKind.orEmpty()
        AlertDialog(onDismissRequest = { operationEntry = null; operationKind = null },
            title = { Text(when (kind) {
                "rename" -> "Rename " + entry.name
                "copy" -> "Copy " + entry.name
                else -> "Move " + entry.name
            }) },
            text = { Column {
                Text(when (kind) {
                    "rename" -> "Enter a new name for this item."
                    "copy" -> "Enter the new name for the copy in this folder."
                    else -> "Enter the destination path relative to the workspace root."
                })
                OutlinedTextField(value = operationInput, onValueChange = { operationInput = it },
                    label = { Text(if (kind == "move") "Destination path" else "Name") },
                    modifier = Modifier.fillMaxWidth().testTag("workspace-operation-input"))
            } },
            confirmButton = {
                TextButton(onClick = {
                    val raw = operationInput.trim()
                    scope.launch {
                        val result = runCatching {
                            withContext(Dispatchers.IO) {
                                val source = resolveWorkspacePath(root, entry.path)
                                val destinationPath = if (kind == "move") raw else {
                                    if (!isSimpleWorkspaceName(raw)) throw IOException("Enter a valid name without path separators.")
                                    val parent = entry.path.substringBeforeLast('/', "")
                                    if (parent.isEmpty()) raw else "$parent/$raw"
                                }
                                val destination = resolveWorkspacePath(root, destinationPath)
                                if (destination == source) throw IOException("The source and destination are the same.")
                                if (entry.isDirectory && destination.path.startsWith(source.path + File.separator))
                                    throw IOException("A folder cannot be copied or moved into itself.")
                                if (destination.exists()) throw IOException("The destination already exists.")
                                destination.parentFile?.let { parent ->
                                    if (!parent.exists() && !parent.mkdirs()) throw IOException("Could not create the destination folder.")
                                    if (!parent.isDirectory) throw IOException("The destination parent is not a folder.")
                                }
                                when (kind) {
                                    "rename", "move" -> {
                                        try { Files.move(source.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE) }
                                        catch (_: java.nio.file.AtomicMoveNotSupportedException) { Files.move(source.toPath(), destination.toPath()) }
                                        updateSyncedPathsAfterMove(context, root, externalRoot, syncedPaths, entry.path, destinationPath).also { syncedPaths = it }
                                    }
                                    "copy" -> copyWorkspaceEntry(root, entry.path, destinationPath)
                                    else -> throw IOException("Unknown file action.")
                                }
                            }
                        }
                        result.onSuccess {
                            operationEntry = null; operationKind = null; refreshKey++
                            message = when (kind) { "rename" -> "Renamed " + entry.name; "copy" -> "Copied " + entry.name; else -> "Moved " + entry.name }
                        }.onFailure { error -> message = error.message ?: "Could not complete the file action." }
                    }
                }, modifier = Modifier.testTag("confirm-workspace-operation")) {
                    Text(when (kind) { "rename" -> "Rename"; "copy" -> "Copy"; else -> "Move" })
                }
            },
            dismissButton = { TextButton(onClick = { operationEntry = null; operationKind = null }) { Text("Cancel") } })
    }

    syncEntry?.let { entry ->
        AlertDialog(onDismissRequest = { syncEntry = null },
            title = { Text("Sync " + entry.name) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Choose which copy should replace the other. The link is remembered for later app-side edits.")
                Button(onClick = {
                    scope.launch {
                        val result = runCatching {
                            withContext(Dispatchers.IO) {
                                mirrorWorkspaceEntry(root, externalRoot, entry.path, entry.path)
                                saveSyncedPaths(context, root, syncedPaths + entry.path)
                            }
                        }
                        result.onSuccess { syncedPaths = it; syncEntry = null; refreshKey++; message = "Synced app copy to external storage." }
                            .onFailure { error -> message = error.message ?: "Could not sync to external storage." }
                    }
                }, modifier = Modifier.fillMaxWidth().testTag("sync-app-to-external")) { Text("App → external storage") }
                Button(onClick = {
                    scope.launch {
                        val result = runCatching {
                            withContext(Dispatchers.IO) {
                                mirrorWorkspaceEntry(externalRoot, root, entry.path, entry.path)
                                saveSyncedPaths(context, root, syncedPaths + entry.path)
                            }
                        }
                        result.onSuccess { syncedPaths = it; syncEntry = null; refreshKey++; message = "Synced external storage to app copy." }
                            .onFailure { error -> message = error.message ?: "Could not sync from external storage." }
                    }
                }, modifier = Modifier.fillMaxWidth().testTag("sync-external-to-app")) { Text("External storage → app") }
            } },
            confirmButton = { TextButton(onClick = { syncEntry = null }) { Text("Cancel") } })
    }

}


private const val CHECK_SYNC_PREFS = "check_workspace_sync"
private fun syncPreferencesKey(root: File): String = "links:" + root.canonicalPath
private fun loadSyncedPaths(context: android.content.Context, root: File): Set<String> {
    val json = context.getSharedPreferences(CHECK_SYNC_PREFS, android.content.Context.MODE_PRIVATE)
        .getString(syncPreferencesKey(root), null) ?: return emptySet()
    return runCatching {
        val array = org.json.JSONArray(json)
        (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }.toSet()
    }.getOrDefault(emptySet())
}
private fun saveSyncedPaths(context: android.content.Context, root: File, paths: Set<String>): Set<String> {
    val normalized = paths.filter(String::isNotBlank).toSet()
    val array = org.json.JSONArray()
    normalized.sorted().forEach(array::put)
    context.getSharedPreferences(CHECK_SYNC_PREFS, android.content.Context.MODE_PRIVATE)
        .edit().putString(syncPreferencesKey(root), array.toString()).apply()
    return normalized
}
private fun isSimpleWorkspaceName(value: String): Boolean =
    value.isNotBlank() && value != "." && value != ".." && !value.contains('/') &&
        !value.contains('\\') && value.indexOf('\u0000') < 0
private fun copyWorkspaceEntry(root: File, sourcePath: String, targetPath: String) {
    val source = resolveWorkspacePath(root, sourcePath)
    val target = resolveWorkspacePath(root, targetPath)
    if (!source.exists()) throw IOException("Source does not exist: " + sourcePath)
    if (target.exists()) throw IOException("Destination already exists: " + targetPath)
    copyTree(source, target, replace = false)
}
private fun copyTree(source: File, target: File, replace: Boolean) {
    if (Files.isSymbolicLink(source.toPath())) throw SecurityException("Symbolic links cannot be copied.")
    if (source.isDirectory) {
        if (target.exists() && !target.isDirectory) {
            if (!replace) throw IOException("Destination has a different item type.")
            if (!target.delete()) throw IOException("Could not replace destination.")
        }
        if (!target.exists() && !target.mkdirs()) throw IOException("Could not create folder: " + target.name)
        val sourceChildren = source.listFiles() ?: throw IOException("Cannot read folder: " + source.path)
        val sourceNames = sourceChildren.map { it.name }.toSet()
        if (replace) {
            (target.listFiles() ?: emptyArray()).filterNot { it.name in sourceNames }.forEach {
                if (!it.deleteRecursively()) throw IOException("Could not remove stale item: " + it.name)
            }
        }
        sourceChildren.forEach { child -> copyTree(child, File(target, child.name), replace) }
    } else {
        if (target.exists() && target.isDirectory) {
            if (!replace) throw IOException("Destination is a folder.")
            if (!target.deleteRecursively()) throw IOException("Could not replace destination.")
        }
        target.parentFile?.let { if (!it.exists() && !it.mkdirs()) throw IOException("Could not create destination folder.") }
        if (target.exists() && !replace) throw IOException("Destination already exists: " + target.path)
        Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}
private fun mirrorWorkspaceEntry(sourceRoot: File, targetRoot: File, sourcePath: String, targetPath: String) {
    val source = resolveWorkspacePath(sourceRoot, sourcePath)
    val target = resolveWorkspacePath(targetRoot, targetPath)
    if (!source.exists()) throw IOException("Source does not exist: " + sourcePath)
    copyTree(source, target, replace = true)
}
private fun syncLinkedWorkspacePaths(root: File, externalRoot: File, links: Set<String>, changedPath: String) {
    links.filter { changedPath == it || changedPath.startsWith(it + "/") }.forEach { linkedPath ->
        val local = resolveWorkspacePath(root, linkedPath)
        val external = resolveWorkspacePath(externalRoot, linkedPath)
        if (local.exists()) mirrorWorkspaceEntry(root, externalRoot, linkedPath, linkedPath)
        else if (external.exists() && !external.deleteRecursively()) throw IOException("Could not propagate deletion of " + linkedPath)
    }
}
private fun updateSyncedPathsAfterDelete(
    context: android.content.Context, root: File, externalRoot: File, links: Set<String>, deletedPath: String,
): Set<String> {
    val retained = linkedSetOf<String>()
    links.forEach { linkedPath ->
        when {
            linkedPath == deletedPath || linkedPath.startsWith(deletedPath + "/") -> {
                val remote = resolveWorkspacePath(externalRoot, linkedPath)
                if (remote.exists() && !remote.deleteRecursively()) throw IOException("Could not delete synced item: " + linkedPath)
            }
            deletedPath.startsWith(linkedPath + "/") -> {
                if (resolveWorkspacePath(root, linkedPath).exists()) mirrorWorkspaceEntry(root, externalRoot, linkedPath, linkedPath)
                retained += linkedPath
            }
            else -> retained += linkedPath
        }
    }
    return saveSyncedPaths(context, root, retained)
}
private fun updateSyncedPathsAfterMove(
    context: android.content.Context, root: File, externalRoot: File, links: Set<String>, oldPath: String, newPath: String,
): Set<String> {
    val updated = linkedSetOf<String>()
    links.forEach { linkedPath ->
        if (linkedPath == oldPath || linkedPath.startsWith(oldPath + "/")) {
            val newLinked = newPath + linkedPath.removePrefix(oldPath)
            val oldRemote = resolveWorkspacePath(externalRoot, linkedPath)
            if (oldRemote.exists() && !oldRemote.deleteRecursively()) throw IOException("Could not remove old synced path.")
            updated += newLinked
        } else updated += linkedPath
    }
    val result = saveSyncedPaths(context, root, updated)
    result.forEach { linkedPath ->
        if (newPath == linkedPath || newPath.startsWith(linkedPath + "/") ||
            oldPath.startsWith(linkedPath + "/")) {
            if (resolveWorkspacePath(root, linkedPath).exists()) mirrorWorkspaceEntry(root, externalRoot, linkedPath, linkedPath)
        }
    }
    return result
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
