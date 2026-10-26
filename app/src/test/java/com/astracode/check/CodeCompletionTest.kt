package com.astracode.check

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeCompletionTest {
    @Test
    fun completionUsesTheDetectedKotlinLanguageProfile() {
        val source = "fun pri"
        val suggestions = codeCompletionSuggestions(source, "src/Main.kt", source.length)
        assertTrue(suggestions.contains("println"))
        assertTrue(suggestions.contains("print"))
    }

    @Test
    fun documentIdentifiersAreSuggestedWhenTheyMatchThePrefix() {
        val source = "fun greeting() = 1\nval result = gre"
        val suggestions = codeCompletionSuggestions(source, "src/Main.kt", source.length)
        assertTrue(suggestions.contains("greeting"))
    }

    @Test
    fun completionUsesPythonWordsForPythonFiles() {
        val source = "def pri"
        assertTrue(codeCompletionSuggestions(source, "main.py", source.length).contains("print"))
    }

    @Test
    fun selectingCompletionReplacesOnlyTheCurrentIdentifierPrefix() {
        val value = TextFieldValue("fun pri()", selection = TextRange(7))
        val completed = applyCodeCompletion(value, "println")
        assertEquals("fun println()", completed?.text)
        assertEquals(TextRange(11), completed?.selection)
    }

    @Test
    fun completionAtMiddleOfLinePreservesSurroundingTextAndCaret() {
        val source = "val result = pri + 1"
        val caret = source.indexOf("pri") + "pri".length
        val prefixRange = codeCompletionPrefixRange(source, caret)
        assertTrue(codeCompletionSuggestions(source, "Main.kt", caret).contains("println"))

        val completed = applyCodeCompletionAtRange(
            TextFieldValue(source, selection = TextRange(caret)),
            "println",
            prefixRange!!,
        )

        assertEquals("val result = println + 1", completed?.text)
        assertEquals(TextRange(source.indexOf("pri") + "println".length), completed?.selection)
    }

    @Test
    fun completionAtMiddleOfIdentifierReplacesTheWholeIdentifier() {
        val source = "val result = printf + 1"
        val caret = source.indexOf("pri") + "pri".length
        val prefixRange = codeCompletionPrefixRange(source, caret)!!

        val completed = applyCodeCompletionAtRange(
            TextFieldValue(source, selection = TextRange(caret)),
            "println",
            prefixRange,
        )

        assertEquals("val result = println + 1", completed?.text)
        assertEquals(TextRange(source.indexOf("pri") + "println".length), completed?.selection)
    }

    @Test
    fun completionRejectsARangeStartingInsideAnotherIdentifier() {
        val value = TextFieldValue("xpri", selection = TextRange(4))
        assertEquals(null, applyCodeCompletionAtRange(value, "println", TextRange(1, 4)))
    }

    @Test
    fun suggestionsFollowChangedPrefixAndCaretPosition() {
        val source = "println()"
        val afterPrin = codeCompletionSuggestions(source, "Main.kt", 4)
        assertTrue(afterPrin.contains("println"))

        val afterPrint = codeCompletionSuggestions(source, "Main.kt", 5)
        assertTrue(afterPrint.contains("println"))
        assertFalse(afterPrint.contains("print"))

        assertTrue(codeCompletionSuggestions(source, "Main.kt", source.length).isEmpty())
    }

    @Test
    fun completionStillTargetsTheActiveCaretWhenTheSelectionAnchorMoves() {
        val value = TextFieldValue("fun pri()", selection = TextRange(4, 7))
        val completed = applyCodeCompletion(value, "println")
        assertEquals("fun println()", completed?.text)
        assertEquals(TextRange(11), completed?.selection)
    }

    @Test
    fun completionFallsBackToTheFinalIdentifierWhenFocusMovesAway() {
        val value = TextFieldValue("fun pri", selection = TextRange(0))
        val completed = applyCodeCompletion(value, "println")
        assertEquals("fun println", completed?.text)
        assertEquals(TextRange(11), completed?.selection)
    }

    @Test
    fun applyingCompletionUsesTheCapturedPrefixEvenIfTheCaretMoves() {
        val value = TextFieldValue("fun pri", selection = TextRange(0))
        val completed = applyCodeCompletionAtRange(value, "println", TextRange(4, 7))
        assertEquals("fun println", completed?.text)
        assertEquals(TextRange(11), completed?.selection)
    }

    @Test
    fun compoundFileSuffixSelectsTheMoreSpecificLanguage() {
        assertEquals("Gradle Kotlin DSL", detectSyntaxLanguage("app/build.gradle.kts")?.name)
    }

    @Test
    fun plainTextAndUnknownFilesDoNotOfferCodeCompletions() {
        assertTrue(codeCompletionSuggestions("pri", "notes.txt", 3).isEmpty())
        assertFalse(codeCompletionSuggestions("some wor", "README.md", 8).isNotEmpty())
    }
}
