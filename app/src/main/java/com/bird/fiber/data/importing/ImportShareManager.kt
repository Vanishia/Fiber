package com.bird.fiber.data.importing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 待导入的外部 Markdown 文件
 *
 * @param fileName 文件名（含 .md 后缀，保存时由 FileRepository 保证后缀）
 * @param content 文件文本内容
 */
data class PendingImport(
    val fileName: String,
    val content: String
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
