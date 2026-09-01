package com.bird.fiber.ui.screens.importing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bird.fiber.data.importing.ImportShareManager
import com.bird.fiber.data.importing.PendingImport
import com.bird.fiber.data.local.library.LibraryEntity
import com.bird.fiber.data.local.library.LibraryRepository
import com.bird.fiber.data.local.library.toTarget
import com.bird.fiber.data.model.FileResult
import com.bird.fiber.data.repository.FileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * 外部文件导入 ViewModel
 *
 * 持有待导入文件（来自其他应用打开/分享），
 * 提供库列表，执行保存到所选库
 */
@HiltViewModel
class ImportViewModel @Inject constructor(
    private val importShareManager: ImportShareManager,
    private val fileRepository: FileRepository,
    libraryRepository: LibraryRepository
) : ViewModel() {

    val pendingImport: StateFlow<PendingImport?> = importShareManager.pendingImport

    val libraries: StateFlow<List<LibraryEntity>> = libraryRepository.getAllLibraries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 保存结果提示（成功/失败文案） */
    private val _saveResult = MutableSharedFlow<String>()
    val saveResult: SharedFlow<String> = _saveResult.asSharedFlow()

    /**
     * 保存待导入文件到指定库
     *
     * 复用 [FileRepository.createMarkdownFile]：同名文件由 SAF 自动改名，
     * 保存同时写入索引，若目标是当前库列表会立即出现该文件
     */
    fun saveToLibrary(library: LibraryEntity) {
        val pending = pendingImport.value ?: return
        viewModelScope.launch {
            val result = fileRepository.createMarkdownFile(
                target = library.toTarget(),
                fileName = pending.fileName,
                content = pending.content
            )
            importShareManager.consume()
            when (result) {
                is FileResult.Success -> {
                    Timber.d("导入成功: ${result.data.uri} -> ${library.name}")
                    _saveResult.emit("已保存到「${library.name}」")
                }
                is FileResult.Error -> {
                    Timber.e("导入失败: ${result.error}")
                    _saveResult.emit("保存失败")
                }
                else -> Unit
            }
        }
    }

    /** 放弃导入 */
    fun dismiss() {
        importShareManager.consume()
    }
}
