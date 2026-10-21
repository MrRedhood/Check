package com.astracode.check

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Isolated regression test: long-press -> Metadata -> dialog visible.
 *
 * NOTE: assertTextContains(value) defaults to substring = false, which requires the node's
 * text to EQUAL value. The metadata dialog text is multi-line ("Name: ...\nType: ..."), so
 * every check here passes substring = true.
 *
 * Path: app/src/androidTest/java/com/astracode/check/WorkspaceMetadataDialogTest.kt
 *
 * It does NOT touch the ZIP editor, so it separates "metadata dialog broken" from
 * "ZIP flow broken". Two cases (plain file, ZIP file) tell us whether ZIP matters at all.
 * On failure it reports, in the assertion message:
 *   - how many workspace-metadata-details nodes exist in merged / unmerged trees
 *   - the metadata status text
 *   - a full unmerged semantics dump of EVERY compose root (activity + each dialog window)
 * so a single CI run shows whether the dialog window is missing, the tag is duplicated,
 * or the text differs.
 */
class WorkspaceMetadataDialogTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private lateinit var workspaceRoot: File

    @Before
    fun prepareFixture() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        workspaceRoot = File(context.filesDir, "workspace")
        workspaceRoot.deleteRecursively()
        File(workspaceRoot, "app/src").mkdirs()
        File(workspaceRoot, "README.txt").writeText("Workspace fixture\n")
        File(workspaceRoot, "app/src/Main.kt").writeText("fun main() = Unit\n")
    }

    @After
    fun cleanFixture() {
        if (::workspaceRoot.isInitialized) workspaceRoot.deleteRecursively()
    }

    @Test
    fun metadataDialogOpensForPlainFile() {
        openBrowser()
        requestMetadata("workspace-entry-README.txt")
        awaitMetadataDialog("Name: README.txt")
        assertMetadataFields()
        composeRule.onNodeWithTag("workspace-metadata-close").performClick()
    }

    @Test
    fun creatingFileWithoutExtensionAddsTxtAndMetadataShowsDescription() {
        openBrowser()
        composeRule.onNodeWithTag("workspace-create-file").performClick()
        composeRule.onNodeWithTag("workspace-new-file-name").performTextInput("my file (1)")
        composeRule.onNodeWithTag("confirm-workspace-create-file").performClick()

        val created = File(workspaceRoot, "my file (1).txt")
        composeRule.waitUntil(timeoutMillis = 5_000) { created.isFile }
        assertEquals("my file (1).txt", created.name)
        composeRule.onNodeWithTag("workspace-editor-back").performClick()
        requestMetadata("workspace-entry-my file (1).txt")
        awaitMetadataDialog("Format: txt file (text file)")
        composeRule.onNodeWithTag("workspace-metadata-details")
            .assertTextContains("Name: my file (1).txt", substring = true)
        composeRule.onNodeWithTag("workspace-metadata-close").performClick()
    }

    @Test
    fun metadataDialogOpensForZipFile() {
        WorkspaceArchive.createZip(
            workspaceRoot,
            listOf("app/src/Main.kt"),
            "project.zip",
            "app/src",
        )
        openBrowser()
        requestMetadata("workspace-entry-project.zip")
        awaitMetadataDialog("Name: project.zip")
        assertMetadataFields()
        composeRule.onNodeWithTag("workspace-metadata-close").performClick()
    }

    private fun openBrowser() {
        composeRule.onNodeWithTag("open-workspace-browser-button").performClick()
    }

    private fun requestMetadata(rowTag: String) {
        waitFor("row $rowTag") { exists(rowTag, unmerged = false) }
        composeRule.onNodeWithTag(rowTag).performTouchInput { longClick() }
        waitFor("action menu") { exists("workspace-action-metadata", unmerged = false) }
        composeRule.onNodeWithTag("workspace-action-metadata").performClick()
    }

    private fun awaitMetadataDialog(expectedName: String) {
        try {
            composeRule.waitUntil(timeoutMillis = 5_000) {
                runCatching {
                    composeRule.onNodeWithTag("workspace-metadata-details")
                        .assertTextContains(expectedName, substring = true)
                }.isSuccess
            }
        } catch (error: Throwable) {
            throw AssertionError(
                "Metadata dialog not found for '${expectedName}'.\n" +
                    "merged nodes with details tag   = ${count("workspace-metadata-details", unmerged = false)}\n" +
                    "unmerged nodes with details tag = ${count("workspace-metadata-details", unmerged = true)}\n" +
                    "dialog title nodes (unmerged)   = ${countText("File metadata")}\n" +
                    "details node text = ${detailsText()}\n" +
                    "status = ${statusText()}\n" +
                    dumpAllRoots(),
                error,
            )
        }
    }

    private fun assertMetadataFields() {
        val details = composeRule.onNodeWithTag("workspace-metadata-details")
        details.assertTextContains("Created:", substring = true)
        details.assertTextContains("Modified:", substring = true)
        details.assertTextContains("Format:", substring = true)
        details.assertTextContains("Storage path:", substring = true)
    }

    private fun waitFor(stage: String, condition: () -> Boolean) {
        try {
            composeRule.waitUntil(timeoutMillis = 5_000, condition = condition)
        } catch (error: Throwable) {
            throw AssertionError("Timed out waiting for UI stage: ${stage}\n" + dumpAllRoots(), error)
        }
    }

    private fun exists(tag: String, unmerged: Boolean): Boolean = count(tag, unmerged) > 0

    private fun count(tag: String, unmerged: Boolean): Int = runCatching {
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = unmerged).fetchSemanticsNodes().size
    }.getOrDefault(-1)

    private fun countText(text: String): Int = runCatching {
        composeRule.onAllNodes(
            androidx.compose.ui.test.hasText(text),
            useUnmergedTree = true,
        ).fetchSemanticsNodes().size
    }.getOrDefault(-1)

    private fun detailsText(): String = runCatching {
        composeRule.onNodeWithTag("workspace-metadata-details").fetchSemanticsNode().config.toString()
    }.getOrDefault("No details node.")

    private fun statusText(): String = runCatching {
        composeRule.onNodeWithTag("workspace-metadata-status").fetchSemanticsNode().config.toString()
    }.getOrDefault("No metadata status node.")

    /** Unmerged dump of every compose root: the activity window and each Dialog window. */
    private fun dumpAllRoots(): String = runCatching {
        val roots = composeRule.onAllNodes(isRoot(), useUnmergedTree = true)
        val total = roots.fetchSemanticsNodes().size
        (0 until total).joinToString("\n") { index ->
            val root: SemanticsNodeInteraction = roots[index]
            "===== ROOT[${index}] of ${total} =====\n" + root.printToString(maxDepth = 14)
        }
    }.getOrElse { "root dump failed: $it" }
}
