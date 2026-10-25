package com.astracode.check

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

internal const val VIRTUALIZED_EDITOR_THRESHOLD_CHARS = 64 * 1024

internal fun shouldUseVirtualizedEditor(sourceLength: Int): Boolean =
    sourceLength >= VIRTUALIZED_EDITOR_THRESHOLD_CHARS

/**
 * Indexes logical lines without building a String for every line.
 * Known edit ranges let normal typing rescan only the changed line; suffix offsets are shifted
 * in place. The document itself remains the authoritative source for saving and search.
 */
internal class VirtualLineIndex(initialText: String) {
    private var sourceText: String = initialText
    private val starts = mutableListOf(0)

    init {
        appendStarts(initialText, 0, initialText.length, includeFinalEmptyLine = true)
    }

    val lineCount: Int get() = starts.size

    fun lineStart(lineIndex: Int): Int = starts[lineIndex.coerceIn(0, starts.lastIndex)]

    fun lineEnd(lineIndex: Int): Int {
        val index = lineIndex.coerceIn(0, starts.lastIndex)
        if (index == starts.lastIndex) return sourceText.length
        var end = starts[index + 1] - 1 // newline position
        if (end > starts[index] && sourceText[end - 1] == '\r') end--
        return end
    }

    fun lineForOffset(offset: Int): Int {
        val target = offset.coerceIn(0, sourceText.length)
        var low = 0
        var high = starts.lastIndex
        while (low <= high) {
            val middle = (low + high) ushr 1
            if (starts[middle] <= target) low = middle + 1 else high = middle - 1
        }
        return high.coerceAtLeast(0)
    }

    fun update(
        newText: String,
        editStart: Int? = null,
        oldEditEnd: Int? = null,
        newEditEnd: Int? = null,
    ) {
        if (newText === sourceText) return
        if (newText == sourceText) {
            sourceText = newText
            return
        }

        val validHint = editStart != null && oldEditEnd != null && newEditEnd != null &&
            editStart in 0..sourceText.length &&
            oldEditEnd in editStart..sourceText.length &&
            newEditEnd in editStart..newText.length &&
            newText.length - sourceText.length == newEditEnd - oldEditEnd

        val start: Int
        val oldEnd: Int
        val newEnd: Int
        if (validHint) {
            start = editStart!!
            oldEnd = oldEditEnd!!
            newEnd = newEditEnd!!
        } else {
            var prefix = 0
            val sharedLimit = minOf(sourceText.length, newText.length)
            while (prefix < sharedLimit && sourceText[prefix] == newText[prefix]) prefix++
            var suffix = 0
            while (
                suffix < sourceText.length - prefix &&
                suffix < newText.length - prefix &&
                sourceText[sourceText.length - suffix - 1] == newText[newText.length - suffix - 1]
            ) suffix++
            start = prefix
            oldEnd = sourceText.length - suffix
            newEnd = newText.length - suffix
        }

        val lineBeforeEdit = lineForOffset(start)
        val scanStart = starts[lineBeforeEdit]
        val firstReusableIndex = upperBound(oldEnd)
        val reusableOldOffset = starts.getOrNull(firstReusableIndex)
        val delta = newEnd - oldEnd
        val reusableNewOffset = reusableOldOffset?.plus(delta)
        val scanLimit = reusableNewOffset ?: newText.length
        val replacementStarts = ArrayList<Int>()
        var cursor = scanStart
        while (cursor < newText.length) {
            val newline = newText.indexOf('\n', cursor)
            if (newline < 0) break
            val nextStart = newline + 1
            if (reusableNewOffset != null && nextStart >= scanLimit) break
            replacementStarts += nextStart
            cursor = nextStart
            if (reusableNewOffset == null && nextStart == newText.length) break
        }

        val removeFrom = lineBeforeEdit + 1
        val removeUntil = firstReusableIndex.coerceIn(removeFrom, starts.size)
        starts.subList(removeFrom, removeUntil).clear()
        starts.addAll(removeFrom, replacementStarts)
        val suffixStart = removeFrom + replacementStarts.size
        for (index in suffixStart until starts.size) starts[index] += delta
        sourceText = newText
    }

