package com.astracode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astracode.ui.models.AtelierFileItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickInspectorSheet(
    item: AtelierFileItem,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onCreateZip: () -> Unit,
    onRename: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onMoveToTrash: () -> Unit,
    onSync: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = {
            Box(
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp).size(width = 42.dp, height = 4.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant, CircleShape),
            )
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("QUICK INSPECTOR", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.5.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(14.dp)) {
                    Icon(
                        if (item.isDirectory) Icons.Outlined.FolderOpen else Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(13.dp).size(25.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(item.name, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(item.path, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(item.kind + " · " + formatSize(item.sizeBytes), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelSmall)
                }
            }
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DetailLine("Format", item.formatLabel ?: item.kind)
                    DetailLine("Created", item.createdLabel ?: "Not recorded by provider")
                    DetailLine("Modified", item.modifiedLabel.ifBlank { "Not recorded by provider" })
                    item.childCount?.let { DetailLine("Direct children", it.toString()) }
                }
            }
            Button(
                onClick = onOpen,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.FolderOpen, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (item.isDirectory) "Open folder" else "Open item")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ActionButton("Move into ZIP", onCreateZip, Modifier.weight(1f), Icons.Outlined.Archive)
                ActionButton("Rename", onRename, Modifier.weight(1f), Icons.Outlined.DriveFileRenameOutline)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ActionButton("Copy", onCopy, Modifier.weight(1f), Icons.Outlined.ContentCopy)
                ActionButton("Move", onMove, Modifier.weight(1f), Icons.Outlined.MoveToInbox)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ActionButton("Sync", onSync, Modifier.weight(1f), Icons.Outlined.Sync)
            }
            OutlinedButton(
                onClick = onMoveToTrash,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Move to Trash")
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(90.dp))
        Text(value, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ActionButton(label: String, onClick: () -> Unit, modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    OutlinedButton(onClick = onClick, shape = CircleShape, modifier = modifier) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, maxLines = 1)
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024L * 1024 -> String.format("%.1f KB", bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    else -> String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
}
