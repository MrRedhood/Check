package com.astracode.check

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class WorkspaceArchiveTest {
    @Test
    fun createAndExtractZipPreservesNestedBinaryAndTextBytes() {
        val root = Files.createTempDirectory("check-workspace-archive").toFile()
        try {
            File(root, "src/nested").mkdirs()
            val binary = byteArrayOf(0, 1, 2, 127, -1, -128, 42)
            File(root, "src/nested/image.bin").writeBytes(binary)
            File(root, "Main.kt").writeText("fun main() = Unit\n", Charsets.UTF_8)

            WorkspaceArchive.createZip(root, listOf("src", "Main.kt"), "bundle.zip")
            assertTrue(File(root, "bundle.zip").isFile)
            WorkspaceArchive.extractZip(root, "bundle.zip", "restored")

            assertArrayEquals(binary, File(root, "restored/src/nested/image.bin").readBytes())
            assertEqualsText("fun main() = Unit\n", File(root, "restored/Main.kt").readText(Charsets.UTF_8))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun moveToZipRemovesOriginalFilesAndFoldersAfterWritingTheArchive() {
        val root = Files.createTempDirectory("check-workspace-move-to-zip").toFile()
        try {
            File(root, "src/nested").mkdirs()
            val sourceBytes = byteArrayOf(0, 1, -1, -128, 42)
            File(root, "src/nested/data.bin").writeBytes(sourceBytes)
            File(root, "Main.kt").writeText("fun main() = Unit\\n", Charsets.UTF_8)

            WorkspaceArchive.moveToZip(root, listOf("src", "Main.kt"), "bundle.zip")

            assertTrue(File(root, "bundle.zip").isFile)
            assertFalse(File(root, "src").exists())
            assertFalse(File(root, "Main.kt").exists())
            WorkspaceArchive.extractZip(root, "bundle.zip", "restored")
            assertArrayEquals(sourceBytes, File(root, "restored/src/nested/data.bin").readBytes())
            assertEqualsText("fun main() = Unit\\n", File(root, "restored/Main.kt").readText(Charsets.UTF_8))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun extractionRejectsZipSlipAndLeavesWorkspaceOutsideDestinationUntouched() {
        val root = Files.createTempDirectory("check-workspace-zip-slip").toFile()
        try {
            ZipOutputStream(File(root, "bad.zip").outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("../outside.txt"))
                zip.write("unsafe".toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            try {
                WorkspaceArchive.extractZip(root, "bad.zip", "restored")
                fail("Unsafe entry should have been rejected.")
            } catch (expected: SecurityException) {
                assertTrue(expected.message.orEmpty().contains("unsafe", ignoreCase = true))
            }
            assertFalse(File(root.parentFile, "outside.txt").exists())
            assertFalse(File(root, "restored").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun extractionRefusesToOverwriteAnExistingDestination() {
        val root = Files.createTempDirectory("check-workspace-no-overwrite").toFile()
        try {
            File(root, "payload.txt").writeText("payload")
            WorkspaceArchive.createZip(root, listOf("payload.txt"), "payload.zip")
            File(root, "restored").mkdirs()
            File(root, "restored/keep.txt").writeText("keep")
            try {
                WorkspaceArchive.extractZip(root, "payload.zip", "restored")
                fail("Existing destination should not be overwritten.")
            } catch (expected: java.io.IOException) {
                assertTrue(expected.message.orEmpty().contains("already exists", ignoreCase = true))
            }
            assertTrue(File(root, "restored/keep.txt").isFile)
            assertEqualsText("keep", File(root, "restored/keep.txt").readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun archiveRefusesPathsOutsideWorkspaceAndExistingOutputs() {
        val root = Files.createTempDirectory("check-workspace-invalid-archive").toFile()
        try {
            File(root, "payload.txt").writeText("ok")
            try {
                WorkspaceArchive.createZip(root, listOf("../payload.txt"), "bad.zip")
                fail("Traversal should have been rejected.")
            } catch (_: SecurityException) {
                // Expected.
            }
            WorkspaceArchive.createZip(root, listOf("payload.txt"), "good.zip")
            try {
                WorkspaceArchive.createZip(root, listOf("payload.txt"), "good.zip")
                fail("Existing archive should not be overwritten.")
            } catch (expected: java.io.IOException) {
                assertTrue(expected.message.orEmpty().contains("already exists", ignoreCase = true))
            }
        } finally {
            root.deleteRecursively()
        }
    }

    private fun assertEqualsText(expected: String, actual: String) {
        org.junit.Assert.assertEquals(expected, actual)
    }
}
