package com.astracode.app.ui.drawer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
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
        composeRule.onNodeWithText("WORKSPACE OPTIONS").assertExists()
        composeRule.onNodeWithText("Rename").assertExists()
        composeRule.onNodeWithText("Delete Workspace").assertExists()
        composeRule.onNodeWithText("Cancel").assertExists()
        composeRule.onNodeWithText("Create File").assertDoesNotExist()
        composeRule.onNodeWithText("Create Folder").assertDoesNotExist()
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
        composeRule.runOnIdle { assert(closed) }
    }
}
