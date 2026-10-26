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

private fun mapOffsetThroughReplacements(
    text: String,
    query: String,
    replacement: String,
    offset: Int,
): Int {
    val cursor = offset.coerceIn(0, text.length)
    var sourceOffset = 0
    var resultOffset = 0

    while (sourceOffset <= text.length) {
        val matchStart = text.indexOf(query, sourceOffset)
        if (matchStart < 0 || matchStart >= cursor) {
            return resultOffset + cursor - sourceOffset
        }

        resultOffset += matchStart - sourceOffset
        val matchEnd = matchStart + query.length
        if (cursor < matchEnd) {
            // Keep the relative caret position when possible; clamp it if replacement is shorter.
            return resultOffset + minOf(cursor - matchStart, replacement.length)
        }

        resultOffset += replacement.length
        sourceOffset = matchEnd
    }
    return resultOffset
}

internal fun replaceAllMatches(
    value: TextFieldValue,
    query: String,
    replacement: String,
): Pair<TextFieldValue, Int> {
    val count = countMatches(value.text, query)
    if (count == 0) return value to 0
    val cursor = value.selection.end.coerceIn(0, value.text.length)
    val newText = value.text.replace(query, replacement)
    val adjustedCursor = mapOffsetThroughReplacements(
        value.text,
        query,
        replacement,
        cursor,
    ).coerceIn(0, newText.length)
    return TextFieldValue(newText, selection = TextRange(adjustedCursor)) to count
}

internal data class EditorToolOutcome(
    val value: TextFieldValue,
    val message: String,
    val clipboardText: String? = null,
)

private data class EditorLineSpan(val start: Int, val contentEnd: Int, val end: Int)
private data class EditorLineRewrite(val text: String, val mapOffset: (Int) -> Int = { it })

private fun editorLineSpans(text: String): List<EditorLineSpan> {
    val spans = mutableListOf<EditorLineSpan>()
    var start = 0
    text.forEachIndexed { index, char ->
        if (char == '\n') {
            spans += EditorLineSpan(start, index, index + 1)
            start = index + 1
        }
    }
    spans += EditorLineSpan(start, text.length, text.length)
    return spans
}

private fun currentEditorLineIndex(value: TextFieldValue, spans: List<EditorLineSpan>): Int {
    val text = value.text
    var caret = value.selection.min.coerceIn(0, text.length)
    if (caret == text.length && text.endsWith('\n') && spans.size > 1) caret--
    return text.take(caret).count { it == '\n' }.coerceIn(0, spans.lastIndex)
}

private fun selectedEditorLineRange(value: TextFieldValue, spans: List<EditorLineSpan>): IntRange {
    val text = value.text
    val start = value.selection.min.coerceIn(0, text.length)
    val endOffset = if (value.selection.collapsed && start == text.length && text.endsWith('\n')) {
        (start - 1).coerceAtLeast(0)
    } else if (value.selection.collapsed) {
        value.selection.end.coerceIn(0, text.length)
    } else {
        (value.selection.max - 1).coerceAtLeast(start)
    }
    val firstLine = text.take(start).count { it == '\n' }.coerceIn(0, spans.lastIndex)
    val lastLine = text.take(endOffset.coerceIn(0, text.length)).count { it == '\n' }
        .coerceIn(firstLine, spans.lastIndex)
    return firstLine..lastLine
}

