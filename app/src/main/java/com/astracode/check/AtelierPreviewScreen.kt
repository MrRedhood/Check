package com.astracode.check

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.astracode.ui.components.AstraCodeBottomNav
import com.astracode.ui.components.CreateZipModal
import com.astracode.ui.components.QuickInspectorSheet
import com.astracode.ui.components.Screen
import com.astracode.ui.models.AtelierDuplicateGroup
import com.astracode.ui.models.AtelierFileItem
import com.astracode.ui.models.AtelierSearchResult
import com.astracode.ui.models.AtelierStorageHealth
import com.astracode.ui.models.AtelierTrashItem
import com.astracode.ui.screens.FilesScreen
import com.astracode.ui.screens.SafetyScreen
import com.astracode.ui.screens.SearchScreen
import com.astracode.ui.screens.TrashScreen
import com.astracode.ui.theme.AstraCodeTheme

@Composable
fun AtelierPreviewScreen(onBack: () -> Unit) {
    val currentScreen = remember { mutableStateOf<Screen>(Screen.Files) }
    val selectedPaths = remember { mutableStateOf(setOf<String>()) }
    val selectionMode = remember { mutableStateOf(false) }
    val message = remember { mutableStateOf<String?>(null) }
    val showZip = remember { mutableStateOf(false) }
    val zipName = remember { mutableStateOf("workspace-snapshot.zip") }
    val inspector = remember { mutableStateOf<AtelierFileItem?>(null) }
    val demoFiles = listOf(
        AtelierFileItem("src", "src", "Folder", true, 0, "modified recently", childCount = 8),
        AtelierFileItem("src/Main.kt", "Main.kt", "Kotlin", false, 15120, "modified 12 min ago", formatLabel = "KOTLIN"),
        AtelierFileItem("assets/logo.webp", "logo.webp", "Image", false, 184200, "modified yesterday", formatLabel = "WEBP"),
        AtelierFileItem("release.zip", "release.zip", "Archive", false, 32800000, "modified yesterday", formatLabel = "ZIP"),
    )
    val demoHits = listOf(
        AtelierSearchResult("src/Main.kt", "Main.kt", "Kotlin", 15120, "modified 12 min ago"),
        AtelierSearchResult("app/build.gradle.kts", "build.gradle.kts", "Gradle", 2048, "modified yesterday"),
    )
    val demoHealth = AtelierStorageHealth(
        totalBytes = 48200000,
        fileCount = 14,
        directoryCount = 6,
        largestFiles = listOf("release.zip" to 32800000L, "assets/logo.webp" to 184200L, "src/Main.kt" to 15120L),
        storageLabel = "Demo fixture · no workspace connected",
    )
    val demoTrash = listOf(
        AtelierTrashItem("preview-1", "src/OldMain.kt", 0L, "demo record · date not real"),
        AtelierTrashItem("preview-2", "assets/old-logo.webp", 0L, "demo record · date not real"),
    )

    AstraCodeTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("OBSIDIAN ATELIER", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                    Text("Design preview", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge)
                    Text("Sample content only · no files are changed in this preview", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onBack) { Text("Back to storage test") }
                }
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (currentScreen.value) {
                    Screen.Files -> FilesScreen(
                        rootName = "AstraCode workspace", currentDirectory = "", storageLabel = "Preview mode",
                        items = demoFiles, selectedPaths = selectedPaths.value, selectionMode = selectionMode.value, savedWorkspaceCount = 0,
                        onAddWorkspace = { message.value = "Preview only: storage picker is not connected here." },
                        onManageWorkspaces = { message.value = "Preview only: workspace management is not connected." },
                        onNewFile = { message.value = "Preview only: no file was created." },
                        onNewFolder = { message.value = "Preview only: no folder was created." },
                        onOpenStandaloneFile = { message.value = "Preview only: no external file was opened." },
                        onManageSavedDocuments = { message.value = "Preview only: saved-file management is not connected." },
                        onOpenSafetyTools = { currentScreen.value = Screen.Safety },
                        onSelectionModeChanged = { selectionMode.value = it; if (!it) selectedPaths.value = emptySet() },
                        onToggleSelection = { path -> selectedPaths.value = if (path in selectedPaths.value) selectedPaths.value - path else selectedPaths.value + path },
                        onBulkAction = { if (it == "zip") { zipName.value = "workspace-snapshot.zip"; showZip.value = true } else message.value = "Preview only: action not executed." },
                        onOpenEntry = { message.value = "Preview only: item selected." },
                        onLongClick = { path -> demoFiles.firstOrNull { it.path == path }?.let { inspector.value = it } },
                        onInspectEntry = { path -> demoFiles.firstOrNull { it.path == path }?.let { inspector.value = it } },
                        onGoUp = { message.value = "Already at preview root." },
                    )
                    Screen.Search -> SearchScreen(
                        hits = demoHits, busy = false,
                        statusMessage = message.value ?: "Demo results only; no workspace search was run.",
                        duplicates = listOf(AtelierDuplicateGroup("demo-hash-not-real", 2048, listOf("src/Config.kt", "backup/Config.kt"))),
                        duplicatesScanned = true, skippedDuplicateFiles = 0, preview = null,
                        onSearch = { _, _, _, _, _ -> message.value = "Preview only: no search was run." },
                        onFindDuplicates = { message.value = "Preview only: no file hashes were scanned." },
                        onOpenPath = { message.value = "Preview only: no file was opened." },
                        onPreviewPath = { message.value = "Preview only: no file was previewed." },
                        onClosePreview = { },
                    )
                    Screen.Safety -> SafetyScreen(
                        health = demoHealth, isLoading = false,
                        message = "Demonstration metrics only; not device storage capacity.",
                        onRefresh = { message.value = "Preview only: no storage scan was run." },
                        onOpenAdvancedTools = { message.value = "Preview only: advanced tools are not connected." },
                    )
                    Screen.Trash -> TrashScreen(
                        items = demoTrash, isLoading = false,
                        message = "Sample entries only; these are not real deleted files.",
                        onRefresh = { message.value = "Preview only: no Trash records were loaded." },
                        onRestore = { _, _ -> message.value = "Preview only: nothing was restored." },
                    )
                }
                }
                message.value?.let { Text(it, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
                AstraCodeBottomNav(currentScreen.value, onTabSelected = { currentScreen.value = it })
            }
        }
    }

    if (showZip.value) {
        CreateZipModal(
            selectedCount = selectedPaths.value.size.coerceAtLeast(1),
            fileName = zipName.value,
            onFileNameChange = { zipName.value = it },
            isBusy = false,
            onDismiss = { showZip.value = false },
            onCreate = { showZip.value = false; message.value = "Preview only: no ZIP was created." },
        )
    }
    inspector.value?.let { item ->
        QuickInspectorSheet(
            item = item,
            onDismiss = { inspector.value = null },
            onOpen = { message.value = "Preview only: no file was opened."; inspector.value = null },
            onCreateZip = { showZip.value = true; inspector.value = null },
            onRename = { message.value = "Preview only: no rename was performed."; inspector.value = null },
            onCopy = { message.value = "Preview only: no copy was performed."; inspector.value = null },
            onMove = { message.value = "Preview only: no move was performed."; inspector.value = null },
            onMoveToTrash = { message.value = "Preview only: nothing was moved to Trash."; inspector.value = null },
        )
    }
}
