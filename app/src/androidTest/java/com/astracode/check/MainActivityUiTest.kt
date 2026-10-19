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
import org.junit.Assert.assertFalse
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
    fun createsAndExtractsZipForMultipleItemsAndUpdatesLinkedExternalFolder() {
        val externalRoot = File(
            InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
            "external-workspace",
        )
        composeRule.onNodeWithTag("open-workspace-browser-button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag("workspace-entry-app").assertIsDisplayed() }.isSuccess
        }

        composeRule.onNodeWithTag("workspace-entry-app").performTouchInput { longClick() }
        composeRule.onNodeWithTag("workspace-action-sync").performClick()
        composeRule.onNodeWithTag("sync-app-to-external").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            File(externalRoot, "app/src/.env").takeIf { it.exists() }?.readText() == "TOKEN=original"
        }

        composeRule.onNodeWithTag("workspace-entry-app").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag("workspace-entry-build.gradle.kts").assertIsDisplayed() }.isSuccess
        }

        composeRule.onNodeWithTag("workspace-toggle-selection").performClick()
        composeRule.onNodeWithTag("workspace-select-build.gradle.kts").performClick()
        composeRule.onNodeWithTag("workspace-select-src").performClick()
        composeRule.onNodeWithTag("workspace-bulk-zip").performClick()
        composeRule.onNodeWithTag("workspace-bulk-input").performTextReplacement("bundle.zip")
        composeRule.onNodeWithTag("confirm-workspace-bulk-action").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            File(workspaceRoot, "app/bundle.zip").isFile &&
                File(externalRoot, "app/bundle.zip").isFile
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag("workspace-entry-bundle.zip").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-entry-bundle.zip").performTouchInput { longClick() }
        composeRule.onNodeWithTag("workspace-action-extract").performClick()
        composeRule.onNodeWithTag("workspace-operation-input").performTextReplacement("app/restored")
        composeRule.onNodeWithTag("confirm-workspace-operation").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
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
    }


    @Test
    fun opensEditsAndManagesZipEntriesWithoutExtracting() {
        WorkspaceArchive.createZip(
            workspaceRoot,
            listOf("app/src/Main.kt"),
            "project.zip",
            "app/src",
        )
        composeRule.onNodeWithTag("open-workspace-browser-button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag("workspace-entry-project.zip").assertIsDisplayed() }.isSuccess
        }

        composeRule.onNodeWithTag("workspace-entry-project.zip").performTouchInput { longClick() }
        composeRule.onNodeWithTag("workspace-action-metadata").performClick()
        composeRule.onNodeWithTag("workspace-metadata-details").assertTextContains("Name: project.zip")
        composeRule.onNodeWithTag("workspace-metadata-details").assertTextContains("Created:")
        composeRule.onNodeWithTag("workspace-metadata-details").assertTextContains("Modified:")
        composeRule.onNodeWithTag("workspace-metadata-details").assertTextContains("Format: ZIP")
        composeRule.onNodeWithTag("workspace-metadata-details").assertTextContains("Storage path:")
        composeRule.onNodeWithText("Close").performClick()

        composeRule.onNodeWithTag("workspace-entry-project.zip").performTouchInput { longClick() }
        composeRule.onNodeWithTag("workspace-action-browse-zip").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag("workspace-zip-entry-Main.kt").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-zip-entry-Main.kt").performClick()
        composeRule.onNodeWithTag("workspace-zip-editor-input").performTextReplacement("fun main() = 7\n")
        composeRule.onNodeWithTag("workspace-zip-save-file").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                WorkspaceArchiveBrowser.read(workspaceRoot, "project.zip", "Main.kt")
                    .toString(Charsets.UTF_8) == "fun main() = 7\n"
            }.getOrDefault(false)
        }
        assertFalse(File(workspaceRoot, "Main.kt").exists())

        composeRule.onNodeWithTag("workspace-zip-editor-back").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag("workspace-zip-entry-Main.kt").assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithTag("workspace-zip-entry-Main.kt").performTouchInput { longClick() }
        composeRule.onNodeWithTag("workspace-zip-action-rename").performClick()
        composeRule.onNodeWithTag("workspace-zip-operation-input").performTextReplacement("App.kt")
        composeRule.onNodeWithTag("workspace-zip-confirm-operation").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag("workspace-zip-entry-App.kt").assertIsDisplayed() }.isSuccess
        }
        assertEquals("fun main() = 7\n", WorkspaceArchiveBrowser.read(workspaceRoot, "project.zip", "App.kt").toString(Charsets.UTF_8))
    }

}
