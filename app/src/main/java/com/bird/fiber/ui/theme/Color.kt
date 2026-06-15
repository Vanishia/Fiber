package com.bird.fiber.ui.theme

import androidx.compose.ui.graphics.Color

// 成功/错误颜色 - 支持深色/浅色模式
val SuccessLight = Color(0xFF2E7D32)  // 深色绿色，适合浅色背景
val SuccessDark = Color(0xFF81C784)   // 浅色绿色，适合深色背景
val ErrorLight = Color(0xFFC62828)    // 深色红色，适合浅色背景
val ErrorDark = Color(0xFFEF5350)     // 浅色红色，适合深色背景

// ============================================
// Material You 风格完整配色方案
// ============================================

// 默认紫色 - Material 3 Baseline (Purple)
object DefaultColorScheme {
    val light = lightScheme(
        primary = Color(0xFF6750A4),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFEADDFF),
        onPrimaryContainer = Color(0xFF21005D),
        secondary = Color(0xFF625B71),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE8DEF8),
        onSecondaryContainer = Color(0xFF1D192B),
        tertiary = Color(0xFF7D5260),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFFD8E4),
        onTertiaryContainer = Color(0xFF31111D),
        surface = Color(0xFFFEF7FF),
        onSurface = Color(0xFF1D1B20),
        surfaceVariant = Color(0xFFE7E0EC),
        onSurfaceVariant = Color(0xFF49454F),
        outline = Color(0xFF79747E),
        outlineVariant = Color(0xFFCAC4D0),
        background = Color(0xFFFEF7FF),
        onBackground = Color(0xFF1D1B20),
    )

    val dark = darkScheme(
        primary = Color(0xFFD0BCFF),
        onPrimary = Color(0xFF381E72),
        primaryContainer = Color(0xFF4F378B),
        onPrimaryContainer = Color(0xFFEADDFF),
        secondary = Color(0xFFCCC2DC),
        onSecondary = Color(0xFF332D41),
        secondaryContainer = Color(0xFF4A4458),
        onSecondaryContainer = Color(0xFFE8DEF8),
        tertiary = Color(0xFFEFB8C8),
        onTertiary = Color(0xFF492532),
        tertiaryContainer = Color(0xFF633B48),
        onTertiaryContainer = Color(0xFFFFD8E4),
        surface = Color(0xFF141218),
        onSurface = Color(0xFFE6E0E9),
        surfaceVariant = Color(0xFF49454F),
        onSurfaceVariant = Color(0xFFCAC4D0),
        outline = Color(0xFF938F99),
        outlineVariant = Color(0xFF49454F),
        background = Color(0xFF141218),
        onBackground = Color(0xFFE6E0E9),
    )
}

// 樱花粉色 - Pink Tonal Palette
object SakuraColorScheme {
    val light = lightScheme(
        primary = Color(0xFFB71C5C),           // 深粉色
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFD9E2),  // 浅粉色容器
        onPrimaryContainer = Color(0xFF3E001E),
        secondary = Color(0xFF74565F),         // 柔和粉棕
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFFFD9E2),
        onSecondaryContainer = Color(0xFF2B151C),
        tertiary = Color(0xFF7C5635),          // 暖棕色
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFFDCC6),
        onTertiaryContainer = Color(0xFF2C1604),
        surface = Color(0xFFFFF8F8),           // 微粉白
        onSurface = Color(0xFF201A1B),
        surfaceVariant = Color(0xFFF2DDE2),    // 粉灰
        onSurfaceVariant = Color(0xFF514347),
        outline = Color(0xFF837377),
        outlineVariant = Color(0xFFD5C2C7),
        background = Color(0xFFFFF8F8),
        onBackground = Color(0xFF201A1B),
    )

    val dark = darkScheme(
        primary = Color(0xFFFFB1C8),           // 亮粉色
        onPrimary = Color(0xFF650033),
        primaryContainer = Color(0xFF8E004A),
        onPrimaryContainer = Color(0xFFFFD9E2),
        secondary = Color(0xFFE3BDC6),
        onSecondary = Color(0xFF422931),
        secondaryContainer = Color(0xFF5A3F47),
        onSecondaryContainer = Color(0xFFFFD9E2),
        tertiary = Color(0xFFEBBC9A),
        onTertiary = Color(0xFF462A14),
        tertiaryContainer = Color(0xFF5F4028),
        onTertiaryContainer = Color(0xFFFFDCC6),
        surface = Color(0xFF181113),           // 深粉黑
        onSurface = Color(0xFFECE0E1),
        surfaceVariant = Color(0xFF514347),
        onSurfaceVariant = Color(0xFFD5C2C7),
        outline = Color(0xFF9D8D91),
        outlineVariant = Color(0xFF514347),
        background = Color(0xFF181113),
        onBackground = Color(0xFFECE0E1),
    )
}

