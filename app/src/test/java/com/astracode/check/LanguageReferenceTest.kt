package com.astracode.check

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageReferenceTest {
    @Test
    fun kotlinKeywordShowsDescriptionAndSyntax() {
        val source = "fun greet() = println(\"Hi\")"
        val result = describeLanguageSymbolAt(source, "Main.kt", source.indexOf("fun") + 1)
        assertNotNull(result)
        assertEquals("fun", result?.symbol)
        assertEquals("Kotlin", result?.language)
        assertEquals("Keyword", result?.category)
        assertTrue(result?.description.orEmpty().contains("function"))
        assertTrue(result?.syntax.orEmpty().contains("fun name"))
    }

    @Test
    fun kotlinStandardFunctionIsDescribed() {
        val source = "println(\"Hello\")"
        val result = describeLanguageSymbolAt(source, "Main.kt", 3)
        assertEquals("println", result?.symbol)
        assertEquals("Built-in function", result?.category)
        assertTrue(result?.description.orEmpty().contains("standard output"))
    }

    @Test
    fun pythonKeywordsUsePythonSpecificDescriptions() {
        val source = "def greet(name):\n    print(name)"
        val result = describeLanguageSymbolAt(source, "main.py", 1)
        assertEquals("Python", result?.language)
        assertEquals("Keyword", result?.category)
        assertTrue(result?.description.orEmpty().contains("Indentation"))
    }

    @Test
    fun findsADeclarationInTheCurrentFile() {
        val source = "val answer = 42\nfun greet() = answer"
        val offset = source.lastIndexOf("greet") + 2
        val result = describeLanguageSymbolAt(source, "Main.kt", offset)
        assertEquals("greet", result?.symbol)
        assertEquals("Local function", result?.category)
        assertEquals(2, result?.declarationLine)
        assertTrue(result?.declarationText.orEmpty().contains("fun greet"))
    }

    @Test
    fun kotlinSafeCallOperatorHasItsOwnDescription() {
        val source = "val displayName = user?.name"
        val offset = source.indexOf("?.") + 1
        val result = describeLanguageSymbolAt(source, "Main.kt", offset)
        assertEquals("?.", result?.symbol)
        assertEquals("Safe-call operator", result?.category)
    }

    @Test
    fun cssHyphenatedPropertyIsRecognized() {
        val source = "font-size: 12px;"
        val result = describeLanguageSymbolAt(source, "styles.css", 3)
        assertEquals("font-size", result?.symbol)
        assertEquals("CSS property", result?.category)
    }

    @Test
    fun stringContentsAreExplainedAsLiterals() {
        val source = "val name = \"Aman\""
        val result = describeLanguageSymbolAt(source, "Main.kt", source.indexOf("Aman") + 1)
        assertEquals("String literal", result?.category)
        assertTrue(result?.description.orEmpty().contains("data rather than code"))
    }

    @Test
    fun unknownFileDoesNotInventALanguage() {
        assertNull(describeLanguageSymbolAt("token", "unknown.extension", 2))
    }
}
