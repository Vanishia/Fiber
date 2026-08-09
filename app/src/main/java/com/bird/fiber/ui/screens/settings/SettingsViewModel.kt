package com.bird.fiber.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bird.fiber.data.settings.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 设置页面 ViewModel
 *
 * 管理所有设置项的状态，使用 DataStore 进行持久化
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState.DEFAULT)
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        // 加载保存的设置
        loadSettings()
    }

    /**
     * 从 DataStore 加载设置
     */
    fun loadSettings() {
        viewModelScope.launch {
            settingsDataStore.settingsFlow.collect { settings ->
                _uiState.value = settings
            }
        }
    }

    /**
     * 设置字体大小级别（0-4 对应 80%, 90%, 100%, 110%, 120%）
     */
    fun setFontSizeLevel(level: Int) {
        if (level in 0..4) {
            _uiState.value = _uiState.value.copy(fontSizeLevel = level)
            viewModelScope.launch {
                settingsDataStore.saveFontSizeLevel(level)
            }
        }
    }

    /**
     * 切换动态颜色
     */
    fun toggleDynamicColor() {
        val newValue = !_uiState.value.isDynamicColorEnabled
        _uiState.value = _uiState.value.copy(isDynamicColorEnabled = newValue)
        viewModelScope.launch {
            settingsDataStore.saveDynamicColorEnabled(newValue)
        }
    }

    /**
     * 设置配色方案
     * 选择配色时自动关闭动态颜色，确保配色能立即生效
     */
    fun setColorScheme(scheme: ColorSchemeType) {
        if (scheme.isRecommended) {
            applySeedColor(scheme, scheme.seedColor)
        }
    }

    /**
     * 使用用户从色环选择的种子色生成全局配色。
     */
    fun setCustomSeedColor(seedColor: Int) {
        applySeedColor(ColorSchemeType.CUSTOM, seedColor or (0xFF shl 24))
    }

    private fun applySeedColor(scheme: ColorSchemeType, seedColor: Int) {
        _uiState.value = _uiState.value.copy(
            colorScheme = scheme,
            themeSeedColor = seedColor,
            isDynamicColorEnabled = false
        )
        viewModelScope.launch {
            settingsDataStore.saveThemeSelection(scheme, seedColor)
        }
    }

    /**
     * 设置深色模式
     */
    fun setDarkMode(mode: DarkMode) {
        _uiState.value = _uiState.value.copy(darkMode = mode)
        viewModelScope.launch {
            settingsDataStore.saveDarkMode(mode)
        }
    }
}
