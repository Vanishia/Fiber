package com.bird.fiber.ui.screens.importing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bird.fiber.data.importing.ImportShareManager
import com.bird.fiber.data.importing.PendingImport
import com.bird.fiber.data.local.library.LibraryEntity
import com.bird.fiber.data.local.library.LibraryRepository
import com.bird.fiber.data.local.library.toTarget
import com.bird.fiber.data.model.FileResult
import com.bird.fiber.data.repository.AttachmentRepository
import com.bird.fiber.data.repository.FileRepository
import com.bird.fiber.domain.usecase.GenerateFileNameUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
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
    private val attachmentRepository: AttachmentRepository,
    private val generateFileName: GenerateFileNameUseCase,
    libraryRepository: LibraryRepository
) : ViewModel() {

    val pendingImport: StateFlow<PendingImport?> = importShareManager.pendingImport

    val libraries: StateFlow<List<LibraryEntity>> = libraryRepository.getAllLibraries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 保存结果提示（成功/失败文案） */
    private val _saveResult = MutableSharedFlow<String>()
    val saveResult: SharedFlow<String> = _saveResult.asSharedFlow()

    /**
     * 保存待导入内容到指定库
     *
     * 文本走 [FileRepository.createMarkdownFile] 直接写入；
     * 图片先用 [AttachmentRepository.copyImage] 拷入该库 attachments/，
     * 再创建一条时间戳命名的笔记引用它（与快速笔记的命名/引用约定一致）。
     * 无论成功失败都会清理缓存的临时图片
     */
    fun saveToLibrary(library: LibraryEntity) {
        val pending = pendingImport.value ?: return
        viewModelScope.launch {
            try {
                if (pending.imagePath != null) {
                    saveImageToLibrary(library, pending.imagePath)
                } else {
                    saveMarkdownToLibrary(library, pending)
                }
            } finally {
                pending.imagePath?.let { File(it).delete() }
                importShareManager.consume()
            }
        }
    }

    private suspend fun saveMarkdownToLibrary(library: LibraryEntity, pending: PendingImport) {
        when (val result = fileRepository.createMarkdownFile(
            target = library.toTarget(),
            fileName = pending.fileName,
            content = pending.content
        )) {
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

    private suspend fun saveImageToLibrary(library: LibraryEntity, imagePath: String) {
        val target = library.toTarget()
        // 缓存文件是应用私有路径，无权限问题，可直接作为 copyImage 的源
        val copyResult = attachmentRepository.copyImage(
            sourceUri = android.net.Uri.fromFile(File(imagePath)).toString(),
            target = target
        )
        if (copyResult !is FileResult.Success) {
            Timber.e("图片入库失败: ${(copyResult as? FileResult.Error)?.error}")
            _saveResult.emit("保存失败")
            return
        }
        val attachment = copyResult.data

        val noteResult = fileRepository.createMarkdownFile(
            target = target,
            fileName = generateFileName(),
            content = attachment.toMarkdown() + "\n"
        )
        when (noteResult) {
            is FileResult.Success -> {
                Timber.d("图片笔记导入成功: ${noteResult.data.uri} -> ${library.name}")
                _saveResult.emit("已保存到「${library.name}」")
            }
            is FileResult.Error -> {
                Timber.e("图片笔记创建失败: ${noteResult.error}")
                // 笔记没建成，删掉刚拷入的图片，避免产生孤儿附件
                attachmentRepository.delete(attachment.uri)
                _saveResult.emit("保存失败")
            }
            else -> Unit
        }
    }

    /** 放弃导入，清理缓存的临时图片 */
    fun dismiss() {
        pendingImport.value?.imagePath?.let { File(it).delete() }
        importShareManager.consume()
    }
}