    private fun upperBound(offset: Int): Int {
        var low = 0
        var high = starts.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (starts[middle] <= offset) low = middle + 1 else high = middle
        }
        return low
    }

    private fun appendStarts(text: String, start: Int, end: Int, includeFinalEmptyLine: Boolean) {
        var cursor = start
        while (cursor < end) {
            val newline = text.indexOf('\n', cursor)
            if (newline < 0 || newline >= end) break
            val nextStart = newline + 1
            if (includeFinalEmptyLine || nextStart < end) starts += nextStart
            cursor = nextStart
        }
    }
}

internal fun insertedTextFromLineChange(oldLine: String, changedLine: String): String {
    var prefix = 0
    val prefixLimit = minOf(oldLine.length, changedLine.length)
    while (prefix < prefixLimit && oldLine[prefix] == changedLine[prefix]) prefix++
    var suffix = 0
    while (
        suffix < oldLine.length - prefix &&
        suffix < changedLine.length - prefix &&
        oldLine[oldLine.length - suffix - 1] == changedLine[changedLine.length - suffix - 1]
    ) suffix++
    return changedLine.substring(prefix, changedLine.length - suffix)
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun VirtualizedCodeEditor(
    value: TextFieldValue,
    path: String,
    onValueChange: (TextFieldValue) -> Boolean,
    readOnly: Boolean,
    wordWrapEnabled: Boolean,
    focusRequester: FocusRequester,
    interactionSource: MutableInteractionSource,
    textColor: Color,
    cursorColor: Color,
    lineNumberColor: Color,
    focusColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier,
    label: String = "File contents",
    placeholder: String? = null,
    testTag: String = "file-editor-input",
) {
    val source = value.text
    val lineIndex = remember(path) { VirtualLineIndex(source) }
    lineIndex.update(source)
    val listState = rememberLazyListState()
    val activeLine = lineIndex.lineForOffset(value.selection.end.coerceIn(0, source.length))
    var editorHasFocus by remember(path) { mutableStateOf(false) }

    LaunchedEffect(activeLine, lineIndex.lineCount) {
        val visible = listState.layoutInfo.visibleItemsInfo
        if (visible.none { it.index == activeLine }) {
            listState.animateScrollToItem(activeLine.coerceIn(0, lineIndex.lineCount - 1))
        }
        if (editorHasFocus) {
            withFrameNanos { }
            focusRequester.requestFocus()
        }
    }

    val editorColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = textColor,
        unfocusedTextColor = textColor,
        focusedBorderColor = focusColor,
        unfocusedBorderColor = borderColor,
        cursorColor = cursorColor,
        focusedLabelColor = focusColor,
        unfocusedLabelColor = lineNumberColor,
    )

    OutlinedTextFieldDefaults.DecorationBox(
        value = source,
        innerTextField = {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().testTag(testTag),
                contentPadding = PaddingValues(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                items(
                    count = lineIndex.lineCount,
                    key = { it },
                    contentType = { "source-line" },
                ) { lineNumber ->
                    val lineStart = lineIndex.lineStart(lineNumber)
                    val lineEnd = lineIndex.lineEnd(lineNumber)
                    val lineText = source.substring(lineStart, lineEnd)
                    val highlightedLine = remember(lineText, path) {
                        val builder = AnnotatedString.Builder()
                        builder.append(lineText)
                        findSyntaxHighlightSpans(
                            lineText,
                            path,
                            lineRange = 1..1,
                            sourceLineStarts = listOf(0),
                        ).forEach { span ->
                            val start = span.start.coerceIn(0, lineText.length)
                            val end = span.end.coerceIn(start, lineText.length)
                            if (end > start) builder.addStyle(span.style, start, end)
                        }
                        builder.toAnnotatedString()
                    }
                    val localSelection = TextRange(
                        (value.selection.start - lineStart).coerceIn(0, lineText.length),
                        (value.selection.end - lineStart).coerceIn(0, lineText.length),
                    )
                    val localComposition = value.composition?.let { composition ->
                        TextRange(
                            (composition.start - lineStart).coerceIn(0, lineText.length),
                            (composition.end - lineStart).coerceIn(0, lineText.length),
                        )
                    }
                    val isCaretLine = lineNumber == activeLine

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            (lineNumber + 1).toString() + " │",
                            modifier = Modifier.width(52.dp).padding(end = 8.dp),
                            color = lineNumberColor,
                            textAlign = TextAlign.End,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            maxLines = 1,
                        )
                        BasicTextField(
                            value = TextFieldValue(highlightedLine, localSelection, localComposition),
                            onValueChange = { changedLineValue ->
                                if (readOnly) return@BasicTextField
                                val selection = value.selection
                                if (changedLineValue.text == lineText) {
                                    val nextSelection = TextRange(
                                        lineStart + changedLineValue.selection.start,
                                        lineStart + changedLineValue.selection.end,
                                    )
                                    val nextComposition = changedLineValue.composition?.let {
                                        TextRange(lineStart + it.start, lineStart + it.end)
                                    }
                                    onValueChange(value.copy(selection = nextSelection, composition = nextComposition))
                                } else {
                                    val crossesLines = selection.min < lineStart || selection.max > lineEnd
                                    val nextSource: String
                                    val nextSelection: TextRange
                                    val nextComposition: TextRange?
                                    val editStart: Int
                                    val oldEditEnd: Int
                                    val newEditEnd: Int
                                    if (crossesLines) {
                                        val inserted = insertedTextFromLineChange(lineText, changedLineValue.text)
                                        editStart = selection.min.coerceIn(0, source.length)
                                        oldEditEnd = selection.max.coerceIn(editStart, source.length)
                                        nextSource = source.replaceRange(editStart, oldEditEnd, inserted)
                                        newEditEnd = editStart + inserted.length
                                        nextSelection = TextRange(newEditEnd)
                                        nextComposition = null
                                    } else {
                                        nextSource = source.replaceRange(lineStart, lineEnd, changedLineValue.text)
                                        editStart = lineStart
                                        oldEditEnd = lineEnd
                                        newEditEnd = lineStart + changedLineValue.text.length
                                        nextSelection = TextRange(
                                            lineStart + changedLineValue.selection.start,
                                            lineStart + changedLineValue.selection.end,
                                        )
                                        nextComposition = changedLineValue.composition?.let {
                                            TextRange(lineStart + it.start, lineStart + it.end)
                                        }
                                    }
                                    val nextValue = TextFieldValue(
                                        nextSource,
                                        selection = nextSelection,
                                        composition = nextComposition,
                                    )
                                    if (onValueChange(nextValue)) {
                                        lineIndex.update(nextSource, editStart, oldEditEnd, newEditEnd)
                                    }
                                }
                            },
                            readOnly = readOnly,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = textColor,
                                fontFamily = FontFamily.Monospace,
                            ),
                            cursorBrush = SolidColor(cursorColor),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                keyboardType = KeyboardType.Text,
                                autoCorrectEnabled = false,
                            ),
                            singleLine = false,
                            maxLines = if (wordWrapEnabled) Int.MAX_VALUE else 1,
                            modifier = Modifier
                                .weight(1f)
                                .then(if (isCaretLine) Modifier.focusRequester(focusRequester) else Modifier)
                                .onFocusChanged { state -> editorHasFocus = state.isFocused }
                                .onPreviewKeyEvent { event ->
                                    handleVirtualEditorKey(
                                        event = event,
                                        lineNumber = lineNumber,
                                        lineStart = lineStart,
                                        lineEnd = lineEnd,
                                        lineText = lineText,
                                        value = value,
                                        lineIndex = lineIndex,
                                        readOnly = readOnly,
                                        onValueChange = { next, editStart, oldEnd, newEnd ->
                                            if (onValueChange(next)) {
                                                if (next.text !== source) {
                                                    lineIndex.update(next.text, editStart, oldEnd, newEnd)
                                                }
                                            }
                                        },
                                    )
                                },
                        )
                    }
                }
            }
        },
        enabled = true,
        singleLine = false,
        visualTransformation = VisualTransformation.None,
        interactionSource = interactionSource,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        colors = editorColors,
        container = {
            OutlinedTextFieldDefaults.Container(
                enabled = true,
                isError = false,
                interactionSource = interactionSource,
                colors = editorColors,
                shape = RoundedCornerShape(16.dp),
            )
        },
    )
}

