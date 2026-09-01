package com.bird.fiber.data.importing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 待导入的外部文件
 *
 * @param fileName 文件名（含后缀；图片导入时为图片名，仅用于对话框展示）
 * @param content Markdown 文本内容；图片导入时为空
 * @param imagePath 图片导入时，收到后立即拷入应用缓存的临时文件路径（防临时读权限失效）；
 *                  非图片导入为 null。消费或放弃后需删除该临时文件
 */
data class PendingImport(
    val fileName: String,
    val content: String,
    val imagePath: String? = null
)

/**
 * 外部导入暂存器
 *
 * MainActivity 收到其他应用打开/分享过来的 .md 文件后，
 * 读取内容暂存到这里；UI 层（选库对话框）消费后清除。
 * 进程内单例，随进程销毁即失效
 */
@Singleton
class ImportShareManager @Inject constructor() {

    private val _pendingImport = MutableStateFlow<PendingImport?>(null)
    val pendingImport: StateFlow<PendingImport?> = _pendingImport.asStateFlow()

    fun offer(import: PendingImport) {
        _pendingImport.value = import
    }

    fun consume() {
        _pendingImport.value = null
    }
}
