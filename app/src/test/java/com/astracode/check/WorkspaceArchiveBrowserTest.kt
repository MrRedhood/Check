package com.astracode.check

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class WorkspaceArchiveBrowserTest {
    @Test
    fun browsesAndReadsFilesWithoutExtractingArchive() {
        val root = Files.createTempDirectory("check-zip-browser").toFile()
        try {
            val originalBytes = byteArrayOf(0, 1, 2, -1, -128, 42)
            createZip(File(root, "project.zip"), mapOf(
                "src/Main.kt" to "fun main() = Unit\n".toByteArray(),
                "assets/image.bin" to originalBytes,
                "README.txt" to "read me".toByteArray(),
            ))
            val entries = WorkspaceArchiveBrowser.list(root, "project.zip")
            assertEquals(listOf("assets", "src", "README.txt"), entries.map { it.name })
            assertTrue(entries[0].isDirectory)
            assertEquals(6L, WorkspaceArchiveBrowser.metadata(root, "project.zip", "assets/image.bin").sizeBytes)
            assertArrayEquals(originalBytes, WorkspaceArchiveBrowser.read(root, "project.zip", "assets/image.bin"))
            assertFalse(File(root, "src").exists())
            assertFalse(File(root, "assets").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun writesAndRenamesEntriesInsideTheArchiveAndPreservesBinaryBytes() {
        val root = Files.createTempDirectory("check-zip-write").toFile()
        try {
            createZip(File(root, "project.zip"), mapOf("src/Main.kt" to "old".toByteArray(), "data.bin" to byteArrayOf(0, -1, 44)))
            WorkspaceArchiveBrowser.write(root, "project.zip", "src/Main.kt", "new text".toByteArray())
            WorkspaceArchiveBrowser.rename(root, "project.zip", "src/Main.kt", "App.kt")
            WorkspaceArchiveBrowser.copy(root, "project.zip", "data.bin", "backup/data-copy.bin")
            WorkspaceArchiveBrowser.move(root, "project.zip", "src/App.kt", "lib/App.kt")

            assertArrayEquals("new text".toByteArray(), WorkspaceArchiveBrowser.read(root, "project.zip", "lib/App.kt"))
            assertArrayEquals(byteArrayOf(0, -1, 44), WorkspaceArchiveBrowser.read(root, "project.zip", "data.bin"))
            assertArrayEquals(byteArrayOf(0, -1, 44), WorkspaceArchiveBrowser.read(root, "project.zip", "backup/data-copy.bin"))
            assertFalse(WorkspaceArchiveBrowser.list(root, "project.zip", "src").any { it.name == "App.kt" })
            WorkspaceArchiveBrowser.delete(root, "project.zip", "backup")
            assertTrue(WorkspaceArchiveBrowser.list(root, "project.zip").none { it.name == "backup" })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun canCreateAndExtractNestedZipWithoutWorkspaceExtraction() {
        val root = Files.createTempDirectory("check-nested-zip").toFile()
        try {
            createZip(File(root, "bundle.zip"), mapOf("src/Main.kt" to "fun main() = Unit".toByteArray(), "src/lib/Util.kt" to "class Util".toByteArray()))
            WorkspaceArchiveBrowser.createNestedZip(root, "bundle.zip", listOf("src"), "backups/src.zip")
            val nested = WorkspaceArchiveBrowser.read(root, "bundle.zip", "backups/src.zip")
            assertTrue(nested.isNotEmpty())
            WorkspaceArchiveBrowser.extractNestedZip(root, "bundle.zip", "backups/src.zip", "restored")
            assertEquals("fun main() = Unit", WorkspaceArchiveBrowser.read(root, "bundle.zip", "restored/src/Main.kt").toString(Charsets.UTF_8))
            assertEquals("class Util", WorkspaceArchiveBrowser.read(root, "bundle.zip", "restored/src/lib/Util.kt").toString(Charsets.UTF_8))
            assertFalse(File(root, "restored").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun unsafeEntryNamesAreRejectedAndExistingArchiveIsNotDamaged() {
        val root = Files.createTempDirectory("check-unsafe-zip").toFile()
        try {
            createZip(File(root, "bad.zip"), mapOf("../outside.txt" to "unsafe".toByteArray()))
            try {
                WorkspaceArchiveBrowser.list(root, "bad.zip")
                fail("Unsafe ZIP path should be rejected")
            } catch (expected: SecurityException) {
                assertTrue(expected.message.orEmpty().contains("unsafe", ignoreCase = true))
            }
            assertFalse(File(root.parentFile, "outside.txt").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun creatingFolderInsideArchiveAndMetadataPreservesFormatAndTimestamp() {
        val root = Files.createTempDirectory("check-zip-folder").toFile()
        try {
            createZip(File(root, "workspace.zip"), mapOf("src/Main.kt" to "one".toByteArray()))
            WorkspaceArchiveBrowser.createDirectory(root, "workspace.zip", "docs")
            WorkspaceArchiveBrowser.write(root, "workspace.zip", "docs/notes.md", "# note".toByteArray())
            val info = WorkspaceArchiveBrowser.metadata(root, "workspace.zip", "docs/notes.md")
            assertEquals("MD", info.format)
            assertEquals(6L, info.sizeBytes)
            assertTrue((info.modifiedAtMillis ?: 0L) > 0L)
            assertTrue(WorkspaceArchiveBrowser.list(root, "workspace.zip").any { it.name == "docs" })
        } finally {
            root.deleteRecursively()
        }
    }

    private fun createZip(file: File, contents: Map<String, ByteArray>) {
        ZipOutputStream(file.outputStream()).use { zip ->
            contents.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }
}
