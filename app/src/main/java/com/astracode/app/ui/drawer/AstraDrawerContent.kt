@file:OptIn(ExperimentalFoundationApi::class)

package com.astracode.app.ui.drawer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import com.astracode.app.ui.components.AText
import com.astracode.app.ui.components.AstraBadge
import com.astracode.app.ui.components.PulseDot
import com.astracode.app.ui.components.borderBottom
import com.astracode.app.ui.components.borderEnd
import com.astracode.app.ui.components.borderTop
import com.astracode.app.ui.components.panel
import com.astracode.app.ui.components.pressScale
import com.astracode.app.ui.theme.AstraColors
import com.astracode.app.ui.theme.AstraIcons
import kotlinx.coroutines.launch

private val C = AstraColors

/** LazyColumn item index of each drawer section (see item order in [AstraDrawerContent]). */
private val DrawerSection.listIndex: Int
    get() = when (this) {
        DrawerSection.Files -> 2
        // The source HTML links "Commands" to #section-commands, which does not exist; File Operations is the closest block.
        DrawerSection.Commands -> 3
        DrawerSection.Ai -> 4
        DrawerSection.Lsp -> 5
        DrawerSection.Prefs -> 6
    }

/**
 * Drawer body. Width is supplied by the caller (86% of screen, max 340dp) so ModalDrawerSheet owns sizing.
 * Stateless: everything comes from [state], everything goes out through [onEvent].
 */
@Composable
fun AstraDrawerContent(
    state: DrawerUiState,
    onEvent: (DrawerEvent) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Column(
        modifier
            .fillMaxHeight()
            .background(C.Drawer)
            .borderEnd(C.Border),
    ) {
        DrawerTop(
            state = state,
            onClose = onClose,
            onSection = { section ->
                onEvent(DrawerEvent.SectionSelected(section))
                scope.launch { listState.animateScrollToItem(section.listIndex) }
            },
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "project") {
                ProjectCard(state.project) { onEvent(DrawerEvent.Action(DrawerAction.CreateWorkspace)) }
            }
            item(key = "workspaces") { WorkspacesSection(state, onEvent) }
            item(key = "files") { Box(Modifier.padding(top = 4.dp)) { FilesSection(state, onEvent) } }
            item(key = "fileops") { FileOpsSection(state, onEvent) }
            item(key = "ai") { Box(Modifier.padding(top = 4.dp)) { AiSection(state, onEvent) } }
            item(key = "lsp") { Box(Modifier.padding(top = 4.dp)) { LspSection(state, onEvent) } }
            item(key = "prefs") { Box(Modifier.padding(top = 4.dp)) { PrefsSection(state, onEvent) } }
        }

        ToolDock(state, onEvent)
    }
}

// ───────────────────────────────────────────── Top: brand header + category rail ─────────────────────────────────────

@Composable
private fun DrawerTop(
    state: DrawerUiState,
    onClose: () -> Unit,
    onSection: (DrawerSection) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(C.Drawer.copy(alpha = 0.95f))
            .borderBottom(C.Border.copy(alpha = 0.8f))
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrandBadge()
                Column(Modifier.weight(1f, fill = false)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        AText("AstraCode", C.Text, 15f, weight = FontWeight.Bold, mono = false, letterSpacing = -0.375f)
                        AstraBadge(
                            text = state.versionLabel,
                            fg = C.Primary,
                            bg = C.Primary.copy(alpha = 0.1f),
                            border = C.Primary.copy(alpha = 0.3f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        PulseDot(C.Primary, 6.dp)
                        AText(state.syncLabel, C.Muted, 11f, Modifier.weight(1f, fill = false))
                    }
                }
            }
            // -mr-1, 40dp touch target
            Box(
                Modifier
                    .offset(x = 4.dp)
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AstraIcons.CloseBold, "Close drawer", Modifier.size(20.dp), C.Muted)
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val selected = state.selectedSection
            NavChip("Files", AstraIcons.Folder, C.Muted, selected == DrawerSection.Files, null) { onSection(DrawerSection.Files) }
            NavChip("Commands", AstraIcons.Bolt, C.Warning, selected == DrawerSection.Commands, null) { onSection(DrawerSection.Commands) }
            NavChip("LSP", AstraIcons.Pencil, C.Secondary, selected == DrawerSection.Lsp, null) { onSection(DrawerSection.Lsp) }
            NavChip("AI", AstraIcons.Sparkle, C.Tertiary, selected == DrawerSection.Ai, C.Tertiary) { onSection(DrawerSection.Ai) }
            NavChip("Prefs", AstraIcons.Gear, C.Muted, selected == DrawerSection.Prefs, null) { onSection(DrawerSection.Prefs) }
        }
    }
}

