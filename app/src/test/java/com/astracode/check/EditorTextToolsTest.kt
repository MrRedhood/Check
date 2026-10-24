package com.astracode.check

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorTextToolsTest {
    @Test
    fun lineNumbersOnlyCoverLogicalLinesInTheBuffer() {
        val transformed = lineNumberVisualTransformation().filter(AnnotatedString("alpha\nbeta\n"))

        assertEquals("1 │ alpha\n2 │ beta\n3 │ ", transformed.text.text)
        assertEquals(4, transformed.offsetMapping.originalToTransformed(0))
        assertEquals(10, transformed.offsetMapping.originalToTransformed(6))
        assertEquals(11, transformed.offsetMapping.transformedToOriginal(15))
    }

    @Test
    fun findNextAndPreviousWrapAndSelectLiteralMatches() {
        val value = TextFieldValue("x + y\nx + z\n", selection = TextRange(0))

        val first = findNextMatch(value, "x +")
        assertEquals(TextRange(0, 3), first.selection)

        val second = findNextMatch(first, "x +")
        assertEquals(TextRange(6, 9), second.selection)

        val wrapped = findNextMatch(second, "x +")
        assertEquals(TextRange(0, 3), wrapped.selection)

        val previous = findPreviousMatch(wrapped.copy(selection = TextRange(10)), "x +")
        assertEquals(TextRange(6, 9), previous.selection)
    }

    @Test
    fun replaceOneThenReplaceAllSupportsWordsSymbolsAndLineBreaks() {
        val initial = TextFieldValue("val target = 1\nval target = 2\n", selection = TextRange(0))
        val firstMatch = findNextMatch(initial, "target")
        val (oneReplaced, didReplace) = replaceOneMatch(firstMatch, "target", "answer")

        assertTrue(didReplace)
        assertEquals("val answer = 1\nval target = 2\n", oneReplaced.text)

        val (allReplaced, count) = replaceAllMatches(oneReplaced, "target", "item")
        assertEquals(1, count)
        assertEquals("val answer = 1\nval item = 2\n", allReplaced.text)

        val multiline = TextFieldValue("first line\nsecond line", selection = TextRange(0))
        val (joined, multilineCount) = replaceAllMatches(multiline, "first line\nsecond line", "joined")
        assertEquals(1, multilineCount)
        assertEquals("joined", joined.text)
    }

    @Test
    fun emptySearchNeverMatchesOrChangesText() {
        val value = TextFieldValue("keep me")
        assertEquals(0, countMatches(value.text, ""))
        val (result, replaced) = replaceOneMatch(value, "", "bad")
        assertFalse(replaced)
        assertEquals(value.text, result.text)
    }
}
