package com.astracode.check

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.File

private const val PREFS_NAME = "file_access_check"
private const val SAVED_URIS_KEY = "saved_document_uris"
private const val ACTIVE_URI_KEY = "active_document_uri"
private const val MAX_DOCUMENT_BYTES = 8L * 1024L * 1024L

private data class SavedDocument(
    val uri: Uri,
    val name: String,
    val canWrite: Boolean,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val initialDocuments = decodeUris(preferences.getString(SAVED_URIS_KEY, null))
            .mapNotNull { uri -> runCatching { documentBookmark(uri) }.getOrNull() }
        val initialActive = preferences.getString(ACTIVE_URI_KEY, null)
            ?.let(Uri::parse)
            ?.let { uri -> initialDocuments.firstOrNull { it.uri == uri } }

        setContent {
            var savedDocuments by remember { mutableStateOf(initialDocuments) }
            var activeDocument by remember { mutableStateOf(initialActive) }
            var editorText by remember { mutableStateOf("") }
            var savedText by remember { mutableStateOf("") }
            var loading by remember { mutableStateOf(initialActive != null) }
            var showSavedFiles by remember { mutableStateOf(false) }
            var showWorkspaceBrowser by remember { mutableStateOf(false) }
            var showAtelierPreview by remember { mutableStateOf(false) }
            var message by remember { mutableStateOf("") }
            val scope = rememberCoroutineScope()

            fun saveBookmarks(documents: List<SavedDocument>) {
                savedDocuments = documents
                preferences.edit()
                    .putString(SAVED_URIS_KEY, encodeUris(documents.map { it.uri }))
                    .apply()
            }

            fun closeFile() {
                activeDocument = null
                editorText = ""
                savedText = ""
                loading = false
                preferences.edit().remove(ACTIVE_URI_KEY).apply()
            }

            fun openPickedFile(uri: Uri) {
                val wasSaved = savedDocuments.any { it.uri == uri }
                val hadPermission = contentResolver.persistedUriPermissions.any { it.uri == uri }
                runCatching {
                    persistReadWritePermission(uri)
                    val bookmark = documentBookmark(uri)
                    require(isSupportedTextDocument(uri, bookmark.name)) {
                        "Choose a text or source-code file."
                    }
                    bookmark
                }.onSuccess { bookmark ->
                    val updated = if (wasSaved) {
                        savedDocuments.map { if (it.uri == uri) bookmark else it }
                    } else {
                        savedDocuments + bookmark
                    }
                    saveBookmarks(updated)
                    activeDocument = bookmark
                    loading = true
                    message = "Opened and saved " + bookmark.name
                    preferences.edit().putString(ACTIVE_URI_KEY, uri.toString()).apply()
                }.onFailure { error ->
                    if (!wasSaved && !hadPermission) releasePermission(uri)
                    message = error.message ?: "Could not open this file."
                }
            }

            val documentPicker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument(),
                onResult = { uri -> if (uri != null) openPickedFile(uri) },
            )

            LaunchedEffect(activeDocument?.uri) {
                val selected = activeDocument
                if (selected == null) {
                    editorText = ""
                    savedText = ""
                    loading = false
                } else {
                    loading = true
                    try {
                        val loaded = withContext(Dispatchers.IO) {
                            readDocument(this@MainActivity, selected.uri)
                        }
                        editorText = loaded
                        savedText = loaded
                        loading = false
                        message = "Opened " + selected.name
                    } catch (error: Exception) {
                        loading = false
                        message = error.message ?: "Could not read this file."
                        activeDocument = null
                        preferences.edit().remove(ACTIVE_URI_KEY).apply()
                    }
                }
            }

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text("Standalone file access check", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                            TextButton(onClick = { showAtelierPreview = true }, modifier = Modifier.testTag("open-atelier-preview")) { Text("UI Preview") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { documentPicker.launch(arrayOf("*/*")) },
                                modifier = Modifier.testTag("open-standalone-file-button"),
                            ) {
                                Icon(Icons.Filled.Description, contentDescription = null)
                                Spacer(Modifier.padding(3.dp))
                                Text("Open file")
                            }
                            Button(
                                onClick = { showSavedFiles = true },
                                modifier = Modifier.testTag("manage-saved-documents-button"),
                            ) {
                                Icon(Icons.Filled.Folder, contentDescription = null)
                                Spacer(Modifier.padding(3.dp))
                                Text("Saved files")
                            }
                            IconButton(
                                onClick = { showWorkspaceBrowser = true },
                                modifier = Modifier.testTag("open-workspace-browser-button"),
                            ) {
                                Icon(Icons.Filled.Folder, contentDescription = "Workspace files")
                            }
                        }
                        if (showAtelierPreview) {
                            AtelierPreviewScreen(onBack = { showAtelierPreview = false })
                        } else if (showWorkspaceBrowser) {
                            WorkspaceBrowserPanel(
                                root = File(filesDir, "workspace"),
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                onClose = { showWorkspaceBrowser = false },
                            )
                        } else {
                        if (message.isNotBlank()) {
                            Text(message, modifier = Modifier.testTag("file-status-message"))
                        }
                        val selected = activeDocument
                        if (selected == null) {
                            Text("No standalone file open.")
                        } else {
                            Text(selected.name, style = MaterialTheme.typography.titleMedium)
                            when {
                                loading -> Text("Opening " + selected.name, modifier = Modifier.testTag("file-loading"))
                                else -> {
                                    OutlinedTextField(
                                        value = editorText,
                                        onValueChange = { editorText = it },
                                        readOnly = !selected.canWrite,
                                        modifier = Modifier.fillMaxWidth().weight(1f).testTag("file-editor-input"),
                                        label = { Text(if (selected.canWrite) "File contents" else "Read-only file contents") },
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    runCatching {
                                                        withContext(Dispatchers.IO) {
                                                            writeDocument(this@MainActivity, selected.uri, editorText)
                                                        }
                                                    }.onSuccess {
                                                        savedText = editorText
                                                        message = "Saved " + selected.name
                                                    }.onFailure { error ->
                                                        message = error.message ?: "Could not save this file."
                                                    }
                                                }
                                            },
                                            enabled = selected.canWrite && editorText != savedText,
                                            modifier = Modifier.testTag("save-file-button"),
                                        ) { Text("Save") }
                                        TextButton(onClick = { closeFile() }) { Text("Close file") }
                                    }
                                }
                            }
                        }
                        }
                    }
                }
            }

            if (showSavedFiles) {
                AlertDialog(
                    onDismissRequest = { showSavedFiles = false },
                    title = { Text("Saved files") },
                    text = {
                        Column {
                            Text(
                                "Files stay here until you remove them. Removing a bookmark only removes AstraCode's access; it does not delete the original file.",
                            )
                            Spacer(Modifier.height(8.dp))
                            if (savedDocuments.isEmpty()) {
                                Text(
                                    "No files saved yet. Choose a file to add one.",
                                    modifier = Modifier.testTag("empty-saved-files-message"),
                                )
                            } else {
                                LazyColumn(modifier = Modifier.fillMaxWidth().height(240.dp)) {
                                    items(savedDocuments, key = { it.uri.toString() }) { bookmark ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            TextButton(
                                                onClick = {
                                                    activeDocument = bookmark
                                                    preferences.edit().putString(ACTIVE_URI_KEY, bookmark.uri.toString()).apply()
                                                    showSavedFiles = false
                                                },
                                                modifier = Modifier.weight(1f),
                                            ) { Text(bookmark.name) }
                                            IconButton(
                                                onClick = {
                                                    val remaining = savedDocuments.filterNot { it.uri == bookmark.uri }
                                                    saveBookmarks(remaining)
                                                    if (activeDocument?.uri == bookmark.uri) closeFile()
                                                    releasePermission(bookmark.uri)
                                                },
                                            ) {
                                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove " + bookmark.name)
                                            }
                                        }
                                        Divider()
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showSavedFiles = false
                                documentPicker.launch(arrayOf("*/*"))
                            },
                            modifier = Modifier.testTag("choose-standalone-file-button"),
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Text("Choose file")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showSavedFiles = false }) { Text("Done") }
                    },
                )
            }
        }
    }

    private fun documentBookmark(uri: Uri): SavedDocument {
        val displayName = runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
        val name = displayName?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/')
            ?: "External file"
        val permission = contentResolver.persistedUriPermissions.firstOrNull { it.uri == uri }
        return SavedDocument(uri, name, permission?.isWritePermission == true)
    }

    private fun persistReadWritePermission(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun releasePermission(uri: Uri) {
        val permission = contentResolver.persistedUriPermissions.firstOrNull { it.uri == uri } ?: return
        var flags = 0
        if (permission.isReadPermission) flags = flags or Intent.FLAG_GRANT_READ_URI_PERMISSION
        if (permission.isWritePermission) flags = flags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        if (flags != 0) runCatching { contentResolver.releasePersistableUriPermission(uri, flags) }
    }

    private fun isSupportedTextDocument(uri: Uri, name: String): Boolean {
        val mime = contentResolver.getType(uri).orEmpty().lowercase()
        val extension = name.substringAfterLast('.', "").lowercase()
        val knownTextExtensions = setOf(
            "kt", "kts", "java", "xml", "json", "txt", "gradle", "properties",
            "html", "htm", "css", "js", "ts", "tsx", "py", "sh", "c", "h", "cpp",
            "hpp", "yml", "yaml", "toml", "ini", "sql", "rs", "go", "rb", "dart",
            "log", "csv", "md", "gitignore", "makefile",
        )
        return mime.startsWith("text/") ||
            mime in setOf("application/json", "application/xml", "application/javascript", "application/x-sh") ||
            extension in knownTextExtensions
    }
}

