package com.astracode.app.ui.drawer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.astracode.app.ui.components.AText
import com.astracode.app.ui.components.PulseDot
import com.astracode.app.ui.components.borderTop
import com.astracode.app.ui.components.panel
import com.astracode.app.ui.components.pressScale
import com.astracode.app.ui.theme.AstraColors
import com.astracode.app.ui.theme.AstraIcons

private val CapsuleBg = Color(0xFF131D2A)
private val CapsuleBorder = Color(0xFF2B3E56)
private val CapsuleDivider = Color(0xFF1E2E42)
private val CapsuleScrim = Color(0xB3070D14) // bg-[#070D14]/70 (backdrop-blur-[3px] dropped for frame budget)
private val CapsuleMuted = Color(0xFF9AA9BC)

private data class CapsuleItem(
    val action: WorkspaceAction,
    val title: String,
    val subtitle: String,
    val shortcut: String,
    val icon: ImageVector,
    val iconTint: Color,
    val iconBg: Color,
    val iconBorder: Color,
    val shortcutFg: Color,
    val shortcutBg: Color,
    val shortcutBorder: Color,
    val containerBg: Color,
    val containerPressed: Color,
    val containerBorder: Color,
    val titleColor: Color,
    val subtitleColor: Color,
)

private val CapsuleItems = listOf(
    CapsuleItem(
        action = WorkspaceAction.Rename,
        title = "Rename", subtitle = "Change workspace name", shortcut = "F2",
        icon = AstraIcons.Pencil,
        iconTint = AstraColors.Secondary, iconBg = Color(0xFF142337), iconBorder = AstraColors.Secondary.copy(alpha = 0.4f),
        shortcutFg = AstraColors.Secondary, shortcutBg = Color(0xFF122135), shortcutBorder = AstraColors.Secondary.copy(alpha = 0.3f),
        containerBg = Color(0xFF192535), containerPressed = Color(0xFF203147), containerBorder = Color(0xFF26374D),
        titleColor = Color(0xFFE1E9F5), subtitleColor = CapsuleMuted,
    ),
    CapsuleItem(
        action = WorkspaceAction.DeleteWorkspace,
        title = "Delete Workspace", subtitle = "Clear or remove this workspace", shortcut = "Confirm",
        icon = AstraIcons.Trash,
        iconTint = AstraColors.Error, iconBg = Color(0xFF2D161C), iconBorder = AstraColors.Error.copy(alpha = 0.4f),
        shortcutFg = AstraColors.Error, shortcutBg = Color(0xFF291419), shortcutBorder = AstraColors.Error.copy(alpha = 0.4f),
        containerBg = Color(0xFF1B1E28), containerPressed = Color(0xFF261E24), containerBorder = Color(0xFF402127),
        titleColor = AstraColors.Error, subtitleColor = Color(0xFFA66E74),
    ),
    CapsuleItem(
        action = WorkspaceAction.Cancel,
        title = "Cancel", subtitle = "Dismiss this menu", shortcut = "Esc",
        icon = AstraIcons.Close,
        iconTint = CapsuleMuted, iconBg = Color(0xFF1A2534), iconBorder = Color(0xFF354860),
        shortcutFg = CapsuleMuted, shortcutBg = Color(0xFF121A25), shortcutBorder = Color(0xFF233144),
        containerBg = Color(0xFF151E2B), containerPressed = Color(0xFF1C283A), containerBorder = Color(0xFF212E40),
        titleColor = Color(0xFFC5D2E2), subtitleColor = Color(0xFF6C7E95),
    ),
)

/**
 * Full-screen scrim + centered capsule. Host it above the drawer (see AstraCodeApp) so the scrim covers the whole window.
 */
@Composable
fun WorkspaceOptionsOverlay(
    workspaceName: String,
    onAction: (WorkspaceAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // animate-in: fade + zoom-in-95 over 150ms
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(150)) }

    val capsuleShape = RoundedCornerShape(24.dp)
    val scrimSource = remember { MutableInteractionSource() }
    val consumeSource = remember { MutableInteractionSource() }

    Box(
        modifier
            .fillMaxSize()
            .background(CapsuleScrim)
            .clickable(interactionSource = scrimSource, indication = null, onClick = onDismiss)
            .systemBarsPadding()
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = progress.value
                    val s = 0.95f + 0.05f * progress.value
                    scaleX = s
                    scaleY = s
                }
                .shadow(24.dp, capsuleShape, ambientColor = Color.Black.copy(alpha = 0.8f), spotColor = Color.Black.copy(alpha = 0.8f))
                .clip(capsuleShape)
                .background(CapsuleBg)
                .border(1.dp, CapsuleBorder, capsuleShape)
                // swallow taps so only the scrim dismisses
                .clickable(interactionSource = consumeSource, indication = null, onClick = {}),
        ) {
            // Ambient top glow line
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 32.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, AstraColors.Secondary, Color.Transparent))),
            )

            Column(Modifier.padding(16.dp)) {
                // Header
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        PulseDot(AstraColors.Secondary, 8.dp)
                        AText(
                            "WORKSPACE OPTIONS", AstraColors.Secondary, 11f,
                            weight = FontWeight.Bold, letterSpacing = 0.55f,
                        )
                    }
                    AText(
                        workspaceName, CapsuleMuted, 10f,
                        Modifier
                            .panel(CircleShape, Color(0xFF1A2636), Color(0xFF293A50))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                Box(Modifier.padding(top = 12.dp, bottom = 12.dp).fillMaxWidth().height(1.dp).background(CapsuleDivider))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CapsuleItems.forEach { item ->
                        CapsuleActionRow(item) { onAction(item.action) }
                    }
                }

                // Footer hint
                Box(
                    Modifier
                        .padding(top = 14.dp)
                        .fillMaxWidth()
                        .borderTop(Color(0xFF1C2A3C))
                        .padding(top = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AText("Tap anywhere outside to close", Color(0xFF627791), 10f)
                }
            }
        }
    }
}

@Composable
private fun CapsuleActionRow(item: CapsuleItem, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .pressScale(source, 0.98f)
            .clip(shape)
            // pressed tint replaces hover:bg-[#203147] from the web design
            .background(if (pressed) item.containerPressed else item.containerBg)
            .border(1.dp, item.containerBorder, shape)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(32.dp)
                    .panel(RoundedCornerShape(12.dp), item.iconBg, item.iconBorder),
                contentAlignment = Alignment.Center,
            ) {
                Icon(item.icon, null, Modifier.size(16.dp), item.iconTint)
            }
            Column(Modifier.weight(1f, fill = false)) {
                AText(item.title, item.titleColor, 12f, weight = FontWeight.SemiBold)
                AText(item.subtitle, item.subtitleColor, 10f, mono = false)
            }
        }
        AText(
            item.shortcut, item.shortcutFg, 10f,
            Modifier
                .panel(RoundedCornerShape(4.dp), item.shortcutBg, item.shortcutBorder)
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}