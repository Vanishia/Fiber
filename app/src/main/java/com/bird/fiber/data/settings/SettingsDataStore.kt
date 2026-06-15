package com.bird.fiber.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bird.fiber.ui.screens.settings.ColorSchemeType
import com.bird.fiber.ui.screens.settings.DarkMode
import com.bird.fiber.ui.screens.settings.SettingsUiState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * 设置数据存储
 *
 * 使用 DataStore 持久化保存用户设置
 */
@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    companion object {
        private val FONT_SIZE_LEVEL = intPreferencesKey("font_size_level")
        private val DYNAMIC_COLOR_ENABLED = booleanPreferencesKey("dynamic_color_enabled")
        private val COLOR_SCHEME = intPreferencesKey("color_scheme")
        private val DARK_MODE = intPreferencesKey("dark_mode")
    }

    /**
     * 获取设置流
     */
    val settingsFlow: Flow<SettingsUiState> = dataStore.data.map { preferences ->
        SettingsUiState(
            fontSizeLevel = preferences[FONT_SIZE_LEVEL] ?: 2,
            isDynamicColorEnabled = preferences[DYNAMIC_COLOR_ENABLED] ?: true,
            colorScheme = ColorSchemeType.entries.getOrNull(
                preferences[COLOR_SCHEME] ?: 0
            ) ?: ColorSchemeType.DEFAULT,
            darkMode = DarkMode.entries.getOrNull(
                preferences[DARK_MODE] ?: 0
            ) ?: DarkMode.SYSTEM
        )
    }

    /**
     * 保存字体大小级别
     */
    suspend fun saveFontSizeLevel(level: Int) {
        dataStore.edit { preferences ->
            preferences[FONT_SIZE_LEVEL] = level
        }
    }

    /**
     * 保存动态颜色开关
     */
    suspend fun saveDynamicColorEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[DYNAMIC_COLOR_ENABLED] = enabled
        }
    }

    /**
     * 保存配色方案
     */
    suspend fun saveColorScheme(scheme: ColorSchemeType) {
        dataStore.edit { preferences ->
            preferences[COLOR_SCHEME] = scheme.ordinal
        }
    }

    /**
     * 保存深色模式
     */
    suspend fun saveDarkMode(mode: DarkMode) {
        dataStore.edit { preferences ->
            preferences[DARK_MODE] = mode.ordinal
        }
    }
}