@Composable
private fun BrandBadge() {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .size(36.dp)
            .shadow(12.dp, shape, ambientColor = C.Primary.copy(alpha = 0.15f), spotColor = C.Primary.copy(alpha = 0.15f))
            .clip(shape)
            .background(Brush.linearGradient(listOf(C.IconBadgeStart, C.IconBadgeEnd)))
            .border(1.dp, C.Primary.copy(alpha = 0.4f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(AstraIcons.Cube, null, Modifier.size(20.dp), C.Primary)
    }
}

@Composable
private fun NavChip(
    label: String,
    icon: ImageVector,
    iconTint: Color,
    selected: Boolean,
    accent: Color?,
    onClick: () -> Unit,
) {
    val bg = if (selected) C.Primary.copy(alpha = 0.15f) else C.Card
    val border = if (selected) C.Primary.copy(alpha = 0.4f) else (accent?.copy(alpha = 0.3f) ?: C.Border)
    val text = if (selected) C.Primary else (accent ?: C.Muted)
    Row(
        Modifier
            .panel(RoundedCornerShape(8.dp), bg, border)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(14.dp), if (selected) C.Primary else iconTint)
        AText(label, text, 11f, weight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

// ───────────────────────────────────────────────── Project card ──────────────────────────────────────────────────────

@Composable
private fun ProjectCard(project: ProjectSummaryUi, onCreate: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(C.CardGradientStart, C.Card)))
            .border(1.dp, C.Border, shape)
            .padding(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(32.dp)
                        .panel(RoundedCornerShape(8.dp), C.Primary.copy(alpha = 0.1f), C.Primary.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AstraIcons.ProjectCube, null, Modifier.size(16.dp), C.Primary)
                }
                Column(Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        AText(project.name, C.Text, 12f, Modifier.weight(1f, fill = false), FontWeight.Bold)
                        FileBadgePill(project.badge, bold = true, hPad = 4.dp)
                    }
                    Row(
                        Modifier.padding(top = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(AstraIcons.GitCircle, null, Modifier.size(12.dp), C.Secondary)
                            AText(project.branch, C.Secondary, 10f)
                        }
                        AText("•", C.Muted, 10f)
                        AText(project.path, C.Primary, 10f, Modifier.weight(1f, fill = false))
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(32.dp)
                    .panel(RoundedCornerShape(8.dp), C.Hover, C.Border.copy(alpha = 0.8f))
                    .clickable(onClick = onCreate),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AstraIcons.Plus, "Create Workspace", Modifier.size(16.dp), C.Primary)
            }
        }

        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.Border.copy(alpha = 0.6f)))
        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                AstraBadge("${project.modifiedCount} mod", C.Warning, C.Warning.copy(alpha = 0.15f), C.Warning.copy(alpha = 0.3f), size = 10f)
                if (project.autoSync) {
                    Row(
                        Modifier
                            .panel(RoundedCornerShape(4.dp), C.Primary.copy(alpha = 0.1f), C.Primary.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(AstraIcons.Sync, null, Modifier.size(10.dp), C.Primary)
                        AText("Auto-Sync", C.Primary, 10f)
                    }
                }
            }
            AText(project.localSizeLabel, C.Muted, 9f)
        }
    }
}

@Composable
private fun FileBadgePill(badge: FileBadge, bold: Boolean, hPad: androidx.compose.ui.unit.Dp = 6.dp) {
    val (bg, fg) = when (badge) {
        FileBadge.Rust -> C.RustBadgeBg to C.RustBadgeFg
        FileBadge.Toml -> C.TomlBadgeBg to C.TomlBadgeFg
        FileBadge.Kotlin -> Color(0xFF33274D) to Color(0xFFC6A0F6)
        FileBadge.Java -> Color(0xFF3A2B18) to Color(0xFFF4BE68)
        FileBadge.Xml -> Color(0xFF142942) to Color(0xFF79A8FF)
        FileBadge.Json -> Color(0xFF173328) to Color(0xFFA5D6A7)
        FileBadge.Text -> C.Card to C.Muted
    }
    AstraBadge(
        text = badge.label,
        fg = fg,
        bg = bg.copy(alpha = 0.2f),
        border = fg.copy(alpha = 0.3f),
        weight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
        hPad = hPad,
    )
}

