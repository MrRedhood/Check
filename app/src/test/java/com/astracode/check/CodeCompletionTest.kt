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
    fun cssCompletionSupportsHyphenatedPropertiesAndReplacesTheWholeProperty() {
        val source = "body { font-size: 12px; }"
        val caret = source.indexOf("font-size") + "font-si".length
        val suggestions = codeCompletionSuggestions(source, "styles.css", caret)
        assertTrue(suggestions.contains("font-size"))

        val prefixRange = codeCompletionPrefixRange(source, caret, "styles.css")!!
        val completed = applyCodeCompletionAtRange(
            TextFieldValue(source, selection = TextRange(caret)),
            "font-size",
            prefixRange,
            "styles.css",
        )

        assertEquals(source, completed?.text)
        assertEquals(
            TextRange(source.indexOf("font-size") + "font-size".length),
            completed?.selection,
        )
    }

    @Test
    fun cssCustomPropertiesAreSuggestedAndAppliedAsWholeIdentifiers() {
        val source = ":root { --brand-color: red; } .card { color: var(--brand); }"
        val prefix = "--brand"
        val tokenStart = source.lastIndexOf(prefix)
        val caret = tokenStart + prefix.length
        val suggestions = codeCompletionSuggestions(source, "styles.css", caret)

        assertTrue(suggestions.contains("--brand-color"))

        val range = codeCompletionPrefixRange(source, caret, "styles.css")!!
        val completed = applyCodeCompletionAtRange(
            TextFieldValue(source, selection = TextRange(caret)),
            "--brand-color",
            range,
            "styles.css",
        )

        assertEquals(
            source.replaceRange(tokenStart, tokenStart + prefix.length, "--brand-color"),
            completed?.text,
        )
        assertEquals(TextRange(tokenStart + "--brand-color".length), completed?.selection)
    }

    @Test
    fun htmlHyphenatedAttributesAreSuggestedAndAppliedAsWholeIdentifiers() {
        val source = """<div data-user-name="one"></div><div data-user-x="two"></div>"""
        val prefix = "data-user-"
        val tokenStart = source.lastIndexOf(prefix)
        val caret = tokenStart + prefix.length
        val suggestions = codeCompletionSuggestions(source, "index.html", caret)

        assertTrue(suggestions.contains("data-user-name"))

        val range = codeCompletionPrefixRange(source, caret, "index.html")!!
        val completed = applyCodeCompletionAtRange(
            TextFieldValue(source, selection = TextRange(caret)),
            "data-user-name",
            range,
            "index.html",
        )

        assertEquals(
            source.replaceRange(tokenStart, tokenStart + "data-user-x".length, "data-user-name"),
            completed?.text,
        )
        assertEquals(TextRange(tokenStart + "data-user-name".length), completed?.selection)
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