private fun rewriteEditorLines(
    value: TextFieldValue,
    lineRange: IntRange,
    rewrite: (String) -> EditorLineRewrite,
): TextFieldValue {
    val text = value.text
    val spans = editorLineSpans(text)
    val newContents = spans.mapIndexed { index, span ->
        val old = text.substring(span.start, span.contentEnd)
        if (index in lineRange) rewrite(old) else EditorLineRewrite(old)
    }
    val rebuilt = buildString {
        newContents.forEachIndexed { index, changed ->
            append(changed.text)
            if (index < spans.lastIndex) append('\n')
        }
    }
    val newStarts = editorLineSpans(rebuilt).map { it.start }
    fun mapOffset(offset: Int): Int {
        val safe = offset.coerceIn(0, text.length)
        val index = text.take(safe).count { it == '\n' }.coerceIn(0, spans.lastIndex)
        val span = spans[index]
        val relative = (safe - span.start).coerceIn(0, span.contentEnd - span.start)
        val mapped = if (index in lineRange) newContents[index].mapOffset(relative) else relative
        return (newStarts[index] + mapped).coerceIn(0, rebuilt.length)
    }
    return TextFieldValue(
        rebuilt,
        selection = TextRange(mapOffset(value.selection.start), mapOffset(value.selection.end)),
        composition = null,
    )
}

