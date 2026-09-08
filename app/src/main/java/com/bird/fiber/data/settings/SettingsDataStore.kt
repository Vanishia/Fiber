package com.bird.fiber.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bird.fiber.ui.screens.settings.ColorSchemeType
import com.bird.fiber.ui.screens.settings.DarkMode
import com.bird.fiber.ui.screens.settings.ListViewMode
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
        private val THEME_SEED_COLOR = intPreferencesKey("theme_seed_color")
        private val DARK_MODE = intPreferencesKey("dark_mode")
        private val LIST_VIEW_MODE = intPreferencesKey("list_view_mode")
        private val UPDATE_NOTIFICATION_ENABLED = booleanPreferencesKey("update_notification_enabled")
        private val LINE_HEIGHT_PERCENT = intPreferencesKey("line_height_percent")
        private val QUICK_SEARCH_KEYWORDS = stringPreferencesKey("quick_search_keywords")

        /** 快捷入口默认关键词 */
        val DEFAULT_QUICK_SEARCH_KEYWORDS = listOf("日报", "TODO", "会议", "灵感", "项目")
    }

    /**
     * 获取设置流
     */
    val settingsFlow: Flow<SettingsUiState> = dataStore.data.map { preferences ->
        val storedScheme = ColorSchemeType.entries.getOrNull(
            preferences[COLOR_SCHEME] ?: ColorSchemeType.BLUE.ordinal
        )
        // 默认紫已不再作为推荐项；旧设置无感迁移到新的默认蓝色。
        val colorScheme = storedScheme
            ?.takeUnless { it == ColorSchemeType.DEFAULT }
            ?: ColorSchemeType.BLUE

        SettingsUiState(
            fontSizeLevel = preferences[FONT_SIZE_LEVEL] ?: 2,
            isDynamicColorEnabled = preferences[DYNAMIC_COLOR_ENABLED] ?: true,
            colorScheme = colorScheme,
            themeSeedColor = preferences[THEME_SEED_COLOR] ?: colorScheme.seedColor,
            darkMode = DarkMode.entries.getOrNull(
                preferences[DARK_MODE] ?: 0
            ) ?: DarkMode.SYSTEM,
            listViewMode = ListViewMode.entries.getOrNull(
                preferences[LIST_VIEW_MODE] ?: 0
            ) ?: ListViewMode.LIST,
            updateNotificationEnabled = preferences[UPDATE_NOTIFICATION_ENABLED] ?: true,
            lineHeightPercent = preferences[LINE_HEIGHT_PERCENT] ?: 100
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
     * 原子保存主题选择。选择种子色时同时关闭系统动态颜色，避免状态短暂回跳。
     */
    suspend fun saveThemeSelection(scheme: ColorSchemeType, seedColor: Int) {
        dataStore.edit { preferences ->
            preferences[DYNAMIC_COLOR_ENABLED] = false
            preferences[COLOR_SCHEME] = scheme.ordinal
            preferences[THEME_SEED_COLOR] = seedColor
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

    /**
     * 保存主页列表视图模式（条目/瀑布流）
     */
    suspend fun saveListViewMode(mode: ListViewMode) {
        dataStore.edit { preferences ->
            preferences[LIST_VIEW_MODE] = mode.ordinal
        }
    }

    /**
     * 保存更新通知开关：开启时检测到新版本会在侧边栏提示
     */
    suspend fun saveUpdateNotificationEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[UPDATE_NOTIFICATION_ENABLED] = enabled
        }
    }

    /**
     * 保存正文行距缩放百分比（在编辑器/条目各自预设行距基础上缩放）
     */
    suspend fun saveLineHeightPercent(percent: Int) {
        dataStore.edit { preferences ->
            preferences[LINE_HEIGHT_PERCENT] = percent
        }
    }

    /**
     * 保存搜索页快捷入口关键词列表（换行分隔存储，避免关键词自身含逗号时解析出错）
     *
     * 传入空列表时清除设置，展示侧回落到默认关键词
     */
    suspend fun saveQuickSearchKeywords(keywords: List<String>) {
        dataStore.edit { preferences ->
            if (keywords.isEmpty()) {
                preferences.remove(QUICK_SEARCH_KEYWORDS)
            } else {
                preferences[QUICK_SEARCH_KEYWORDS] = keywords.joinToString("\n")
            }
        }
    }

    /**
     * 获取搜索页快捷入口关键词列表，未设置时返回默认列表
     */
    val quickSearchKeywordsFlow: Flow<List<String>> = dataStore.data.map { preferences ->
        preferences[QUICK_SEARCH_KEYWORDS]
            ?.split("\n")
            ?.filter { it.isNotBlank() }
            ?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_QUICK_SEARCH_KEYWORDS
    }
}
