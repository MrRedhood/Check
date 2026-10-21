package com.astracode.check

import org.junit.Assert.assertEquals
import org.junit.Test

class FileFormatsTest {
    @Test
    fun namesWithoutExtensionsBecomeTextFiles() {
        assertEquals("my file (1).txt", normalizeNewTextFileName("my file (1)"))
        assertEquals("README.txt", normalizeNewTextFileName(" README. "))
        assertEquals("notes.txt", normalizeNewTextFileName("notes"))
    }

    @Test
    fun explicitExtensionsArePreserved() {
        assertEquals("Hello.kt", normalizeNewTextFileName("Hello.kt"))
        assertEquals("readme.md", normalizeNewTextFileName("readme.md"))
        assertEquals(".env.txt", normalizeNewTextFileName(".env"))
    }

    @Test
    fun metadataShowsExtensionAndReadableFormat() {
        assertEquals("txt file (text file)", formatFileTypeLabel("note.txt"))
        assertEquals("md file (Markdown document)", formatFileTypeLabel("README.md"))
        assertEquals("kt file (Kotlin source file)", formatFileTypeLabel("Main.kt"))
        assertEquals("zip file (ZIP archive)", formatFileTypeLabel("project.zip"))
    }

    @Test
    fun metadataHandlesUnknownAndExtensionlessFiles() {
        assertEquals("xyz file (xyz format)", formatFileTypeLabel("data.xyz"))
        assertEquals("File (no extension)", formatFileTypeLabel("LICENSE"))
    }
}
