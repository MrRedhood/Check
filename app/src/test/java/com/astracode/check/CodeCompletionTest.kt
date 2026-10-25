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
    fun compoundFileSuffixSelectsTheMoreSpecificLanguage() {
        assertEquals("Gradle Kotlin DSL", detectSyntaxLanguage("app/build.gradle.kts")?.name)
    }

    @Test
    fun plainTextAndUnknownFilesDoNotOfferCodeCompletions() {
        assertTrue(codeCompletionSuggestions("pri", "notes.txt", 3).isEmpty())
        assertFalse(codeCompletionSuggestions("some wor", "README.md", 8).isNotEmpty())
    }
}
