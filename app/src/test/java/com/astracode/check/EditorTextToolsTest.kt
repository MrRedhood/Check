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
        assertEquals(14, transformed.offsetMapping.originalToTransformed(6))
        assertEquals(7, transformed.offsetMapping.transformedToOriginal(15))
    }

    @Test
    fun braceFoldingIgnoresBracesInsideStringsAndComments() {
        val source = "fun main() {\n    val marker = \"{ text }\"\n    /* } is not a block end */\n}\n"
        val regions = findCodeFoldRegions(source)

        assertEquals(1, regions.size)
        assertEquals(1, regions.single().startLine)
        assertEquals(4, regions.single().endLine)
        assertEquals("fun main() {", regions.single().label)

        val folded = codeFoldingVisualTransformation(regions, setOf(1)).filter(AnnotatedString(source))
        assertEquals("1 │ fun main() { … 3 lines folded …\n5 │ ", folded.text.text)
        assertEquals(4, folded.offsetMapping.originalToTransformed(0))
        for (offset in 0..source.length) {
            assertTrue(folded.offsetMapping.originalToTransformed(offset) in 0..folded.text.length)
        }
        for (offset in 0..folded.text.length) {
            assertTrue(folded.offsetMapping.transformedToOriginal(offset) in 0..source.length)
        }
    }

    @Test
    fun braceFoldingFindsNestedRegionsAndKeepsLogicalLineNumbers() {
        val source = "fun outer() {\n    if (true) {\n        println(\"ok\")\n    }\n}\n"
        val regions = findCodeFoldRegions(source)

        assertEquals(2, regions.size)
        assertEquals(1, regions[0].startLine)
        assertEquals(5, regions[0].endLine)
        assertEquals(2, regions[1].startLine)
        assertEquals(4, regions[1].endLine)
        val folded = codeFoldingVisualTransformation(regions, setOf(1)).filter(AnnotatedString(source))
        assertEquals("1 │ fun outer() { … 4 lines folded …\n6 │ ", folded.text.text)
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

    @Test
    fun syntaxHighlighterRecognizesMoreThan150LanguageNamesAndCommonTypes() {
        assertTrue(supportedSyntaxLanguageCount() >= 150)
        assertEquals("Kotlin", detectSyntaxLanguage("src/Main.kt")?.name)
        assertEquals("Python", detectSyntaxLanguage("script.py")?.name)
        assertEquals("HTML", detectSyntaxLanguage("view.html")?.name)
        assertEquals("SQL", detectSyntaxLanguage("query.sql")?.name)

        val source = "fun greet(name: String) {\n  // note\n  println(\"hello\")\n  val n = 42\n}"
        val spans = findSyntaxHighlightSpans(source, "Main.kt")
        assertTrue(spans.any { source.substring(it.start, it.end) == "fun" && it.style.color == SyntaxPalette.Keyword })
        assertTrue(spans.any { source.substring(it.start, it.end) == "String" && it.style.color == SyntaxPalette.Type })
        assertTrue(spans.any { source.substring(it.start, it.end) == "// note" && it.style.color == SyntaxPalette.Comment })
        assertTrue(spans.any { source.substring(it.start, it.end) == "\"hello\"" && it.style.color == SyntaxPalette.String })
        assertTrue(spans.any { source.substring(it.start, it.end) == "42" && it.style.color == SyntaxPalette.Number })
    }

    @Test
    fun syntaxHighlightingPreservesEditorTextAndComposesWithLineNumbers() {
        val source = "fun answer() = 42\n"
        val transformed = codeFoldingVisualTransformation(emptyList(), emptySet(), "Answer.kt")
            .filter(AnnotatedString(source))

        assertEquals("1 │ fun answer() = 42\n2 │ ", transformed.text.text)
        val keywordStart = transformed.text.text.indexOf("fun")
        assertTrue(transformed.text.spanStyles.any {
            it.start == keywordStart && it.end == keywordStart + 3 &&
                it.item.color == SyntaxPalette.Keyword
        })
        for (offset in 0..source.length) {
            assertTrue(transformed.offsetMapping.originalToTransformed(offset) in 0..transformed.text.length)
        }
    }

    @Test
    fun unknownAndOversizedFilesSkipHighlightingSafely() {
        assertTrue(findSyntaxHighlightSpans("val x = 1", "notes.unknownext").isEmpty())
        assertTrue(findSyntaxHighlightSpans("x".repeat(512 * 1024 + 1), "main.kt").isEmpty())
    }

}
