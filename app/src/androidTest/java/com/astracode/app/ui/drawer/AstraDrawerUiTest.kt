package com.astracode.app.ui.drawer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import org.junit.Rule
import org.junit.Test
import com.astracode.app.ui.theme.AstraTheme

class AstraDrawerUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun longPressWorkspaceShowsOnlyWorkspaceManagementActions() {
        composeRule.setContent {
            AstraTheme {
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
                Box(Modifier.fillMaxSize()) {
                    AstraDrawerContent(
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
                    state.optionsTarget?.let { target ->
                        WorkspaceOptionsOverlay(
                            workspaceName = target.name,
                            onAction = { action ->
                                state = state.copy(optionsTarget = null)
                            },
                            onDismiss = { state = state.copy(optionsTarget = null) },
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("drawer-workspace-workspace-test").performTouchInput { longClick() }
        composeRule.onNodeWithText("WORKSPACE OPTIONS").assertExists()
        composeRule.onNodeWithText("Rename").assertExists()
        composeRule.onNodeWithText("Delete Workspace").assertExists()
        composeRule.onNodeWithText("Cancel").assertExists()
        composeRule.onNodeWithText("Create File").assertExists()
    }
}
