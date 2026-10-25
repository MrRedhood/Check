package com.astracode.check

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

internal data class CodeFoldRegion(
    val startLine: Int,
    val endLine: Int,
    val startOffset: Int,
    val endOffset: Int,
    val label: String,
) {
    val hiddenLineCount: Int get() = endLine - startLine
}

/**
 * Finds multiline brace-delimited regions without treating braces inside strings or comments as code.
 * Folding changes only the visual transformation; the original source buffer remains unchanged.
 */
internal fun findCodeFoldRegions(text: String): List<CodeFoldRegion> {
    if (text.isEmpty()) return emptyList()
    val lineStarts = mutableListOf(0)
    text.forEachIndexed { index, character -> if (character == '\n') lineStarts += index + 1 }
    fun lineForOffset(offset: Int): Int {
        var low = 0
        var high = lineStarts.lastIndex
        while (low <= high) {
            val middle = (low + high) ushr 1
            if (lineStarts[middle] <= offset) low = middle + 1 else high = middle - 1
        }
        return high + 1
    }

    val opens = mutableListOf<Int>()
    val pairs = mutableListOf<Pair<Int, Int>>()
    var mode = 0 // code, line comment, block comment, string, char, triple string, quoted identifier
    var blockDepth = 0
    var i = 0
    while (i < text.length) {
        val c = text[i]
        when (mode) {
            1 -> { if (c == '\n') mode = 0; i++ }
            2 -> {
                if (c == '/' && text.getOrNull(i + 1) == '*') { blockDepth++; i += 2 }
                else if (c == '*' && text.getOrNull(i + 1) == '/') {
                    blockDepth--; i += 2; if (blockDepth == 0) mode = 0
                } else i++
            }
            3, 4 -> {
                val quote = if (mode == 3) '"' else '\''
                if (c == '\\') i = (i + 2).coerceAtMost(text.length)
                else { if (c == quote) mode = 0; i++ }
            }
            5 -> { if (text.startsWith("\"\"\"", i)) { mode = 0; i += 3 } else i++ }
            6 -> { if (c.code == 96) mode = 0; i++ }
            else -> when {
                c == '/' && text.getOrNull(i + 1) == '/' -> { mode = 1; i += 2 }
                c == '/' && text.getOrNull(i + 1) == '*' -> { mode = 2; blockDepth = 1; i += 2 }
                c == '#' -> { mode = 1; i++ }
                c == '"' && text.startsWith("\"\"\"", i) -> { mode = 5; i += 3 }
                c == '"' -> { mode = 3; i++ }
                c == '\'' -> { mode = 4; i++ }
                c.code == 96 -> { mode = 6; i++ }
                c == '{' -> { opens += i; i++ }
                c == '}' -> {
                    if (opens.isNotEmpty()) pairs += opens.removeAt(opens.lastIndex) to i
                    i++
                }
                else -> i++
            }
        }
    }

    return pairs.mapNotNull { (open, close) ->
        val startLine = lineForOffset(open)
        val endLine = lineForOffset(close)
        if (endLine <= startLine + 1) null
        else {
            val start = lineStarts[startLine - 1]
            val newline = text.indexOf('\n', start)
            val end = if (newline < 0) text.length else newline
            val header = text.substring(start, end).trim().replace(Regex("\\s+"), " ")
            CodeFoldRegion(startLine, endLine, open, close, header.take(36) + if (header.length > 36) "…" else "")
        }
    }.sortedWith(compareBy<CodeFoldRegion> { it.startLine }.thenByDescending { it.endLine })
        .distinctBy { it.startLine }
}

internal fun lineNumberVisualTransformation(): VisualTransformation =
    codeFoldingVisualTransformation(emptyList(), emptySet())

