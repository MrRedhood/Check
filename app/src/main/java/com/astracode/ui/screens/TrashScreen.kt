package com.astracode.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astracode.ui.models.AtelierTrashItem

@Composable
fun TrashScreen(
    modifier: Modifier = Modifier,
    items: List<AtelierTrashItem>,
    isLoading: Boolean,
    message: String?,
    onRefresh: () -> Unit,
    onRestore: (AtelierTrashItem, String?) -> Unit,
) {
    val alternateDestination = remember { mutableStateOf("") }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("RECOVERY ZONE", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.5.sp)
            Text("Trash & restore", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("Restore items without overwriting existing files.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }

        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("LIFECYCLE RETENTION", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.1.sp, modifier = Modifier.weight(1f))
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
                        Text("30 DAYS", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
                    }
                }
                Text("Isolated staging", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "Deleted items are kept in workspace recovery storage. Expired records are eligible for cleanup when retention maintenance runs.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(items.size.toString() + if (items.size == 1) " item in Trash" else " items in Trash", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    TextButton(onClick = onRefresh, enabled = !isLoading) {
                        androidx.compose.material3.Icon(Icons.Outlined.Refresh, contentDescription = null)
                        Text("Refresh")
                    }
                }
            }
        }

        if (isLoading) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(strokeWidth = 2.dp)
                Text("Loading recovery records…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (!message.isNullOrBlank()) {
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }

        if (items.isEmpty() && !isLoading) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Trash is empty", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    Text("Items moved to Trash will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        } else if (items.isNotEmpty()) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Alternate restore path (optional)", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = alternateDestination.value,
                        onValueChange = { alternateDestination.value = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Workspace-relative destination") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                    )
                    Text("Leave empty to restore to the original path. If a destination already exists, the restore will be refused.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
            items.forEachIndexed { index, item ->
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(item.originalPath.substringAfterLast('/'), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(item.originalPath, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("Deleted: " + item.deletedLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                            }
                            Text("TRASH", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelSmall)
                        }
                        Divider(color = MaterialTheme.colorScheme.outlineVariant)
                        Button(
                            onClick = { onRestore(item, alternateDestination.value.trim().takeIf { it.isNotEmpty() }) },
                            enabled = !isLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                            shape = CircleShape,
                            modifier = Modifier.fillMaxWidth().testTag("trash-restore-${item.id}"),
                        ) {
                            androidx.compose.material3.Icon(Icons.Outlined.Restore, contentDescription = null)
                            Spacer(Modifier.padding(2.dp))
                            Text("Restore item")
                        }
                    }
                }
            }
        }
    }
}
