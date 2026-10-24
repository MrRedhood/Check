package com.astracode.check

import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption

private const val MAX_WORKSPACE_TEXT_BYTES = 8L * 1024L * 1024L
private const val MAX_OPEN_EDITOR_TABS = 25
// This budget counts both each tab draft and its saved snapshot (UTF-16 code units).
private const val MAX_OPEN_EDITOR_BUFFER_CHARS = 16L * 1024L * 1024L
private const val EDITOR_AUTOSAVE_DEBOUNCE_MS = 300L
private const val EDITOR_AUTOSAVE_MAX_WAIT_MS = 15_000L

private data class WorkspaceEditorSaveOutcome(
    val linkedSyncSucceeded: Boolean,
    val linkedSyncError: String? = null,
)

private data class WorkspaceEditorTab(
    val path: String,
    val text: String = "",
    val savedText: String = "",
    val isLoading: Boolean = true,
)

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
    var editorTabs by remember(root) { mutableStateOf(emptyList<WorkspaceEditorTab>()) }
    var firstDirtyAt by remember(root) { mutableStateOf(emptyMap<String, Long>()) }
    var lastEditedAt by remember(root) { mutableStateOf(emptyMap<String, Long>()) }
    var autosaveRetryAt by remember(root) { mutableStateOf(emptyMap<String, Long>()) }
    var autosaveInProgress by remember(root) { mutableStateOf(false) }
    var savingEditorPath by remember(root) { mutableStateOf<String?>(null) }
    var replacingOldestTab by remember(root) { mutableStateOf(false) }
    var pendingCloseTabPath by remember(root) { mutableStateOf<String?>(null) }
    var showDiscardTabDialog by remember(root) { mutableStateOf(false) }
    var showDiscardWorkspaceDialog by remember(root) { mutableStateOf(false) }
    var message by remember(root) { mutableStateOf("") }
    var recentTrashIds by remember(root) { mutableStateOf(emptyList<String>()) }
    var pendingRenamePreview by remember(root) { mutableStateOf<Map<String, String>?>(null) }
    var safetyExtension by remember(root) { mutableStateOf("") }
    var safetyMinBytes by remember(root) { mutableStateOf("") }
    var safetyMaxBytes by remember(root) { mutableStateOf("") }
    var showSafetyTools by remember(root) { mutableStateOf(false) }
    var safetyQuery by remember(root) { mutableStateOf("") }
    var safetyRenameDraft by remember(root) { mutableStateOf("") }
    var safetyRestoreDestination by remember(root) { mutableStateOf("") }
    var safetyResults by remember(root) { mutableStateOf("") }
    var safetyTrashItems by remember(root) { mutableStateOf(emptyList<WorkspaceTrashItem>()) }
    var previewResult by remember(root) { mutableStateOf<WorkspacePreview?>(null) }
    var actionEntry by remember(root) { mutableStateOf<WorkspaceEntry?>(null) }
    var pendingDeleteEntry by remember(root) { mutableStateOf<WorkspaceEntry?>(null) }
    var operationEntry by remember(root) { mutableStateOf<WorkspaceEntry?>(null) }
    var operationKind by remember(root) { mutableStateOf<String?>(null) }
    var operationInput by remember(root) { mutableStateOf("") }
    var syncEntry by remember(root) { mutableStateOf<WorkspaceEntry?>(null) }
    var selectionMode by remember(root) { mutableStateOf(false) }
    var selectedPaths by remember(root) { mutableStateOf(emptySet<String>()) }
    var bulkActionKind by remember(root) { mutableStateOf<String?>(null) }
    var bulkInput by remember(root) { mutableStateOf("") }
    var archivePath by remember(root) { mutableStateOf<String?>(null) }
    var archiveDirectory by remember(root) { mutableStateOf("") }
    var archiveEntries by remember(root) { mutableStateOf(emptyList<WorkspaceArchiveBrowser.Entry>()) }
    var openedArchiveEntry by remember(root) { mutableStateOf<String?>(null) }
    var archiveEditorText by remember(root) { mutableStateOf("") }
    var archiveSavedText by remember(root) { mutableStateOf("") }
    var archiveLoading by remember(root) { mutableStateOf(false) }
    var archiveActionEntry by remember(root) { mutableStateOf<WorkspaceArchiveBrowser.Entry?>(null) }
    var archiveOperationEntry by remember(root) { mutableStateOf<WorkspaceArchiveBrowser.Entry?>(null) }
    var archiveOperationKind by remember(root) { mutableStateOf<String?>(null) }
    var archiveOperationInput by remember(root) { mutableStateOf("") }
    var metadataDetails by remember(root) { mutableStateOf<String?>(null) }
    var metadataLoading by remember(root) { mutableStateOf(false) }
    var metadataRequestPath by remember(root) { mutableStateOf<String?>(null) }
    var metadataStatus by remember(root) { mutableStateOf("idle") }
    var showCreateFileDialog by remember(root) { mutableStateOf(false) }
    var createFileName by remember(root) { mutableStateOf("") }
    val context = LocalContext.current
    val externalRoot = remember(root) { File(root.parentFile, "external-workspace") }
    var syncedPaths by remember(root) { mutableStateOf(loadSyncedPaths(context, root)) }
    val scope = rememberCoroutineScope()
    val activeEditorTab = editorTabs.firstOrNull { it.path == openedFile }
    val editorText = activeEditorTab?.text.orEmpty()
    val savedText = activeEditorTab?.savedText.orEmpty()
    val loading = activeEditorTab?.isLoading ?: false

    val saveMutex = remember(root) { Mutex() }

    fun clearEditorAutosaveSchedule(path: String) {
        firstDirtyAt = firstDirtyAt - path
        lastEditedAt = lastEditedAt - path
        autosaveRetryAt = autosaveRetryAt - path
    }

    fun replaceOldestTabAndOpen(oldestPath: String, newPath: String) {
        if (editorTabs.none { it.path == oldestPath }) {
            replacingOldestTab = false
            message = "Could not replace the oldest editor tab. Try opening the file again."
            return
        }
        editorTabs = editorTabs.filterNot { it.path == oldestPath } +
            WorkspaceEditorTab(path = newPath, isLoading = true)
        clearEditorAutosaveSchedule(oldestPath)
        currentDirectory = newPath.substringBeforeLast('/', "")
        openedFile = newPath
        pendingCloseTabPath = null
        showDiscardTabDialog = false
        replacingOldestTab = false
    }

    suspend fun saveWorkspaceEditorTab(
        path: String,
        contentToSave: String,
    ): Result<WorkspaceEditorSaveOutcome> {
        saveMutex.lock()
        savingEditorPath = path
        try {
            val beforeSave = editorTabs.firstOrNull { it.path == path }
                ?: return Result.failure(IOException("This editor tab is no longer open."))
            if (beforeSave.text == contentToSave && beforeSave.savedText == contentToSave) {
                return Result.success(WorkspaceEditorSaveOutcome(linkedSyncSucceeded = true))
            }

            val syncResult = try {
                withContext(Dispatchers.IO) {
                    writeWorkspaceText(root, path, contentToSave)
                    runCatching {
                        syncLinkedWorkspacePaths(root, externalRoot, syncedPaths, path)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                return Result.failure(error)
            }

            val stillOpen = editorTabs.firstOrNull { it.path == path }
            if (stillOpen != null) {
                editorTabs = editorTabs.map { tab ->
                    if (tab.path == path) tab.copy(savedText = contentToSave) else tab
                }
                val latestText = editorTabs.firstOrNull { it.path == path }?.text
                if (latestText == contentToSave) {
                    clearEditorAutosaveSchedule(path)
                } else {
                    // Edits made while the disk write was running start a fresh bounded save window.
                    val now = SystemClock.elapsedRealtime()
                    firstDirtyAt = firstDirtyAt + (path to now)
                    lastEditedAt = lastEditedAt + (path to now)
                    autosaveRetryAt = autosaveRetryAt - path
                }
            }

            return Result.success(
                WorkspaceEditorSaveOutcome(
                    linkedSyncSucceeded = syncResult.isSuccess,
                    linkedSyncError = syncResult.exceptionOrNull()?.message,
                ),
            )
        } finally {
            savingEditorPath = null
            saveMutex.unlock()
        }
    }

    fun updateEditorText(newText: String) {
        if (replacingOldestTab) return
        val path = openedFile ?: return
        val currentTab = editorTabs.firstOrNull { it.path == path } ?: return
        if (newText == currentTab.text) return

        val otherBufferUnits = editorTabs.asSequence()
            .filterNot { it.path == path }
            .sumOf { it.text.length.toLong() + it.savedText.length.toLong() }
        if (otherBufferUnits + newText.length.toLong() + currentTab.savedText.length.toLong() >
            MAX_OPEN_EDITOR_BUFFER_CHARS
        ) {
            message = "Editor memory limit reached. Save or close another tab before adding more text."
            return
        }

        val now = SystemClock.elapsedRealtime()
        if (currentTab.text == currentTab.savedText || firstDirtyAt[path] == null || path in autosaveRetryAt) {
            firstDirtyAt = firstDirtyAt + (path to now)
        }
        lastEditedAt = lastEditedAt + (path to now)
        autosaveRetryAt = autosaveRetryAt - path
        editorTabs = editorTabs.map { tab ->
            if (tab.path == path) tab.copy(text = newText) else tab
        }
    }

    fun activateEditorFile(path: String) {
        if (replacingOldestTab) return
        val existing = editorTabs.firstOrNull { it.path == path }
        if (existing != null) {
            currentDirectory = path.substringBeforeLast('/', "")
            openedFile = path
            return
        }

        val estimatedBytes = runCatching {
            Files.size(resolveWorkspacePath(root, path).toPath())
        }.getOrDefault(MAX_WORKSPACE_TEXT_BYTES + 1L)
        if (estimatedBytes > MAX_WORKSPACE_TEXT_BYTES) {
            message = "Files larger than 8 MiB cannot be opened in the editor yet."
            return
        }

        if (editorTabs.size >= MAX_OPEN_EDITOR_TABS) {
            val oldest = editorTabs.first()
            val remainingBufferUnits = editorTabs.drop(1)
                .sumOf { it.text.length.toLong() + it.savedText.length.toLong() }
            if (remainingBufferUnits + estimatedBytes * 2L > MAX_OPEN_EDITOR_BUFFER_CHARS) {
                message = "The editor memory limit would be exceeded even after closing the oldest tab."
                return
            }
            if (oldest.text == oldest.savedText) {
                // A clean or not-yet-loaded tab already matches disk; no extra write is needed.
                replaceOldestTabAndOpen(oldest.path, path)
                return
            }

            replacingOldestTab = true
            scope.launch {
                val result = saveWorkspaceEditorTab(oldest.path, oldest.text)
                result.onSuccess { outcome ->
                    replaceOldestTabAndOpen(oldest.path, path)
                    message = if (outcome.linkedSyncSucceeded) {
                        "Saved and closed the oldest tab to open ${path.substringAfterLast('/')}."
                    } else {
                        "Saved locally, but linked sync failed: " +
                            (outcome.linkedSyncError ?: "unknown sync error")
                    }
                }.onFailure { error ->
                    replacingOldestTab = false
                    message = "Could not auto-save the oldest tab; it was kept open. " +
                        (error.message ?: "Unknown save error.")
                }
            }
            return
        }

        val currentBufferUnits = editorTabs.sumOf {
            it.text.length.toLong() + it.savedText.length.toLong()
        }
        if (currentBufferUnits + estimatedBytes * 2L > MAX_OPEN_EDITOR_BUFFER_CHARS) {
            message = "The open-file memory limit is reached. Save and close another tab before opening this file."
            return
        }
        editorTabs = editorTabs + WorkspaceEditorTab(path = path, isLoading = true)
        currentDirectory = path.substringBeforeLast('/', "")
        openedFile = path
    }

    fun removeEditorTab(path: String) {
        val oldIndex = editorTabs.indexOfFirst { it.path == path }
        if (oldIndex < 0) return
        val remaining = editorTabs.filterNot { it.path == path }
        editorTabs = remaining
        clearEditorAutosaveSchedule(path)
        if (openedFile == path) {
            val nextPath = remaining.getOrNull(oldIndex.coerceAtMost(remaining.lastIndex))?.path
            currentDirectory = nextPath?.substringBeforeLast('/', "") ?: currentDirectory
            openedFile = nextPath
        }
        pendingCloseTabPath = null
        showDiscardTabDialog = false
    }

    fun dismissTabClosePrompt() {
        val path = pendingCloseTabPath
        showDiscardTabDialog = false
        pendingCloseTabPath = null
        if (path != null) {
            val tab = editorTabs.firstOrNull { it.path == path }
            if (tab != null && tab.text != tab.savedText) {
                // Give the user a fresh debounce window after choosing to keep editing.
                val now = SystemClock.elapsedRealtime()
                firstDirtyAt = firstDirtyAt + (path to now)
                lastEditedAt = lastEditedAt + (path to now)
                autosaveRetryAt = autosaveRetryAt - path
            } else {
                clearEditorAutosaveSchedule(path)
            }
        }
    }

    fun requestCloseEditorTab(path: String) {
        val tab = editorTabs.firstOrNull { it.path == path } ?: return
        if (tab.text != tab.savedText) {
            pendingCloseTabPath = path
            showDiscardTabDialog = true
        } else {
            removeEditorTab(path)
        }
    }

    fun requestCloseWorkspace() {
        if (editorTabs.any { it.text != it.savedText }) {
            showDiscardWorkspaceDialog = true
        } else {
            onClose()
        }
    }

    LaunchedEffect(root) {
        withContext(Dispatchers.IO) {
            runCatching { WorkspaceRecovery.cleanupExpiredTrash(root) }
            runCatching { WorkspaceRecovery.cleanupExpiredJournal(root) }
        }
    }

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
            if (recentTrashIds.isEmpty()) message = ""
        } catch (error: Exception) {
            message = error.message ?: "Could not load this folder."
            entries = emptyList()
            folderCounts = emptyMap()
        }
    }

    LaunchedEffect(root, openedFile) {
        val path = openedFile ?: return@LaunchedEffect
        val tab = editorTabs.firstOrNull { it.path == path } ?: return@LaunchedEffect
        if (!tab.isLoading) return@LaunchedEffect
        try {
            val text = withContext(Dispatchers.IO) { readWorkspaceText(root, path) }
            val otherBufferChars = editorTabs.asSequence()
                .filterNot { it.path == path }
                .sumOf { it.text.length.toLong() + it.savedText.length.toLong() }
            if (editorTabs.size > 1 &&
                otherBufferChars + text.length.toLong() * 2L > MAX_OPEN_EDITOR_BUFFER_CHARS
            ) {
                throw IOException("Opening this file would exceed the editor memory limit. Close another tab first.")
            }
            editorTabs = editorTabs.map { current ->
                if (current.path == path) current.copy(text = text, savedText = text, isLoading = false)
                else current
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            editorTabs = editorTabs.filterNot { it.path == path }
            clearEditorAutosaveSchedule(path)
            if (openedFile == path) openedFile = null
            message = error.message ?: "Could not open this file."
        }
    }




    LaunchedEffect(
        root,
        editorTabs,
        firstDirtyAt,
        lastEditedAt,
        autosaveRetryAt,
        autosaveInProgress,
        savingEditorPath,
        replacingOldestTab,
        pendingCloseTabPath,
        showDiscardTabDialog,
    ) {
        // Do not start a save while the user is deciding whether to discard this draft.
        // A save already in progress is tracked separately and disables the discard action.
        if (replacingOldestTab || autosaveInProgress || savingEditorPath != null || showDiscardTabDialog) {
            return@LaunchedEffect
        }

        val allDirtyTabs = editorTabs.filter { !it.isLoading && it.text != it.savedText }
        val dirtyPaths = allDirtyTabs.map { it.path }.toSet()
        val retainedFirstDirtyAt = firstDirtyAt.filterKeys { it in dirtyPaths }
        val retainedLastEditedAt = lastEditedAt.filterKeys { it in dirtyPaths }
        val retainedRetryAt = autosaveRetryAt.filterKeys { it in dirtyPaths }
        if (retainedFirstDirtyAt != firstDirtyAt) firstDirtyAt = retainedFirstDirtyAt
        if (retainedLastEditedAt != lastEditedAt) lastEditedAt = retainedLastEditedAt
        if (retainedRetryAt != autosaveRetryAt) autosaveRetryAt = retainedRetryAt
        if (allDirtyTabs.isEmpty()) return@LaunchedEffect

        val now = SystemClock.elapsedRealtime()
        val nextSave = allDirtyTabs.map { tab ->
            val firstEdit = firstDirtyAt[tab.path] ?: now
            val lastEdit = lastEditedAt[tab.path] ?: firstEdit
            val normalDueAt = minOf(
                lastEdit + EDITOR_AUTOSAVE_DEBOUNCE_MS,
                firstEdit + EDITOR_AUTOSAVE_MAX_WAIT_MS,
            )
            tab.path to maxOf(normalDueAt, autosaveRetryAt[tab.path] ?: Long.MIN_VALUE)
        }.minByOrNull { it.second } ?: return@LaunchedEffect

        delay((nextSave.second - now).coerceAtLeast(0L))
        val tabToSave = editorTabs.firstOrNull { it.path == nextSave.first }
        if (tabToSave != null && !tabToSave.isLoading && tabToSave.text != tabToSave.savedText &&
            !autosaveInProgress && !replacingOldestTab && savingEditorPath == null && !showDiscardTabDialog
        ) {
            val pathToSave = tabToSave.path
            val contentToSave = tabToSave.text
            autosaveInProgress = true
            autosaveRetryAt = autosaveRetryAt - pathToSave
            // Keep one autosave operation in flight, including linked-workspace synchronization.
            scope.launch {
                try {
                    saveWorkspaceEditorTab(pathToSave, contentToSave)
                        .onSuccess { outcome ->
                            if (!outcome.linkedSyncSucceeded) {
                                message = "Autosaved locally, but linked sync failed: " +
                                    (outcome.linkedSyncError ?: "unknown sync error")
                            }
                        }
                        .onFailure { error ->
                            val retryNow = SystemClock.elapsedRealtime()
                            firstDirtyAt = firstDirtyAt + (pathToSave to retryNow)
                            lastEditedAt = lastEditedAt + (pathToSave to retryNow)
                            autosaveRetryAt = autosaveRetryAt +
                                (pathToSave to (retryNow + EDITOR_AUTOSAVE_MAX_WAIT_MS))
                            message = "Autosave failed for ${pathToSave.substringAfterLast('/')} : " +
                                (error.message ?: "Unknown save error.") + " The draft remains open."
                        }
                } finally {
                    autosaveInProgress = false
                }
            }
        }
    }

    LaunchedEffect(root, archivePath, archiveDirectory, refreshKey) {
        val selectedArchive = archivePath
        if (selectedArchive == null) {
            archiveEntries = emptyList()
            return@LaunchedEffect
        }
        try {
            archiveEntries = withContext(Dispatchers.IO) {
                WorkspaceArchiveBrowser.list(root, selectedArchive, archiveDirectory)
            }
        } catch (error: Exception) {
            archiveEntries = emptyList()
            message = error.message ?: "Could not read ZIP folder."
        }
    }

    LaunchedEffect(root, archivePath, openedArchiveEntry) {
        val selectedArchive = archivePath
        val selectedEntry = openedArchiveEntry
        if (selectedArchive == null || selectedEntry == null) {
            archiveEditorText = ""
            archiveSavedText = ""
            archiveLoading = false
            return@LaunchedEffect
        }
        archiveLoading = true
        try {
            val text = withContext(Dispatchers.IO) {
                readWorkspaceArchiveText(root, selectedArchive, selectedEntry)
            }
            archiveEditorText = text
            archiveSavedText = text
        } catch (error: Exception) {
            message = error.message ?: "Could not open ZIP entry."
            openedArchiveEntry = null
        } finally {
            archiveLoading = false
        }
    }

    LaunchedEffect(root, metadataRequestPath) {
        val path = metadataRequestPath ?: return@LaunchedEffect
        metadataStatus = "running"
        metadataLoading = true
        try {
            val details = withContext(Dispatchers.IO) {
                workspaceMetadataText(root, path)
            }
            metadataDetails = details
            metadataStatus = "success"
        } catch (error: kotlinx.coroutines.CancellationException) {
            metadataStatus = "cancelled"
            throw error
        } catch (error: Exception) {
            metadataStatus = "error: ${error.javaClass.simpleName}: ${error.message ?: "Could not read metadata."}"
            message = error.message ?: "Could not read metadata."
        } finally {
            metadataLoading = false
            metadataRequestPath = null
        }
    }

    Column(modifier = modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (metadataStatus != "idle") {
            Text("Metadata status: $metadataStatus", modifier = Modifier.testTag("workspace-metadata-status"))
        }
        if (metadataLoading) {
            Text("Reading metadata…", modifier = Modifier.testTag("workspace-metadata-loading"))
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (archivePath == null) "Workspace files" else "ZIP: " + archivePath!!.substringAfterLast("/"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (openedFile == null && openedArchiveEntry == null && archivePath == null && !selectionMode) {
                TextButton(
                    onClick = { createFileName = ""; showCreateFileDialog = true },
                    modifier = Modifier.testTag("workspace-create-file"),
                ) { Text("New file") }
            }
            if (openedFile == null && openedArchiveEntry == null) {
                TextButton(
                    onClick = {
                        selectionMode = !selectionMode
                        selectedPaths = emptySet()
                    },
                    modifier = Modifier.testTag("workspace-toggle-selection"),
                ) { Text(if (selectionMode) "Cancel selection" else "Select") }
            }
            if (openedFile == null && openedArchiveEntry == null && archivePath == null) {
                TextButton(
                    onClick = {
                        showSafetyTools = true
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) { WorkspaceRecovery.listTrash(root) } }
                                .onSuccess { safetyTrashItems = it }
                                .onFailure { error -> safetyResults = error.message ?: "Could not load Trash." }
                        }
                    },
                    modifier = Modifier.testTag("workspace-safety-tools"),
                ) { Text("File safety") }
            }
            TextButton(onClick = ::requestCloseWorkspace, modifier = Modifier.testTag("close-workspace-browser")) { Text("Close") }
        }
        if (openedFile == null && message.isNotBlank()) {
            Text(message, modifier = Modifier.fillMaxWidth().testTag("workspace-browser-message"))
        }
        if (selectionMode && openedFile == null && openedArchiveEntry == null && archivePath == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${selectedPaths.size} selected", modifier = Modifier.weight(1f))
                TextButton(
                    onClick = { bulkActionKind = "zip"; bulkInput = "archive.zip" },
                    enabled = selectedPaths.isNotEmpty(),
                    modifier = Modifier.testTag("workspace-bulk-zip"),
                ) { Text("Move into ZIP") }
                TextButton(
                    onClick = { bulkActionKind = "move"; bulkInput = currentDirectory.substringBeforeLast('/', "") },
                    enabled = selectedPaths.isNotEmpty(),
                    modifier = Modifier.testTag("workspace-bulk-move"),
                ) { Text("Move") }
                TextButton(
                    onClick = { bulkActionKind = "copy"; bulkInput = if (currentDirectory.isEmpty()) "copies" else currentDirectory + "/copies" },
                    enabled = selectedPaths.isNotEmpty(),
                    modifier = Modifier.testTag("workspace-bulk-copy"),
                ) { Text("Copy") }
                TextButton(
                    onClick = {
                        val paths = selectedPaths.sorted()
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    paths.joinToString("\n\n") { workspaceMetadataText(root, it) }
                                }
                            }.onSuccess { metadataDetails = it }
                                .onFailure { error -> message = error.message ?: "Could not read metadata." }
                        }
                    },
                    enabled = selectedPaths.isNotEmpty(),
                    modifier = Modifier.testTag("workspace-bulk-metadata"),
                ) { Text("Metadata") }
                TextButton(
                    onClick = { bulkActionKind = "delete" },
                    enabled = selectedPaths.isNotEmpty(),
                    modifier = Modifier.testTag("workspace-bulk-delete"),
                ) { Text("Delete") }
            }
        }
        if (openedFile != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { openedFile = null }, modifier = Modifier.testTag("workspace-editor-back")) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = null)
                    Text("Files")
                }
                Text(openedFile!!.substringAfterLast('/'), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                editorTabs.forEach { tab ->
                    val tagSuffix = tab.path.replace("/", "__")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = { activateEditorFile(tab.path) },
                            modifier = Modifier.testTag("workspace-tab-$tagSuffix"),
                        ) {
                            val dirtyMarker = if (tab.text != tab.savedText) " •" else ""
                            Text(tab.path.substringAfterLast('/') + dirtyMarker, maxLines = 1)
                        }
                        TextButton(
                            onClick = { requestCloseEditorTab(tab.path) },
                            modifier = Modifier.testTag("workspace-tab-close-$tagSuffix"),
                        ) { Text("×") }
                    }
                }
            }
            if (loading) {
                Text("Opening file…", modifier = Modifier.testTag("workspace-editor-loading"))
            } else {
                OutlinedTextField(
                    value = editorText,
                    onValueChange = ::updateEditorText,
                    modifier = Modifier.fillMaxWidth().weight(1f).testTag("workspace-editor-input"),
                    label = { Text("File contents") },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val path = openedFile ?: return@Button
                            val contentToSave = editorText
                            scope.launch {
                                saveWorkspaceEditorTab(path, contentToSave)
                                    .onSuccess { outcome ->
                                        refreshKey++
                                        message = if (outcome.linkedSyncSucceeded) {
                                            "Saved " + path.substringAfterLast('/')
                                        } else {
                                            "Saved locally, but the linked copy could not be updated: " +
                                                (outcome.linkedSyncError ?: "sync error")
                                        }
                                    }
                                    .onFailure { error ->
                                        message = error.message ?: "Could not save this file."
                                    }
                            }
                        },
                        enabled = !loading && editorText != savedText && !replacingOldestTab,
                        modifier = Modifier.testTag("workspace-save-file"),
                    ) { Text("Save") }
                    TextButton(onClick = { openedFile = null }) { Text("Back to Files") }
                }
            }

            if (message.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(message, modifier = Modifier.weight(1f).testTag("workspace-message"))
                    if (recentTrashIds.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    var restored = 0
                                    val remaining = mutableListOf<String>()
                                    recentTrashIds.asReversed().forEach { trashId ->
                                        runCatching { withContext(Dispatchers.IO) { WorkspaceRecovery.restoreFromTrash(root, trashId) } }
                                            .onSuccess { restoredPath ->
                                                restored++
                                                runCatching {
                                                    withContext(Dispatchers.IO) {
                                                        syncLinkedWorkspacePaths(root, externalRoot, syncedPaths, restoredPath)
                                                    }
                                                }
                                            }
                                            .onFailure { remaining += trashId }
                                    }
                                    recentTrashIds = remaining
                                    safetyTrashItems = withContext(Dispatchers.IO) {
                                        runCatching { WorkspaceRecovery.listTrash(root) }.getOrDefault(emptyList())
                                    }
                                    refreshKey++
                                    message = if (remaining.isEmpty()) {
                                        "Restored $restored item(s)."
                                    } else {
                                        "Restored $restored item(s); ${remaining.size} item(s) remain in Trash. Choose an alternate path in File safety if needed."
                                    }
                                }
                            },
                            modifier = Modifier.testTag("workspace-undo-delete"),
                        ) { Text("Undo") }
                    }
                }
            }
        } else if (openedArchiveEntry != null) {
            Column(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { openedArchiveEntry = null; refreshKey++ }, modifier = Modifier.testTag("workspace-zip-editor-back")) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null)
                        Text("ZIP files")
                    }
                    Text(openedArchiveEntry!!.substringAfterLast("/"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (archiveLoading) {
                    Text("Opening ZIP entry…", modifier = Modifier.testTag("workspace-zip-editor-loading"))
                } else {
                    OutlinedTextField(
                        value = archiveEditorText,
                        onValueChange = { archiveEditorText = it },
                        modifier = Modifier.fillMaxWidth().weight(1f).testTag("workspace-zip-editor-input"),
                        label = { Text("File contents (saved back into ZIP)") },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val zip = archivePath ?: return@Button
                                val item = openedArchiveEntry ?: return@Button
                                val text = archiveEditorText
                                scope.launch {
                                    runCatching {
                                        withContext(Dispatchers.IO) {
                                            WorkspaceArchiveBrowser.write(root, zip, item, text.toByteArray(Charsets.UTF_8))
                                            syncLinkedWorkspacePaths(root, externalRoot, syncedPaths, zip)
                                        }
                                    }.onSuccess {
                                        archiveSavedText = text
                                        refreshKey++
                                        message = "Saved " + item.substringAfterLast("/")
                                    }.onFailure { error -> message = error.message ?: "Could not save ZIP entry." }
                                }
                            },
                            enabled = !archiveLoading && archiveEditorText != archiveSavedText,
                            modifier = Modifier.testTag("workspace-zip-save-file"),
                        ) { Text("Save to ZIP") }
                        TextButton(onClick = { openedArchiveEntry = null; refreshKey++ }) { Text("Close file") }
                    }
                }
                if (message.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(message, modifier = Modifier.weight(1f).testTag("workspace-message"))
                    if (recentTrashIds.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    var restored = 0
                                    val remaining = mutableListOf<String>()
                                    recentTrashIds.asReversed().forEach { trashId ->
                                        runCatching { withContext(Dispatchers.IO) { WorkspaceRecovery.restoreFromTrash(root, trashId) } }
                                            .onSuccess { restoredPath ->
                                                restored++
                                                runCatching {
                                                    withContext(Dispatchers.IO) {
                                                        syncLinkedWorkspacePaths(root, externalRoot, syncedPaths, restoredPath)
                                                    }
                                                }
                                            }
                                            .onFailure { remaining += trashId }
                                    }
                                    recentTrashIds = remaining
                                    safetyTrashItems = withContext(Dispatchers.IO) {
                                        runCatching { WorkspaceRecovery.listTrash(root) }.getOrDefault(emptyList())
                                    }
                                    refreshKey++
                                    message = if (remaining.isEmpty()) {
                                        "Restored $restored item(s)."
                                    } else {
                                        "Restored $restored item(s); ${remaining.size} item(s) remain in Trash. Choose an alternate path in File safety if needed."
                                    }
                                }
                            },
                            modifier = Modifier.testTag("workspace-undo-delete"),
                        ) { Text("Undo") }
                    }
                }
            }
            }
        } else if (archivePath != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = {
                        if (archiveDirectory.isNotEmpty()) {
                            archiveDirectory = archiveDirectory.substringBeforeLast("/", "")
                        } else {
                            archivePath = null
                            selectedPaths = emptySet()
                            selectionMode = false
                        }
                        refreshKey++
                    },
                    modifier = Modifier.testTag("workspace-zip-up"),
                ) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = null)
                    Text(if (archiveDirectory.isEmpty()) "Files" else "Up")
                }
                Text(
                    (archiveDirectory.substringAfterLast("/").ifEmpty { "ZIP root" }) +
                        " · " + archiveEntries.size + if (archiveEntries.size == 1) " item" else " items",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("workspace-zip-current-folder-count"),
                )
            }
            if (selectionMode) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(selectedPaths.size.toString() + " selected", modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            archiveOperationKind = "zip"
                            archiveOperationEntry = archiveEntries.firstOrNull { it.path in selectedPaths }
                            archiveOperationInput = if (archiveDirectory.isEmpty()) "archive.zip" else archiveDirectory + "/archive.zip"
                        },
                        enabled = selectedPaths.isNotEmpty(),
                        modifier = Modifier.testTag("workspace-zip-bulk-create"),
                    ) { Text("Move into ZIP") }
                    TextButton(
                        onClick = {
                            archiveOperationKind = "copy-selected"
                            archiveOperationEntry = archiveEntries.firstOrNull { it.path in selectedPaths }
                            archiveOperationInput = if (archiveDirectory.isEmpty()) "copies" else archiveDirectory + "/copies"
                        },
                        enabled = selectedPaths.isNotEmpty(),
                        modifier = Modifier.testTag("workspace-zip-bulk-copy"),
                    ) { Text("Copy") }
                    TextButton(
                        onClick = {
                            archiveOperationKind = "move-selected"
                            archiveOperationEntry = archiveEntries.firstOrNull { it.path in selectedPaths }
                            archiveOperationInput = if (archiveDirectory.isEmpty()) "moved" else archiveDirectory.substringBeforeLast("/", "")
                        },
                        enabled = selectedPaths.isNotEmpty(),
                        modifier = Modifier.testTag("workspace-zip-bulk-move"),
                    ) { Text("Move") }
                    TextButton(
                        onClick = {
                            val paths = selectedPaths.toList()
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        paths.sorted().joinToString("\\n\\n") { workspaceArchiveMetadataText(root, archivePath.orEmpty(), it) }
                                    }
                                }.onSuccess { metadataDetails = it }
                                    .onFailure { error -> message = error.message ?: "Could not read metadata." }
                            }
                        },
                        enabled = selectedPaths.isNotEmpty(),
                        modifier = Modifier.testTag("workspace-zip-bulk-metadata"),
                    ) { Text("Metadata") }
                    TextButton(
                        onClick = {
                            archiveOperationKind = "delete-selected"
                            archiveOperationEntry = archiveEntries.firstOrNull { it.path in selectedPaths }
                        },
                        enabled = selectedPaths.isNotEmpty(),
                        modifier = Modifier.testTag("workspace-zip-bulk-delete"),
                    ) { Text("Delete") }
                }
            }
            if (message.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(message, modifier = Modifier.weight(1f).testTag("workspace-message"))
                    if (recentTrashIds.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    var restored = 0
                                    val remaining = mutableListOf<String>()
                                    recentTrashIds.asReversed().forEach { trashId ->
                                        runCatching { withContext(Dispatchers.IO) { WorkspaceRecovery.restoreFromTrash(root, trashId) } }
                                            .onSuccess { restoredPath ->
                                                restored++
                                                runCatching {
                                                    withContext(Dispatchers.IO) {
                                                        syncLinkedWorkspacePaths(root, externalRoot, syncedPaths, restoredPath)
                                                    }
                                                }
                                            }
                                            .onFailure { remaining += trashId }
                                    }
                                    recentTrashIds = remaining
                                    safetyTrashItems = withContext(Dispatchers.IO) {
                                        runCatching { WorkspaceRecovery.listTrash(root) }.getOrDefault(emptyList())
                                    }
                                    refreshKey++
                                    message = if (remaining.isEmpty()) {
                                        "Restored $restored item(s)."
                                    } else {
                                        "Restored $restored item(s); ${remaining.size} item(s) remain in Trash. Choose an alternate path in File safety if needed."
                                    }
                                }
                            },
                            modifier = Modifier.testTag("workspace-undo-delete"),
                        ) { Text("Undo") }
                    }
                }
            }
            if (archiveEntries.isEmpty() && message.isBlank()) Text("This ZIP folder is empty.", modifier = Modifier.testTag("workspace-zip-empty-folder"))
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(archiveEntries, key = { it.path }) { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (selectionMode) {
                                        selectedPaths = if (item.path in selectedPaths) selectedPaths - item.path else selectedPaths + item.path
                                    } else if (item.isDirectory) {
                                        archiveDirectory = item.path
                                        refreshKey++
                                    } else {
                                        openedArchiveEntry = item.path
                                    }
                                },
                                onLongClick = {
                                    if (selectionMode) {
                                        selectedPaths = if (item.path in selectedPaths) selectedPaths - item.path else selectedPaths + item.path
                                    } else archiveActionEntry = item
                                },
                            )
                            .padding(horizontal = 6.dp, vertical = 8.dp)
                            .testTag("workspace-zip-entry-" + item.name),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (selectionMode) {
                            Checkbox(
                                checked = item.path in selectedPaths,
                                onCheckedChange = { checked ->
                                    selectedPaths = if (checked) selectedPaths + item.path else selectedPaths - item.path
                                },
                                modifier = Modifier.testTag("workspace-zip-select-" + item.name),
                            )
                        }
                        Icon(if (item.isDirectory) Icons.Filled.Folder else Icons.Filled.Description, contentDescription = null)
                        Spacer(Modifier.padding(4.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                (if (item.isDirectory) "Folder · " else item.format + " · ") + item.sizeBytes.toString() + " bytes",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.testTag("workspace-zip-detail-" + item.name),
                            )
                        }
                    }
                    Divider()
                }
            }
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
            if (message.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(message, modifier = Modifier.weight(1f).testTag("workspace-message"))
                    if (recentTrashIds.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    var restored = 0
                                    val remaining = mutableListOf<String>()
                                    recentTrashIds.asReversed().forEach { trashId ->
                                        runCatching { withContext(Dispatchers.IO) { WorkspaceRecovery.restoreFromTrash(root, trashId) } }
                                            .onSuccess { restoredPath ->
                                                restored++
                                                runCatching {
                                                    withContext(Dispatchers.IO) {
                                                        syncLinkedWorkspacePaths(root, externalRoot, syncedPaths, restoredPath)
                                                    }
                                                }
                                            }
                                            .onFailure { remaining += trashId }
                                    }
                                    recentTrashIds = remaining
                                    safetyTrashItems = withContext(Dispatchers.IO) {
                                        runCatching { WorkspaceRecovery.listTrash(root) }.getOrDefault(emptyList())
                                    }
                                    refreshKey++
                                    message = if (remaining.isEmpty()) {
                                        "Restored $restored item(s)."
                                    } else {
                                        "Restored $restored item(s); ${remaining.size} item(s) remain in Trash. Choose an alternate path in File safety if needed."
                                    }
                                }
                            },
                            modifier = Modifier.testTag("workspace-undo-delete"),
                        ) { Text("Undo") }
                    }
                }
            }
            if (entries.isEmpty() && message.isBlank()) Text("This folder is empty.", modifier = Modifier.testTag("workspace-empty-folder"))
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f).testTag("workspace-file-list"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(entries, key = { it.path }) { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (selectionMode) {
                                        selectedPaths = if (entry.path in selectedPaths) selectedPaths - entry.path
                                            else selectedPaths + entry.path
                                    } else if (entry.isDirectory) {
                                        currentDirectory = entry.path
                                        refreshKey++
                                    } else if (entry.name.endsWith(".zip", ignoreCase = true)) {
                                        operationEntry = entry
                                        operationKind = "extract"
                                        operationInput = entry.path.replace(Regex("(?i)\\.zip$"), "")
                                    } else activateEditorFile(entry.path)
                                },
                                onLongClick = {
                                    if (selectionMode) {
                                        selectedPaths = if (entry.path in selectedPaths) selectedPaths - entry.path
                                            else selectedPaths + entry.path
                                    } else actionEntry = entry
                                },
                            )
                            .padding(horizontal = 6.dp, vertical = 8.dp)
                            .testTag("workspace-entry-" + entry.name),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (selectionMode) {
                            Checkbox(
                                checked = entry.path in selectedPaths,
                                onCheckedChange = { checked ->
                                    selectedPaths = if (checked) selectedPaths + entry.path else selectedPaths - entry.path
                                },
                                modifier = Modifier.testTag("workspace-select-" + entry.name),
                            )
                        }
                        Icon(
                            imageVector = when {
                                entry.isDirectory -> Icons.Filled.Folder
                                entry.name.endsWith(".zip", ignoreCase = true) -> Icons.Filled.Archive
                                else -> Icons.Filled.Description
                            },
                            contentDescription = when {
                                entry.isDirectory -> "Folder"
                                entry.name.endsWith(".zip", ignoreCase = true) -> "ZIP archive"
                                else -> "File"
                            },
                            modifier = Modifier.testTag("workspace-entry-icon-${entry.name}"),
                        )
                        Spacer(Modifier.padding(4.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    entry.name,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (!selectionMode && !entry.isDirectory && editorTabs.any { it.path == entry.path }) {
                                    TextButton(
                                        onClick = { requestCloseEditorTab(entry.path) },
                                        enabled = savingEditorPath != entry.path && !replacingOldestTab,
                                        modifier = Modifier.testTag("workspace-close-open-file-" + entry.name),
                                    ) { Text("×") }
                                }
                            }
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


    archiveActionEntry?.let { item ->
        AlertDialog(
            onDismissRequest = { archiveActionEntry = null },
            title = { Text(item.name + " options inside ZIP") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    TextButton(onClick = {
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    workspaceArchiveMetadataText(root, archivePath.orEmpty(), item.path)
                                }
                            }.onSuccess { metadataDetails = it; archiveActionEntry = null }
                                .onFailure { error -> message = error.message ?: "Could not read metadata." }
                        }
                    }, modifier = Modifier.fillMaxWidth().testTag("workspace-zip-action-metadata")) { Text("Metadata") }
                    TextButton(onClick = {
                        archiveOperationEntry = item
                        archiveOperationKind = "zip"
                        archiveOperationInput = if (archiveDirectory.isEmpty()) item.name + ".zip" else archiveDirectory + "/" + item.name + ".zip"
                        selectedPaths = emptySet()
                        archiveActionEntry = null
                    }, modifier = Modifier.fillMaxWidth().testTag("workspace-zip-action-create-zip")) { Text("Create ZIP inside this ZIP") }
                    if (!item.isDirectory && item.name.endsWith(".zip", ignoreCase = true)) {
                        TextButton(onClick = {
                            archiveOperationEntry = item
                            archiveOperationKind = "extract"
                            archiveOperationInput = if (archiveDirectory.isEmpty()) item.name.removeSuffix(".zip") else archiveDirectory + "/" + item.name.removeSuffix(".zip")
                            archiveActionEntry = null
                        }, modifier = Modifier.fillMaxWidth().testTag("workspace-zip-action-extract")) { Text("Extract inside this ZIP") }
                    }
                    if (item.isDirectory) {
                        TextButton(onClick = {
                            archiveOperationEntry = item
                            archiveOperationKind = "mkdir"
                            archiveOperationInput = item.path + "/new-folder"
                            archiveActionEntry = null
                        }, modifier = Modifier.fillMaxWidth().testTag("workspace-zip-action-new-folder")) { Text("New folder") }
                    }
                    TextButton(onClick = { archiveOperationEntry = item; archiveOperationKind = "rename"; archiveOperationInput = item.name; archiveActionEntry = null },
                        modifier = Modifier.fillMaxWidth().testTag("workspace-zip-action-rename")) { Text("Rename") }
                    TextButton(onClick = { archiveOperationEntry = item; archiveOperationKind = "copy"; archiveOperationInput = joinWorkspacePath(item.path.substringBeforeLast("/", ""), "copy-" + item.name); archiveActionEntry = null },
                        modifier = Modifier.fillMaxWidth().testTag("workspace-zip-action-copy")) { Text("Copy") }
                    TextButton(onClick = { archiveOperationEntry = item; archiveOperationKind = "move"; archiveOperationInput = joinWorkspacePath(item.path.substringBeforeLast("/", ""), "moved-" + item.name); archiveActionEntry = null },
                        modifier = Modifier.fillMaxWidth().testTag("workspace-zip-action-move")) { Text("Move / change path") }
                    TextButton(onClick = { archiveOperationEntry = item; archiveOperationKind = "delete"; archiveOperationInput = ""; archiveActionEntry = null },
                        modifier = Modifier.fillMaxWidth().testTag("workspace-zip-action-delete")) { Text("Delete") }
                }
            },
            confirmButton = { TextButton(onClick = { archiveActionEntry = null }) { Text("Cancel") } },
        )
    }

    archiveOperationEntry?.let { item ->
        val kind = archiveOperationKind.orEmpty()
        AlertDialog(
            onDismissRequest = { archiveOperationEntry = null; archiveOperationKind = null },
            title = { Text(when (kind) {
                "zip" -> "Create ZIP inside archive"
                "extract" -> "Extract ZIP inside archive"
                "mkdir" -> "Create folder inside archive"
                "copy" -> "Copy ZIP entry"
                "move" -> "Move ZIP entry"
                "copy-selected" -> "Copy selected ZIP entries"
                "move-selected" -> "Move selected ZIP entries"
                "delete-selected" -> "Delete selected ZIP entries?"
                "delete" -> "Delete ZIP entry?"
                else -> "Rename ZIP entry"
            }) },
            text = {
                Column {
                    Text(when (kind) {
                        "zip" -> "Compress selected file(s) or folder(s) into a nested ZIP. Nothing is extracted to the workspace."
                        "extract" -> "Enter the destination folder inside this ZIP. Existing entry paths will not be overwritten."
                        "mkdir" -> "Enter a folder path inside the ZIP."
                        "copy", "move" -> "Enter the destination path relative to the ZIP root."
                        "copy-selected", "move-selected" -> "Enter the destination folder inside the ZIP."
                        "delete", "delete-selected" -> "Delete this entry and any children from the archive?"
                        else -> "Enter the new name for this item."
                    })
                    if (kind != "delete" && kind != "delete-selected") {
                        OutlinedTextField(
                            value = archiveOperationInput,
                            onValueChange = { archiveOperationInput = it },
                            label = { Text(if (kind == "rename") "Name" else if (kind == "copy-selected" || kind == "move-selected") "Destination folder" else "Path inside ZIP") },
                            modifier = Modifier.fillMaxWidth().testTag("workspace-zip-operation-input"),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val zip = archivePath ?: return@TextButton
                        val raw = archiveOperationInput.trim()
                        val action = archiveOperationKind.orEmpty()
                        val selectedPathsNow = selectedPaths.toList()
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    when (action) {
                                        "zip" -> {
                                            val output = if (raw.lowercase().endsWith(".zip")) raw else raw + ".zip"
                                            val chosen = if (selectedPathsNow.isNotEmpty()) selectedPathsNow
                                                else listOf(item.path)
                                            WorkspaceArchiveBrowser.createNestedZip(root, zip, chosen, output, archiveDirectory)
                                        }
                                        "extract" -> WorkspaceArchiveBrowser.extractNestedZip(root, zip, item.path, raw)
                                        "mkdir" -> WorkspaceArchiveBrowser.createDirectory(root, zip, raw)
                                        "rename" -> WorkspaceArchiveBrowser.rename(root, zip, item.path, raw)
                                        "copy" -> WorkspaceArchiveBrowser.copy(root, zip, item.path, raw)
                                        "move" -> WorkspaceArchiveBrowser.move(root, zip, item.path, raw)
                                        "copy-selected" -> WorkspaceArchiveBrowser.copyMultiple(root, zip, selectedPathsNow, raw)
                                        "move-selected" -> WorkspaceArchiveBrowser.moveMultiple(root, zip, selectedPathsNow, raw)
                                        "delete" -> WorkspaceArchiveBrowser.delete(root, zip, item.path)
                                        "delete-selected" -> WorkspaceArchiveBrowser.deleteMultiple(root, zip, selectedPathsNow)
                                        else -> throw IOException("Unknown ZIP action.")
                                    }
                                    syncLinkedWorkspacePaths(root, externalRoot, syncedPaths, zip)
                                }
                            }.onSuccess {
                                archiveOperationEntry = null
                                archiveOperationKind = null
                                selectedPaths = emptySet()
                                selectionMode = false
                                refreshKey++
                                message = "Updated ZIP: " + zip.substringAfterLast("/")
                            }.onFailure { error -> message = error.message ?: "Could not update ZIP archive." }
                        }
                    },
                    modifier = Modifier.testTag("workspace-zip-confirm-operation"),
                ) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { archiveOperationEntry = null; archiveOperationKind = null }) { Text("Cancel") } },
        )
    }

    if (showSafetyTools) {
        AlertDialog(
            onDismissRequest = {
                if (pendingRenamePreview != null) {
                    pendingRenamePreview = null
                    safetyResults = "Rename cancelled. No files were changed."
                } else {
                    showSafetyTools = false
                }
            },
            title = {
                Text(if (pendingRenamePreview != null) "Review batch rename" else "File safety tools")
            },
            text = {
                val renameReview = pendingRenamePreview
                if (renameReview != null) {
                    Column(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("Review every proposed change before applying it:")
                        renameReview.forEach { (old, new) -> Text("$old  →  $new") }
                    }
                } else {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Search the workspace by path or filename.")
                    OutlinedTextField(
                        value = safetyQuery,
                        onValueChange = { safetyQuery = it },
                        label = { Text("Search text") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("workspace-safety-search-input"),
                    )
                    OutlinedTextField(
                        value = safetyExtension,
                        onValueChange = { safetyExtension = it },
                        label = { Text("Extension (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("workspace-safety-extension"),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = safetyMinBytes,
                            onValueChange = { safetyMinBytes = it.filter(Char::isDigit) },
                            label = { Text("Min bytes") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("workspace-safety-min-bytes"),
                        )
                        OutlinedTextField(
                            value = safetyMaxBytes,
                            onValueChange = { safetyMaxBytes = it.filter(Char::isDigit) },
                            label = { Text("Max bytes") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("workspace-safety-max-bytes"),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(
                            onClick = {
                                // Read Compose state on the UI thread before dispatching disk I/O.
                                val requestedQuery = safetyQuery.trim()
                                val requestedExtension = safetyExtension.trim().takeIf { it.isNotEmpty() }
                                val requestedMinBytes = safetyMinBytes.toLongOrNull()
                                val requestedMaxBytes = safetyMaxBytes.toLongOrNull()
                                safetyResults = "Searching…"
                                scope.launch {
                                    val result = runCatching {
                                        withContext(Dispatchers.IO) {
                                            WorkspaceRecovery.search(
                                                root,
                                                WorkspaceSearchOptions(
                                                    query = requestedQuery,
                                                    extension = requestedExtension,
                                                    minSizeBytes = requestedMinBytes,
                                                    maxSizeBytes = requestedMaxBytes,
                                                ),
                                            )
                                        }
                                    }
                                    result.onSuccess { hits ->
                                        safetyResults = if (hits.isEmpty()) "No matching files."
                                        else hits.joinToString("\n") { "${it.path} · ${it.sizeBytes} bytes" }
                                    }.onFailure { error ->
                                        safetyResults = "Search failed: " + (error.message ?: "unknown error")
                                    }
                                }
                            },
                            modifier = Modifier.testTag("workspace-safety-search"),
                        ) { Text("Search") }
                        TextButton(
                            onClick = {
                                scope.launch {
                                    runCatching { withContext(Dispatchers.IO) { WorkspaceRecovery.findDuplicates(root) } }
                                        .onSuccess { groups ->
                                            safetyResults = if (groups.isEmpty()) "No exact duplicates found."
                                            else groups.joinToString("\n\n") { group ->
                                                "SHA-256 ${group.sha256}\n${group.sizeBytes} bytes\n" + group.paths.joinToString("\n")
                                            }
                                        }.onFailure { error -> safetyResults = "Duplicate scan failed: " + (error.message ?: "unknown error") }
                                }
                            },
                            modifier = Modifier.testTag("workspace-safety-duplicates"),
                        ) { Text("Find duplicates") }
                    }
                    // Keep operation feedback beside the controls that produced it, above the fold.
                    if (safetyResults.isNotBlank()) {
                        Text(safetyResults, modifier = Modifier.testTag("workspace-safety-results"))
                    }
                    TextButton(
                        onClick = {
                            scope.launch {
                                runCatching { withContext(Dispatchers.IO) { WorkspaceRecovery.storageReport(root) } }
                                    .onSuccess { report ->
                                        safetyResults = "Workspace size: ${report.totalBytes} bytes\nFiles: ${report.fileCount}\nFolders: ${report.directoryCount}\nLargest files:\n" +
                                            report.largestFiles.joinToString("\n") { "${it.first} · ${it.second} bytes" } +
                                            "\nAvailable disk space: ${report.usableBytes} bytes"
                                    }.onFailure { error -> safetyResults = "Storage scan failed: " + (error.message ?: "unknown error") }
                            }
                        },
                        modifier = Modifier.testTag("workspace-safety-health"),
                    ) { Text("Storage health") }

                    Divider()
                    Text("Batch rename for the current folder. Enter one mapping per line: old-name=new-name.")
                    OutlinedTextField(
                        value = safetyRenameDraft,
                        onValueChange = { safetyRenameDraft = it },
                        label = { Text("Rename mappings") },
                        modifier = Modifier.fillMaxWidth().testTag("workspace-batch-rename-input"),
                    )
                    TextButton(
                        onClick = {
                            val lines = safetyRenameDraft.lines().filter { it.isNotBlank() }
                            val pairs = lines.mapNotNull { line ->
                                val separator = line.indexOf('=')
                                if (separator <= 0 || separator == line.lastIndex) null
                                else line.substring(0, separator).trim() to line.substring(separator + 1).trim()
                            }
                            val proposed = pairs.toMap()
                            val currentNames = runCatching { listWorkspaceDirectory(root, currentDirectory).map { it.name } }
                                .getOrElse {
                                    safetyResults = "Could not list the current folder: " + (it.message ?: "unknown error")
                                    emptyList()
                                }
                            val invalidName = proposed.any { (old, new) ->
                                !isSimpleWorkspaceName(old) || !isSimpleWorkspaceName(new)
                            }
                            val duplicateTargets = proposed.values.map { it.lowercase() }.distinct().size != proposed.size
                            val conflictingTarget = proposed.values.any { target ->
                                currentNames.any { existing -> existing.equals(target, ignoreCase = true) } &&
                                    proposed.keys.none { source -> source.equals(target, ignoreCase = true) }
                            }
                            when {
                                proposed.isEmpty() || proposed.size != lines.size -> {
                                    pendingRenamePreview = null
                                    safetyResults = "Each rename must be a unique old-name=new-name line."
                                }
                                invalidName -> {
                                    pendingRenamePreview = null
                                    safetyResults = "Use simple file names without path separators or reserved system names."
                                }
                                duplicateTargets -> {
                                    pendingRenamePreview = null
                                    safetyResults = "Two items cannot be renamed to the same destination."
                                }
                                conflictingTarget -> {
                                    pendingRenamePreview = null
                                    safetyResults = "One or more destination names already exist. Resolve those conflicts before continuing."
                                }
                                else -> {
                                    pendingRenamePreview = proposed
                                    safetyResults = "Review the proposed changes below. No files have been renamed yet."
                                }
                            }
                        },
                        modifier = Modifier.testTag("workspace-batch-rename"),
                    ) { Text("Preview rename") }
                    Divider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Trash · ${safetyTrashItems.size} item(s)", modifier = Modifier.weight(1f))
                        TextButton(
                            onClick = {
                                scope.launch {
                                    runCatching { withContext(Dispatchers.IO) { WorkspaceRecovery.listTrash(root) } }
                                        .onSuccess { safetyTrashItems = it }
                                        .onFailure { error -> safetyResults = "Could not load Trash: " + (error.message ?: "unknown error") }
                                }
                            },
                            modifier = Modifier.testTag("workspace-refresh-trash"),
                        ) { Text("Refresh") }
                    }
                    OutlinedTextField(
                        value = safetyRestoreDestination,
                        onValueChange = { safetyRestoreDestination = it },
                        label = { Text("Alternate restore path (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("workspace-restore-destination"),
                    )
                    if (safetyTrashItems.isEmpty()) {
                        Text("Trash is empty.")
                    } else {
                        safetyTrashItems.forEach { item ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.originalPath, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text("Deleted ${java.time.Instant.ofEpochMilli(item.deletedAtMillis)}", style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(
                                    onClick = {
                                        val alternatePath = safetyRestoreDestination.trim().takeIf { it.isNotEmpty() }
                                        scope.launch {
                                            runCatching {
                                                withContext(Dispatchers.IO) {
                                                    WorkspaceRecovery.restoreFromTrash(root, item.id, alternatePath)
                                                }
                                            }.onSuccess { restoredPath ->
                                                safetyRestoreDestination = ""
                                                refreshKey++
                                                safetyTrashItems = withContext(Dispatchers.IO) { WorkspaceRecovery.listTrash(root) }
                                                message = "Restored ${restoredPath}"
                                                safetyResults = message
                                            }.onFailure { error ->
                                                safetyResults = "Restore failed: " + (error.message ?: "unknown error") +
                                                    ". Choose an alternate path if the original path is occupied."
                                            }
                                        }
                                    },
                                    modifier = Modifier.testTag("workspace-trash-restore-${item.id}"),
                                ) { Text("Restore") }
                            }
                        }
                    }
                }
                }
            },
            confirmButton = {
                if (pendingRenamePreview != null) {
                    TextButton(
                        onClick = {
                            val reviewed = pendingRenamePreview?.toMap() ?: emptyMap()
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        WorkspaceRecovery.batchRename(root, currentDirectory, reviewed)
                                    }
                                }.onSuccess { results ->
                                    pendingRenamePreview = null
                                    safetyRenameDraft = ""
                                    refreshKey++
                                    safetyResults = "Renamed ${results.size} item(s):\n" +
                                        results.joinToString("\n") { "${it.from} → ${it.to}" }
                                }.onFailure { error ->
                                    pendingRenamePreview = null
                                    safetyResults = "Batch rename failed before any unsafe overwrite: " +
                                        (error.message ?: "unknown error")
                                }
                            }
                        },
                        modifier = Modifier.testTag("workspace-batch-rename-confirm"),
                    ) { Text("Confirm rename") }
                } else {
                    TextButton(
                        onClick = { showSafetyTools = false },
                        modifier = Modifier.testTag("workspace-safety-close"),
                    ) { Text("Close") }
                }
            },
            dismissButton = {
                if (pendingRenamePreview != null) {
                    TextButton(
                        onClick = {
                            pendingRenamePreview = null
                            safetyResults = "Rename cancelled. No files were changed."
                        },
                        modifier = Modifier.testTag("workspace-batch-rename-cancel"),
                    ) { Text("Cancel") }
                }
            },
        )
    }

    previewResult?.let { preview ->
        val bitmap = remember(preview.imageBytes) {
            preview.imageBytes?.let { bytes ->
                runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()
            }
        }
        AlertDialog(
            onDismissRequest = { previewResult = null },
            title = { Text("Read-only preview · ${preview.path}") },
            text = {
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    Text(preview.message, style = MaterialTheme.typography.bodySmall)
                    if (preview.text != null) {
                        val content = if (preview.text.length > 30000) preview.text.take(30000) + "\n\n[Preview clipped at 30,000 characters]" else preview.text
                        Text(content)
                    }
                    if (preview.imageBytes != null) {
                        if (bitmap != null) {
                            Image(bitmap = bitmap, contentDescription = "Read-only image preview", modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp))
                        } else {
                            Text("The bytes could not be decoded as an image.")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { previewResult = null }, modifier = Modifier.testTag("workspace-preview-close")) { Text("Close") } },
        )
    }

    metadataDetails?.let { details ->
        AlertDialog(
            onDismissRequest = { metadataDetails = null },
            title = { Text("File metadata") },
            text = { Text(details, modifier = Modifier.testTag("workspace-metadata-details")) },
            confirmButton = { TextButton(onClick = { metadataDetails = null }, modifier = Modifier.testTag("workspace-metadata-close")) { Text("Close") } },
        )
    }


    actionEntry?.let { entry ->
        AlertDialog(onDismissRequest = { actionEntry = null },
            title = { Text(entry.name + " options") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TextButton(
                    onClick = {
                        selectedPaths = setOf(entry.path)
                        bulkActionKind = "zip"
                        bulkInput = if (entry.name.endsWith(".zip", ignoreCase = true))
                            entry.name.dropLast(4) + "-copy.zip"
                        else entry.name + ".zip"
                        actionEntry = null
                    },
                    modifier = Modifier.fillMaxWidth().testTag("workspace-action-zip"),
                ) { Text("Move into ZIP") }
                if (entry.name.endsWith(".zip", ignoreCase = true) && !entry.isDirectory) {
                    TextButton(
                        onClick = {
                            operationEntry = entry
                            operationKind = "extract"
                            operationInput = entry.path.replace(Regex("(?i)\\.zip$"), "")
                            actionEntry = null
                        },
                        modifier = Modifier.fillMaxWidth().testTag("workspace-action-extract"),
                    ) { Text("Extract ZIP") }
                }
                TextButton(
                    onClick = {
                        actionEntry = null
                        metadataDetails = null
                        message = ""
                        metadataLoading = true
                        metadataStatus = "requested"
                        metadataRequestPath = entry.path
                    },
                    modifier = Modifier.fillMaxWidth().testTag("workspace-action-metadata"),
                ) { Text("Metadata") }
                if (!entry.isDirectory && !entry.name.endsWith(".zip", ignoreCase = true)) {
                    TextButton(
                        onClick = {
                            actionEntry = null
                            scope.launch {
                                runCatching { withContext(Dispatchers.IO) { WorkspaceRecovery.preview(root, entry.path) } }
                                    .onSuccess { previewResult = it }
                                    .onFailure { error -> message = error.message ?: "Could not preview this file." }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("workspace-action-preview"),
                    ) { Text("Preview (read-only)") }
                }
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
                                val trash = WorkspaceRecovery.moveToTrash(root, entry.path)
                                val updated = updateSyncedPathsAfterDelete(context, root, externalRoot, syncedPaths, entry.path)
                                trash.id to updated
                            }
                        }
                        result.onSuccess { (trashId, updatedLinks) ->
                            recentTrashIds = listOf(trashId)
                            syncedPaths = updatedLinks
                            pendingDeleteEntry = null
                            refreshKey++
                            message = "Moved " + entry.name + " to Trash. Undo is available."
                        }.onFailure { error ->
                            pendingDeleteEntry = null
                            message = error.message ?: "Could not delete item."
                        }
                    }
                }, modifier = Modifier.testTag("confirm-workspace-delete")) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteEntry = null }) { Text("Cancel") } })
    }

    if (showCreateFileDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFileDialog = false },
            title = { Text("Create text file") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("A name without an extension will be saved as a .txt file. Existing extensions are preserved.")
                    OutlinedTextField(
                        value = createFileName,
                        onValueChange = { createFileName = it },
                        label = { Text("File name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("workspace-new-file-name"),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val normalizedName = normalizeNewTextFileName(createFileName)
                        if (!isSimpleWorkspaceName(normalizedName)) {
                            message = "Enter a valid file name without path separators."
                        } else {
                            val destinationPath = joinWorkspacePath(currentDirectory, normalizedName)
                            scope.launch {
                                val result = runCatching {
                                    withContext(Dispatchers.IO) {
                                        val target = resolveWorkspacePath(root, destinationPath)
                                        if (target.exists()) throw IOException("A file with that name already exists.")
                                        val parent = target.parentFile
                                            ?: throw IOException("The destination folder is not available.")
                                        if (!parent.exists() && !parent.mkdirs()) {
                                            throw IOException("Could not create the destination folder.")
                                        }
                                        if (!target.createNewFile()) throw IOException("Could not create the file.")
                                        destinationPath
                                    }
                                }
                                result.onSuccess { path ->
                                    showCreateFileDialog = false
                                    createFileName = ""
                                    activateEditorFile(path)
                                    refreshKey++
                                    message = "Created $normalizedName"
                                }.onFailure { error ->
                                    message = error.message ?: "Could not create file."
                                }
                            }
                        }
                    },
                    modifier = Modifier.testTag("confirm-workspace-create-file"),
                ) { Text("Create file") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFileDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showDiscardTabDialog) {
        val pathToClose = pendingCloseTabPath
        val tabToClose = editorTabs.firstOrNull { it.path == pathToClose }
        val hasUnsavedChanges = tabToClose != null && tabToClose.text != tabToClose.savedText
        val saveStillRunning = pathToClose != null && savingEditorPath == pathToClose
        AlertDialog(
            onDismissRequest = { dismissTabClosePrompt() },
            title = {
                Text(if (hasUnsavedChanges) "Discard unsaved changes?" else "File saved automatically")
            },
            text = {
                val name = pathToClose?.substringAfterLast('/') ?: "this file"
                Text(
                    if (hasUnsavedChanges) {
                        "Close $name and discard edits that have not been saved?"
                    } else {
                        "The latest changes to $name have been saved. Close the tab?"
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val path = pendingCloseTabPath
                        if (path != null && path != savingEditorPath) {
                            // If autosave finished while the prompt was open, close the clean tab;
                            // otherwise this is the user's explicit discard decision.
                            removeEditorTab(path)
                        }
                    },
                    enabled = !saveStillRunning,
                    modifier = Modifier.testTag("workspace-tab-discard-confirm"),
                ) { Text(if (hasUnsavedChanges) "Discard changes" else "Close tab") }
            },
            dismissButton = {
                TextButton(
                    onClick = { dismissTabClosePrompt() },
                    modifier = Modifier.testTag("workspace-tab-discard-cancel"),
                ) { Text("Keep editing") }
            },
        )
    }

    if (showDiscardWorkspaceDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardWorkspaceDialog = false },
            title = { Text("Discard unsaved changes?") },
            text = { Text("One or more open files have unsaved edits. Close the workspace and discard those edits?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardWorkspaceDialog = false
                        onClose()
                    },
                    modifier = Modifier.testTag("workspace-discard-all-confirm"),
                ) { Text("Discard changes and close") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDiscardWorkspaceDialog = false },
                    modifier = Modifier.testTag("workspace-discard-all-cancel"),
                ) { Text("Keep editing") }
            },
        )
    }

    operationEntry?.let { entry ->
        val kind = operationKind.orEmpty()
        AlertDialog(onDismissRequest = { operationEntry = null; operationKind = null },
            title = { Text(when (kind) {
                "rename" -> "Rename " + entry.name
                "copy" -> "Copy " + entry.name
                "extract" -> "Extract " + entry.name
                else -> "Move " + entry.name
            }) },
            text = { Column {
                Text(
                    when (kind) {
                        "rename" -> "Enter a new name for this item."
                        "copy" -> "Enter the new name for the copy in this folder."
                        "extract" -> "ZIP files cannot be edited directly. Extract the contents first, then open the extracted files to edit them. Choose a destination folder; existing folders will not be overwritten."
                        else -> "Enter the destination path relative to the workspace root."
                    },
                    modifier = if (kind == "extract") Modifier.testTag("workspace-extract-explanation") else Modifier,
                )
                OutlinedTextField(value = operationInput, onValueChange = { operationInput = it },
                    label = { Text(if (kind == "move" || kind == "extract") "Destination path" else "Name") },
                    modifier = Modifier.fillMaxWidth().testTag("workspace-operation-input"))
            } },
            confirmButton = {
                TextButton(onClick = {
                    val raw = operationInput.trim()
                    scope.launch {
                        val result = runCatching {
                            withContext(Dispatchers.IO) {
                                val source = resolveWorkspacePath(root, entry.path)
                                val destinationPath = if (kind == "move" || kind == "extract") raw else {
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
                                    "extract" -> {
                                        WorkspaceArchive.extractZip(root, entry.path, destinationPath)
                                        syncLinkedWorkspacePaths(root, externalRoot, syncedPaths, destinationPath)
                                    }
                                    else -> throw IOException("Unknown file action.")
                                }
                            }
                        }
                        result.onSuccess {
                            operationEntry = null; operationKind = null; refreshKey++
                            message = when (kind) {
                                "rename" -> "Renamed " + entry.name
                                "copy" -> "Copied " + entry.name
                                "extract" -> "Extracted " + entry.name
                                else -> "Moved " + entry.name
                            }
                        }.onFailure { error -> message = error.message ?: "Could not complete the file action." }
                    }
                }, modifier = Modifier.testTag("confirm-workspace-operation")) {
                    Text(when (kind) { "rename" -> "Rename"; "copy" -> "Copy"; "extract" -> "Extract"; else -> "Move" })
                }
            },
            dismissButton = { TextButton(onClick = { operationEntry = null; operationKind = null }) { Text("Cancel") } })
    }


    bulkActionKind?.let { kind ->
        val selected = selectedPaths.sorted()
        AlertDialog(
            onDismissRequest = { bulkActionKind = null },
            title = {
                Text(when (kind) {
                    "zip" -> "Move ${selected.size} item(s) into ZIP"
                    "move" -> "Move ${selected.size} item(s)"
                    "copy" -> "Copy ${selected.size} item(s)"
                    else -> "Delete ${selected.size} item(s)?"
                })
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(when (kind) {
                        "zip" -> "Create a ZIP containing the selected files and folders, then remove the originals only after the archive is written successfully."
                        "move" -> "Enter a destination folder relative to the workspace root. The folder will be created if needed."
                        "copy" -> "Copy selected items into a destination folder. The folder will be created if needed."
                        else -> "Delete all selected items and their contents. Linked external copies will also be deleted where they exist."
                    })
                    if (kind != "delete") {
                        OutlinedTextField(
                            value = bulkInput,
                            onValueChange = { bulkInput = it },
                            label = { Text(if (kind == "zip") "ZIP file name" else "Destination folder path") },
                            modifier = Modifier.fillMaxWidth().testTag("workspace-bulk-input"),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val raw = bulkInput.trim()
                        val paths = selected
                        scope.launch {
                            val trashedItems = mutableListOf<WorkspaceTrashItem>()
                            val result = runCatching {
                                withContext(Dispatchers.IO) {
                                    if (paths.isEmpty()) throw IOException("Select at least one item.")
                                    var links = syncedPaths
                                    val summary = when (kind) {
                                        "zip" -> {
                                            val zipName = when {
                                                raw.isBlank() -> throw IOException("Enter a ZIP file name.")
                                                raw.lowercase().endsWith(".zip") -> raw
                                                else -> "$raw.zip"
                                            }
                                            if (!isSimpleWorkspaceName(zipName)) throw IOException("Enter a ZIP file name without path separators.")
                                            val archivePath = joinWorkspacePath(currentDirectory, zipName)
                                            WorkspaceArchive.moveToZip(root, paths, archivePath, currentDirectory)
                                            paths.sortedByDescending { it.count { ch -> ch == '/' } }.forEach { path ->
                                                links = updateSyncedPathsAfterDelete(context, root, externalRoot, links, path)
                                            }
                                            syncLinkedWorkspacePaths(root, externalRoot, links, archivePath)
                                            "Moved selected items into $zipName"
                                        }
                                        "move", "copy" -> {
                                            val destinationFolder = if (raw.isBlank()) root.canonicalFile else resolveWorkspacePath(root, raw)
                                            if (destinationFolder.exists() && !destinationFolder.isDirectory) throw IOException("The destination is not a folder.")
                                            val destinations = paths.map { sourcePath ->
                                                val source = resolveWorkspacePath(root, sourcePath)
                                                val targetPath = joinWorkspacePath(raw, source.name)
                                                val target = resolveWorkspacePath(root, targetPath)
                                                if (target == source) throw IOException("The destination is the current folder.")
                                                if (source.isDirectory && target.path.startsWith(source.path + File.separator)) {
                                                    throw IOException("A folder cannot be moved into itself.")
                                                }
                                                if (target.exists()) throw IOException("Destination already exists: $targetPath")
                                                targetPath
                                            }
                                            if (destinations.toSet().size != destinations.size) throw IOException("Multiple items have the same destination.")
                                            if (!destinationFolder.exists() && !destinationFolder.mkdirs()) throw IOException("Could not create the destination folder.")
                                            paths.zip(destinations).forEach { (sourcePath, targetPath) ->
                                                if (kind == "move") {
                                                    val source = resolveWorkspacePath(root, sourcePath)
                                                    val target = resolveWorkspacePath(root, targetPath)
                                                    target.parentFile?.let { if (!it.exists() && !it.mkdirs()) throw IOException("Could not create destination parent.") }
                                                    try { Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE) }
                                                    catch (_: java.nio.file.AtomicMoveNotSupportedException) { Files.move(source.toPath(), target.toPath()) }
                                                    links = updateSyncedPathsAfterMove(context, root, externalRoot, links, sourcePath, targetPath)
                                                } else {
                                                    copyWorkspaceEntry(root, sourcePath, targetPath)
                                                    syncLinkedWorkspacePaths(root, externalRoot, links, targetPath)
                                                }
                                            }
                                            (if (kind == "move") "Moved " else "Copied ") + paths.size + " item(s)"
                                        }
                                        else -> {
                                            paths.sortedByDescending { it.count { ch -> ch == '/' } }.forEach { path ->
                                                trashedItems += WorkspaceRecovery.moveToTrash(root, path)
                                                links = updateSyncedPathsAfterDelete(context, root, externalRoot, links, path)
                                            }
                                            "Moved ${paths.size} item(s) to Trash"
                                        }
                                    }
                                    Triple(links, summary, trashedItems.toList())
                                }
                            }
                            result.onSuccess { (links, summary, trashRecords) ->
                                syncedPaths = links
                                recentTrashIds = trashRecords.map { it.id }
                                bulkActionKind = null
                                selectedPaths = emptySet()
                                selectionMode = false
                                refreshKey++
                                message = if (trashRecords.isNotEmpty()) "$summary. Undo is available." else summary
                            }.onFailure { error ->
                                syncedPaths = loadSyncedPaths(context, root)
                                recentTrashIds = trashedItems.map { it.id }
                                bulkActionKind = null
                                selectedPaths = emptySet()
                                selectionMode = false
                                refreshKey++
                                message = if (trashedItems.isNotEmpty()) {
                                    "Partial completion: ${trashedItems.size} item(s) moved to Trash. Undo is available. " +
                                        (error.message ?: "A later item failed.")
                                } else {
                                    error.message ?: "Could not complete the bulk operation."
                                }
                            }
                        }
                    },
                    modifier = Modifier.testTag("confirm-workspace-bulk-action"),
                ) { Text(when (kind) { "zip" -> "Create ZIP & Move"; "copy" -> "Copy"; "move" -> "Move"; else -> "Delete" }) }
            },
            dismissButton = {
                TextButton(onClick = { bulkActionKind = null }) { Text("Cancel") }
            },
        )
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



