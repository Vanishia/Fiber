package com.bird.fiber.ui.screens.editor

/**
 * 编辑与预览视图共用的正文行距倍数（相对字号）。
 * 编辑视图（BasicTextField lineHeight）与预览视图（TextView setLineHeight）
 * 都按"精确行高 = 字号 × 倍数"设置，保证两种模式排版一致
 */
internal const val BODY_LINE_HEIGHT_MULTIPLIER = 1.25f