internal fun applyEditorTool(action: EditorToolAction, value: TextFieldValue, path: String): EditorToolOutcome {
    val text = value.text
    val spans = editorLineSpans(text)
    val lineIndex = currentEditorLineIndex(value, spans)
    val lineRange = selectedEditorLineRange(value, spans)
    val line = spans[lineIndex]
    val lineText = text.substring(line.start, line.contentEnd)

    return when (action) {
        EditorToolAction.SELECT_CURRENT_LINE -> EditorToolOutcome(
            value.copy(selection = TextRange(line.start, line.contentEnd), composition = null),
            "Selected line " + (lineIndex + 1) + ".",
        )
        EditorToolAction.DUPLICATE_LINE -> {
            val source = lineText
            val afterLine = line.end
            val insertion = if (line.end > line.contentEnd) source + "\n" else "\n" + source
            val insertionStart = afterLine + if (line.end > line.contentEnd) 0 else 1
            val updated = text.substring(0, afterLine) + insertion + text.substring(afterLine)
            val relative = (value.selection.end - line.start).coerceIn(0, source.length)
            val caret = (insertionStart + relative).coerceIn(0, updated.length)
            EditorToolOutcome(TextFieldValue(updated, selection = TextRange(caret)), "Duplicated line " + (lineIndex + 1) + ".")
        }
        EditorToolAction.MOVE_LINE_UP, EditorToolAction.MOVE_LINE_DOWN -> {
            val lines = splitEditorLines(text)
            val visibleLineCount = if (text.endsWith('\n')) (lines.size - 1).coerceAtLeast(1) else lines.size
            val targetIndex = lineIndex + if (action == EditorToolAction.MOVE_LINE_UP) -1 else 1
            if (targetIndex !in 0 until visibleLineCount) {
                EditorToolOutcome(value, if (targetIndex < 0) "Already at the first line." else "Already at the last line.")
            } else {
                val relativeCaret = (value.selection.end - line.start).coerceIn(0, lineText.length)
                val temporary = lines[lineIndex]
                lines[lineIndex] = lines[targetIndex]
                lines[targetIndex] = temporary
                val updated = lines.joinToString("\n")
                val caretStart = editorLineSpans(updated)[targetIndex].start
                val caret = (caretStart + relativeCaret.coerceAtMost(lines[targetIndex].length)).coerceIn(0, updated.length)
                EditorToolOutcome(TextFieldValue(updated, selection = TextRange(caret)), "Moved line to " + (targetIndex + 1) + ".")
            }
        }
        EditorToolAction.INSERT_LINE_BELOW -> {
            val insertionAt = line.end
            val updated = text.substring(0, insertionAt) + "\n" + text.substring(insertionAt)
            val caret = if (line.end > line.contentEnd) insertionAt else insertionAt + 1
            EditorToolOutcome(TextFieldValue(updated, selection = TextRange(caret)), "Inserted a line below.")
        }
        EditorToolAction.INDENT -> {
            val updated = rewriteEditorLines(value, lineRange) { old ->
                EditorLineRewrite("    " + old) { offset -> offset + 4 }
            }
            EditorToolOutcome(updated, "Indented " + lineRange.count() + " line(s).")
        }
        EditorToolAction.OUTDENT -> {
            val updated = rewriteEditorLines(value, lineRange) { old ->
                val remove = when {
                    old.startsWith("\t") -> 1
                    old.startsWith("    ") -> 4
                    old.startsWith("   ") -> 3
                    old.startsWith("  ") -> 2
                    old.startsWith(" ") -> 1
                    else -> 0
                }
                EditorLineRewrite(old.drop(remove)) { offset -> (offset - remove).coerceAtLeast(0) }
            }
            EditorToolOutcome(updated, "Outdented " + lineRange.count() + " line(s).")
        }
        EditorToolAction.TOGGLE_COMMENT -> {
            val prefix = commentPrefixForPath(path)
            if (prefix == null) {
                EditorToolOutcome(value, "Comment toggling is not configured for this file type.")
            } else {
                val selectedLines = lineRange.map { index -> text.substring(spans[index].start, spans[index].contentEnd) }
                val allCommented = selectedLines.filter { it.isNotBlank() }.all { old ->
                    val indentation = old.takeWhile { it == ' ' || it == '\t' }
                    old.substring(indentation.length).startsWith(prefix)
                }
                val updated = rewriteEditorLines(value, lineRange) { old ->
                    if (old.isBlank()) {
                        EditorLineRewrite(old)
                    } else {
                        val indentationLength = old.takeWhile { it == ' ' || it == '\t' }.length
                        val rest = old.drop(indentationLength)
                        if (allCommented) {
                            val removedSpacing = if (rest.startsWith(prefix + " ")) prefix.length + 1 else prefix.length
                            EditorLineRewrite(old.take(indentationLength) + rest.drop(removedSpacing)) { offset ->
                                when {
                                    offset <= indentationLength -> offset
                                    offset <= indentationLength + removedSpacing -> indentationLength
                                    else -> (offset - removedSpacing).coerceAtLeast(indentationLength)
                                }
                            }
                        } else {
                            EditorLineRewrite(old.take(indentationLength) + prefix + " " + rest) { offset ->
                                if (offset <= indentationLength) offset else offset + prefix.length + 1
                            }
                        }
                    }
                }
                EditorToolOutcome(updated, if (allCommented) "Uncommented selected line(s)." else "Commented selected line(s).")
            }
        }
        EditorToolAction.WRAP_PARENTHESES, EditorToolAction.WRAP_QUOTES -> {
            val delimiter = if (action == EditorToolAction.WRAP_PARENTHESES) "(" to ")" else "\"" to "\""
            val start: Int
            val end: Int
            if (!value.selection.collapsed) {
                start = value.selection.min.coerceIn(0, text.length)
                end = value.selection.max.coerceIn(start, text.length)
            } else {
                start = line.start
                end = line.contentEnd
            }
            val selected = text.substring(start, end)
            val replacement = delimiter.first + selected + delimiter.second
            val updated = text.replaceRange(start, end, replacement)
            val nextSelection = if (value.selection.collapsed) {
                TextRange(start + replacement.length)
            } else {
                TextRange(start + delimiter.first.length, start + delimiter.first.length + selected.length)
            }
            EditorToolOutcome(TextFieldValue(updated, selection = nextSelection), "Wrapped text with " + delimiter.first + delimiter.second + ".")
        }
        EditorToolAction.SORT_SELECTED_LINES -> {
            if (value.selection.collapsed) {
                EditorToolOutcome(value, "Select lines first to sort them.")
            } else {
                val start = spans[lineRange.first].start
                val end = spans[lineRange.last].contentEnd
                val sorted = lineRange.map { spans[it] }.map { text.substring(it.start, it.contentEnd) }.sorted()
                val replacement = sorted.joinToString("\n")
                val updated = text.replaceRange(start, end, replacement)
                EditorToolOutcome(
                    TextFieldValue(updated, selection = TextRange(start, (start + replacement.length).coerceAtMost(updated.length))),
                    "Sorted " + sorted.size + " selected line(s).",
                )
            }
        }
        EditorToolAction.JOIN_SELECTED_LINES -> {
            if (value.selection.collapsed || lineRange.first == lineRange.last) {
                EditorToolOutcome(value, "Select multiple lines to join them.")
            } else {
                val start = spans[lineRange.first].start
                val end = spans[lineRange.last].contentEnd
                val joined = lineRange.map { spans[it] }
                    .map { text.substring(it.start, it.contentEnd).trim() }
                    .filter { it.isNotEmpty() }
                    .joinToString(" ")
                val updated = text.replaceRange(start, end, joined)
                EditorToolOutcome(TextFieldValue(updated, selection = TextRange(start + joined.length)), "Joined selected lines.")
            }
        }
        EditorToolAction.TRIM_TRAILING_WHITESPACE -> {
            val updated = text.replace(Regex("[ \\t]+(?=\\r?$)", RegexOption.MULTILINE), "")
            val prefix = text.substring(0, value.selection.end.coerceIn(0, text.length))
                .replace(Regex("[ \\t]+(?=\\r?$)", RegexOption.MULTILINE), "")
            EditorToolOutcome(
                TextFieldValue(updated, selection = TextRange(prefix.length.coerceIn(0, updated.length))),
                if (updated == text) "No trailing whitespace found." else "Removed trailing whitespace.",
            )
        }
        EditorToolAction.CONVERT_TABS_TO_SPACES -> {
            val beforeCaret = text.substring(0, value.selection.end.coerceIn(0, text.length))
            val updated = text.replace("\t", "    ")
            val caret = value.selection.end + beforeCaret.count { it == '\t' } * 3
            EditorToolOutcome(
                TextFieldValue(updated, selection = TextRange(caret.coerceIn(0, updated.length))),
                if (updated == text) "No tabs found." else "Converted tabs to four spaces.",
            )
        }
        EditorToolAction.SMART_SELECT -> {
            val caret = value.selection.min.coerceIn(0, text.length)
            val enclosing = findCodeFoldRegions(text)
                .filter { caret in it.startOffset..it.endOffset }
                .minByOrNull { it.endOffset - it.startOffset }
            if (enclosing != null) {
                EditorToolOutcome(
                    value.copy(selection = TextRange(enclosing.startOffset, enclosing.endOffset + 1), composition = null),
                    "Selected code block on lines " + enclosing.startLine + "–" + enclosing.endLine + ".",
                )
            } else {
                val identifier = identifierRangeAt(text, caret)
                if (identifier != null) {
                    EditorToolOutcome(value.copy(selection = TextRange(identifier.first, identifier.last + 1), composition = null), "Selected identifier.")
                } else {
                    EditorToolOutcome(value.copy(selection = TextRange(line.start, line.contentEnd), composition = null), "Selected current line.")
                }
            }
        }
        EditorToolAction.CONTEXT_CAPSULE -> EditorToolOutcome(value, "Copied AstraCode Context Capsule to clipboard.", createEditorContextCapsule(value, path))
        EditorToolAction.SELECTION_PULSE -> {
            val selected = if (value.selection.collapsed) lineText else text.substring(value.selection.min, value.selection.max)
            val lines = selected.count { it == '\n' } + 1
            val words = Regex("[\\p{L}\\p{N}_]+").findAll(selected).count()
            val nonEmptyLines = selected.split('\n').count { it.isNotBlank() }
            EditorToolOutcome(
                value,
                "Selection Pulse: " + selected.length + " chars · " + words + " words · " + lines + " line(s) · " + nonEmptyLines + " non-empty.",
            )
        }
        EditorToolAction.DESCRIBE_SYMBOL -> EditorToolOutcome(value, "Opened the symbol reference.")
    }
}

