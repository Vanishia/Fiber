package com.bird.fiber.data.local

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import androidx.core.net.toUri
import com.bird.fiber.data.local.library.LibraryRepository
import com.bird.fiber.data.model.Attachment
import com.bird.fiber.data.model.FileError
import com.bird.fiber.data.model.FileResult
import com.bird.fiber.data.model.LibraryTarget
import com.bird.fiber.data.repository.AttachmentRepository
import com.bird.fiber.data.local.library.toTarget
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AttachmentRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val libraryRepository: LibraryRepository
) : AttachmentRepository {

    override suspend fun copyImage(
        sourceUri: String,
        target: LibraryTarget?
    ): FileResult<Attachment> = withContext(Dispatchers.IO) {
        val libraryTarget = target
            ?: libraryRepository.getActiveLibrary().firstOrNull()?.toTarget()
            ?: return@withContext FileResult.Error(FileError.Unknown("未选择笔记库，请先添加库"))
        val targetFolderUri = libraryTarget.folderUri

        val source = sourceUri.toUri()
        var createdFile: DocumentFile? = null
        try {
            val root = DocumentFile.fromTreeUri(context, targetFolderUri.toUri())
                ?: return@withContext FileResult.Error(FileError.NotFound(targetFolderUri))
            val attachments = root.findFile(ATTACHMENTS_DIRECTORY)
                ?: root.createDirectory(ATTACHMENTS_DIRECTORY)
                ?: return@withContext FileResult.Error(
                    FileError.IOFailed(targetFolderUri, IllegalStateException("无法创建附件目录"))
                )
            if (!attachments.isDirectory) {
                return@withContext FileResult.Error(
                    FileError.IOFailed(targetFolderUri, IllegalStateException("attachments 不是文件夹"))
                )
            }

            val mimeType = context.contentResolver.getType(source) ?: "image/jpeg"
            if (!mimeType.startsWith("image/")) {
                return@withContext FileResult.Error(FileError.Unknown("选择的文件不是图片"))
            }
            val extension = resolveExtension(source, mimeType)
            val fileName = generateFileName(extension)
            createdFile = attachments.createFile(mimeType, fileName)
                ?: return@withContext FileResult.Error(
                    FileError.IOFailed(targetFolderUri, IllegalStateException("无法创建图片文件"))
                )

            val copied = context.contentResolver.openInputStream(source)?.use { input ->
                context.contentResolver.openOutputStream(createdFile.uri, "wt")?.use { output ->
                    input.copyTo(output)
                    output.flush()
                    true
                }
            } ?: false

            if (!copied) {
                createdFile.delete()
                return@withContext FileResult.Error(FileError.IOFailed(sourceUri, IllegalStateException("无法复制图片")))
            }

            val actualFileName = createdFile.name ?: fileName
            FileResult.Success(
                Attachment(
                    displayName = actualFileName,
                    relativePath = "$ATTACHMENTS_DIRECTORY/$actualFileName",
                    uri = createdFile.uri.toString(),
                    libraryTarget = libraryTarget
                )
            )
        } catch (e: SecurityException) {
            createdFile?.delete()
            Timber.e(e, "AttachmentRepository: permission denied source=%s", sourceUri)
            FileResult.Error(FileError.PermissionDenied(sourceUri))
        } catch (e: Exception) {
            createdFile?.delete()
            Timber.e(e, "AttachmentRepository: copy failed source=%s", sourceUri)
            FileResult.Error(FileError.IOFailed(sourceUri, e))
        }
    }

    override fun resolveUri(markdownFileUri: String, relativePath: String): String? {
        val normalized = relativePath.removePrefix("./").replace('\\', '/')
        if (!normalized.startsWith("$ATTACHMENTS_DIRECTORY/") || ".." in normalized.split('/')) {
            return null
        }

        return try {
            val noteUri = markdownFileUri.toUri()
            val rootDocumentId = DocumentsContract.getTreeDocumentId(noteUri)
            val treeUri = DocumentsContract.buildTreeDocumentUri(noteUri.authority, rootDocumentId)
            var current = DocumentFile.fromTreeUri(context, treeUri) ?: return null
            normalized.split('/').forEach { segment ->
                current = current.findFile(segment) ?: return null
            }
            current.uri.toString()
        } catch (e: Exception) {
            Timber.w(e, "AttachmentRepository: resolve failed path=%s", relativePath)
            null
        }
    }

    private fun resolveExtension(sourceUri: Uri, mimeType: String): String {
        val displayName = context.contentResolver.query(
            sourceUri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
        val fromName = displayName?.substringAfterLast('.', "")?.lowercase()?.takeIf { it.matches(EXTENSION_PATTERN) }
        return fromName ?: MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "jpg"
    }

    private fun generateFileName(extension: String): String {
        val timestamp = LocalDateTime.now().format(FILE_NAME_TIME_FORMAT)
        val suffix = UUID.randomUUID().toString().take(6)
        return "$timestamp-$suffix.$extension"
    }

    companion object {
        const val ATTACHMENTS_DIRECTORY = "attachments"
        private val FILE_NAME_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")
        private val EXTENSION_PATTERN = Regex("[a-z0-9]{1,8}")
    }
}
