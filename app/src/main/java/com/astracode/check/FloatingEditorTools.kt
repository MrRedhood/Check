package com.astracode.check

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

internal enum class EditorToolGroup(val title: String) {
    BASIC("Basic"),
    ADVANCED("Advanced"),
    ASTRACODE("AstraCode"),
}

internal enum class EditorToolAction(
    val group: EditorToolGroup,
    val title: String,
    val description: String,
) {
    DUPLICATE_LINE(EditorToolGroup.BASIC, "Duplicate line", "Copy the current line below itself."),
    MOVE_LINE_UP(EditorToolGroup.BASIC, "Move line up", "Move the current line one line higher."),
    MOVE_LINE_DOWN(EditorToolGroup.BASIC, "Move line down", "Move the current line one line lower."),
    INSERT_LINE_BELOW(EditorToolGroup.BASIC, "Insert line below", "Add an empty line below the current line."),
    INDENT(EditorToolGroup.BASIC, "Indent", "Indent the current line or selected lines by four spaces."),
    OUTDENT(EditorToolGroup.BASIC, "Outdent", "Remove one indentation level from the current or selected lines."),
    TOGGLE_COMMENT(EditorToolGroup.BASIC, "Toggle comment", "Comment or uncomment lines using the file language."),
    WRAP_PARENTHESES(EditorToolGroup.ADVANCED, "Wrap in parentheses", "Wrap the selection, or current line, in parentheses."),
    WRAP_QUOTES(EditorToolGroup.ADVANCED, "Wrap in quotes", "Wrap the selection, or current line, in double quotes."),
    SORT_SELECTED_LINES(EditorToolGroup.ADVANCED, "Sort selected lines", "Sort the lines covered by the selection."),
    JOIN_SELECTED_LINES(EditorToolGroup.ADVANCED, "Join selected lines", "Join the selected lines into one line."),
    TRIM_TRAILING_WHITESPACE(EditorToolGroup.ADVANCED, "Trim trailing spaces", "Remove spaces at the ends of lines across this file."),
    CONVERT_TABS_TO_SPACES(EditorToolGroup.ADVANCED, "Tabs to spaces", "Convert tab characters to four spaces."),
    SMART_SELECT(EditorToolGroup.ASTRACODE, "Smart select", "Select the closest code block, identifier, or current line."),
    SELECT_CURRENT_LINE(EditorToolGroup.ASTRACODE, "Select current line", "Select the complete current line without its newline."),
    CONTEXT_CAPSULE(EditorToolGroup.ASTRACODE, "Context Capsule", "Copy code with filename, line numbers, and nearby context for an AI handoff."),
    SELECTION_PULSE(EditorToolGroup.ASTRACODE, "Selection Pulse", "Summarize characters, words, lines, and non-empty lines in the selection."),
}

@Composable
internal fun FloatingEditorTools(
    editorKey: String,
    modifier: Modifier = Modifier,
    onAction: (EditorToolAction) -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val parentWidth = maxWidth
        val parentHeight = maxHeight
        val maxX = (maxWidth - 124.dp).coerceAtLeast(0.dp).value
        val maxY = (maxHeight - 48.dp).coerceAtLeast(0.dp).value
        var position by remember(editorKey) { mutableStateOf<Offset?>(null) }
        var showTools by remember(editorKey) { mutableStateOf(false) }
        var selectedGroup by remember(editorKey) { mutableStateOf(EditorToolGroup.BASIC) }
        val defaultPosition = Offset(maxX, maxY)
        val currentPosition = position ?: defaultPosition

        Row(
            modifier = Modifier
                .offset {
                    androidx.compose.ui.unit.IntOffset(
                        with(density) { currentPosition.x.dp.roundToPx() },
                        with(density) { currentPosition.y.dp.roundToPx() },
                    )
                }
                .width(124.dp)
                .height(48.dp)
                .testTag("editor-tools-floating-surface"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(28.dp),
                shadowElevation = 8.dp,
                tonalElevation = 4.dp,
            ) {
                Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(width = 42.dp, height = 48.dp)
                            .pointerInput(editorKey, parentWidth, parentHeight) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val current = position ?: defaultPosition
                                    val dx = with(density) { dragAmount.x.toDp().value }
                                    val dy = with(density) { dragAmount.y.toDp().value }
                                    position = Offset(
                                        (current.x + dx).coerceIn(0f, maxX),
                                        (current.y + dy).coerceIn(0f, maxY),
                                    )
                                }
                            }
                            .testTag("editor-tools-drag-handle"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("⠿", style = MaterialTheme.typography.titleLarge)
                    }
                    TextButton(
                        onClick = { showTools = true },
                        modifier = Modifier.weight(1f).height(48.dp).testTag("editor-tools-open"),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    ) {
                        Text("Tools", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        if (showTools) {
            Dialog(onDismissRequest = { showTools = false }) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 6.dp,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("AstraCode Editor Tools", style = MaterialTheme.typography.titleLarge)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            EditorToolGroup.values().forEach { group ->
                                TextButton(
                                    onClick = { selectedGroup = group },
                                    modifier = Modifier.testTag("editor-tools-group-" + group.name.lowercase()),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                ) {
                                    Text(
                                        group.title,
                                        fontWeight = if (selectedGroup == group) FontWeight.Bold else FontWeight.Normal,
                                    )
                                }
                            }
                        }
                        Column(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            EditorToolAction.values().filter { it.group == selectedGroup }.forEach { action ->
                                TextButton(
                                    onClick = {
                                        showTools = false
                                        onAction(action)
                                    },
                                    modifier = Modifier.fillMaxWidth().testTag("editor-tool-" + action.name.lowercase()),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Text(action.title, style = MaterialTheme.typography.titleSmall)
                                        Text(action.description, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { showTools = false }, modifier = Modifier.testTag("editor-tools-close")) {
                                Text("Close")
                            }
                        }
                    }
                }
            }
        }
    }
}