internal fun codeFoldingVisualTransformation(
    foldableRegions: List<CodeFoldRegion>,
    collapsedLineStarts: Set<Int>,
    sourcePath: String? = null,
    highlightedLines: IntRange? = null,
): VisualTransformation = VisualTransformation { input ->
    val text = input.text
    val starts = mutableListOf(0)
    text.forEachIndexed { index, character -> if (character == '\n') starts += index + 1 }
    val lineCount = starts.size
    val digitWidth = lineCount.toString().length
    val collapsedEnds = foldableRegions.filter {
        it.startLine in collapsedLineStarts && it.startLine in 1..lineCount &&
            it.endLine in (it.startLine + 1)..lineCount &&
            text.getOrNull(it.startOffset) == '{' && text.getOrNull(it.endOffset) == '}'
    }.associate { it.startLine to it.endLine }

    val offsets = IntArray(text.length + 1)
    val syntaxSpans = sourcePath?.let {
        findSyntaxHighlightSpans(text, it, highlightedLines, starts)
    }.orEmpty()
    val output = AnnotatedString.Builder()
    var syntaxSpanIndex = 0
    fun appendStyledSource(start: Int, end: Int) {
        var offset = start
        while (offset < end) {
            while (syntaxSpanIndex < syntaxSpans.size && syntaxSpans[syntaxSpanIndex].end <= offset) {
                syntaxSpanIndex++
            }
            val span = syntaxSpans.getOrNull(syntaxSpanIndex)
            val plainEnd = if (span == null || span.start >= end) end
                else if (span.start > offset) minOf(end, span.start) else offset
            if (plainEnd > offset) {
                val base = output.length
                for (sourceOffset in offset until plainEnd) offsets[sourceOffset] = base + sourceOffset - offset
                output.append(text.substring(offset, plainEnd))
                offsets[plainEnd] = output.length
                offset = plainEnd
            } else if (span != null && span.start <= offset && span.end > offset) {
                val styledEnd = minOf(end, span.end)
                val base = output.length
                for (sourceOffset in offset until styledEnd) offsets[sourceOffset] = base + sourceOffset - offset
                output.pushStyle(span.style)
                output.append(text.substring(offset, styledEnd))
                output.pop()
                offsets[styledEnd] = output.length
                offset = styledEnd
                if (styledEnd >= span.end) syntaxSpanIndex++
            } else {
                val base = output.length
                offsets[offset] = base
                output.append(text[offset].toString())
                offset++
                offsets[offset] = output.length
            }
        }
    }
    var hiddenThrough = 0
    for (lineIndex in starts.indices) {
        val line = lineIndex + 1
        val start = starts[lineIndex]
        val hasNewline = lineIndex < starts.lastIndex
        val end = if (hasNewline) starts[lineIndex + 1] - 1 else text.length
        if (line <= hiddenThrough) {
            for (offset in start until end) offsets[offset] = output.length
            offsets[end] = output.length
            continue
        }
        output.append(line.toString().padStart(digitWidth))
        output.append(" │ ")
        appendStyledSource(start, end)
        val foldEnd = collapsedEnds[line]
        if (foldEnd != null && foldEnd > line) {
            output.append(" … ")
            output.append((foldEnd - line).toString())
            output.append(" lines folded …")
            hiddenThrough = maxOf(hiddenThrough, foldEnd)
        }
        offsets[end] = output.length
        if (hasNewline) output.append('\n')
    }
    offsets[text.length] = output.length
    val transformed = output.toAnnotatedString()
    TransformedText(transformed, object : OffsetMapping {
        override fun originalToTransformed(offset: Int): Int =
            offsets[offset.coerceIn(0, text.length)]
        override fun transformedToOriginal(offset: Int): Int {
            val target = offset.coerceIn(0, transformed.length)
            var low = 0
            var high = offsets.lastIndex
            while (low < high) {
                val middle = (low + high) ushr 1
                if (offsets[middle] < target) low = middle + 1 else high = middle
            }
            return low
        }
    })
}


internal fun countMatches(text: String, query: String): Int {
    if (query.isEmpty()) return 0
    var count = 0
    var next = 0
    while (next <= text.length - query.length) {
        val found = text.indexOf(query, next)
        if (found < 0) break
        count++
        next = found + query.length
    }
    return count
}

private fun findNextMatchStart(text: String, query: String, startAt: Int): Int? {
    if (query.isEmpty()) return null
    val start = startAt.coerceIn(0, text.length)
    return text.indexOf(query, start).takeIf { it >= 0 } ?: text.indexOf(query).takeIf { it >= 0 }
}

internal fun findNextMatch(value: TextFieldValue, query: String): TextFieldValue {
    val start = findNextMatchStart(value.text, query, value.selection.max) ?: return value
    return value.copy(selection = TextRange(start, start + query.length), composition = null)
}

internal fun findPreviousMatch(value: TextFieldValue, query: String): TextFieldValue {
    if (query.isEmpty()) return value
    val startAt = if (value.selection.collapsed) value.selection.start - 1 else value.selection.min - 1
    val found = value.text.lastIndexOf(query, startAt.coerceAtMost(value.text.length - query.length))
        .takeIf { it >= 0 } ?: value.text.lastIndexOf(query)
    if (found < 0) return value
    return value.copy(selection = TextRange(found, found + query.length), composition = null)
}

internal fun replaceOneMatch(
    value: TextFieldValue,
    query: String,
    replacement: String,
): Pair<TextFieldValue, Boolean> {
    if (query.isEmpty()) return value to false
    val selection = value.selection
    val selectedTextIsMatch = !selection.collapsed &&
        selection.max - selection.min == query.length &&
        value.text.substring(selection.min, selection.max) == query
    val start = if (selectedTextIsMatch) {
        selection.min
    } else {
        findNextMatchStart(value.text, query, selection.max) ?: return value to false
    }
    val end = start + query.length
    val newText = value.text.replaceRange(start, end, replacement)
    return TextFieldValue(newText, selection = TextRange(start + replacement.length)) to true
}

internal fun replaceAllMatches(
    value: TextFieldValue,
    query: String,
    replacement: String,
): Pair<TextFieldValue, Int> {
    val count = countMatches(value.text, query)
    if (count == 0) return value to 0
    val cursor = value.selection.end.coerceIn(0, value.text.length)
    val matchesBeforeCursor = countMatches(value.text.substring(0, cursor), query)
    val newText = value.text.replace(query, replacement)
    val adjustedCursor = (
        cursor + matchesBeforeCursor * (replacement.length - query.length)
    ).coerceIn(0, newText.length)
    return TextFieldValue(newText, selection = TextRange(adjustedCursor)) to count
}