private fun splitEditorLines(text: String): MutableList<String> {
    val result = mutableListOf<String>()
    var start = 0
    text.forEachIndexed { index, char ->
        if (char == '\n') {
            result += text.substring(start, index)
            start = index + 1
        }
    }
    result += text.substring(start)
    return result
}

private fun commentPrefixForPath(path: String): String? = when (path.substringAfterLast('.', "").lowercase()) {
    "kt", "kts", "java", "c", "h", "cc", "cpp", "hpp", "js", "jsx", "ts", "tsx", "go", "rs", "swift", "dart", "php", "scala", "groovy" -> "//"
    "py", "sh", "bash", "rb", "yml", "yaml", "toml", "ini", "properties", "r", "pl" -> "#"
    "sql", "lua", "hs" -> "--"
    else -> null
}

private fun identifierRangeAt(text: String, offset: Int): IntRange? {
    if (text.isEmpty()) return null
    val caret = offset.coerceIn(0, text.length)
    var index = if (caret == text.length) caret - 1 else caret
    fun isIdentifier(char: Char): Boolean = char.isLetterOrDigit() || char == '_' || char == '$'
    if (index < 0 || !isIdentifier(text[index])) {
        if (index + 1 < text.length && isIdentifier(text[index + 1])) index++
        else return null
    }
    var start = index
    var end = index
    while (start > 0 && isIdentifier(text[start - 1])) start--
    while (end + 1 < text.length && isIdentifier(text[end + 1])) end++
    return start..end
}

