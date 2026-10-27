package com.astracode.app.ui.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.astracode.app.ui.theme.AstraColors
import com.astracode.app.ui.theme.AstraTheme

/**
 * Full-window editor navigation drawer. The existing workspace tree remains a separate view;
 * the editor drawer provides a faster route to open buffers, actions, settings, and workspace management.
 */
@Composable
fun AstraEditorDrawer(
    state: DrawerUiState,
    onEvent: (DrawerEvent) -> Unit,
    onClose: () -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        AstraTheme {
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .background(AstraColors.DrawerScrim)
                    .testTag("editor-hamburger-drawer"),
            ) {
                val drawerWidth = (maxWidth * 0.86f).coerceAtMost(340.dp)
                Row(Modifier.fillMaxSize()) {
                    AstraDrawerContent(
                        state = state,
                        onEvent = onEvent,
                        onClose = onClose,
                        modifier = Modifier.width(drawerWidth).fillMaxHeight(),
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(onClick = onClose)
                            .testTag("editor-drawer-outside-dismiss"),
                    )
                }
                state.optionsTarget?.let { target ->
                    WorkspaceOptionsOverlay(
                        workspaceName = target.name,
                        onAction = { action ->
                            onEvent(DrawerEvent.WorkspaceOptionSelected(target.id, action))
                        },
                        onDismiss = { onEvent(DrawerEvent.WorkspaceOptionsDismissed) },
                    )
                }
            }
        }
    }
}