// ───────────────────────────────────────────────── Shared pieces ─────────────────────────────────────────────────────

@Composable
private fun SectionTitle(
    icon: ImageVector,
    tint: Color,
    title: String,
    titleColor: Color,
    weight: FontWeight,
    trailing: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(14.dp), tint)
            AText(title.uppercase(), titleColor, 11f, weight = weight, letterSpacing = 0.55f)
        }
        trailing()
    }
}

@Composable
private fun Accordion(
    leading: @Composable () -> Unit,
    title: String,
    hint: (@Composable () -> Unit)?,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val rotation = animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    Column(Modifier.fillMaxWidth().panel(shape, C.Card, C.Border)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                leading()
                AText(title.uppercase(), C.Muted, 12f, weight = FontWeight.SemiBold, letterSpacing = 0.6f)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                hint?.invoke()
                Icon(
                    AstraIcons.ChevronDown, null,
                    Modifier.size(16.dp).graphicsLayer { rotationZ = rotation.value },
                    C.Muted,
                )
            }
        }
        AnimatedVisibility(visible = expanded) {
            Column {
                Box(Modifier.fillMaxWidth().height(1.dp).background(C.Border.copy(alpha = 0.4f)))
                content()
            }
        }
    }
}

// ─────────────────────────────────────────────────── Workspaces ──────────────────────────────────────────────────────

@Composable
private fun WorkspacesSection(state: DrawerUiState, onEvent: (DrawerEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionTitle(AstraIcons.Grid, C.Primary, "Workspaces", C.Muted, FontWeight.SemiBold) {
            AstraBadge(
                "Hold for menu", C.Secondary.copy(alpha = 0.9f), C.Secondary.copy(alpha = 0.1f), null,
                size = 10f, weight = FontWeight.Normal,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            state.workspaces.forEach { ws ->
                WorkspaceRow(
                    ws = ws,
                    highlighted = state.optionsTarget?.id == ws.id,
                    onClick = { onEvent(DrawerEvent.WorkspaceClicked(ws.id)) },
                    onLongClick = { onEvent(DrawerEvent.WorkspaceLongPressed(ws.id)) },
                )
            }
        }
    }
}

@Composable
private fun WorkspaceRow(
    ws: WorkspaceUi,
    highlighted: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val isActive = ws.isActive || ws.kind == WorkspaceKind.Active
    val bg = when {
        highlighted -> C.HighlightedWorkspace
        isActive -> C.Selected
        else -> C.Card
    }
    val borderColor = when {
        highlighted -> C.Secondary
        isActive -> C.Primary.copy(alpha = 0.5f)
        else -> C.Border
    }
    Row(
        Modifier
            .fillMaxWidth()
            .panel(shape, bg, borderColor, borderWidth = if (highlighted) 2.dp else 1.dp)
            .heightIn(min = 48.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .testTag("drawer-workspace-" + ws.id)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                isActive -> Box(
                    Modifier.size(10.dp)
                        .drawBehind { drawCircle(C.Primary.copy(alpha = 0.2f), radius = size.minDimension / 2f + 4.dp.toPx()) }
                        .background(C.Primary, CircleShape),
                )
                ws.kind == WorkspaceKind.Private -> Icon(AstraIcons.Lock, null, Modifier.size(16.dp), C.Muted)
                ws.kind == WorkspaceKind.Managed -> Icon(AstraIcons.Folder, null, Modifier.size(16.dp), C.Secondary)
                else -> Icon(AstraIcons.Link, null, Modifier.size(16.dp), C.Secondary)
            }
            Column(Modifier.weight(1f, fill = false)) {
                AText(
                    ws.name, C.Text, 12f,
                    weight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                    mono = false,
                )
                if (isActive) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        PulseDot(C.Primary, 5.dp)
                        AText(ws.subtitle, C.Primary.copy(alpha = 0.9f), 10f)
                    }
                } else {
                    when (ws.kind) {
                        WorkspaceKind.Active -> AText(ws.subtitle, C.Primary, 10f)
                        WorkspaceKind.Private -> AText(ws.subtitle, C.Muted, 10f)
                        WorkspaceKind.Managed -> AText(ws.subtitle, C.Secondary.copy(alpha = 0.8f), 10f)
                        WorkspaceKind.RemoteBookmark -> AText(ws.subtitle, C.Secondary.copy(alpha = 0.8f), 10f)
                    }
                }
            }
        }
        if (isActive) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (ws.modifiedCount > 0) AstraBadge("${ws.modifiedCount}", C.Warning, C.Warning.copy(alpha = 0.2f), C.Warning.copy(alpha = 0.3f))
                Icon(AstraIcons.Check, null, Modifier.size(16.dp), C.Primary)
            }
        } else {
            when (ws.kind) {
                WorkspaceKind.Active -> Icon(AstraIcons.Check, null, Modifier.size(16.dp), C.Primary)
                WorkspaceKind.Private -> AText(ws.trailing, C.Muted, 10f)
                WorkspaceKind.Managed, WorkspaceKind.RemoteBookmark -> Box(
                    Modifier.size(28.dp).clip(RoundedCornerShape(4.dp)).clickable(onClick = onLongClick),
                    contentAlignment = Alignment.Center,
                ) { Icon(AstraIcons.MoreVert, "Workspace menu", Modifier.size(16.dp), C.Muted) }
            }
        }
    }
}

