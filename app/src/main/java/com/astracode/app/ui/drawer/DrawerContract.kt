package com.astracode.app.ui.drawer

import androidx.compose.runtime.Immutable

enum class FileBadge(val label: String) { Rust(".rs"), Toml(".toml"), Kotlin(".kt"), Java(".java"), Xml(".xml"), Json(".json"), Text("TXT") }

enum class WorkspaceKind { Active, Private, Managed, RemoteBookmark }

enum class DrawerSection { Files, Commands, Lsp, Ai, Prefs }

enum class WorkspaceAction { CreateFile, CreateFolder, Rename, DeleteWorkspace, Cancel }

enum class DrawerAction {
    CreateWorkspace,
    NewFile, NewFolder, Save, SaveAll,
    RecentFiles, FindInFiles, GoToLine,
    AskAi, FixBorrowChecker, GenerateCode, ExplainCode,
    GoToDefinition, FindReferences,
    OpenTerminal, OpenGitChanges, RunTests, OpenConfig, OpenExplorer,
}

@Immutable
data class ProjectSummaryUi(
    val name: String,
    val badge: FileBadge,
    val branch: String,
    val path: String,
    val modifiedCount: Int,
    val autoSync: Boolean,
    val localSizeLabel: String,
)

@Immutable
data class WorkspaceUi(
    val id: String,
    val name: String,
    val kind: WorkspaceKind,
    val subtitle: String,
    val trailing: String,
    val modifiedCount: Int = 0,
    val isActive: Boolean = false,
)

@Immutable
data class BufferUi(
    val name: String,
    val badge: FileBadge,
    val trailing: String,
    val isActive: Boolean,
    val path: String = name,
    val isDirty: Boolean = false,
)

@Immutable
data class DrawerUiState(
    val versionLabel: String,
    val syncLabel: String,
    val selectedSection: DrawerSection,
    val project: ProjectSummaryUi,
    val workspaces: List<WorkspaceUi>,
    /** Non-null while the long-press capsule is visible. */
    val optionsTarget: WorkspaceUi?,
    val fileCount: Int,
    val projectTypeLabel: String,
    val buffers: List<BufferUi>,
    val unsavedCount: Int,
    val aiModelLabel: String,
    val aiFixLabel: String,
    val aiFixCode: String,
    val lspName: String,
    val lspStatus: String,
    val lspErrors: Int,
    val lspWarnings: Int,
    val gitAdded: Int,
    val gitRemoved: Int,
    val gitAvailable: Boolean = false,
    val fileOpsExpanded: Boolean,
    val lspExpanded: Boolean,
    val prefsExpanded: Boolean,
    val fontSize: Int,
    val wordWrap: Boolean,
) {
    companion object {
        // TODO: replace with data from core-filesystem (workspace index) / feature-editor (buffers) / LSP bridge.
        val Empty = DrawerUiState(
            versionLabel = "v0.1.0",
            syncLabel = "Local workspace",
            selectedSection = DrawerSection.Files,
            project = ProjectSummaryUi(
                name = "Workspace",
                badge = FileBadge.Text,
                branch = "local",
                path = "/",
                modifiedCount = 0,
                autoSync = false,
                localSizeLabel = "",
            ),
            workspaces = emptyList(),
            optionsTarget = null,
            fileCount = 0,
            projectTypeLabel = "Text",
            buffers = emptyList(),
            unsavedCount = 0,
            aiModelLabel = "Not connected",
            aiFixLabel = "AI tools are not connected",
            aiFixCode = "",
            lspName = "Language services",
            lspStatus = "Not connected",
            lspErrors = 0,
            lspWarnings = 0,
            gitAdded = 0,
            gitRemoved = 0,
            gitAvailable = false,
            fileOpsExpanded = true,
            lspExpanded = false,
            prefsExpanded = false,
            fontSize = 14,
            wordWrap = true,
        )
    }
}

internal fun fileBadgeForPath(path: String): FileBadge {
    val extension = path.substringAfterLast('/').substringAfterLast('.', missingDelimiterValue = "").lowercase()
    return when (extension) {
        "rs" -> FileBadge.Rust
        "toml" -> FileBadge.Toml
        "kt", "kts" -> FileBadge.Kotlin
        "java" -> FileBadge.Java
        "xml" -> FileBadge.Xml
        "json" -> FileBadge.Json
        else -> FileBadge.Text
    }
}

sealed interface DrawerEvent {
    data class SectionSelected(val section: DrawerSection) : DrawerEvent
    data class WorkspaceClicked(val id: String) : DrawerEvent
    data class WorkspaceLongPressed(val id: String) : DrawerEvent
    data object WorkspaceOptionsDismissed : DrawerEvent
    data class WorkspaceOptionSelected(val workspaceId: String, val action: WorkspaceAction) : DrawerEvent
    data class BufferClicked(val name: String, val path: String = name) : DrawerEvent
    data class BufferClosed(val name: String, val path: String = name) : DrawerEvent
    data class Action(val action: DrawerAction) : DrawerEvent
    data object ToggleFileOps : DrawerEvent
    data object ToggleLsp : DrawerEvent
    data object TogglePrefs : DrawerEvent
    data class FontSizeStep(val delta: Int) : DrawerEvent
    data object WordWrapToggled : DrawerEvent
}

/** One-shot commands for other modules (core-filesystem, feature-terminal, core-buildengine, AI gateway). */
sealed interface DrawerEffect {
    data class RunAction(val action: DrawerAction) : DrawerEffect
    data class SwitchWorkspace(val id: String) : DrawerEffect
    data class WorkspaceOption(val workspaceId: String, val action: WorkspaceAction) : DrawerEffect
    data class OpenBuffer(val name: String) : DrawerEffect
    data class CloseBuffer(val name: String) : DrawerEffect
    data object CloseDrawer : DrawerEffect
}