// 天空蓝色 - Blue Tonal Palette
object BlueColorScheme {
    val light = lightScheme(
        primary = Color(0xFF0061A4),           // 天空蓝
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFBFDBFF),  // 纯浅蓝容器 - 减少紫色
        onPrimaryContainer = Color(0xFF001D36),
        secondary = Color(0xFF536B7A),         // 灰蓝 - 更偏蓝
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFD0E4F5), // 纯蓝灰容器
        onSecondaryContainer = Color(0xFF101C2B),
        tertiary = Color(0xFF3A6A7A),          // 青蓝 - 替代紫灰
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFBDE9F5),
        onTertiaryContainer = Color(0xFF001F27),
        surface = Color(0xFFF8FBFF),           // 纯蓝白 - 减少紫调
        onSurface = Color(0xFF1A1C1E),
        surfaceVariant = Color(0xFFD8E3EB),    // 偏蓝的灰
        onSurfaceVariant = Color(0xFF42474E),
        outline = Color(0xFF72777F),
        outlineVariant = Color(0xFFC1C7CF),
        background = Color(0xFFF8FBFF),
        onBackground = Color(0xFF1A1C1E),
    )

    val dark = darkScheme(
        primary = Color(0xFF9ECAFF),           // 亮天蓝
        onPrimary = Color(0xFF003258),
        primaryContainer = Color(0xFF00497D),
        onPrimaryContainer = Color(0xFFBFDBFF),
        secondary = Color(0xFFB4C9D8),         // 偏蓝的灰
        onSecondary = Color(0xFF1F333D),
        secondaryContainer = Color(0xFF354A54),
        onSecondaryContainer = Color(0xFFD0E4F5),
        tertiary = Color(0xFFA3D5E0),          // 青蓝
        onTertiary = Color(0xFF003640),
        tertiaryContainer = Color(0xFF1D4D59),
        onTertiaryContainer = Color(0xFFBDE9F5),
        surface = Color(0xFF101418),           // 纯深蓝黑 - 减少紫调
        onSurface = Color(0xFFE0E3E8),
        surfaceVariant = Color(0xFF3A454E),    // 偏蓝的灰
        onSurfaceVariant = Color(0xFFBEC8CF),
        outline = Color(0xFF89929A),
        outlineVariant = Color(0xFF3A454E),
        background = Color(0xFF101418),
        onBackground = Color(0xFFE0E3E8),
    )
}