private fun handleVirtualEditorKey(
    event: KeyEvent,
    lineNumber: Int,
    lineStart: Int,
    lineEnd: Int,
    lineText: String,
    value: TextFieldValue,
    lineIndex: VirtualLineIndex,
    readOnly: Boolean,
    onValueChange: (TextFieldValue, Int, Int, Int) -> Unit,
): Boolean {
    if (event.type != KeyEventType.KeyDown || readOnly) return false
    val source = value.text
    val selection = value.selection
    val collapsed = selection.collapsed
    val caret = selection.end.coerceIn(0, source.length)

    if (event.key == Key.Tab) {
        val start = selection.min.coerceIn(0, source.length)
        val end = selection.max.coerceIn(start, source.length)
        val updated = source.replaceRange(start, end, "    ")
        onValueChange(
            TextFieldValue(updated, selection = TextRange(start + 4)),
            start,
            end,
            start + 4,
        )
        return true
    }

    if (collapsed && event.key == Key.Backspace && caret == lineStart && lineNumber > 0) {
        val previousLineEnd = lineIndex.lineEnd(lineNumber - 1)
        val updated = source.replaceRange(previousLineEnd, lineStart, "")
        onValueChange(
            TextFieldValue(updated, selection = TextRange(previousLineEnd)),
            previousLineEnd,
            lineStart,
            previousLineEnd,
        )
        return true
    }

    if (collapsed && event.key == Key.Delete && caret == lineEnd && lineNumber < lineIndex.lineCount - 1) {
        val nextLineStart = lineIndex.lineStart(lineNumber + 1)
        val updated = source.replaceRange(lineEnd, nextLineStart, "")
        onValueChange(
            TextFieldValue(updated, selection = TextRange(lineEnd)),
            lineEnd,
            nextLineStart,
            lineEnd,
        )
        return true
    }

    if (collapsed && (event.key == Key.DirectionUp || event.key == Key.DirectionDown)) {
        val targetLine = if (event.key == Key.DirectionUp) lineNumber - 1 else lineNumber + 1
        if (targetLine !in 0 until lineIndex.lineCount) return true
        val targetStart = lineIndex.lineStart(targetLine)
        val targetEnd = lineIndex.lineEnd(targetLine)
        val targetCaret = targetStart + (caret - lineStart).coerceIn(0, targetEnd - targetStart)
        onValueChange(
            value.copy(selection = TextRange(targetCaret), composition = null),
            targetCaret,
            targetCaret,
            targetCaret,
        )
        return true
    }

    if (collapsed && event.key == Key.DirectionLeft && caret == lineStart && lineNumber > 0) {
        val target = lineIndex.lineEnd(lineNumber - 1)
        onValueChange(value.copy(selection = TextRange(target), composition = null), target, target, target)
        return true
    }

    if (collapsed && event.key == Key.DirectionRight && caret == lineEnd &&
        lineNumber < lineIndex.lineCount - 1
    ) {
        val target = lineIndex.lineStart(lineNumber + 1)
        onValueChange(value.copy(selection = TextRange(target), composition = null), target, target, target)
        return true
    }

    if (!collapsed && (event.key == Key.DirectionUp || event.key == Key.DirectionDown) && event.isShiftPressed) {
        val targetLine = if (event.key == Key.DirectionUp) lineNumber - 1 else lineNumber + 1
        if (targetLine !in 0 until lineIndex.lineCount) return true
        val targetStart = lineIndex.lineStart(targetLine)
        val targetEnd = lineIndex.lineEnd(targetLine)
        val targetCaret = targetStart + (caret - lineStart).coerceIn(0, targetEnd - targetStart)
        onValueChange(
            value.copy(selection = TextRange(selection.start, targetCaret), composition = null),
            targetCaret,
            targetCaret,
            targetCaret,
        )
        return true
    }

    return false
}