private fun workspaceMetadataText(root: File, relativePath: String): String {
    val file = resolveWorkspacePath(root, relativePath)
    if (!file.exists()) throw IOException("Item no longer exists: " + relativePath)
    val attributes = Files.readAttributes(
        file.toPath(),
        java.nio.file.attribute.BasicFileAttributes::class.java,
        LinkOption.NOFOLLOW_LINKS,
    )
    val isDirectory = attributes.isDirectory
    val size = if (isDirectory) {
        Files.walk(file.toPath()).use { stream ->
            stream.iterator().asSequence()
                .filter { Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) }
                .sumOf { Files.size(it) }
        }
    } else attributes.size()
    val format = if (isDirectory) "Folder" else formatFileTypeLabel(file.name)
    return "Name: " + file.name +
        "\nType: " + (if (isDirectory) "Folder" else "File") +
        "\nSize: " + size + " bytes" +
        "\nCreated: " + attributes.creationTime().toInstant().toString() +
        "\nModified: " + attributes.lastModifiedTime().toInstant().toString() +
        "\nFormat: " + format +
        "\nStorage path: " + file.absolutePath
}

private fun workspaceArchiveMetadataText(root: File, archivePath: String, entryPath: String): String {
    val item = WorkspaceArchiveBrowser.metadata(root, archivePath, entryPath)
    return "Name: " + item.name +
        "\nType: " + (if (item.isDirectory) "Folder" else "File") +
        "\nSize: " + item.sizeBytes + " bytes" +
        "\nCreated: Not recorded by this ZIP entry" +
        "\nModified: " + (item.modifiedAtMillis?.let { java.time.Instant.ofEpochMilli(it).toString() } ?: "Not recorded") +
        "\nFormat: " + (if (item.isDirectory) "Folder" else formatFileTypeLabel(item.name)) +
        "\nStorage path: " + archivePath + "!/" + item.path
}