// 雅致灰色 - Neutral/Gray Tonal Palette
object GrayColorScheme {
    val light = lightScheme(
        primary = Color(0xFF5D5F61),           // 石墨灰
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFE2E2E5),  // 浅灰容器
        onPrimaryContainer = Color(0xFF1A1C1E),
        secondary = Color(0xFF5E5E61),         // 中灰
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE5E1E5),
        onSecondaryContainer = Color(0xFF1B1B1D),
        tertiary = Color(0xFF605E61),          // 暖灰
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFE5E1E4),
        onTertiaryContainer = Color(0xFF1C1B1D),
        surface = Color(0xFFFCFCFC),           // 纯白灰
        onSurface = Color(0xFF1A1C1E),
        surfaceVariant = Color(0xFFE2E2E5),
        onSurfaceVariant = Color(0xFF454749),
        outline = Color(0xFF757779),
        outlineVariant = Color(0xFFC5C6C9),
        background = Color(0xFFFCFCFC),
        onBackground = Color(0xFF1A1C1E),
    )

    val dark = darkScheme(
        primary = Color(0xFFC6C6CA),           // 银灰
        onPrimary = Color(0xFF2F3033),
        primaryContainer = Color(0xFF45474A),
        onPrimaryContainer = Color(0xFFE2E2E5),
        secondary = Color(0xFFC8C5C9),
        onSecondary = Color(0xFF313033),
        secondaryContainer = Color(0xFF474649),
        onSecondaryContainer = Color(0xFFE5E1E5),
        tertiary = Color(0xFFCAC5C9),
        onTertiary = Color(0xFF322F32),
        tertiaryContainer = Color(0xFF494649),
        onTertiaryContainer = Color(0xFFE5E1E4),
        surface = Color(0xFF131315),           // 深黑灰
        onSurface = Color(0xFFE5E2E5),
        surfaceVariant = Color(0xFF454749),
        onSurfaceVariant = Color(0xFFC5C6C9),
        outline = Color(0xFF8E9194),
        outlineVariant = Color(0xFF454749),
        background = Color(0xFF131315),
        onBackground = Color(0xFFE5E2E5),
    )
}

// 海水青色 - Teal/Ocean Tonal Palette
object OceanColorScheme {
    val light = lightScheme(
        primary = Color(0xFF006B6B),           // 深海青
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFBCECEB),  // 浅海青容器
        onPrimaryContainer = Color(0xFF002020),
        secondary = Color(0xFF4A6363),         // 灰青
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFCCE8E7),
        onSecondaryContainer = Color(0xFF051F1F),
        tertiary = Color(0xFF4A5F70),          // 蓝灰
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFD3E4F5),
        onTertiaryContainer = Color(0xFF0F1D29),
        surface = Color(0xFFF4FBFA),           // 微青白
        onSurface = Color(0xFF161D1D),
        surfaceVariant = Color(0xFFDAE5E4),
        onSurfaceVariant = Color(0xFF3F4948),
        outline = Color(0xFF6F7979),
        outlineVariant = Color(0xFFBEC9C8),
        background = Color(0xFFF4FBFA),
        onBackground = Color(0xFF161D1D),
    )

    val dark = darkScheme(
        primary = Color(0xFF9ECBCC),           // 亮海青
        onPrimary = Color(0xFF003738),
        primaryContainer = Color(0xFF004F50),
        onPrimaryContainer = Color(0xFFBCECEB),
        secondary = Color(0xFFB0CCCB),
        onSecondary = Color(0xFF1B3534),
        secondaryContainer = Color(0xFF324B4B),
        onSecondaryContainer = Color(0xFFCCE8E7),
        tertiary = Color(0xFFB9C9D9),
        onTertiary = Color(0xFF243440),
        tertiaryContainer = Color(0xFF3A4B57),
        onTertiaryContainer = Color(0xFFD3E4F5),
        surface = Color(0xFF0E1515),           // 深海青黑
        onSurface = Color(0xFFDDE4E3),
        surfaceVariant = Color(0xFF3F4948),
        onSurfaceVariant = Color(0xFFBEC9C8),
        outline = Color(0xFF899392),
        outlineVariant = Color(0xFF3F4948),
        background = Color(0xFF0E1515),
        onBackground = Color(0xFFDDE4E3),
    )
}

