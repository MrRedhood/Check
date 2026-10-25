package com.astracode.check

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VirtualizedCodeEditorTest {
    @Test
    fun lineIndexFindsLogicalLinesIncludingTrailingEmptyLine() {
        val index = VirtualLineIndex("one\ntwo\n")

        assertEquals(3, index.lineCount)
        assertEquals(0, index.lineStart(0))
        assertEquals(4, index.lineStart(1))
        assertEquals(8, index.lineStart(2))
        assertEquals(3, index.lineEnd(0))
        assertEquals(7, index.lineEnd(1))
        assertEquals(8, index.lineEnd(2))
        assertEquals(2, index.lineForOffset(8))
    }

    @Test
    fun lineIndexHandlesWindowsLineEndingsWithoutShowingCarriageReturns() {
        val index = VirtualLineIndex("first\r\nsecond\r\n")

        assertEquals(3, index.lineCount)
        assertEquals("first", "first\r\nsecond\r\n".substring(index.lineStart(0), index.lineEnd(0)))
        assertEquals("second", "first\r\nsecond\r\n".substring(index.lineStart(1), index.lineEnd(1)))
    }

    @Test
    fun incrementalLineIndexUpdateHandlesInsertedAndRemovedNewlines() {
        val original = "one\ntwo\nthree"
        val index = VirtualLineIndex(original)

        val inserted = original.replaceRange(5, 5, "x\n")
        index.update(inserted, editStart = 5, oldEditEnd = 5, newEditEnd = 7)
        assertEquals(4, index.lineCount)
        assertEquals(listOf("one", "tx", "wo", "three"), (0 until index.lineCount).map {
            inserted.substring(index.lineStart(it), index.lineEnd(it))
        })

        val joined = inserted.replaceRange(6, 7, "")
        index.update(joined, editStart = 6, oldEditEnd = 7, newEditEnd = 6)
        assertEquals(3, index.lineCount)
        assertEquals(listOf("one", "txwo", "three"), (0 until index.lineCount).map {
            joined.substring(index.lineStart(it), index.lineEnd(it))
        })
    }

    @Test
    fun lineIndexFallsBackSafelyForProgrammaticWholeDocumentChanges() {
        val index = VirtualLineIndex("old\ntext")
        val replacement = "new\ncontent\nwith\nlines\n"

        index.update(replacement)

        assertEquals(5, index.lineCount)
        assertEquals(listOf("new", "content", "with", "lines", ""), (0 until index.lineCount).map {
            replacement.substring(index.lineStart(it), index.lineEnd(it))
        })
    }

    @Test
    fun multilineSelectionReplacementExtractsOnlyInsertedText() {
        assertEquals("X", insertedTextFromLineChange("abcde", "abXde"))
        assertEquals("", insertedTextFromLineChange("abcde", "ab"))
        assertEquals("\nnext", insertedTextFromLineChange("line", "line\nnext"))
    }

    @Test
    fun primitiveLineIndexHandlesHundredsOfThousandsOfLines() {
        val source = buildString {
            repeat(200_000) {
                append("x\\n")
            }
        }
        val index = VirtualLineIndex(source)

        assertEquals(200_001, index.lineCount)
        assertEquals(source.length, index.lineStart(200_000))
        assertEquals("x", source.substring(index.lineStart(199_999), index.lineEnd(199_999)))
    }

    @Test
    fun largeDocumentThresholdLeavesShortFilesOnExistingEditor() {
        assertFalse(shouldUseVirtualizedEditor(VIRTUALIZED_EDITOR_THRESHOLD_CHARS - 1))
        assertTrue(shouldUseVirtualizedEditor(VIRTUALIZED_EDITOR_THRESHOLD_CHARS))
    }
}
