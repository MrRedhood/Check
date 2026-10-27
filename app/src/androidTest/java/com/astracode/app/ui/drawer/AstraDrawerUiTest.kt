package com.astracode.app.ui.drawer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AstraDrawerUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun longPressWorkspaceShowsOnlyWorkspaceManagementActions() {
        composeRule.setContent {
            var state by remember {
                mutableStateOf(
                    DrawerUiState.Empty.copy(
                        project = DrawerUiState.Empty.project.copy(name = "Test Project"),
                        workspaces = listOf(
                            WorkspaceUi(
                                id = "workspace-test",
                                name = "Workspace Test",
                                kind = WorkspaceKind.Managed,
                                subtitle = "App-managed workspace",
                                trailing = "",
                                isActive = true,
                            ),
                        ),
                    ),
                )
            }
            AstraEditorDrawer(
                state = state,
                onEvent = { event ->
                    when (event) {
                        is DrawerEvent.WorkspaceLongPressed -> state =
                            state.copy(optionsTarget = state.workspaces.firstOrNull { it.id == event.id })
                        DrawerEvent.WorkspaceOptionsDismissed -> state = state.copy(optionsTarget = null)
                        is DrawerEvent.WorkspaceOptionSelected -> state = state.copy(optionsTarget = null)
                        else -> Unit
                    }
                },
                onClose = {},
            )
        }

        composeRule.onNodeWithTag("drawer-workspace-workspace-test").performTouchInput { longClick() }
        composeRule.onNodeWithText("WORKSPACE OPTIONS").assertIsDisplayed()
        composeRule.onNodeWithText("Rename").assertIsDisplayed()
        composeRule.onNodeWithText("Delete Workspace").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("Create File").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Create Folder").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun inactiveBufferStillHasAWorkingCloseControl() {
        composeRule.setContent {
            AstraEditorDrawer(
                state = DrawerUiState.Empty.copy(
                    buffers = listOf(
                        BufferUi("Main.kt", FileBadge.Kotlin, "", isActive = true, path = "Main.kt"),
                        BufferUi("Util.kt", FileBadge.Kotlin, "24 L", isActive = false, path = "src/Util.kt"),
                    ),
                ),
                onEvent = {},
                onClose = {},
            )
        }
        composeRule.onNodeWithTag("drawer-buffer-close-src__Util.kt").assertIsDisplayed()
    }

    @Test
    fun outsideTapDismissesEditorDrawer() {
        var closed = false
        composeRule.setContent {
            AstraEditorDrawer(
                state = DrawerUiState.Empty,
                onEvent = {},
                onClose = { closed = true },
            )
        }
        composeRule.onNodeWithTag("editor-drawer-outside-dismiss").performTouchInput { click() }
        composeRule.runOnIdle { assertTrue(closed) }
    }
}