// 落叶黄色 - Amber/Yellow Tonal Palette
object AmberColorScheme {
    val light = lightScheme(
        primary = Color(0xFF845400),           // 琥珀色
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFDDB8),  // 浅琥珀容器
        onPrimaryContainer = Color(0xFF2A1700),
        secondary = Color(0xFF705D48),         // 棕黄
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFFCDFC0),
        onSecondaryContainer = Color(0xFF261A0A),
        tertiary = Color(0xFF53643E),          // 橄榄绿
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFD7E9B9),
        onTertiaryContainer = Color(0xFF121F03),
        surface = Color(0xFFFFFBFF),           // 暖白
        onSurface = Color(0xFF1F1B16),
        surfaceVariant = Color(0xFFF0E0D0),
        onSurfaceVariant = Color(0xFF4F4539),
        outline = Color(0xFF817567),
        outlineVariant = Color(0xFFD2C4B4),
        background = Color(0xFFFFFBFF),
        onBackground = Color(0xFF1F1B16),
    )

    val dark = darkScheme(
        primary = Color(0xFFFFB95D),           // 亮琥珀
        onPrimary = Color(0xFF462A00),
        primaryContainer = Color(0xFF643F00),
        onPrimaryContainer = Color(0xFFFFDDB8),
        secondary = Color(0xFFDEC3A5),
        onSecondary = Color(0xFF3E2E1A),
        secondaryContainer = Color(0xFF56442E),
        onSecondaryContainer = Color(0xFFFCDFC0),
        tertiary = Color(0xFFBBCD9F),
        onTertiary = Color(0xFF273513),
        tertiaryContainer = Color(0xFF3D4C28),
        onTertiaryContainer = Color(0xFFD7E9B9),
        surface = Color(0xFF16130F),           // 深棕黑
        onSurface = Color(0xFFEAE1D9),
        surfaceVariant = Color(0xFF4F4539),
        onSurfaceVariant = Color(0xFFD2C4B4),
        outline = Color(0xFF9B8F80),
        outlineVariant = Color(0xFF4F4539),
        background = Color(0xFF16130F),
        onBackground = Color(0xFFEAE1D9),
    )
}

// 辅助函数：构建完整的浅色 ColorScheme
private fun lightScheme(
    primary: Color,
    onPrimary: Color,
    primaryContainer: Color,
    onPrimaryContainer: Color,
    secondary: Color,
    onSecondary: Color,
    secondaryContainer: Color,
    onSecondaryContainer: Color,
    tertiary: Color,
    onTertiary: Color,
    tertiaryContainer: Color,
    onTertiaryContainer: Color,
    surface: Color,
    onSurface: Color,
    surfaceVariant: Color,
    onSurfaceVariant: Color,
    outline: Color,
    outlineVariant: Color,
    background: Color,
    onBackground: Color,
) = androidx.compose.material3.lightColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    secondary = secondary,
    onSecondary = onSecondary,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onSecondaryContainer,
    tertiary = tertiary,
    onTertiary = onTertiary,
    tertiaryContainer = tertiaryContainer,
    onTertiaryContainer = onTertiaryContainer,
    surface = surface,
    onSurface = onSurface,
    surfaceVariant = surfaceVariant,
    onSurfaceVariant = onSurfaceVariant,
    outline = outline,
    outlineVariant = outlineVariant,
    background = background,
    onBackground = onBackground,
    error = ErrorLight,
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    inversePrimary = primaryContainer,
    inverseSurface = surfaceVariant,
    inverseOnSurface = onSurfaceVariant,
    surfaceTint = primary,
)

// 辅助函数：构建完整的深色 ColorScheme
private fun darkScheme(
    primary: Color,
    onPrimary: Color,
    primaryContainer: Color,
    onPrimaryContainer: Color,
    secondary: Color,
    onSecondary: Color,
    secondaryContainer: Color,
    onSecondaryContainer: Color,
    tertiary: Color,
    onTertiary: Color,
    tertiaryContainer: Color,
    onTertiaryContainer: Color,
    surface: Color,
    onSurface: Color,
    surfaceVariant: Color,
    onSurfaceVariant: Color,
    outline: Color,
    outlineVariant: Color,
    background: Color,
    onBackground: Color,
) = androidx.compose.material3.darkColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    secondary = secondary,
    onSecondary = onSecondary,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onSecondaryContainer,
    tertiary = tertiary,
    onTertiary = onTertiary,
    tertiaryContainer = tertiaryContainer,
    onTertiaryContainer = onTertiaryContainer,
    surface = surface,
    onSurface = onSurface,
    surfaceVariant = surfaceVariant,
    onSurfaceVariant = onSurfaceVariant,
    outline = outline,
    outlineVariant = outlineVariant,
    background = background,
    onBackground = onBackground,
    error = ErrorDark,
    onError = Color.Black,
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    inversePrimary = onPrimaryContainer,
    inverseSurface = onSurfaceVariant,
    inverseOnSurface = surfaceVariant,
    surfaceTint = primary,
)