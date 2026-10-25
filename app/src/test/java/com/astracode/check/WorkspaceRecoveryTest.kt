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

    @Test fun trashedDirectoryRestoresNestedFilesAndEmptyDirectories() {
        val root = Files.createTempDirectory("check-trash-directory").toFile()
        try {
            File(root, "project/src/nested").mkdirs()
            File(root, "project/src/Main.kt").writeText("fun main() = Unit")
            File(root, "project/src/nested/config.json").writeText("{\"enabled\":true}")
            File(root, "project/empty").mkdir()

            val trash = WorkspaceRecovery.moveToTrash(root, "project")
            assertFalse(File(root, "project").exists())
            assertTrue(WorkspaceRecovery.listTrash(root).any { it.id == trash.id })

            WorkspaceRecovery.restoreFromTrash(root, trash.id)

            assertEquals("fun main() = Unit", File(root, "project/src/Main.kt").readText())
            assertEquals("{\"enabled\":true}", File(root, "project/src/nested/config.json").readText())
            assertTrue(File(root, "project/empty").isDirectory)
            assertTrue(WorkspaceRecovery.listTrash(root).none { it.id == trash.id })
        } finally { root.deleteRecursively() }
    }

    @Test fun directoryRestoreCollisionKeepsNestedDataInTrashUntilAlternateRestore() {
        val root = Files.createTempDirectory("check-trash-directory-collision").toFile()
        try {
            File(root, "project/src").mkdirs()
            File(root, "project/src/Main.kt").writeText("original data")
            val trash = WorkspaceRecovery.moveToTrash(root, "project")

            File(root, "project/src").mkdirs()
            File(root, "project/src/Main.kt").writeText("newer data")
            try {
                WorkspaceRecovery.restoreFromTrash(root, trash.id)
                fail("Restore must not replace an existing directory.")
            } catch (expected: java.io.IOException) {
                assertTrue(expected.message.orEmpty().contains("already exists"))
            }

            assertTrue(WorkspaceRecovery.listTrash(root).any { it.id == trash.id })
            assertEquals("newer data", File(root, "project/src/Main.kt").readText())

            WorkspaceRecovery.restoreFromTrash(root, trash.id, "project-recovered")
            assertEquals("original data", File(root, "project-recovered/src/Main.kt").readText())
            assertEquals("newer data", File(root, "project/src/Main.kt").readText())
            assertTrue(WorkspaceRecovery.listTrash(root).none { it.id == trash.id })
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
            val png = java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/pZkAAAAASUVORK5CYII=")
            File(root, "src/pixel.png").writeBytes(png)
            val imagePreview = WorkspaceRecovery.preview(root, "src/pixel.png")
            assertTrue(imagePreview.supported)
            assertEquals("image/png", imagePreview.mimeType)
            assertTrue(imagePreview.imageBytes!!.contentEquals(png))
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

    @Test fun workspaceSystemFilesAreReservedAndExcludedFromSearchAndReports() {
        val root = Files.createTempDirectory("check-reserved-system-files").toFile()
        try {
            File(root, ".astracode-write-abandoned.tmp").writeText("partial data")
            File(root, ".astracode-provider-backup.orphan").writeText("recoverable backup")
            File(root, "visible.txt").writeText("user data")
            assertTrue(WorkspaceRecovery.isInternalPath(".astracode-trash/items/x"))
            assertTrue(WorkspaceRecovery.isInternalPath(".astracode-write-abandoned.tmp"))
            assertTrue(WorkspaceRecovery.isInternalPath("src/.astracode-provider-backup.orphan"))
            assertTrue(WorkspaceRecovery.isInternalPath(".ASTRACODE-TRASH/items/x"))
            assertTrue(WorkspaceRecovery.isInternalPath("src/.ASTRACODE-BACKUP-orphan"))
            assertTrue(WorkspaceRecovery.search(root, WorkspaceSearchOptions(includeHidden = true)).none {
                it.path.startsWith(".astracode-")
            })
            assertTrue(WorkspaceRecovery.findDuplicates(root).none {
                it.paths.any { path -> path.startsWith(".astracode-") }
            })
            assertTrue(WorkspaceRecovery.storageReport(root).largestFiles.none {
                it.first.startsWith(".astracode-")
            })
            try {
                WorkspaceRecovery.resolve(root, ".astracode-write-abandoned.tmp")
                fail("System-generated files must not be treated as user workspace files.")
            } catch (_: SecurityException) {
            }
            try {
                WorkspaceRecovery.resolve(root, ".ASTRACODE-WRITE-abandoned.tmp")
                fail("Case variants of system paths must also be rejected.")
            } catch (_: SecurityException) {
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun internalPathDetectionIgnoresCaseForReservedPrefixes() {
        assertTrue(WorkspaceRecovery.isInternalPath(".ASTRACODE-TRASH/items/example"))
        assertTrue(WorkspaceRecovery.isInternalPath("nested/.ASTRACODE-BACKUP-orphan"))
        assertFalse(WorkspaceRecovery.isInternalPath(".gitignore"))
    }

    @Test fun journalStoresOperationMetadataButNotFileContents() {
        val root = Files.createTempDirectory("check-operation-journal").toFile()
        try {
            WorkspaceRecovery.recordOperation(root, "copy-verified", listOf("secret-name.txt"), "success")
            val journal = File(root, "${WorkspaceRecovery.OPERATIONS_DIRECTORY}/journal")
            val entry = journal.listFiles()?.singleOrNull()
            assertNotNull(entry)
            assertFalse(entry!!.readText().contains("secret contents"))
            assertTrue(entry.readText().contains("copy-verified"))
        } finally { root.deleteRecursively() }
    }
}
