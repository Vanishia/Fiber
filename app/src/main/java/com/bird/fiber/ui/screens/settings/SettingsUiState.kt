package com.bird.fiber.ui.screens.settings

/**
 * 配色方案类型
 */
enum class ColorSchemeType(val label: String) {
    DEFAULT("默认紫"),
    SAKURA("樱花粉"),
    BLUE("天空蓝"),
    OCEAN("海水青"),
    GRAY("雅致灰"),
    AMBER("落叶黄")
}

/**
 * 深色模式设置
 */
enum class DarkMode {
    SYSTEM,  // 跟随系统
    LIGHT,   // 始终浅色
    DARK     // 始终深色
}

/**
 * 设置页面 UI 状态
 *
 * @property fontSizeLevel 字体大小级别（0-4 对应 80%, 90%, 100%, 110%, 120%）
 * @property isDynamicColorEnabled 是否启用动态颜色（Android 12+）
 * @property colorScheme 配色方案
 * @property darkMode 深色模式设置
 */
data class SettingsUiState(
    val fontSizeLevel: Int = 2,  // 默认 100%
    val isDynamicColorEnabled: Boolean = true,
    val colorScheme: ColorSchemeType = ColorSchemeType.DEFAULT,
    val darkMode: DarkMode = DarkMode.SYSTEM
) {
    companion object {
        val DEFAULT = SettingsUiState()

        // 字体大小级别对应的百分比
        val FONT_SIZE_PERCENTAGES = listOf(80, 90, 100, 110, 120)
    }

    /**
     * 获取当前字体大小百分比
     */
    fun getFontSizePercent(): Int = FONT_SIZE_PERCENTAGES.getOrElse(fontSizeLevel) { 100 }
}
