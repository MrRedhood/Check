package com.astracode.check

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
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
    }

}
