package io.ahxxm.ic.ui

import io.ahxxm.ic.domain.FolderSummary
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

interface FolderRepository {
    suspend fun getFolders(): List<FolderSummary>
}

data class FolderBrowserState(
    val folders: List<FolderSummary> = emptyList(),
    val hasPermission: Boolean = false
)

class FolderBrowserViewModel(
    private val repository: FolderRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val _state = MutableStateFlow(FolderBrowserState())
    val state: StateFlow<FolderBrowserState> = _state.asStateFlow()

    fun onPermissionChanged(granted: Boolean) {
        _state.value = _state.value.copy(hasPermission = granted)
    }

    suspend fun loadFolders() {
        if (!_state.value.hasPermission) return
        val folders = withContext(ioDispatcher) { repository.getFolders() }
        _state.value = _state.value.copy(folders = folders)
    }
}
