package com.bird.fiber.data.config

import androidx.compose.ui.unit.dp

/**
 * 编辑器配置
 *
 * 集中管理编辑器相关的配置值，避免硬编码
 */
object EditorConfig {

    /**
     * 默认是否进入预览模式
     *
     * true: 打开文件默认进入预览模式
     * false: 打开文件默认进入编辑模式
     */
    const val DEFAULT_PREVIEW_MODE = true

    /**
     * 编辑器内边距（dp）
     */
    val EDITOR_PADDING = 16.dp

    /**
     * 保存按钮加载指示器大小（dp）
     */
    val SAVE_BUTTON_LOADING_SIZE = 24.dp

    /**
     * 保存按钮加载指示器描边宽度（dp）
     */
    val SAVE_BUTTON_STROKE_WIDTH = 2.dp

    /**
     * 渲染超时时间（毫秒）
     *
     * Markdown 渲染的最大等待时间，超过此时间认为渲染失败
     */
    const val RENDER_TIMEOUT_MS = 5000L

    /**
     * 未保存确认对话框标题
     */
    const val UNSAVED_DIALOG_TITLE = "未保存的修改"

    /**
     * 未保存确认对话框消息
     */
    const val UNSAVED_DIALOG_MESSAGE = "您有未保存的修改，是否直接退出？"

    /**
     * 保存成功 Toast 消息
     */
    const val SAVE_SUCCESS_MESSAGE = "✔修改已保存ψ(｀∇´)ψ!"

    /**
     * 保存中提示文字
     */
    const val SAVING_TEXT = "保存中..."

    /**
     * 返回按钮描述
     */
    const val BACK_BUTTON_DESCRIPTION = "返回"

    /**
     * 编辑按钮描述
     */
    const val EDIT_BUTTON_DESCRIPTION = "编辑"

    /**
     * 预览按钮描述
     */
    const val PREVIEW_BUTTON_DESCRIPTION = "预览"

    /**
     * 保存按钮描述
     */
    const val SAVE_BUTTON_DESCRIPTION = "保存"
}
