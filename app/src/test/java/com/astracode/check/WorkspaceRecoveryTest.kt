package com.astracode.check

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class WorkspaceRecoveryTest {
    @Test fun atomicWriteReplacesWholeFileAndRejectsTraversal() {
        val root = Files.createTempDirectory("check-atomic-write").toFile()
        try {
            WorkspaceRecovery.writeAtomically(root, "config.json", "{\"version\":1}".toByteArray())
            WorkspaceRecovery.writeAtomically(root, "config.json", "{\"version\":2}".toByteArray())
            assertEquals("{\"version\":2}", File(root, "config.json").readText())
            try { WorkspaceRecovery.writeAtomically(root, "../outside.txt", "no".toByteArray()); fail("Traversal must fail.") }
            catch (_: SecurityException) { assertFalse(File(root.parentFile, "outside.txt").exists()) }
        } finally { root.deleteRecursively() }
    }

    @Test fun deletedItemCanBeRestoredWithoutOverwritingAnExistingFile() {
        val root = Files.createTempDirectory("check-trash-restore").toFile()
        try {
            File(root, "folder").mkdir()
            File(root, "folder/notes.txt").writeText("important data")
            val trash = WorkspaceRecovery.moveToTrash(root, "folder/notes.txt")
            assertFalse(File(root, "folder/notes.txt").exists())
            File(root, "folder/notes.txt").writeText("newer file")
            try { WorkspaceRecovery.restoreFromTrash(root, trash.id); fail("Restore must not overwrite.") }
            catch (expected: java.io.IOException) { assertTrue(expected.message.orEmpty().contains("already exists")) }
            WorkspaceRecovery.restoreFromTrash(root, trash.id, "folder/notes-restored.txt")
            assertEquals("important data", File(root, "folder/notes-restored.txt").readText())
            assertEquals("newer file", File(root, "folder/notes.txt").readText())
            assertTrue(WorkspaceRecovery.listTrash(root).isEmpty())
        } finally { root.deleteRecursively() }
    }

    @Test fun retentionCleanupOnlyRemovesExpiredValidTrashRecords() {
        val root = Files.createTempDirectory("check-trash-retention").toFile()
        try {
            File(root, "old.txt").writeText("old")
            val old = WorkspaceRecovery.moveToTrash(root, "old.txt", nowMillis = 1_000L)
            File(root, "unrelated.keep").writeText("keep")
            assertEquals(1, WorkspaceRecovery.cleanupExpiredTrash(root, nowMillis = 10_000L, retentionMillis = 5_000L))
            assertTrue(WorkspaceRecovery.listTrash(root).none { it.id == old.id })
            assertEquals("keep", File(root, "unrelated.keep").readText())
        } finally { root.deleteRecursively() }
    }

    @Test fun verifiedCopyPreviewSearchDuplicateFinderAndHealthReportWork() {
        val root = Files.createTempDirectory("check-file-tools").toFile()
        try {
            File(root, "src").mkdir()
            File(root, "src/a.txt").writeText("same bytes")
            File(root, "src/b.txt").writeText("same bytes")
            var lastProgress = 0L
            WorkspaceRecovery.copyVerified(root, "src/a.txt", "copy.txt", progress = { copied, _ -> lastProgress = copied })
            assertEquals("same bytes", File(root, "copy.txt").readText())
            assertEquals(10L, lastProgress)
            assertTrue(WorkspaceRecovery.findDuplicates(root).any { it.paths.contains("src/a.txt") && it.paths.contains("src/b.txt") })
            assertEquals("same bytes", WorkspaceRecovery.preview(root, "src/a.txt").text)
            File(root, "src/app.bin").writeBytes(byteArrayOf(0, 1, 2))
            assertFalse(WorkspaceRecovery.preview(root, "src/app.bin").supported)
            assertTrue(WorkspaceRecovery.search(root, WorkspaceSearchOptions(query = "a.txt")).any { it.path == "src/a.txt" })
            val report = WorkspaceRecovery.storageReport(root)
            assertTrue(report.totalBytes >= 23L)
            assertTrue(report.fileCount >= 4)
        } finally { root.deleteRecursively() }
    }

    @Test fun batchRenameValidatesTargetsBeforeChangingFilesAndSupportsCycles() {
        val root = Files.createTempDirectory("check-batch-rename").toFile()
        try {
            File(root, "a.txt").writeText("A")
            File(root, "b.txt").writeText("B")
            try {
                WorkspaceRecovery.batchRename(root, "", mapOf("a.txt" to "same.txt", "b.txt" to "same.txt"))
                fail("Duplicate targets must fail.")
            } catch (_: java.io.IOException) {
                assertEquals("A", File(root, "a.txt").readText())
                assertEquals("B", File(root, "b.txt").readText())
            }
            assertEquals(2, WorkspaceRecovery.batchRename(root, "", mapOf("a.txt" to "b.txt", "b.txt" to "a.txt")).size)
            assertEquals("A", File(root, "b.txt").readText())
            assertEquals("B", File(root, "a.txt").readText())
        } finally { root.deleteRecursively() }
    }

    @Test fun journalStoresOperationMetadataButNotFileContents() {
        val root = Files.createTempDirectory("check-operation-journal").toFile()
        try {
            WorkspaceRecovery.recordOperation(root, "copy-verified", listOf("secret-name.txt"), "success")
            val journal = File(root, "@@{WorkspaceRecovery.OPERATIONS_DIRECTORY}/journal")
            val entry = journal.listFiles()?.singleOrNull()
            assertNotNull(entry)
            assertFalse(entry!!.readText().contains("secret contents"))
            assertTrue(entry.readText().contains("copy-verified"))
        } finally { root.deleteRecursively() }
    }
}