// ───────────────────────────────────────────────────── Files ─────────────────────────────────────────────────────────

@Composable
private fun FilesSection(state: DrawerUiState, onEvent: (DrawerEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionTitle(AstraIcons.Folder, C.Secondary, "File Explorer", C.Secondary, FontWeight.Bold) {
            AstraBadge(
                "${state.fileCount} files", C.Secondary, C.Secondary.copy(alpha = 0.15f), C.Secondary.copy(alpha = 0.3f),
                size = 10f, weight = FontWeight.Bold, hPad = 8.dp, radius = 50.dp,
            )
        }
        Column(Modifier.fillMaxWidth().panel(RoundedCornerShape(12.dp), C.Card, C.Border)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(C.BufferHeader)
                    .borderBottom(C.Border.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                AText("ACTIVE BUFFERS (${state.buffers.size})", C.Muted, 10f)
                AText(state.projectTypeLabel, C.Primary, 10f, weight = FontWeight.Medium)
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(C.Inset.copy(alpha = 0.6f))
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                state.buffers.forEach { buffer ->
                    BufferRow(
                        buffer = buffer,
                        onClick = { onEvent(DrawerEvent.BufferClicked(buffer.name, buffer.path)) },
                        onClose = { onEvent(DrawerEvent.BufferClosed(buffer.name, buffer.path)) },
                    )
                }
            }
            Column(Modifier.fillMaxWidth().borderTop(C.Border.copy(alpha = 0.6f))) {
                NavLinkRow(AstraIcons.Folder, "Open Explorer", { onEvent(DrawerEvent.Action(DrawerAction.OpenExplorer)) }) {
                    AText("Browse workspace", C.Muted, 10f)
                }
                NavDivider()
                NavLinkRow(AstraIcons.Clock, "Recent Files", { onEvent(DrawerEvent.Action(DrawerAction.RecentFiles)) }) {
                    AText("Alt+P", C.Muted, 10f)
                }
                NavDivider()
                NavLinkRow(AstraIcons.Search, "Find in Files", { onEvent(DrawerEvent.Action(DrawerAction.FindInFiles)) }) {
                    AText(
                        ".*regex", C.Secondary, 10f,
                        Modifier
                            .panel(RoundedCornerShape(4.dp), C.Secondary.copy(alpha = 0.1f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                NavDivider()
                NavLinkRow(AstraIcons.ChevronsRight, "Go to Line", { onEvent(DrawerEvent.Action(DrawerAction.GoToLine)) }) {
                    AText(":line", C.Muted, 10f)
                }
            }
        }
    }
}

@Composable
private fun BufferRow(buffer: BufferUi, onClick: () -> Unit, onClose: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    val rowModifier = if (buffer.isActive) {
        Modifier.panel(shape, C.Selected, C.Primary.copy(alpha = 0.4f))
    } else {
        Modifier.clip(shape)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .then(rowModifier)
            .heightIn(min = 44.dp)
            .clickable(onClick = onClick)
            .testTag("drawer-buffer-" + buffer.path.replace("/", "__"))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FileBadgePill(buffer.badge, bold = true, hPad = if (buffer.badge == FileBadge.Toml) 4.dp else 6.dp)
            AText(
                buffer.name,
                if (buffer.isActive) C.Primary else C.Text,
                12f,
                Modifier.weight(1f, fill = false),
                if (buffer.isActive) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (buffer.isDirty) {
                AstraBadge("M", C.Warning, C.Warning.copy(alpha = 0.2f), C.Warning.copy(alpha = 0.3f))
            }
            if (buffer.isActive) {
                Box(
                    Modifier
                        .size(8.dp)
                        .shadow(8.dp, CircleShape, ambientColor = C.Primary.copy(alpha = 0.8f), spotColor = C.Primary.copy(alpha = 0.8f))
                        .background(C.Primary, CircleShape),
                )
            } else if (buffer.trailing.isNotBlank()) {
                AText(buffer.trailing, C.Muted, 10f)
            }
            Box(
                Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onClose)
                    .testTag("drawer-buffer-close-" + buffer.path.replace("/", "__")),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AstraIcons.Close, "Close buffer", Modifier.size(14.dp), C.Muted)
            }
        }
    }
}

@Composable
private fun NavDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(C.Border.copy(alpha = 0.4f)))
}