private fun decodeUris(encoded: String?): List<Uri> {
    if (encoded.isNullOrBlank()) return emptyList()
    return runCatching {
        val json = JSONArray(encoded)
        List(json.length()) { index -> Uri.parse(json.getString(index)) }.distinctBy { it.toString() }
    }.getOrDefault(emptyList())
}

private fun encodeUris(uris: List<Uri>): String =
    JSONArray().apply { uris.distinctBy { it.toString() }.forEach { put(it.toString()) } }.toString()

private fun readDocument(context: Context, uri: Uri): String {
    val input = context.contentResolver.openInputStream(uri)
        ?: throw IOException("The selected provider could not open this file.")
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    var total = 0L
    input.use { stream ->
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            total += count
            if (total > MAX_DOCUMENT_BYTES) throw IOException("Files larger than 8 MB are not supported yet.")
            output.write(buffer, 0, count)
        }
    }
    return output.toByteArray().toString(Charsets.UTF_8)
}

private fun writeDocument(context: Context, uri: Uri, content: String) {
    val bytes = content.toByteArray(Charsets.UTF_8)
    if (bytes.size.toLong() > MAX_DOCUMENT_BYTES) throw IOException("Files larger than 8 MB are not supported yet.")
    val output = context.contentResolver.openOutputStream(uri, "wt")
        ?: throw IOException("The selected provider could not open this file for writing.")
    output.use { stream ->
        stream.write(bytes)
        stream.flush()
    }
}
