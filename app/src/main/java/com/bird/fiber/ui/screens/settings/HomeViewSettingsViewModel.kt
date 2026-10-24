package com.bird.fiber.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bird.fiber.data.local.library.LibraryRepository
import com.bird.fiber.data.settings.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class LibraryHomeViewSetting(
    val libraryId: String,
    val libraryName: String,
    val viewMode: ListViewMode = ListViewMode.LIST
)

data class HomeViewSettingsUiState(
    val libraries: List<LibraryHomeViewSetting> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class HomeViewSettingsViewModel @Inject constructor(
    libraryRepository: LibraryRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {
    val uiState: StateFlow<HomeViewSettingsUiState> = combine(
        libraryRepository.getAllLibraries(), settingsDataStore.settingsFlow
    ) { libraries, settings ->
        HomeViewSettingsUiState(
            libraries = libraries.map {
                LibraryHomeViewSetting(it.id, it.name, settings.listViewModeForLibrary(it.id))
            },
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeViewSettingsUiState())

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun setLibraryViewMode(libraryId: String, mode: ListViewMode) {
        if (uiState.value.libraries.none { it.libraryId == libraryId }) return
        viewModelScope.launch {
            try {
                settingsDataStore.saveLibraryListViewMode(libraryId, mode)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Timber.e(error, "保存库主页视图失败")
                _errorMessage.value = "保存失败，请重试"
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
