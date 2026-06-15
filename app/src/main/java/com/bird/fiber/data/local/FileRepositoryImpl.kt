package com.bird.fiber.data.local

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.bird.fiber.data.local.library.LibraryRepository
import com.bird.fiber.data.model.FileError
import com.bird.fiber.data.model.FileResult
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

    override val currentFolderUri: Flow<String?> = libraryRepository.getActiveLibrary()
        .map { it?.folderUri }

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
        folderUri: String,
        fileName: String,
        content: String
    ): FileResult<MarkdownFileMeta> = ioFileResult(
        target = folderUri,
        action = "create"
    ) {
        val finalFileName = ensureMarkdownExtension(fileName)
        val folderTreeUri = Uri.parse(folderUri)
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
            FileError.IOFailed(folderUri, IllegalStateException("无法创建文档"))
        )

        if (content.isNotEmpty()) {
            context.contentResolver.openOutputStream(newFileUri, "wt")?.use { outputStream ->
                outputStream.write(content.toByteArray())
                outputStream.flush()
            }
        }

        val metadata = queryFileMetadata(newFileUri, finalFileName)
        val activeLibrary = libraryRepository.getActiveLibrary().firstOrNull()
        if (activeLibrary != null) {
            fileIndexer.insertFile(
                contentResolver = context.contentResolver,
                libraryId = activeLibrary.id,
                rootFolderUri = activeLibrary.folderUri,
                fileUri = newFileUri.toString()
            )
        }

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
    }

    override suspend fun saveFileContent(fileUri: String, content: String): FileResult<Unit> = ioFileResult(
        target = fileUri,
        action = "save"
    ) {
        context.contentResolver.openOutputStream(Uri.parse(fileUri), "wt")?.use { outputStream ->
            outputStream.write(content.toByteArray())
            outputStream.flush()
        } ?: return@ioFileResult FileResult.Error(FileError.NotFound(fileUri))

        fileIndexer.updateFileAfterSave(fileUri, content)
        FileResult.Success(Unit)
    }

    override suspend fun deleteFile(fileUri: String): FileResult<Unit> = ioFileResult(
        target = fileUri,
        action = "delete"
    ) {
        DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(fileUri))
        fileIndexer.deleteFile(fileUri)
        FileResult.Success(Unit)
    }

    override suspend fun renameFile(fileUri: String, newName: String): FileResult<Unit> = ioFileResult(
        target = fileUri,
        action = "rename"
    ) {
        val finalFileName = ensureMarkdownExtension(newName)
        val renamedUri = DocumentsContract.renameDocument(
            context.contentResolver,
            Uri.parse(fileUri),
            finalFileName
        ) ?: return@ioFileResult FileResult.Error(
            FileError.IOFailed(fileUri, IllegalStateException("无法重命名文档，可能存在同名文件"))
        )

        fileIndexer.deleteFile(fileUri)
        libraryRepository.getActiveLibrary().firstOrNull()?.let { library ->
            fileIndexer.insertFile(
                contentResolver = context.contentResolver,
                libraryId = library.id,
                rootFolderUri = library.folderUri,
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
