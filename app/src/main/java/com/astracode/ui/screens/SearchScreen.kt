package com.astracode.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astracode.ui.models.AtelierDuplicateGroup
import com.astracode.ui.models.AtelierPreview
import com.astracode.ui.models.AtelierSearchResult

@Composable
fun SearchScreen(
    hits: List<AtelierSearchResult>,
    busy: Boolean,
    statusMessage: String?,
    duplicates: List<AtelierDuplicateGroup>,
    duplicatesScanned: Boolean,
    skippedDuplicateFiles: Int,
    preview: AtelierPreview?,
    onSearch: (String, String, String, String, Boolean) -> Unit,
    onFindDuplicates: () -> Unit,
    onOpenPath: (String) -> Unit,
    onPreviewPath: (String) -> Unit,
    onClosePreview: () -> Unit,
) {
    var query = remember { androidx.compose.runtime.mutableStateOf("") }
    var extension = remember { androidx.compose.runtime.mutableStateOf("") }
    var minimumSize = remember { androidx.compose.runtime.mutableStateOf("") }
    var maximumSize = remember { androidx.compose.runtime.mutableStateOf("") }
    var includeHidden = remember { androidx.compose.runtime.mutableStateOf(false) }
    val previewBitmap = remember(preview?.imageBytes) {
        preview?.imageBytes?.let { bytes ->
            runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("FIND IN WORKSPACE", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.5.sp)
            Text("Search & inspect", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("Search names and paths, then inspect files before changing anything.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }

        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query.value,
                    onValueChange = { query.value = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Name or path contains") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                )
                OutlinedTextField(
                    value = extension.value,
                    onValueChange = { extension.value = it.removePrefix(".") },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Extension (optional, e.g. kt)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = minimumSize.value,
                        onValueChange = { minimumSize.value = it.filter(Char::isDigit) },
                        modifier = Modifier.weight(1f),
                        label = { Text("Min bytes") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                    )
                    OutlinedTextField(
                        value = maximumSize.value,
                        onValueChange = { maximumSize.value = it.filter(Char::isDigit) },
                        modifier = Modifier.weight(1f),
                        label = { Text("Max bytes") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = includeHidden.value, onCheckedChange = { includeHidden.value = it })
                    Text("Include hidden files and folders", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    onClick = { onSearch(query.value, extension.value, minimumSize.value, maximumSize.value, includeHidden.value) },
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    IconSearchLabel("Search workspace")
                }
            }
        }

        if (!statusMessage.isNullOrBlank()) {
            Text(statusMessage, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("RESULTS", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.3.sp, modifier = Modifier.weight(1f))
                Text(hits.size.toString(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
            if (hits.isEmpty() && !statusMessage.isNullOrBlank() && !busy) {
                Text("No matching items yet. Refine the query or search again.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            hits.forEach { hit ->
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(hit.name, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(hit.path, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(hit.kind + " · " + formatSize(hit.sizeBytes) + if (hit.modifiedLabel.isNotBlank()) " · " + hit.modifiedLabel else "", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { onPreviewPath(hit.path) }, enabled = !busy) { Text("Preview") }
                            TextButton(onClick = { onOpenPath(hit.path) }, enabled = !busy) { Text("Open") }
                        }
                    }
                }
            }
        }

        Divider(color = MaterialTheme.colorScheme.outlineVariant)
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Exact duplicate analysis", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Files are grouped by SHA-256 hash. Nothing is deleted automatically.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Button(
                    onClick = onFindDuplicates,
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (busy) "Scanning…" else "Scan for duplicates") }
                if (duplicatesScanned && duplicates.isEmpty()) {
                    Text("No exact duplicate groups found. Files skipped: $skippedDuplicateFiles.", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
                }
                duplicates.forEachIndexed { index, group ->
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("Group ${index + 1} · ${formatSize(group.sizeBytes)} each", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                            Text(group.sha256, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            group.paths.forEach { path ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(path, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    TextButton(onClick = { onOpenPath(path) }, enabled = !busy) { Text("Open") }
                                }
                            }
                        }
                    }
                }
                if (duplicatesScanned) {
                    Text("Files skipped during scan: $skippedDuplicateFiles", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        preview?.let { item ->
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("READ-ONLY PREVIEW", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.2.sp)
                    Text(item.path, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    Text(item.message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    if (previewBitmap != null) {
                        Image(bitmap = previewBitmap, contentDescription = "Read-only preview of ${item.path}", modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp))
                    } else if (item.text != null) {
                        Text(item.text.take(12000), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = onClosePreview) { Text("Close preview") }
                }
            }
        }
    }
}

@Composable
private fun IconSearchLabel(label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Icon(Icons.Outlined.Search, contentDescription = null)
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024L * 1024 -> String.format("%.1f KB", bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    else -> String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
}
