package com.astracode.check

import androidx.compose.ui.geometry.Offset

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MainActivityUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private lateinit var workspaceRoot: File

    @Before
    fun prepareWorkspaceFixture() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        workspaceRoot = File(context.filesDir, "workspace")
        workspaceRoot.deleteRecursively()
        File(context.filesDir, "external-workspace").deleteRecursively()
        context.getSharedPreferences("check_workspace_sync", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        File(context.filesDir, "external-workspace/app/src").mkdirs()
        File(context.filesDir, "external-workspace/app/src/.env").writeText("TOKEN=external-original")
        File(workspaceRoot, "app/src").mkdirs()
        File(workspaceRoot, "empty").mkdirs()
        File(workspaceRoot, ".gitignore").writeText("build/\n")
        File(workspaceRoot, "README.txt").writeText("Workspace fixture\n")
        File(workspaceRoot, "app/build.gradle.kts").writeText("plugins {}\n")
        File(workspaceRoot, "app/src/Main.kt").writeText("fun main() = Unit\n")
        File(workspaceRoot, "app/src/.env").writeText("TOKEN=original")
    }

    @After
    fun cleanWorkspaceFixture() {
        if (::workspaceRoot.isInitialized) workspaceRoot.deleteRecursively()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.filesDir, "external-workspace").deleteRecursively()
    }

    @Test
    fun savedFilesManagerShowsEmptyStateAndChooseFileAction() {
        composeRule.onNodeWithTag("manage-saved-documents-button").performClick()
        composeRule.onNodeWithTag("empty-saved-files-message").assertIsDisplayed()
        composeRule.onNodeWithTag("choose-standalone-file-button").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Files stay here until you remove them. Removing a bookmark only removes AstraCode's access; it does not delete the original file.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            "Removing a bookmark only removes AstraCode's access; it does not delete the original file.",
            substring = true,
        ).assertIsDisplayed()
    }

    @Test
    fun workspaceShowsStandaloneFilePickerAction() {
        composeRule.onNodeWithTag("open-standalone-file-button").assertIsDisplayed()
        composeRule.onNodeWithTag("manage-saved-documents-button").assertIsDisplayed()
        composeRule.onNodeWithText("No standalone file open.").assertIsDisplayed()
    }

    @Test
    fun folderCountsIncludeFilesFoldersAndHiddenEntriesAndHiddenFilesCanBeEdited() {
        composeRule.onNodeWithTag("open-workspace-browser-button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag("workspace-current-folder-count")
                    .assertTextEquals("Workspace root · 4 items")
            }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-entry-.gitignore").assertIsDisplayed()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag("workspace-entry-app").assertTextContains("2 items")
                composeRule.onNodeWithTag("workspace-entry-empty").assertTextContains("0 items")
            }.isSuccess
        }

        composeRule.onNodeWithTag("workspace-entry-app").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag("workspace-entry-src").assertTextContains("2 items") }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-entry-src").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag("workspace-entry-.env").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-entry-.env").assertTextContains("Hidden file · 14 bytes")

        composeRule.onNodeWithTag("workspace-entry-.env").performClick()
        composeRule.onNodeWithTag("workspace-editor-input").assertIsDisplayed()
        composeRule.onNodeWithTag("workspace-editor-input").performTextReplacement("TOKEN=updated")
        composeRule.onNodeWithTag("workspace-save-file").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            File(workspaceRoot, "app/src/.env").readText() == "TOKEN=updated"
        }
        assertEquals("TOKEN=updated", File(workspaceRoot, "app/src/.env").readText())
    }

    @Test
    fun languageAwareCompletionAppearsAndCanBeApplied() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workspaceRoot = File(context.filesDir, "workspace")
        val sample = File(workspaceRoot, "CompletionSample.kt")
        sample.writeText("ready")

        composeRule.onNodeWithTag("open-workspace-browser-button").performClick()
        waitForUiStage("completion sample appears in workspace") {
            runCatching {
                composeRule.onNodeWithTag("workspace-entry-CompletionSample.kt").assertIsDisplayed()
            }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-entry-CompletionSample.kt").performClick()
        val editor = composeRule.onNodeWithTag("workspace-editor-input")
        waitForUiStage("completion editor opens with the sample text") {
            runCatching { editor.assertTextContains("ready", substring = true) }.isSuccess
        }

        editor.performClick()
        editor.performTextInput(" fun pri")
        waitForUiStage("completion prefix reaches editor state") {
            runCatching { editor.assertTextContains("fun pri", substring = true) }.isSuccess
        }
        waitForUiStage("println suggestion is rendered") {
            runCatching { composeRule.onNodeWithTag("editor-completion-println").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("editor-completion-println").performClick()
        waitForUiStage("chosen completion replaces the prefix") {
            runCatching { assertEquals("1 │ ready fun println", editableEditorText()) }.isSuccess
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            sample.readText() == "ready fun println"
        }
        assertEquals("ready fun println", sample.readText())
    }

    @Test
    fun longPressShowsFileOperationsAndBothSyncDirections() {
        val externalRoot = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "external-workspace")
        composeRule.onNodeWithTag("open-workspace-browser-button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag("workspace-entry-app").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-entry-app").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag("workspace-entry-src").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-entry-src").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag("workspace-entry-.env").assertIsDisplayed() }.isSuccess
        }

        composeRule.onNodeWithTag("workspace-entry-.env").performTouchInput { longClick() }
        composeRule.onNodeWithTag("workspace-action-sync").assertIsDisplayed()
        composeRule.onNodeWithTag("workspace-action-delete").assertIsDisplayed()
        composeRule.onNodeWithTag("workspace-action-rename").assertIsDisplayed()
        composeRule.onNodeWithTag("workspace-action-copy").assertIsDisplayed()
        composeRule.onNodeWithTag("workspace-action-move").assertIsDisplayed()
        composeRule.onNodeWithTag("workspace-action-sync").performClick()
        composeRule.onNodeWithTag("sync-app-to-external").assertIsDisplayed()
        composeRule.onNodeWithTag("sync-external-to-app").assertIsDisplayed()
        composeRule.onNodeWithTag("sync-app-to-external").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            File(externalRoot, "app/src/.env").takeIf { it.exists() }?.readText() == "TOKEN=original"
        }

        File(externalRoot, "app/src/.env").writeText("TOKEN=external-updated")
        composeRule.onNodeWithTag("workspace-entry-.env").performTouchInput { longClick() }
        composeRule.onNodeWithTag("workspace-action-sync").performClick()
        composeRule.onNodeWithTag("sync-external-to-app").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            File(workspaceRoot, "app/src/.env").readText() == "TOKEN=external-updated"
        }

        composeRule.onNodeWithTag("workspace-entry-.env").performClick()
        composeRule.onNodeWithTag("workspace-editor-input").performTextReplacement("TOKEN=edited-in-app")
        composeRule.onNodeWithTag("workspace-save-file").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            File(externalRoot, "app/src/.env").readText() == "TOKEN=edited-in-app"
        }

        assertEquals("TOKEN=edited-in-app", File(externalRoot, "app/src/.env").readText())

        composeRule.onNodeWithTag("workspace-editor-back").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag("workspace-entry-.env").assertIsDisplayed() }.isSuccess
        }

        composeRule.onNodeWithTag("workspace-entry-.env").performTouchInput { longClick() }
        composeRule.onNodeWithTag("workspace-action-rename").performClick()
        composeRule.onNodeWithTag("workspace-operation-input").performTextReplacement(".env.renamed")
        composeRule.onNodeWithTag("confirm-workspace-operation").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            File(workspaceRoot, "app/src/.env.renamed").exists() &&
                File(externalRoot, "app/src/.env.renamed").exists() &&
                !File(externalRoot, "app/src/.env").exists()
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag("workspace-entry-.env.renamed").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-entry-.env.renamed").performTouchInput { longClick() }
        composeRule.onNodeWithTag("workspace-action-copy").performClick()
        composeRule.onNodeWithTag("workspace-operation-input").performTextReplacement("copy.env")
        composeRule.onNodeWithTag("confirm-workspace-operation").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { File(workspaceRoot, "app/src/copy.env").exists() }
        assertEquals("TOKEN=edited-in-app", File(workspaceRoot, "app/src/copy.env").readText())

        composeRule.onNodeWithTag("workspace-entry-.env.renamed").performTouchInput { longClick() }
        composeRule.onNodeWithTag("workspace-action-move").performClick()
        composeRule.onNodeWithTag("workspace-operation-input").performTextReplacement("app/moved.env")
        composeRule.onNodeWithTag("confirm-workspace-operation").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            File(workspaceRoot, "app/moved.env").exists() &&
                File(externalRoot, "app/moved.env").exists() &&
                !File(externalRoot, "app/src/.env.renamed").exists()
        }

        composeRule.onNodeWithTag("workspace-go-up").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag("workspace-entry-moved.env").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-entry-moved.env").performTouchInput { longClick() }
        composeRule.onNodeWithTag("workspace-action-delete").performClick()
        composeRule.onNodeWithTag("confirm-workspace-delete").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            !File(workspaceRoot, "app/moved.env").exists() && !File(externalRoot, "app/moved.env").exists()
        }
    }

    @Test
    fun syncingFolderDoesNotCopyOrDeleteInternalRecoveryArtifacts() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val externalRoot = File(context.filesDir, "external-workspace")
        val sourceArtifact = File(workspaceRoot, "app/src/.ASTRACODE-backup-orphan")
        val targetArtifact = File(externalRoot, "app/src/.ASTRACODE-backup-target")
        sourceArtifact.writeText("source recovery data")
        targetArtifact.writeText("target recovery data")

        composeRule.onNodeWithTag("open-workspace-browser-button").performClick()
        waitForUiStage("app folder before sync") {
            runCatching { composeRule.onNodeWithTag("workspace-entry-app").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-entry-app").performTouchInput { longClick() }
        composeRule.onNodeWithTag("workspace-action-sync").performClick()
        composeRule.onNodeWithTag("sync-app-to-external").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            File(externalRoot, "app/src/Main.kt").isFile &&
                targetArtifact.isFile && targetArtifact.readText() == "target recovery data"
        }
        assertFalse(File(externalRoot, "app/src/.ASTRACODE-backup-orphan").exists())
        assertTrue(sourceArtifact.isFile)
        assertEquals("source recovery data", sourceArtifact.readText())
        assertEquals("target recovery data", targetArtifact.readText())
    }

    @Test
    fun createsAndExtractsZipForMultipleItemsAndUpdatesLinkedExternalFolder() {
        val externalRoot = File(
            InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
            "external-workspace",
        )
        composeRule.onNodeWithTag("open-workspace-browser-button").performClick()
        waitForUiStage("workspace app entry before sync") {
            runCatching { composeRule.onNodeWithTag("workspace-entry-app").assertIsDisplayed() }.isSuccess
        }

        composeRule.onNodeWithTag("workspace-entry-app").performTouchInput { longClick() }
        composeRule.onNodeWithTag("workspace-action-sync").performClick()
        composeRule.onNodeWithTag("sync-app-to-external").performClick()
        waitForUiStage("external .env after sync", timeoutMillis = 5_000) {
            File(externalRoot, "app/src/.env").takeIf { it.exists() }?.readText() == "TOKEN=original"
        }

        composeRule.onNodeWithTag("workspace-entry-app").performClick()
        waitForUiStage("build file inside app folder") {
            runCatching { composeRule.onNodeWithTag("workspace-entry-build.gradle.kts").assertIsDisplayed() }.isSuccess
        }

        composeRule.onNodeWithTag("workspace-toggle-selection").performClick()
        composeRule.onNodeWithTag("workspace-select-build.gradle.kts")
            .assertIsDisplayed().assertIsEnabled().performClick()
        waitForUiStage("build.gradle.kts is selected") {
            runCatching {
                composeRule.onNodeWithTag("workspace-selection-count").assertTextEquals("1 selected")
            }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-select-src")
            .assertIsDisplayed().assertIsEnabled().performClick()
        waitForUiStage("build.gradle.kts and src are selected") {
            runCatching {
                composeRule.onNodeWithTag("workspace-selection-count").assertTextEquals("2 selected")
            }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-bulk-zip").performClick()
        composeRule.onNodeWithTag("workspace-bulk-input").performTextReplacement("bundle.zip")
        composeRule.onNodeWithTag("confirm-workspace-bulk-action").performClick()

        waitForUiStage("bulk ZIP creation and linked copy", timeoutMillis = 5_000) {
            File(workspaceRoot, "app/bundle.zip").isFile &&
                File(externalRoot, "app/bundle.zip").isFile &&
                !File(workspaceRoot, "app/build.gradle.kts").exists() &&
                !File(workspaceRoot, "app/src").exists()
        }

        waitForUiStage("bundle ZIP entry after bulk action") {
            runCatching { composeRule.onNodeWithTag("workspace-entry-bundle.zip").assertIsDisplayed() }.isSuccess
        }
        waitForUiStage("ZIP creation dialog is dismissed") {
            runCatching {
                composeRule.onAllNodesWithTag("workspace-bulk-input").assertCountEquals(0)
            }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-entry-bundle.zip").performClick()
        composeRule.onNodeWithTag("workspace-operation-input").assertIsDisplayed()
        composeRule.onNodeWithTag("workspace-operation-input").performTextReplacement("app/restored")
        composeRule.onNodeWithTag("confirm-workspace-operation").performClick()

        waitForUiStage("restored workspace files and linked copy", timeoutMillis = 5_000) {
            File(workspaceRoot, "app/restored/build.gradle.kts").isFile &&
                File(workspaceRoot, "app/restored/src/Main.kt").isFile &&
                File(externalRoot, "app/restored/src/Main.kt").isFile
        }
        assertEquals("plugins {}\n", File(workspaceRoot, "app/restored/build.gradle.kts").readText())
        assertEquals("fun main() = Unit\n", File(workspaceRoot, "app/restored/src/Main.kt").readText())
        assertEquals(
            "TOKEN=original",
            File(workspaceRoot, "app/restored/src/.env").readText(),
        )
        waitForUiStage("extraction dialog is dismissed") {
            runCatching {
                composeRule.onAllNodesWithTag("workspace-operation-input").assertCountEquals(0)
            }.isSuccess
        }
    }


    @Test
    fun tappingZipRequiresExtractionBeforeFilesCanBeEdited() {
        WorkspaceArchive.createZip(
            workspaceRoot,
            listOf("app/src/Main.kt"),
            "project.zip",
            "app/src",
        )
        composeRule.onNodeWithTag("open-workspace-browser-button").performClick()
        waitForUiStage("workspace ZIP row") {
            runCatching { composeRule.onNodeWithTag("workspace-entry-project.zip").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-entry-icon-project.zip", useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithTag("workspace-entry-project.zip").performTouchInput { longClick() }
        composeRule.onNodeWithTag("workspace-action-metadata").performClick()
        waitForUiStage("metadata details") {
            runCatching {
                composeRule.onNodeWithTag("workspace-metadata-details")
                    .assertTextContains("Name: project.zip", substring = true)
            }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-metadata-details").assertTextContains("Format: zip file (ZIP archive)", substring = true)
        composeRule.onNodeWithTag("workspace-metadata-close").performClick()

        composeRule.onNodeWithTag("workspace-entry-project.zip").performClick()
        waitForUiStage("extraction prompt") {
            runCatching { composeRule.onNodeWithTag("workspace-operation-input").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-extract-explanation")
            .assertTextContains("cannot be edited directly", substring = true)
        composeRule.onNodeWithTag("workspace-operation-input").performTextReplacement("restored-project")
        composeRule.onNodeWithTag("confirm-workspace-operation").performClick()

        waitForUiStage("extracted folder appears") {
            runCatching { composeRule.onNodeWithTag("workspace-entry-restored-project").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-entry-restored-project").performClick()
        waitForUiStage("extracted file appears") {
            runCatching { composeRule.onNodeWithTag("workspace-entry-Main.kt").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-entry-Main.kt").performClick()
        waitForUiStage("extracted file editor") {
            runCatching { composeRule.onNodeWithTag("workspace-editor-input").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-editor-input").performTextReplacement("fun main() = 7\n")
        composeRule.onNodeWithTag("workspace-save-file").performClick()
        waitForUiStage("edited extracted file saved") {
            File(workspaceRoot, "restored-project/Main.kt").takeIf { it.isFile }?.readText() == "fun main() = 7\n"
        }
    }

    @Test
    fun fileSafetyToolsSupportSearchPreviewFirstRenameAndUndoDelete() {
        composeRule.onNodeWithTag("open-workspace-browser-button").performClick()
        waitForUiStage("safety tool button") {
            runCatching { composeRule.onNodeWithTag("workspace-safety-tools").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-safety-tools").performClick()
        composeRule.onNodeWithTag("workspace-safety-search-input").performTextReplacement("Main.kt")
        composeRule.onNodeWithTag("workspace-safety-search").performScrollTo().assertIsDisplayed().performClick()
        waitForUiStage("search results complete", timeoutMillis = 10_000) {
            runCatching {
                val text = composeRule.onNodeWithTag("workspace-safety-results")
                    .fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("") { it.text }
                assertTrue("Search is still running", text != "Searching…")
                assertTrue("Search completed with unexpected result: $text",
                    text.contains("app/src/Main.kt") || text.startsWith("Search failed:") || text == "No matching files.")
            }.isSuccess
        }
        val searchResultText = composeRule.onNodeWithTag("workspace-safety-results")
            .fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("") { it.text }
        assertTrue(
            "Search should find the fixture file; actual result was: $searchResultText",
            searchResultText.contains("app/src/Main.kt"),
        )

        composeRule.onNodeWithTag("workspace-batch-rename-input")
            .performTextReplacement("README.txt=README-renamed.txt")
        composeRule.onNodeWithTag("workspace-batch-rename").performScrollTo().performClick()
        composeRule.onNodeWithTag("workspace-batch-rename-confirm").assertIsDisplayed()
        assertFalse(File(workspaceRoot, "README-renamed.txt").exists())
        composeRule.onNodeWithTag("workspace-batch-rename-confirm").performClick()
        waitForUiStage("batch rename committed") {
            File(workspaceRoot, "README-renamed.txt").isFile &&
                !File(workspaceRoot, "README.txt").exists()
        }

        waitForUiStage("safety dialog returns to close state after rename") {
            runCatching {
                composeRule.onNodeWithTag("workspace-safety-close").assertIsDisplayed()
            }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-safety-close").performClick()
        waitForUiStage("renamed file appears in browser") {
            runCatching {
                composeRule.onNodeWithTag("workspace-entry-README-renamed.txt").assertIsDisplayed()
            }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-entry-README-renamed.txt")
            .performTouchInput { longClick() }
        composeRule.onNodeWithTag("workspace-action-delete").performClick()
        composeRule.onNodeWithTag("confirm-workspace-delete").performClick()
        waitForUiStage("Trash deletion offers Undo") {
            !File(workspaceRoot, "README-renamed.txt").exists() &&
                runCatching { composeRule.onNodeWithTag("workspace-undo-delete").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-undo-delete").performClick()
        waitForUiStage("Undo restores original bytes") {
            File(workspaceRoot, "README-renamed.txt").takeIf { it.isFile }?.readText() == "Workspace fixture\n"
        }
    }

    @Test
    fun atelierPreviewNavigatesThroughDesignedScreens() {
        composeRule.onNodeWithTag("open-atelier-preview").performClick()
        composeRule.onNodeWithText("Design preview").assertIsDisplayed()
        composeRule.onNodeWithText("Sample content only · no files are changed in this preview").assertIsDisplayed()

        composeRule.onNodeWithTag("bottom-nav-search").performClick()
        composeRule.onNodeWithText("Search & inspect").assertIsDisplayed()

        composeRule.onNodeWithTag("bottom-nav-safety").performClick()
        composeRule.onNodeWithText("Safety & health").assertIsDisplayed()

        composeRule.onNodeWithTag("bottom-nav-trash").performClick()
        composeRule.onNodeWithText("Trash & restore").assertIsDisplayed()

        composeRule.onNodeWithTag("bottom-nav-files").performClick()
        composeRule.onNodeWithText("WORKSPACE / FILE SYSTEM").assertIsDisplayed()
    }

    @Test
    fun mobileBackReturnsToPreviousTabInsteadOfExitingApp() {
        composeRule.onNodeWithTag("open-atelier-preview").performClick()
        composeRule.onNodeWithText("Design preview").assertIsDisplayed()
        composeRule.onNodeWithTag("bottom-nav-search").performClick()
        composeRule.onNodeWithText("Search & inspect").assertIsDisplayed()

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }

        composeRule.onNodeWithText("Design preview").assertIsDisplayed()
        composeRule.onNodeWithText("WORKSPACE / FILE SYSTEM").assertIsDisplayed()
        composeRule.activityRule.scenario.onActivity { activity ->
            assertFalse("Back navigation must not finish the app.", activity.isFinishing)
        }

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.onNodeWithText("Standalone file access check").assertIsDisplayed()
        composeRule.activityRule.scenario.onActivity { activity ->
            assertFalse("Returning from the preview must leave the app open.", activity.isFinishing)
        }
    }

    @Test
    fun fileRowsDoNotShowOverflowOrOpenInspectorOnLongPress() {
        composeRule.onNodeWithTag("open-atelier-preview").performClick()
        composeRule.onNodeWithText("Design preview").assertIsDisplayed()
        composeRule.onNodeWithTag("bottom-nav-files").performClick()

        composeRule.onNodeWithTag("workspace-inspect-src").assertIsDisplayed()
        assertTrue(runCatching { composeRule.onNodeWithTag("workspace-entry-options-src").assertIsDisplayed() }.isFailure)

        composeRule.onNodeWithTag("entry-src").performTouchInput { longClick() }
        assertTrue(runCatching { composeRule.onNodeWithText("QUICK INSPECTOR").assertIsDisplayed() }.isFailure)
    }

    @Test
    fun inspectorButtonOpensScrollableFileActions() {
        composeRule.onNodeWithTag("open-atelier-preview").performClick()
        composeRule.onNodeWithText("Design preview").assertIsDisplayed()
        composeRule.onNodeWithTag("bottom-nav-files").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag("workspace-inspect-src").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-inspect-src").performClick()
        waitForUiStage("inspector opened from info button") {
            runCatching { composeRule.onNodeWithText("QUICK INSPECTOR").assertIsDisplayed() }.isSuccess
        }

        composeRule.onNodeWithText("Rename").fetchSemanticsNode()
        composeRule.onNodeWithText("Copy").fetchSemanticsNode()
        composeRule.onNodeWithText("Move").fetchSemanticsNode()
        composeRule.onNodeWithText("Sync").fetchSemanticsNode()
        composeRule.onNodeWithText("Format").fetchSemanticsNode()
        composeRule.onNodeWithText("Created").fetchSemanticsNode()
        composeRule.onNodeWithText("Modified").fetchSemanticsNode()
        composeRule.onNodeWithText("Direct children").fetchSemanticsNode()
        composeRule.onNodeWithText("Move to Trash").fetchSemanticsNode()
    }


    @Test
    fun multipleEditorTabsPreserveDraftsAndSaveToTheCorrectFiles() {
        composeRule.onNodeWithTag("open-workspace-browser-button").performClick()
        waitForUiStage("workspace root") {
            runCatching { composeRule.onNodeWithTag("workspace-entry-README.txt").assertIsDisplayed() }.isSuccess
        }

        composeRule.onNodeWithTag("workspace-entry-README.txt").performClick()
        waitForUiStage("README editor loaded") {
            runCatching { composeRule.onNodeWithTag("workspace-editor-input").assertTextContains("Workspace fixture", substring = true) }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-editor-input").performTextReplacement("README unsaved draft")

        composeRule.onNodeWithTag("workspace-editor-back").performClick()
        composeRule.onNodeWithTag("workspace-entry-app").performClick()
        waitForUiStage("app source folder") {
            runCatching { composeRule.onNodeWithTag("workspace-entry-src").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-entry-src").performClick()
        waitForUiStage("Main.kt row") {
            runCatching { composeRule.onNodeWithTag("workspace-entry-Main.kt").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-entry-Main.kt").performClick()
        waitForUiStage("Main.kt editor loaded") {
            runCatching { composeRule.onNodeWithTag("workspace-editor-input").assertTextContains("fun main() = Unit", substring = true) }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-editor-input").performTextReplacement("fun main() = 42")

        composeRule.onNodeWithTag("workspace-editor-tabs")
            .performScrollToNode(hasTestTag("workspace-tab-README.txt"))
        composeRule.onNodeWithTag("workspace-tab-README.txt").performClick()
        waitForUiStage("README tab restores its unsaved draft", timeoutMillis = 10_000) {
            runCatching { composeRule.onNodeWithTag("workspace-editor-input").assertTextContains("README unsaved draft", substring = true) }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-editor-tabs")
            .performScrollToNode(hasTestTag("workspace-tab-app__src__Main.kt"))
        composeRule.onNodeWithTag("workspace-tab-app__src__Main.kt").assertIsDisplayed()
        composeRule.onNodeWithTag("workspace-tab-app__src__Main.kt").performClick()
        waitForUiStage("Main.kt tab becomes active") {
            runCatching {
                composeRule.onNodeWithTag("workspace-editor-active-file").assertTextEquals("Main.kt")
            }.isSuccess
        }
        waitForUiStage("Main.kt tab restores its unsaved draft", timeoutMillis = 10_000) {
            runCatching { composeRule.onNodeWithTag("workspace-editor-input").assertTextContains("fun main() = 42", substring = true) }.isSuccess
        }

        composeRule.onNodeWithTag("workspace-save-file").performClick()
        waitForUiStage("Main.kt saved") {
            File(workspaceRoot, "app/src/Main.kt").takeIf { it.isFile }?.readText() == "fun main() = 42"
        }
        composeRule.onNodeWithTag("workspace-tab-README.txt").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag("workspace-editor-input").assertTextContains("README unsaved draft", substring = true) }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-save-file").performClick()
        waitForUiStage("README saved") {
            File(workspaceRoot, "README.txt").takeIf { it.isFile }?.readText() == "README unsaved draft"
        }

        assertEquals("fun main() = 42", File(workspaceRoot, "app/src/Main.kt").readText())
        assertEquals("README unsaved draft", File(workspaceRoot, "README.txt").readText())
    }

    @Test
    fun renameSavesDirtyEditorDraftBeforeMovingTheFile() {
        composeRule.onNodeWithTag("open-workspace-browser-button").performClick()
        waitForUiStage("README row before rename") {
            runCatching { composeRule.onNodeWithTag("workspace-entry-README.txt").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-entry-README.txt").performClick()
        waitForUiStage("README editor before rename") {
            runCatching { composeRule.onNodeWithTag("workspace-editor-input").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-editor-input").performTextReplacement("Unsaved text that must follow the rename")
        composeRule.onNodeWithTag("workspace-editor-back").performClick()

        composeRule.onNodeWithTag("workspace-entry-README.txt").performTouchInput { longClick() }
        composeRule.onNodeWithTag("workspace-action-rename").performClick()
        composeRule.onNodeWithTag("workspace-operation-input").performTextReplacement("README-renamed.txt")
        composeRule.onNodeWithTag("confirm-workspace-operation").performClick()

        val renamed = File(workspaceRoot, "README-renamed.txt")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            renamed.isFile && runCatching { renamed.readText() == "Unsaved text that must follow the rename" }.getOrDefault(false)
        }
        assertEquals(false, File(workspaceRoot, "README.txt").exists())
        composeRule.waitUntil(timeoutMillis = 1_000) { !File(workspaceRoot, "README.txt").exists() }
        assertEquals("Unsaved text that must follow the rename", renamed.readText())
    }

    @Test
    fun bulkDeleteSavesDirtyDraftBeforeMovingItToTrash() {
        composeRule.onNodeWithTag("open-workspace-browser-button").performClick()
        waitForUiStage("README row before bulk delete") {
            runCatching { composeRule.onNodeWithTag("workspace-entry-README.txt").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-entry-README.txt").performClick()
        waitForUiStage("README editor before bulk delete") {
            runCatching { composeRule.onNodeWithTag("workspace-editor-input").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-editor-input")
            .performTextReplacement("Unsaved contents that must be recoverable")
        composeRule.onNodeWithTag("workspace-editor-back").performClick()
        waitForUiStage("README row returns after leaving the editor") {
            runCatching { composeRule.onNodeWithTag("workspace-entry-README.txt").assertIsDisplayed() }.isSuccess
        }

        composeRule.onNodeWithTag("workspace-toggle-selection").performClick()
        // Selection mode adds a toolbar, so scroll the row back into view before clicking it.
        composeRule.onNodeWithTag("workspace-file-list")
            .performScrollToNode(hasTestTag("workspace-entry-README.txt"))
        // The clickable row owns the selection semantics; clicking the row is also
        // accessible and avoids depending on the nested checkbox's merged semantics.
        waitForUiStage("README row is selectable") {
            runCatching { composeRule.onNodeWithTag("workspace-entry-README.txt").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-entry-README.txt").performClick()
        composeRule.onNodeWithTag("workspace-bulk-delete").performClick()
        composeRule.onNodeWithTag("confirm-workspace-bulk-action").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            !File(workspaceRoot, "README.txt").exists() &&
                WorkspaceRecovery.listTrash(workspaceRoot).any { it.originalPath == "README.txt" }
        }
        val record = WorkspaceRecovery.listTrash(workspaceRoot).single { it.originalPath == "README.txt" }
        val payload = File(workspaceRoot, ".astracode-trash/items/${record.id}/payload")
        assertEquals(true, payload.isFile)
        assertEquals("Unsaved contents that must be recoverable", payload.readText())
        composeRule.waitUntil(timeoutMillis = 2_000) {
            runCatching { composeRule.onNodeWithTag("workspace-editor-input").assertIsDisplayed() }.isFailure
        }
    }

    @Test
    fun bulkMoveKeepsOpenEditorTabAttachedToMovedPath() {
        composeRule.onNodeWithTag("open-workspace-browser-button").performClick()
        waitForUiStage("README row before bulk move") {
            runCatching { composeRule.onNodeWithTag("workspace-entry-README.txt").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-entry-README.txt").performClick()
        waitForUiStage("README editor before bulk move") {
            runCatching {
                composeRule.onNodeWithTag("workspace-editor-input")
                    .assertTextContains("Workspace fixture", substring = true)
            }.isSuccess
        }
        val draft = "This editor tab must follow its file"
        composeRule.onNodeWithTag("workspace-editor-input").performTextReplacement(draft)
        composeRule.onNodeWithTag("workspace-editor-back").performClick()
        waitForUiStage("README row returns after leaving the editor") {
            runCatching { composeRule.onNodeWithTag("workspace-entry-README.txt").assertIsDisplayed() }.isSuccess
        }

        composeRule.onNodeWithTag("workspace-toggle-selection").performClick()
        composeRule.onNodeWithTag("workspace-file-list")
            .performScrollToNode(hasTestTag("workspace-entry-README.txt"))
        waitForUiStage("README row is selectable for bulk move") {
            runCatching { composeRule.onNodeWithTag("workspace-entry-README.txt").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-entry-README.txt").performClick()
        composeRule.onNodeWithTag("workspace-bulk-move").performClick()
        composeRule.onNodeWithTag("workspace-bulk-input").performTextReplacement("app")
        composeRule.onNodeWithTag("confirm-workspace-bulk-action").performClick()

        val moved = File(workspaceRoot, "app/README.txt")
        composeRule.waitUntil(timeoutMillis = 10_000) {
            moved.isFile && moved.readText() == draft && !File(workspaceRoot, "README.txt").exists()
        }
        composeRule.onNodeWithTag("workspace-entry-app").performClick()
        waitForUiStage("moved README row") {
            runCatching { composeRule.onNodeWithTag("workspace-entry-README.txt").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-entry-README.txt").performClick()
        waitForUiStage("moved README editor uses existing tab") {
            runCatching {
                composeRule.onNodeWithTag("workspace-editor-input").assertTextContains(draft, substring = true)
                composeRule.onNodeWithTag("workspace-tab-app__README.txt").assertIsDisplayed()
            }.isSuccess
        }
        composeRule.onAllNodesWithTag("workspace-tab-app__README.txt").assertCountEquals(1)
        composeRule.onAllNodesWithTag("workspace-tab-README.txt").assertCountEquals(0)
    }

    @Test
    fun dirtyEditorTabClosePromptDoesNotRaceAutosave() {
        composeRule.onNodeWithTag("open-workspace-browser-button").performClick()
        waitForUiStage("README row") {
            runCatching { composeRule.onNodeWithTag("workspace-entry-README.txt").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-entry-README.txt").performClick()
        waitForUiStage("README editor") {
            runCatching { composeRule.onNodeWithTag("workspace-editor-input").assertIsDisplayed() }.isSuccess
        }

        val file = File(workspaceRoot, "README.txt")
        val original = file.readText()
        val draft = "unsaved draft handled by autosave"
        composeRule.onNodeWithTag("workspace-editor-input").performTextReplacement(draft)
        composeRule.onNodeWithTag("workspace-tab-close-README.txt").performClick()

        val promptVisible = runCatching {
            composeRule.onNodeWithTag("workspace-tab-discard-confirm").assertIsDisplayed()
        }.isSuccess

        if (promptVisible) {
            // If a save began before the prompt appeared, wait for it to finish before
            // allowing the user to discard anything. Otherwise the original file stays untouched.
            waitForUiStage("close decision is safe", timeoutMillis = 5_000) {
                runCatching {
                    composeRule.onNodeWithTag("workspace-tab-discard-confirm").assertIsEnabled()
                }.isSuccess
            }
            if (file.readText() != draft) {
                assertEquals(original, file.readText())
            }
            composeRule.onNodeWithTag("workspace-tab-discard-cancel").performClick()
            waitForUiStage("draft retained after cancel") {
                runCatching {
                    composeRule.onNodeWithTag("workspace-editor-input")
                        .assertTextContains(draft, substring = true)
                }.isSuccess
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { file.readText() == draft }.getOrDefault(false)
        }
        waitForUiStage("draft is saved automatically") {
            file.readText() == draft
        }

        // Once autosave has succeeded the tab is clean, so closing it must not show
        // a discard prompt or pretend the already-saved text can be rolled back.
        if (runCatching {
                composeRule.onNodeWithTag("workspace-editor-input").assertIsDisplayed()
            }.isSuccess
        ) {
            composeRule.onNodeWithTag("workspace-tab-close-README.txt").performClick()
        }
        waitForUiStage("returned to files after closing tab") {
            runCatching { composeRule.onNodeWithTag("workspace-entry-README.txt").assertIsDisplayed() }.isSuccess
        }
        assertEquals(draft, file.readText())
    }

    @Test
    fun editorKeeps25TabsAndAutoSavesOldestBeforeOpening26th() {
        (1..26).forEach { index ->
            File(workspaceRoot, "tab-$index.txt").writeText("tab content $index")
        }
        composeRule.onNodeWithTag("open-workspace-browser-button").performClick()
        waitForUiStage("workspace file list") {
            runCatching { composeRule.onNodeWithTag("workspace-file-list").assertIsDisplayed() }.isSuccess
        }

        (1..25).forEach { index ->
            composeRule.onNodeWithTag("workspace-file-list")
                .performScrollToNode(hasTestTag("workspace-entry-tab-$index.txt"))
            composeRule.onNodeWithTag("workspace-entry-tab-$index.txt").performClick()
            waitForUiStage("tab $index loaded") {
                runCatching {
                    composeRule.onNodeWithTag("workspace-editor-input")
                        .assertTextContains("tab content $index", substring = true)
                }.isSuccess
            }
            composeRule.onNodeWithTag("workspace-editor-back").performClick()
        }

        // Make the oldest retained tab dirty immediately before opening the 26th file.
        composeRule.onNodeWithTag("workspace-file-list")
            .performScrollToNode(hasTestTag("workspace-entry-tab-1.txt"))
        composeRule.onNodeWithTag("workspace-entry-tab-1.txt").performClick()
        waitForUiStage("oldest tab reactivated") {
            runCatching {
                composeRule.onNodeWithTag("workspace-editor-input")
                    .assertTextContains("tab content 1", substring = true)
            }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-editor-input").performTextReplacement("tab 1 autosaved draft")
        composeRule.onNodeWithTag("workspace-editor-back").performClick()

        composeRule.onNodeWithTag("workspace-file-list")
            .performScrollToNode(hasTestTag("workspace-entry-tab-26.txt"))
        composeRule.onNodeWithTag("workspace-entry-tab-26.txt").performClick()
        waitForUiStage("26th tab opened after oldest-tab save and eviction", timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag("workspace-editor-input")
                    .assertTextContains("tab content 26", substring = true)
            }.isSuccess
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            File(workspaceRoot, "tab-1.txt").readText() == "tab 1 autosaved draft"
        }
        composeRule.onAllNodesWithTag("workspace-tab-close-tab-1.txt").assertCountEquals(0)
        assertEquals("tab 1 autosaved draft", File(workspaceRoot, "tab-1.txt").readText())
    }

    @Test
    fun workspaceEditorAutoSavesDraftWithoutPressingSave() {
        val file = File(workspaceRoot, "autosave-sample.txt")
        file.writeText("before")
        composeRule.onNodeWithTag("open-workspace-browser-button").performClick()
        waitForUiStage("workspace file list for autosave") {
            runCatching { composeRule.onNodeWithTag("workspace-file-list").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-file-list")
            .performScrollToNode(hasTestTag("workspace-entry-autosave-sample.txt"))
        composeRule.onNodeWithTag("workspace-entry-autosave-sample.txt").performClick()
        waitForUiStage("autosave file loaded") {
            runCatching {
                composeRule.onNodeWithTag("workspace-editor-input")
                    .assertTextContains("before", substring = true)
            }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-editor-input").performTextReplacement("saved by autosave")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { file.readText() == "saved by autosave" }.getOrDefault(false)
        }
        assertEquals("saved by autosave", file.readText())
    }

    @Test
    fun editorTabsCanCloseAndFilesCanReopenFromWorkspace() {
        composeRule.onNodeWithTag("open-workspace-browser-button").performClick()
        waitForUiStage("README row") {
            runCatching { composeRule.onNodeWithTag("workspace-entry-README.txt").assertIsDisplayed() }.isSuccess
        }

        composeRule.onNodeWithTag("workspace-entry-README.txt").performClick()
        waitForUiStage("README editor opened from workspace") {
            runCatching {
                composeRule.onNodeWithTag("workspace-editor-input")
                    .assertTextContains("Workspace fixture", substring = true)
            }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-tab-close-README.txt").assertIsDisplayed()

        // Returning to Workspace hides editor tabs; Workspace rows themselves have no close button.
        composeRule.onNodeWithTag("workspace-editor-back").performClick()
        waitForUiStage("Workspace row returns") {
            runCatching { composeRule.onNodeWithTag("workspace-entry-README.txt").assertIsDisplayed() }.isSuccess
        }
        composeRule.onAllNodesWithTag("workspace-close-open-file-README.txt").assertCountEquals(0)

        composeRule.onNodeWithTag("workspace-entry-README.txt").performClick()
        waitForUiStage("README editor reselected") {
            runCatching {
                composeRule.onNodeWithTag("workspace-editor-input")
                    .assertTextContains("Workspace fixture", substring = true)
            }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-tab-close-README.txt").performClick()
        waitForUiStage("README row returns after closing its editor tab") {
            runCatching { composeRule.onNodeWithTag("workspace-entry-README.txt").assertIsDisplayed() }.isSuccess
        }
        composeRule.onAllNodesWithTag("workspace-tab-close-README.txt").assertCountEquals(0)
        composeRule.onAllNodesWithTag("workspace-close-open-file-README.txt").assertCountEquals(0)

        // Files remain in Workspace after closing, and opening one again creates a new editor tab.
        composeRule.onNodeWithTag("workspace-entry-README.txt").performClick()
        waitForUiStage("README reopened in editor") {
            runCatching {
                composeRule.onNodeWithTag("workspace-editor-input")
                    .assertTextContains("Workspace fixture", substring = true)
            }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-tab-close-README.txt").assertIsDisplayed()
    }

    @Test
    fun workspaceEditorCanCollapseAndExpandCodeWithoutChangingSourceText() {
        val sample = File(workspaceRoot, "FoldSample.kt")
        val source = "fun main() {\n    println(\"first\")\n    println(\"second\")\n}\n"
        sample.writeText(source)
        composeRule.onNodeWithTag("open-workspace-browser-button").performClick()
        waitForUiStage("fold sample appears") {
            runCatching { composeRule.onNodeWithTag("workspace-entry-FoldSample.kt").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-entry-FoldSample.kt").performClick()
        waitForUiStage("fold sample editor opens") {
            runCatching {
                composeRule.onNodeWithTag("workspace-editor-input")
                    .assertTextContains("fun main()", substring = true)
            }.isSuccess
        }

        val foldButton = composeRule.onNodeWithTag("workspace-fold-toggle-1")
        foldButton.assertIsDisplayed()
        foldButton.performClick()
        foldButton.assertTextContains("▸", substring = true)
        assertEquals(source, sample.readText())

        foldButton.performClick()
        foldButton.assertTextContains("▾", substring = true)
        assertEquals(source, sample.readText())
    }

    @Test
    fun workspaceEditorInsertsEmojiAndSymbolsTogglesWordWrapAndAutosaves() {
        val sample = File(workspaceRoot, "UnicodeEditor.txt")
        sample.writeText("start")
        composeRule.onNodeWithTag("open-workspace-browser-button").performClick()
        waitForUiStage("unicode editor file appears") {
            runCatching { composeRule.onNodeWithTag("workspace-entry-UnicodeEditor.txt").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-entry-UnicodeEditor.txt").performClick()
        waitForUiStage("unicode editor is ready") {
            runCatching {
                composeRule.onNodeWithTag("workspace-editor-input")
                    .assertTextContains("start", substring = true)
            }.isSuccess
        }

        composeRule.onNodeWithTag("workspace-editor-input").performTextReplacement("hello ")
        composeRule.onNodeWithTag("workspace-editor-input").performTextInput("😀")
        composeRule.onNodeWithTag("workspace-editor-input").assertTextContains("hello 😀", substring = true)

        composeRule.onNodeWithTag("workspace-editor-word-wrap-toggle").assertTextContains("Word wrap: On")
        composeRule.onNodeWithTag("workspace-editor-word-wrap-toggle").performClick()
        composeRule.onNodeWithTag("workspace-editor-word-wrap-toggle").assertTextContains("Word wrap: Off")
        composeRule.onNodeWithTag("workspace-editor-word-wrap-toggle").performClick()
        composeRule.onNodeWithTag("workspace-editor-word-wrap-toggle").assertTextContains("Word wrap: On")

        composeRule.waitUntil(timeoutMillis = 10_000) {
            sample.readText() == "hello 😀"
        }
        assertEquals("hello 😀", sample.readText())
    }

    private fun currentUiDiagnosticSnapshot(): String {
        val tags = listOf(
            "workspace-editor-input",
            "workspace-editor-loading",
            "workspace-editor-active-file",
            "workspace-selection-count",
            "workspace-bulk-input",
            "workspace-browser-message",
            "workspace-safety-results",
            "workspace-tab-README.txt",
            "workspace-tab-app__src__Main.kt",
        )
        return tags.joinToString(prefix = " [UI snapshot: ", postfix = "]") { tag ->
            val summary = runCatching {
                composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes()
                    .take(2)
                    .joinToString(" | ") { node ->
                        node.config.toString().replace('\n', ' ').take(700)
                    }
            }.getOrElse { "<unavailable: ${it.javaClass.simpleName}>" }
            tag + "=" + summary.ifEmpty { "<none>" }
        }
    }

    private fun currentBulkZipFilesystemDiagnosticSnapshot(): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val externalRoot = File(context.filesDir, "external-workspace")

        fun describe(root: File, relativePath: String): String {
            val file = File(root, relativePath)
            val children = if (file.isDirectory) {
                file.list()?.sorted()?.joinToString(",") ?: "<unreadable>"
            } else {
                "-"
            }
            return "$relativePath(exists=${file.exists()},file=${file.isFile},dir=${file.isDirectory},bytes=${file.length()},children=[$children])"
        }

        val details = listOf(
            describe(workspaceRoot, "app"),
            describe(workspaceRoot, "app/bundle.zip"),
            describe(workspaceRoot, "app/build.gradle.kts"),
            describe(workspaceRoot, "app/src"),
            describe(externalRoot, "app"),
            describe(externalRoot, "app/bundle.zip"),
            describe(externalRoot, "app/build.gradle.kts"),
            describe(externalRoot, "app/src"),
            describe(externalRoot, "app/src/.env"),
        )
        val links = context.getSharedPreferences("check_workspace_sync", android.content.Context.MODE_PRIVATE)
            .all.toSortedMap().toString().take(800)
        return " [bulk ZIP filesystem: ${details.joinToString("; ")}; syncPrefs=$links]"
    }

    private fun waitForUiStage(stage: String, timeoutMillis: Long = 5_000, condition: () -> Boolean) {
        try {
            composeRule.waitUntil(timeoutMillis = timeoutMillis, condition = condition)
        } catch (error: Throwable) {
            val filesystemSnapshot = if (stage == "bulk ZIP creation and linked copy") {
                currentBulkZipFilesystemDiagnosticSnapshot()
            } else {
                ""
            }
            throw AssertionError(
                "Timed out waiting for UI stage: " + stage + currentUiDiagnosticSnapshot() + filesystemSnapshot,
                error,
            )
        }
    }

    private fun editableEditorText(): String = composeRule
        .onNodeWithTag("workspace-editor-input")
        .fetchSemanticsNode()
        .config[SemanticsProperties.EditableText]
        .text


    @Test
    fun switchingEditorTabsRestoresEachUnsavedDraft() {
        val firstFile = File(workspaceRoot, "DraftOne.kt")
        val secondFile = File(workspaceRoot, "DraftTwo.kt")
        firstFile.writeText("first saved")
        secondFile.writeText("second saved")

        composeRule.onNodeWithTag("open-workspace-browser-button").performClick()
        composeRule.onNodeWithTag("workspace-file-list")
            .performScrollToNode(hasTestTag("workspace-entry-DraftOne.kt"))
        waitForUiStage("first draft file appears") {
            runCatching { composeRule.onNodeWithTag("workspace-entry-DraftOne.kt").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-entry-DraftOne.kt").performClick()
        val editor = composeRule.onNodeWithTag("workspace-editor-input")
        waitForUiStage("first draft editor opens") {
            runCatching { editor.assertTextContains("first saved", substring = true) }.isSuccess
        }
        editor.performTextReplacement("first unsaved draft")

        composeRule.onNodeWithTag("workspace-editor-back").performClick()
        composeRule.onNodeWithTag("workspace-file-list")
            .performScrollToNode(hasTestTag("workspace-entry-DraftTwo.kt"))
        waitForUiStage("second draft file appears") {
            runCatching { composeRule.onNodeWithTag("workspace-entry-DraftTwo.kt").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-entry-DraftTwo.kt").performClick()
        waitForUiStage("second draft editor opens") {
            runCatching { editor.assertTextContains("second saved", substring = true) }.isSuccess
        }
        editor.performTextReplacement("second unsaved draft")

        composeRule.onNodeWithTag("workspace-tab-DraftOne.kt").performClick()
        waitForUiStage("switching back restores the first draft") {
            runCatching { assertEquals("1 │ first unsaved draft", editableEditorText()) }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-tab-DraftTwo.kt").performClick()
        waitForUiStage("switching forward restores the second draft") {
            runCatching { assertEquals("1 │ second unsaved draft", editableEditorText()) }.isSuccess
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            secondFile.readText() == "second unsaved draft"
        }
        composeRule.onNodeWithTag("workspace-tab-DraftOne.kt").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            firstFile.readText() == "first unsaved draft"
        }
        assertEquals("first unsaved draft", firstFile.readText())
        assertEquals("second unsaved draft", secondFile.readText())
    }

    @Test
    fun switchingBetweenDirtyTabsRepeatedlyKeepsDraftsWithTheirFiles() {
        val firstFile = File(workspaceRoot, "DraftOne.kt")
        val secondFile = File(workspaceRoot, "DraftTwo.kt")
        firstFile.writeText("first saved")
        secondFile.writeText("second saved")

        composeRule.onNodeWithTag("open-workspace-browser-button").performClick()
        composeRule.onNodeWithTag("workspace-file-list")
            .performScrollToNode(hasTestTag("workspace-entry-DraftOne.kt"))
        composeRule.onNodeWithTag("workspace-entry-DraftOne.kt").performClick()
        val editor = composeRule.onNodeWithTag("workspace-editor-input")
        waitForUiStage("stress test first editor opens") {
            runCatching { editor.assertTextContains("first saved", substring = true) }.isSuccess
        }
        editor.performTextReplacement("first draft 0")
        composeRule.onNodeWithTag("workspace-editor-back").performClick()

        composeRule.onNodeWithTag("workspace-file-list")
            .performScrollToNode(hasTestTag("workspace-entry-DraftTwo.kt"))
        composeRule.onNodeWithTag("workspace-entry-DraftTwo.kt").performClick()
        waitForUiStage("stress test second editor opens") {
            runCatching { editor.assertTextContains("second saved", substring = true) }.isSuccess
        }
        editor.performTextReplacement("second draft 0")

        repeat(15) { iteration ->
            composeRule.onNodeWithTag("workspace-tab-DraftOne.kt").performClick()
            waitForUiStage("stress restore first draft $iteration") {
                editableEditorText() == "1 │ first draft $iteration"
            }
            editor.performTextReplacement("first draft ${iteration + 1}")

            composeRule.onNodeWithTag("workspace-tab-DraftTwo.kt").performClick()
            waitForUiStage("stress restore second draft $iteration") {
                editableEditorText() == "1 │ second draft $iteration"
            }
            editor.performTextReplacement("second draft ${iteration + 1}")
        }

        composeRule.onNodeWithTag("workspace-tab-DraftOne.kt").performClick()
        waitForUiStage("final first draft survives switching") {
            editableEditorText() == "1 │ first draft 15"
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            firstFile.readText() == "first draft 15" && secondFile.readText() == "second draft 15"
        }
        assertEquals("first draft 15", firstFile.readText())
        assertEquals("second draft 15", secondFile.readText())
    }

    @Test
    fun workspaceEditorCanFindAndReplaceSingleAndAllMatches() {
        val sample = File(workspaceRoot, "SearchReplace.kt")
        sample.writeText("val target = 1\nval target = 2\n")
        composeRule.onNodeWithTag("open-workspace-browser-button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag("workspace-entry-SearchReplace.kt").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-entry-SearchReplace.kt").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag("workspace-editor-input")
                    .assertTextContains("val target = 1", substring = true)
            }.isSuccess
        }

        composeRule.onNodeWithTag("workspace-editor-find-toggle").performClick()
        composeRule.onNodeWithTag("workspace-editor-find-input").performTextReplacement("target")
        composeRule.onNodeWithTag("workspace-editor-replace-input").performTextReplacement("answer")
        composeRule.onNodeWithTag("workspace-editor-find-next").performClick()
        composeRule.onNodeWithTag("workspace-editor-replace-one").performClick()
        waitForUiStage("single replacement updates the editor") {
            runCatching {
                composeRule.onNodeWithTag("workspace-editor-input").assertTextContains("val answer = 1", substring = true)
                composeRule.onNodeWithTag("workspace-editor-input").assertTextContains("val target = 2", substring = true)
            }.isSuccess
        }

        composeRule.onNodeWithTag("workspace-editor-replace-input").performTextReplacement("item")
        composeRule.onNodeWithTag("workspace-editor-replace-all").performClick()
        waitForUiStage("replace-all updates the editor") {
            runCatching {
                composeRule.onNodeWithTag("workspace-editor-input").assertTextContains("val item = 2", substring = true)
                composeRule.onNodeWithTag("workspace-editor-input").assertTextContains("val answer = 1", substring = true)
            }.isSuccess
        }

        composeRule.onNodeWithTag("workspace-save-file").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            sample.readText() == "val answer = 1\nval item = 2\n"
        }
        assertEquals("val answer = 1\nval item = 2\n", sample.readText())
    }


    @Test
    fun floatingEditorToolsCanMoveAndApplyBasicAndAstraCodeActions() {
        val sample = File(workspaceRoot, "FloatingTools.kt")
        sample.writeText("val answer = 42")
        composeRule.onNodeWithTag("open-workspace-browser-button").performClick()
        waitForUiStage("floating tools sample appears") {
            runCatching { composeRule.onNodeWithTag("workspace-entry-FloatingTools.kt").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-entry-FloatingTools.kt").performClick()
        waitForUiStage("floating tools editor opens") {
            runCatching { composeRule.onNodeWithTag("workspace-editor-input").assertIsDisplayed() }.isSuccess
        }

        composeRule.onNodeWithTag("editor-tools-drag-handle").performTouchInput {
            down(center)
            moveBy(Offset(-120f, -120f))
            up()
        }
        composeRule.onNodeWithTag("editor-tools-open").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("editor-tools-group-basic").assertIsDisplayed()
        composeRule.onNodeWithTag("editor-tool-duplicate_line").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            sample.readText() == "val answer = 42\nval answer = 42"
        }
        assertEquals("val answer = 42\nval answer = 42", sample.readText())

        composeRule.onNodeWithTag("editor-tools-open").performClick()
        composeRule.onNodeWithTag("editor-tools-group-astracode").performClick()
        composeRule.onNodeWithTag("editor-tool-context_capsule").performClick()
        composeRule.onNodeWithText("Copied AstraCode Context Capsule to clipboard.").assertIsDisplayed()
    }

    @Test
    fun editorToolsDescribeKotlinSymbolsOffline() {
        val sample = File(workspaceRoot, "SymbolReference.kt")
        sample.writeText("")
        composeRule.onNodeWithTag("open-workspace-browser-button").performClick()
        waitForUiStage("symbol reference sample appears") {
            runCatching { composeRule.onNodeWithTag("workspace-entry-SymbolReference.kt").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-entry-SymbolReference.kt").performClick()
        waitForUiStage("symbol reference editor opens") {
            runCatching { composeRule.onNodeWithTag("workspace-editor-input").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-editor-input").performTextInput("println")
        composeRule.onNodeWithTag("editor-tools-open").performClick()
        composeRule.onNodeWithTag("editor-tools-group-astracode").performClick()
        composeRule.onNodeWithTag("editor-tool-describe_symbol").performClick()
        waitForUiStage("symbol description is displayed") {
            runCatching { composeRule.onNodeWithTag("language-reference-dialog").assertIsDisplayed() }.isSuccess
        }
        val categoryText = composeRule.onNodeWithTag("language-reference-category", useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsProperties.Text].joinToString(" ") { it.text }
        val descriptionText = composeRule.onNodeWithTag("language-reference-description", useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsProperties.Text].joinToString(" ") { it.text }
        val expectedReference = describeLanguageSymbolAt("println", "SymbolReference.kt", 7)
        composeRule.onNodeWithTag("language-reference-symbol", useUnmergedTree = true).assertTextContains("println")
        assertTrue(
            "Symbol reference category mismatch: actual=[$categoryText], expected helper=[$expectedReference], description=[$descriptionText]",
            categoryText.contains("Built-in function"),
        )
        assertTrue(
            "Symbol reference description mismatch: actual=[$descriptionText], expected helper=[$expectedReference]",
            descriptionText.contains("standard output"),
        )
        composeRule.onNodeWithTag("language-reference-close").performClick()
    }



    @Test
    fun workspaceTreeMenuShowsConnectedFoldersAndOpensFiles() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workspace = context.filesDir.toPath().resolve("workspace")
        Files.createDirectories(workspace.resolve("app/src"))
        val source = workspace.resolve("app/src/Main.kt")
        Files.write(source, "fun main() = Unit\n".toByteArray(Charsets.UTF_8))
        composeRule.activityRule.scenario.recreate()

        composeRule.onNodeWithTag("open-workspace-browser-button").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching { composeRule.onNodeWithTag("workspace-tree-menu-button").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-tree-menu-button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag("workspace-tree-root").assertIsDisplayed() }.isSuccess
        }

        composeRule.onNodeWithTag("workspace-tree-root").performTouchInput { longClick() }
        composeRule.onNodeWithTag("workspace-tree-context-new-file").assertIsDisplayed()
        listOf(
            "workspace-tree-context-rename",
            "workspace-tree-context-delete",
            "workspace-tree-context-change-path",
        ).forEach { tag ->
            assertTrue(
                "Workspace root must not offer $tag",
                runCatching { composeRule.onNodeWithTag(tag).assertIsDisplayed() }.isFailure,
            )
        }
        composeRule.onNodeWithTag("workspace-tree-context-cancel").performClick()
        composeRule.onAllNodesWithTag("workspace-tree-context-new-file").assertCountEquals(0)

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag("workspace-tree-folder-app").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-tree-folder-app").performTouchInput { longClick() }
        composeRule.onNodeWithTag("workspace-tree-context-new-file").assertIsDisplayed()
        composeRule.onNodeWithTag("workspace-tree-context-rename").assertIsDisplayed()
        composeRule.onNodeWithTag("workspace-tree-context-delete").assertIsDisplayed()
        composeRule.onNodeWithTag("workspace-tree-context-change-path").assertIsDisplayed()
        composeRule.onNodeWithTag("workspace-tree-context-cancel").performClick()
        composeRule.onNodeWithTag("workspace-tree-folder-app").assertIsDisplayed()

        composeRule.onNodeWithTag("workspace-tree-expand-root").performClick()
        assertTrue(
            runCatching { composeRule.onNodeWithTag("workspace-tree-folder-app").assertIsDisplayed() }.isFailure,
        )
        composeRule.onNodeWithTag("workspace-tree-expand-root").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag("workspace-tree-folder-app").assertIsDisplayed() }.isSuccess
        }
        assertTrue(
            runCatching { composeRule.onNodeWithTag("workspace-tree-folder-app-src").assertIsDisplayed() }.isFailure,
        )
        composeRule.onNodeWithTag("workspace-tree-expand-app").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag("workspace-tree-folder-app-src").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-tree-expand-app-src").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag("workspace-tree-file-app-src-Main.kt").assertIsDisplayed() }.isSuccess
        }

        composeRule.onNodeWithTag("workspace-tree-expand-app").performClick()
        assertTrue(
            runCatching { composeRule.onNodeWithTag("workspace-tree-folder-app-src").assertIsDisplayed() }.isFailure,
        )
        composeRule.onNodeWithTag("workspace-tree-expand-app").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag("workspace-tree-file-app-src-Main.kt").assertIsDisplayed()
            }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-tree-file-app-src-Main.kt").performTouchInput { longClick() }
        assertTrue(
            runCatching { composeRule.onNodeWithTag("workspace-tree-context-new-file").assertIsDisplayed() }.isFailure,
        )
        composeRule.onNodeWithTag("workspace-tree-context-rename").assertIsDisplayed()
        composeRule.onNodeWithTag("workspace-tree-context-delete").assertIsDisplayed()
        composeRule.onNodeWithTag("workspace-tree-context-change-path").assertIsDisplayed()
        composeRule.onNodeWithTag("workspace-tree-context-cancel").performClick()
        composeRule.onNodeWithTag("workspace-tree-file-app-src-Main.kt").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag("workspace-editor-input").assertTextContains("fun main() = Unit", substring = true)
            }.isSuccess
        }
        assertEquals("fun main() = Unit\n", String(Files.readAllBytes(source), Charsets.UTF_8))
    }
}
