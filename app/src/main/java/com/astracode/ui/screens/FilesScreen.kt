package com.astracode.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astracode.ui.models.AtelierFileItem

@Composable
fun FilesScreen(
    modifier: Modifier = Modifier,
    rootName: String,
    currentDirectory: String,
    storageLabel: String,
    items: List<AtelierFileItem>,
    selectedPaths: Set<String>,
    selectionMode: Boolean,
    savedWorkspaceCount: Int,
    onAddWorkspace: () -> Unit,
    onManageWorkspaces: () -> Unit,
    onNewFile: () -> Unit,
    onNewFolder: () -> Unit,
    onOpenStandaloneFile: () -> Unit,
    onManageSavedDocuments: () -> Unit,
    onOpenSafetyTools: () -> Unit,
    onSelectionModeChanged: (Boolean) -> Unit,
    onToggleSelection: (String) -> Unit,
    onBulkAction: (String) -> Unit,
    onOpenEntry: (String) -> Unit,
    onLongClick: (String) -> Unit,
    onInspectEntry: (String) -> Unit,
    onGoUp: () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "WORKSPACE / FILE SYSTEM",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    if (currentDirectory.isBlank()) rootName else currentDirectory.substringAfterLast('/'),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    storageLabel + " · " + savedWorkspaceCount + if (savedWorkspaceCount == 1) " saved folder" else " saved folders",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onAddWorkspace, modifier = Modifier.testTag("add-workspace-button")) {
                Icon(Icons.Outlined.FolderOpen, contentDescription = "Add workspace folder", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onManageWorkspaces, modifier = Modifier.testTag("manage-workspaces-button")) {
                Icon(Icons.Outlined.Folder, contentDescription = "Manage workspaces", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onNewFolder, modifier = Modifier.testTag("new-folder-button")) {
                Icon(Icons.Outlined.Add, contentDescription = "New folder", tint = MaterialTheme.colorScheme.primary)
            }
            Button(
                onClick = onNewFile,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("new-file-button"),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            ) { Text("New file", maxLines = 1) }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onOpenStandaloneFile,
                modifier = Modifier.weight(1f).testTag("open-standalone-file-button"),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text("Open file", maxLines = 1)
            }
            OutlinedButton(
                onClick = onManageSavedDocuments,
                modifier = Modifier.weight(1f).testTag("manage-saved-documents-button"),
                shape = RoundedCornerShape(12.dp),
            ) { Text("Saved files", maxLines = 1) }
            OutlinedButton(
                onClick = onOpenSafetyTools,
                modifier = Modifier.weight(1f).testTag("workspace-safety-tools"),
                shape = RoundedCornerShape(12.dp),
            ) { Text("Safety", maxLines = 1) }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 14.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (currentDirectory.isBlank()) rootName else "$rootName / $currentDirectory",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    items.size.toString() + if (items.size == 1) " ITEM" else " ITEMS",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.2.sp,
                )
            }
            if (currentDirectory.isNotBlank()) {
                TextButton(onClick = onGoUp, enabled = !selectionMode, modifier = Modifier.testTag("workspace-go-up")) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Up")
                }
            }
            TextButton(
                onClick = { onSelectionModeChanged(!selectionMode) },
                modifier = Modifier.testTag("workspace-toggle-selection"),
            ) { Text(if (selectionMode) "Cancel" else "Select") }
        }

        if (selectionMode) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            selectedPaths.size.toString() + " selected",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = { onBulkAction("zip") },
                            enabled = selectedPaths.isNotEmpty(),
                            modifier = Modifier.testTag("workspace-bulk-zip"),
                        ) { Text("Create ZIP") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { onBulkAction("copy") }, enabled = selectedPaths.isNotEmpty(), modifier = Modifier.testTag("workspace-bulk-copy")) { Text("Copy") }
                        TextButton(onClick = { onBulkAction("move") }, enabled = selectedPaths.isNotEmpty(), modifier = Modifier.testTag("workspace-bulk-move")) { Text("Move") }
                        TextButton(onClick = { onBulkAction("delete") }, enabled = selectedPaths.isNotEmpty(), modifier = Modifier.testTag("workspace-bulk-delete")) { Text("Move to Trash", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }

        Divider(color = MaterialTheme.colorScheme.outlineVariant)
        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(38.dp))
                    Text("This folder is empty", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
                    Text("Create a file or folder to get started.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    Button(onClick = onNewFile, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary), modifier = Modifier.testTag("new-file-button")) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("New file")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(items, key = { it.path }) { item ->
                    AtelierFileRow(
                        item = item,
                        selected = item.path in selectedPaths,
                        selectionMode = selectionMode,
                        onClick = { if (selectionMode) onToggleSelection(item.path) else onOpenEntry(item.path) },
                        onLongClick = { if (selectionMode) onToggleSelection(item.path) else onLongClick(item.path) },
                        onInspect = { onInspectEntry(item.path) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AtelierFileRow(
    item: AtelierFileItem,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onInspect: () -> Unit,
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick).testTag("entry-${item.name}"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { onClick() }, modifier = Modifier.testTag("workspace-select-${item.name}"))
            }
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(
                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                contentAlignment = Alignment.Center,
            ) {
                val icon = when {
                    item.isDirectory -> Icons.Outlined.Folder
                    item.kind.equals("archive", ignoreCase = true) || item.name.endsWith(".zip", true) -> Icons.Outlined.Archive
                    else -> Icons.Outlined.Description
                }
                Icon(
                    imageVector = icon,
                    contentDescription = item.kind,
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp).testTag("entry-icon-${item.name}"),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.name, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    buildString {
                        if (item.name.startsWith(".") && item.isDirectory) append("Hidden folder · ")
                        else if (item.name.startsWith(".") && !item.isDirectory) append("Hidden file · ")
                        if (item.childCount != null && item.isDirectory) append(item.childCount).append(" items")
                        else append(formatSize(item.sizeBytes))
                        if (item.modifiedLabel.isNotBlank()) append(" · ").append(item.modifiedLabel)
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selectionMode && selected) {
                Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            } else {
                IconButton(onClick = onInspect, modifier = Modifier.size(36.dp).testTag("workspace-inspect-${item.path}")) {
                    Icon(Icons.Outlined.Info, contentDescription = "Inspect ${item.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onLongClick, modifier = Modifier.size(36.dp).testTag("workspace-entry-options-${item.path}")) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "${item.name} options", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024L * 1024 -> String.format("%.1f KB", bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    else -> String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
}
