package com.bird.fiber.data.local

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.bird.fiber.data.local.library.LibraryRepository
import com.bird.fiber.data.local.library.toTarget
import com.bird.fiber.data.model.FileError
import com.bird.fiber.data.model.FileResult
import com.bird.fiber.data.model.LibraryTarget
import com.bird.fiber.data.model.MarkdownFileMeta
import com.bird.fiber.data.repository.FileRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val libraryRepository: LibraryRepository,
    private val fileIndexer: FileIndexer
) : FileRepository {

    companion object {
        private const val MARKDOWN_EXTENSION = ".md"
    }

    override val currentLibraryTarget: Flow<LibraryTarget?> = libraryRepository.getActiveLibrary()
        .map { it?.toTarget() }

    override suspend fun selectRootFolder(): FileResult<String> = withContext(Dispatchers.IO) {
        FileResult.Error(FileError.Unknown("需要通过 Activity 启动文件夹选择器"))
    }

    override suspend fun readFileContent(fileUri: String): FileResult<String> = ioFileResult(
        target = fileUri,
        action = "read"
    ) {
        Timber.d("FileRepository: reading file=%s", fileUri)

        val content = StringBuilder()
        context.contentResolver.openInputStream(Uri.parse(fileUri))?.use { inputStream ->
            BufferedReader(inputStream.reader()).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    content.append(line).append("\n")
                }
            }
        } ?: return@ioFileResult FileResult.Error(FileError.NotFound(fileUri))

        FileResult.Success(content.toString())
    }

    override suspend fun createMarkdownFile(
        target: LibraryTarget,
        fileName: String,
        content: String
    ): FileResult<MarkdownFileMeta> = ioFileResult(
        target = target.folderUri,
        action = "create"
    ) {
        val finalFileName = ensureMarkdownExtension(fileName)
        val folderTreeUri = Uri.parse(target.folderUri)
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(
            folderTreeUri,
            DocumentsContract.getTreeDocumentId(folderTreeUri)
        )

        val newFileUri = DocumentsContract.createDocument(
            context.contentResolver,
            documentUri,
            "text/markdown",
            finalFileName
        ) ?: return@ioFileResult FileResult.Error(
            FileError.IOFailed(target.folderUri, IllegalStateException("无法创建文档"))
        )

        try {
            if (content.isNotEmpty()) {
                val outputStream = context.contentResolver.openOutputStream(newFileUri, "wt")
                    ?: throw IllegalStateException("无法写入新建文档")
                outputStream.use {
                    it.write(content.toByteArray())
                    it.flush()
                }
            }

            val indexed = fileIndexer.insertFile(
                contentResolver = context.contentResolver,
                libraryId = target.libraryId,
                rootFolderUri = target.folderUri,
                fileUri = newFileUri.toString()
            )
            if (!indexed) {
                throw IllegalStateException("无法写入文件索引")
            }

            val metadata = queryFileMetadata(newFileUri, finalFileName)
            FileResult.Success(
                MarkdownFileMeta(
                    uri = newFileUri.toString(),
                    name = metadata.displayName.removeSuffix(MARKDOWN_EXTENSION),
                    path = metadata.displayName,
                    lastModified = metadata.lastModified,
                    size = metadata.size,
                    preview = ""
                )
            )
        } catch (e: Exception) {
            fileIndexer.deleteFile(newFileUri.toString())
            runCatching {
                DocumentsContract.deleteDocument(context.contentResolver, newFileUri)
            }.onFailure { cleanupError ->
                Timber.e(cleanupError, "FileRepository: cleanup failed file=%s", newFileUri)
            }
            throw e
        }
    }

    override suspend fun saveFileContent(fileUri: String, content: String): FileResult<Unit> = ioFileResult(
        target = fileUri,
        action = "save"
    ) {
        context.contentResolver.openOutputStream(Uri.parse(fileUri), "wt")?.use { outputStream ->
            outputStream.write(content.toByteArray())
            outputStream.flush()
        } ?: return@ioFileResult FileResult.Error(FileError.NotFound(fileUri))

        val actualSize = queryFileMetadata(Uri.parse(fileUri), "").size
        fileIndexer.updateFileAfterSave(fileUri, content, actualSize)
        FileResult.Success(Unit)
    }

    override suspend fun deleteFile(fileUri: String): FileResult<Unit> = ioFileResult(
        target = fileUri,
        action = "delete"
    ) {
        val deleted = DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(fileUri))
        if (!deleted) {
            // 底层文件没删掉时保留索引，避免列表和真实文件状态不一致
            return@ioFileResult FileResult.Error(
                FileError.IOFailed(fileUri, IllegalStateException("无法删除文档"))
            )
        }
        fileIndexer.deleteFile(fileUri)
        FileResult.Success(Unit)
    }

    override suspend fun renameFile(fileUri: String, newName: String): FileResult<Unit> = ioFileResult(
        target = fileUri,
        action = "rename"
    ) {
        val finalFileName = ensureMarkdownExtension(newName)
        // 重命名前先记录文件当前索引所属的库，避免跨库操作时把索引挂到活动库
        val indexedLibraryId = fileIndexer.getIndexedLibraryId(fileUri)
        val renamedUri = DocumentsContract.renameDocument(
            context.contentResolver,
            Uri.parse(fileUri),
            finalFileName
        ) ?: return@ioFileResult FileResult.Error(
            FileError.IOFailed(fileUri, IllegalStateException("无法重命名文档，可能存在同名文件"))
        )

        fileIndexer.deleteFile(fileUri)
        val library = indexedLibraryId?.let { libraryRepository.getLibraryById(it) }
            ?: libraryRepository.getActiveLibrary().firstOrNull()
        library?.let {
            fileIndexer.insertFile(
                contentResolver = context.contentResolver,
                libraryId = it.id,
                rootFolderUri = it.folderUri,
                fileUri = renamedUri.toString()
            )
        }

        FileResult.Success(Unit)
    }

    override suspend fun hasSelectedFolder(): Boolean {
        return libraryRepository.getActiveLibrary().firstOrNull() != null
    }

    private suspend fun <T> ioFileResult(
        target: String,
        action: String,
        block: suspend () -> FileResult<T>
    ): FileResult<T> = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: SecurityException) {
            Timber.e(e, "FileRepository: %s failed by permission target=%s", action, target)
            FileResult.Error(FileError.PermissionDenied(target))
        } catch (e: Exception) {
            Timber.e(e, "FileRepository: %s failed target=%s", action, target)
            FileResult.Error(FileError.IOFailed(target, e))
        }
    }

    private fun ensureMarkdownExtension(fileName: String): String {
        return if (fileName.endsWith(MARKDOWN_EXTENSION)) fileName else "$fileName$MARKDOWN_EXTENSION"
    }

    private fun queryFileMetadata(fileUri: Uri, fallbackDisplayName: String): FileMetadata {
        context.contentResolver.query(
            fileUri,
            arrayOf(
                OpenableColumns.DISPLAY_NAME,
                OpenableColumns.SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            ),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val displayName = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                val size = cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE))
                val lastModified = cursor.getLong(
                    cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                )
                return FileMetadata(displayName, size, lastModified)
            }
        }

        return FileMetadata(
            displayName = fallbackDisplayName,
            size = 0L,
            lastModified = System.currentTimeMillis()
        )
    }
}

private data class FileMetadata(
    val displayName: String,
    val size: Long,
    val lastModified: Long
)
