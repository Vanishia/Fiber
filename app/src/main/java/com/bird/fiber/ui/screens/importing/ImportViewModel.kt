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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    /** 保存失败提示 */
    private val _saveResult = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val saveResult: SharedFlow<String> = _saveResult.asSharedFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    /** 等待打开的文件；前台页面完成导航后确认，避免后台保存成功时丢失事件。 */
    private val _openEditor = MutableStateFlow<String?>(null)
    val openEditor: StateFlow<String?> = _openEditor.asStateFlow()

    fun onEditorOpened(fileUri: String) {
        _openEditor.compareAndSet(fileUri, null)
    }

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
        // 必须在 launch 之前占用，防止同一帧连续点击创建多个文件、重复导航。
        if (!_isSaving.compareAndSet(false, true)) return
        viewModelScope.launch {
            val fileUri = try {
                if (pending.imagePath != null) {
                    saveImageToLibrary(library, pending.imagePath)
                } else {
                    saveMarkdownToLibrary(library, pending)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "外部文件导入失败")
                _saveResult.emit("保存失败")
                null
            } finally {
                try {
                    pending.imagePath?.let { path ->
                        withContext(NonCancellable + Dispatchers.IO) { File(path).delete() }
                    }
                } finally {
                    importShareManager.consume(pending)
                    _isSaving.value = false
                }
            }
            // 先关闭选库对话框、结束保存，再允许前台导航。
            if (fileUri != null) _openEditor.value = fileUri
        }
    }

    private suspend fun saveMarkdownToLibrary(library: LibraryEntity, pending: PendingImport): String? {
        return when (val result = fileRepository.createMarkdownFile(
            target = library.toTarget(),
            fileName = pending.fileName,
            content = pending.content
        )) {
            is FileResult.Success -> {
                Timber.d("导入成功: ${result.data.uri} -> ${library.name}")
                result.data.uri
            }
            is FileResult.Error -> {
                Timber.e("导入失败: ${result.error}")
                _saveResult.emit("保存失败")
                null
            }
            else -> null
        }
    }

    private suspend fun saveImageToLibrary(library: LibraryEntity, imagePath: String): String? {
        val target = library.toTarget()
        // 缓存文件是应用私有路径，无权限问题，可直接作为 copyImage 的源
        val copyResult = attachmentRepository.copyImage(
            sourceUri = android.net.Uri.fromFile(File(imagePath)).toString(),
            target = target
        )
        if (copyResult !is FileResult.Success) {
            Timber.e("图片入库失败: ${(copyResult as? FileResult.Error)?.error}")
            _saveResult.emit("保存失败")
            return null
        }
        val attachment = copyResult.data

        val noteResult = fileRepository.createMarkdownFile(
            target = target,
            fileName = generateFileName(),
            content = attachment.toMarkdown() + "\n"
        )
        return when (noteResult) {
            is FileResult.Success -> {
                Timber.d("图片笔记导入成功: ${noteResult.data.uri} -> ${library.name}")
                noteResult.data.uri
            }
            is FileResult.Error -> {
                Timber.e("图片笔记创建失败: ${noteResult.error}")
                // 笔记没建成，删掉刚拷入的图片，避免产生孤儿附件
                attachmentRepository.delete(attachment.uri)
                _saveResult.emit("保存失败")
                null
            }
            else -> null
        }
    }

    /** 放弃导入，清理缓存的临时图片 */
    fun dismiss() {
        if (_isSaving.value) return
        val pending = pendingImport.value ?: return
        importShareManager.consume(pending)
        pending.imagePath?.let { path ->
            viewModelScope.launch(Dispatchers.IO) { File(path).delete() }
        }
    }
}