@Composable
private fun NavLinkRow(icon: ImageVector, label: String, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(16.dp), C.Secondary)
            AText(label, C.Text, 12f, weight = FontWeight.Medium, mono = false)
        }
        trailing()
    }
}

// ───────────────────────────────────────────────── File operations ───────────────────────────────────────────────────

@Composable
private fun FileOpsSection(state: DrawerUiState, onEvent: (DrawerEvent) -> Unit) {
    Accordion(
        leading = { Icon(AstraIcons.EditSquare, null, Modifier.size(14.dp), C.Primary) },
        title = "File Operations",
        hint = { AText("Quick Actions", C.Muted, 10f) },
        expanded = state.fileOpsExpanded,
        onToggle = { onEvent(DrawerEvent.ToggleFileOps) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FileOpTile("New File", AstraIcons.PlusLines, C.Primary, Modifier.weight(1f), null) {
                    onEvent(DrawerEvent.Action(DrawerAction.NewFile))
                }
                FileOpTile("New Folder", AstraIcons.FolderPlus, C.Secondary, Modifier.weight(1f), null) {
                    onEvent(DrawerEvent.Action(DrawerAction.NewFolder))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FileOpTile(
                    "Save", AstraIcons.Save, C.Text, Modifier.weight(1f),
                    trailing = { AText("^S", C.Muted, 9f) },
                ) { onEvent(DrawerEvent.Action(DrawerAction.Save)) }
                FileOpTile(
                    "Save All", AstraIcons.SaveAll, C.Primary, Modifier.weight(1f),
                    trailing = {
                        AstraBadge(
                            "${state.unsavedCount} mod", C.Primary, C.Primary.copy(alpha = 0.2f), null,
                            weight = FontWeight.Bold, hPad = 4.dp,
                        )
                    },
                ) { onEvent(DrawerEvent.Action(DrawerAction.SaveAll)) }
            }
        }
    }
}

@Composable
private fun FileOpTile(
    label: String,
    icon: ImageVector,
    iconTint: Color,
    modifier: Modifier,
    trailing: (@Composable () -> Unit)?,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    Row(
        modifier
            .pressScale(source, 0.98f)
            .panel(RoundedCornerShape(8.dp), C.Inset, C.Border.copy(alpha = 0.7f))
            .heightIn(min = 46.dp)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(16.dp), iconTint)
            AText(label, C.Text, 12f, weight = FontWeight.Medium, mono = false)
        }
        trailing?.invoke()
    }
}

// ───────────────────────────────────────────────────── AI copilot ────────────────────────────────────────────────────

