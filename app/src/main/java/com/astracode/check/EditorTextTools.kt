package com.astracode.check

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

internal fun lineNumberVisualTransformation(): VisualTransformation = VisualTransformation { input ->
    val original = input.text
    val lineCount = original.count { it == '\n' } + 1
    val digitWidth = lineCount.toString().length
    val linePrefixWidth = digitWidth + 3
    val offsets = IntArray(original.length + 1)
    val transformed = StringBuilder(original.length + lineCount * linePrefixWidth)
    var lineStart = 0
    var lineNumber = 1

    for (index in 0..original.length) {
        if (index == lineStart) {
            transformed.append(lineNumber.toString().padStart(digitWidth)).append(" │ ")
            lineNumber++
        }
        offsets[index] = transformed.length
        if (index < original.length) {
            val character = original[index]
            transformed.append(character)
            if (character == '\n') lineStart = index + 1
        }
    }

    val transformedText = transformed.toString()
    TransformedText(
        text = AnnotatedString(transformedText),
        offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                offsets[offset.coerceIn(0, original.length)]

            override fun transformedToOriginal(offset: Int): Int {
                val target = offset.coerceIn(0, transformedText.length)
                var low = 0
                var high = offsets.lastIndex
                while (low < high) {
                    val middle = (low + high) ushr 1
                    if (offsets[middle] < target) low = middle + 1 else high = middle
                }
                return low
            }
        },
    )
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