private fun createEditorContextCapsule(value: TextFieldValue, path: String): String {
    val text = value.text
    val spans = editorLineSpans(text)
    val selectedStart = value.selection.min.coerceIn(0, text.length)
    val selectedEnd = value.selection.max.coerceIn(selectedStart, text.length)
    val startLine = text.take(selectedStart).count { it == '\n' }
    val endLine = text.take(if (selectedEnd > selectedStart) selectedEnd - 1 else selectedStart).count { it == '\n' }
    val firstContextLine = (startLine - 2).coerceAtLeast(0)
    val lastContextLine = (endLine + 2).coerceAtMost(spans.lastIndex)
    val contextStart = spans[firstContextLine].start
    val contextEnd = spans[lastContextLine].contentEnd
    val selectionStart = if (value.selection.collapsed) spans[startLine.coerceIn(0, spans.lastIndex)].start else selectedStart
    val selectionEnd = if (value.selection.collapsed) spans[startLine.coerceIn(0, spans.lastIndex)].contentEnd else selectedEnd
    val selectedCode = text.substring(selectionStart, selectionEnd)
    val selectedLines = selectedCode.split('\n')
    val wasTruncated = selectedLines.size > 200
    val shownCode = if (wasTruncated) selectedLines.take(200).joinToString("\n") else selectedCode
    val payload = if (value.selection.collapsed) {
        text.substring(contextStart, contextEnd)
    } else {
        buildString {
            append(text.substring(contextStart, selectedStart))
            appendLine("<<< SELECTED CODE START >>>")
            append(shownCode)
            appendLine()
            appendLine("<<< SELECTED CODE END >>>")
            append(text.substring(selectedEnd, contextEnd))
        }
    }
    val language = when (path.substringAfterLast('.', "").lowercase()) {
        "kt", "kts" -> "kotlin"
        "java" -> "java"
        "py" -> "python"
        "js", "jsx" -> "javascript"
        "ts", "tsx" -> "typescript"
        "json" -> "json"
        "xml" -> "xml"
        "html", "htm" -> "html"
        "sh", "bash" -> "bash"
        "sql" -> "sql"
        else -> ""
    }
    val longestFence = Regex("\\u0060+").findAll(payload).maxOfOrNull { it.value.length } ?: 0
    val fence = "\u0060".repeat(maxOf(3, longestFence + 1))
    return buildString {
        appendLine("AstraCode Context Capsule")
        appendLine("File: " + path.ifBlank { "(untitled)" })
        appendLine("Lines: " + (startLine + 1) + "–" + (endLine + 1))
        appendLine(if (value.selection.collapsed) "Payload: current line with nearby context" else "Payload: selected code with nearby context")
        appendLine(fence + language)
        append(payload)
        appendLine()
        append(fence)
        appendLine()
        if (wasTruncated) appendLine("[Selection truncated: first 200 lines copied]")
    }
}

