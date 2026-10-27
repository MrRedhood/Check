package com.astracode.app.ui.drawer

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class DrawerViewModel : ViewModel() {

    private val _state = MutableStateFlow(DrawerUiState.Empty)
    val state: StateFlow<DrawerUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<DrawerEffect>(extraBufferCapacity = 16)
    val effects: SharedFlow<DrawerEffect> = _effects.asSharedFlow()

    fun onEvent(event: DrawerEvent) {
        when (event) {
            is DrawerEvent.SectionSelected -> _state.update { it.copy(selectedSection = event.section) }

            // Switching needs the workspace repository, so the UI only requests it.
            is DrawerEvent.WorkspaceClicked -> _effects.tryEmit(DrawerEffect.SwitchWorkspace(event.id))

            is DrawerEvent.WorkspaceLongPressed -> _state.update { s ->
                s.copy(optionsTarget = s.workspaces.firstOrNull { it.id == event.id })
            }

            DrawerEvent.WorkspaceOptionsDismissed -> _state.update { it.copy(optionsTarget = null) }

            is DrawerEvent.WorkspaceOptionSelected -> {
                _state.update { it.copy(optionsTarget = null) }
                if (event.action != WorkspaceAction.Cancel) {
                    // Delete/Rename must be confirmed + executed by core-filesystem (atomic ops), not here.
                    _effects.tryEmit(DrawerEffect.WorkspaceOption(event.workspaceId, event.action))
                }
            }

            is DrawerEvent.BufferClicked -> _effects.tryEmit(DrawerEffect.OpenBuffer(event.name))
            is DrawerEvent.BufferClosed -> _effects.tryEmit(DrawerEffect.CloseBuffer(event.name))
            is DrawerEvent.Action -> _effects.tryEmit(DrawerEffect.RunAction(event.action))

            DrawerEvent.ToggleFileOps -> _state.update { it.copy(fileOpsExpanded = !it.fileOpsExpanded) }
            DrawerEvent.ToggleLsp -> _state.update { it.copy(lspExpanded = !it.lspExpanded) }
            DrawerEvent.TogglePrefs -> _state.update { it.copy(prefsExpanded = !it.prefsExpanded) }

            is DrawerEvent.FontSizeStep -> _state.update {
                it.copy(fontSize = (it.fontSize + event.delta).coerceIn(8, 32))
            }

            DrawerEvent.WordWrapToggled -> _state.update { it.copy(wordWrap = !it.wordWrap) }
        }
    }
}