@Composable
private fun AiSection(state: DrawerUiState, onEvent: (DrawerEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionTitle(AstraIcons.Sparkle, C.Tertiary, "AI Copilot & Assist", C.Tertiary, FontWeight.Bold) {
            AstraBadge(state.aiModelLabel, C.Tertiary, C.Tertiary.copy(alpha = 0.2f), C.Tertiary.copy(alpha = 0.3f))
        }
        val shape = RoundedCornerShape(12.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .shadow(16.dp, shape, ambientColor = C.Tertiary.copy(alpha = 0.06f), spotColor = C.Tertiary.copy(alpha = 0.06f))
                .panel(shape, C.Card, C.Tertiary.copy(alpha = 0.4f))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Primary prompt box
            val promptSource = remember { MutableInteractionSource() }
            Row(
                Modifier
                    .fillMaxWidth()
                    .pressScale(promptSource, 0.99f)
                    .panel(RoundedCornerShape(8.dp), C.Tertiary.copy(alpha = 0.1f), C.Tertiary.copy(alpha = 0.3f))
                    .heightIn(min = 48.dp)
                    .clickable(interactionSource = promptSource, indication = null) {
                        onEvent(DrawerEvent.Action(DrawerAction.AskAi))
                    }
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(AstraIcons.Chat, null, Modifier.size(16.dp), C.Tertiary)
                    AText("Ask AI Code Assistant", C.Tertiary, 12f, weight = FontWeight.SemiBold, mono = false)
                }
                AText(
                    "⌘I", C.Tertiary, 10f,
                    Modifier
                        .panel(RoundedCornerShape(4.dp), C.Tertiary.copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }

            // Quick fix
            val fixSource = remember { MutableInteractionSource() }
            Row(
                Modifier
                    .fillMaxWidth()
                    .pressScale(fixSource, 0.99f)
                    .panel(RoundedCornerShape(8.dp), C.Error.copy(alpha = 0.1f), C.Error.copy(alpha = 0.3f))
                    .heightIn(min = 44.dp)
                    .clickable(interactionSource = fixSource, indication = null) {
                        onEvent(DrawerEvent.Action(DrawerAction.FixBorrowChecker))
                    }
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(AstraIcons.BoxPlus, null, Modifier.size(16.dp), C.Error)
                    AText(state.aiFixLabel, C.Error, 12f, Modifier.weight(1f, fill = false), FontWeight.Medium, mono = false)
                }
                AstraBadge(state.aiFixCode, C.Error, C.Error.copy(alpha = 0.2f), null, weight = FontWeight.Bold)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AiOpTile("Generate Code", "From comments", Modifier.weight(1f)) {
                    onEvent(DrawerEvent.Action(DrawerAction.GenerateCode))
                }
                AiOpTile("Explain Code", "Selected block", Modifier.weight(1f)) {
                    onEvent(DrawerEvent.Action(DrawerAction.ExplainCode))
                }
            }
        }
    }
}

@Composable
private fun AiOpTile(title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .panel(RoundedCornerShape(8.dp), C.Inset, C.Border.copy(alpha = 0.6f))
            .heightIn(min = 44.dp)
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        AText(title, C.Tertiary, 12f, weight = FontWeight.SemiBold, mono = false)
        AText(subtitle, C.Muted, 9f)
    }
}

// ─────────────────────────────────────────────────────── LSP ─────────────────────────────────────────────────────────