private fun readWorkspaceArchiveText(root: File, archivePath: String, entryPath: String): String {
    val extension = entryPath.substringAfterLast('.', "").lowercase()
    val supported = setOf(
        "kt", "kts", "java", "xml", "json", "md", "txt", "gradle", "properties",
        "html", "htm", "css", "js", "jsx", "ts", "tsx", "py", "sh", "bash",
        "c", "h", "cc", "cpp", "hpp", "yml", "yaml", "toml", "ini", "cfg",
        "conf", "sql", "rs", "go", "rb", "php", "dart", "swift", "log", "csv",
        "env", "gitignore", "makefile",
    )
    if (extension !in supported) {
        throw IOException("This file is not a supported text or source file. Open Metadata or extract it to use another viewer.")
    }
    val bytes = WorkspaceArchiveBrowser.read(root, archivePath, entryPath)
    if (bytes.size.toLong() > MAX_WORKSPACE_TEXT_BYTES) {
        throw IOException("Files larger than 8 MB cannot be edited in the current editor.")
    }
    if (bytes.any { it == 0.toByte() }) {
        throw IOException("This file contains binary data. It remains available inside the ZIP and can be extracted if needed.")
    }
    return bytes.toString(Charsets.UTF_8)
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
private fun joinWorkspacePath(parent: String, child: String): String =
    if (parent.isBlank()) child else "$parent/$child"

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
    return files.filterNot { file ->
        val candidate = if (relativePath.isEmpty()) file.name else relativePath + "/" + file.name
        WorkspaceRecovery.isInternalPath(candidate)
    }.map { file ->
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
    WorkspaceRecovery.writeAtomically(root, relativePath, bytes, MAX_WORKSPACE_TEXT_BYTES)
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