@Composable
private fun LspSection(state: DrawerUiState, onEvent: (DrawerEvent) -> Unit) {
    Accordion(
        leading = { PulseDot(C.Primary, 8.dp) },
        title = state.lspName,
        hint = {
            AstraBadge(
                state.lspStatus, C.Primary, C.Primary.copy(alpha = 0.1f), C.Primary.copy(alpha = 0.2f),
                size = 10f, weight = FontWeight.Normal,
            )
        },
        expanded = state.lspExpanded,
        onToggle = { onEvent(DrawerEvent.ToggleLsp) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LspTile("Go to Definition", "F12", Modifier.weight(1f)) {
                    onEvent(DrawerEvent.Action(DrawerAction.GoToDefinition))
                }
                LspTile("Find References", "Shift+F12", Modifier.weight(1f)) {
                    onEvent(DrawerEvent.Action(DrawerAction.FindReferences))
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .panel(RoundedCornerShape(8.dp), C.Inset, C.Border.copy(alpha = 0.5f))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AText("Diagnostics:", C.Muted, 11f)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AstraBadge("${state.lspErrors} err", C.Error, C.Error.copy(alpha = 0.2f), null, size = 10f, weight = FontWeight.Bold)
                    AstraBadge("${state.lspWarnings} warn", C.Warning, C.Warning.copy(alpha = 0.2f), null, size = 10f, weight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun LspTile(title: String, shortcut: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .panel(RoundedCornerShape(8.dp), C.Inset, C.Border.copy(alpha = 0.5f))
            .heightIn(min = 44.dp)
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        AText(title, C.Secondary, 12f, weight = FontWeight.Medium, mono = false)
        AText(shortcut, C.Muted, 9f)
    }
}

// ───────────────────────────────────────────────────── Preferences ───────────────────────────────────────────────────

@Composable
private fun PrefsSection(state: DrawerUiState, onEvent: (DrawerEvent) -> Unit) {
    Accordion(
        leading = { Icon(AstraIcons.Gear, null, Modifier.size(14.dp), C.Muted) },
        title = "Editor Settings",
        hint = null,
        expanded = state.prefsExpanded,
        onToggle = { onEvent(DrawerEvent.TogglePrefs) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 40.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AText("Font Size", C.Text, 12f, weight = FontWeight.Medium, mono = false)
                Row(
                    Modifier.panel(RoundedCornerShape(8.dp), C.Inset, C.Border).padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StepButton("-") { onEvent(DrawerEvent.FontSizeStep(-1)) }
                    AText("${state.fontSize}px", C.Primary, 12f, Modifier.padding(horizontal = 4.dp))
                    StepButton("+") { onEvent(DrawerEvent.FontSizeStep(+1)) }
                }
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 40.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AText("Word Wrap", C.Text, 12f, weight = FontWeight.Medium, mono = false)
                AstraToggle(checked = state.wordWrap) { onEvent(DrawerEvent.WordWrapToggled) }
            }
        }
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AText(label, C.Muted, 12f, weight = FontWeight.Bold)
    }
}

/** 36x20 pill toggle from the design (primary/30 track, primary thumb when on). */
@Composable
private fun AstraToggle(checked: Boolean, onToggle: () -> Unit) {
    val thumbOffset = animateDpAsState(if (checked) 14.dp else 0.dp, label = "thumb")
    Box(
        Modifier
            .size(width = 36.dp, height = 20.dp)
            .clip(CircleShape)
            .background(if (checked) C.Primary.copy(alpha = 0.3f) else C.Inset)
            .border(1.dp, if (checked) C.Primary.copy(alpha = 0.5f) else C.Border, CircleShape)
            .clickable(role = Role.Switch, onClick = onToggle)
            .padding(horizontal = 3.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset { androidx.compose.ui.unit.IntOffset(thumbOffset.value.roundToPx(), 0) }
                .size(16.dp)
                .background(if (checked) C.Primary else C.Muted, CircleShape),
        )
    }
}

// ───────────────────────────────────────────────────── Bottom dock ───────────────────────────────────────────────────

@Composable
private fun ToolDock(state: DrawerUiState, onEvent: (DrawerEvent) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(C.Dock)
            .borderTop(C.Border.copy(alpha = 0.9f))
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            DockTile(Modifier.weight(1f), "Term", C.Text, FontWeight.SemiBold, { onEvent(DrawerEvent.Action(DrawerAction.OpenTerminal)) }) {
                Icon(AstraIcons.Terminal, "Terminal", Modifier.size(16.dp), C.Primary)
            }
            DockTile(
                Modifier.weight(1f), "+${state.gitAdded} -${state.gitRemoved}", C.Secondary, FontWeight.SemiBold,
                { onEvent(DrawerEvent.Action(DrawerAction.OpenGitChanges)) },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(AstraIcons.GitCircle, "Git Changes", Modifier.size(14.dp), C.Secondary)
                }
            }
            DockTile(Modifier.weight(1f), "Test", C.Text, FontWeight.SemiBold, { onEvent(DrawerEvent.Action(DrawerAction.RunTests)) }) {
                Icon(AstraIcons.Play, "Run Cargo Tests", Modifier.size(16.dp), C.Warning)
            }
            DockTile(Modifier.weight(1f), "Config", C.Muted, FontWeight.Normal, { onEvent(DrawerEvent.Action(DrawerAction.OpenConfig)) }) {
                Icon(AstraIcons.Gear, "Quick Settings", Modifier.size(16.dp), C.Muted)
            }
        }
    }
}

@Composable
private fun DockTile(
    modifier: Modifier,
    label: String,
    labelColor: Color,
    weight: FontWeight,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    Column(
        modifier
            .pressScale(source)
            .panel(RoundedCornerShape(12.dp), C.Card, C.Border)
            .heightIn(min = 48.dp)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        icon()
        Spacer(Modifier.height(4.dp))
        AText(label, labelColor, 9f, weight = weight)
    }
}