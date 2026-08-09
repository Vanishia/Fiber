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
            val sourceDisplayName = queryDisplayName(source)
            val extension = resolveExtension(sourceDisplayName, mimeType)
            val fileName = AttachmentFileNameGenerator.generate(sourceDisplayName, extension)
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

    override suspend fun delete(uri: String): FileResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val deleted = DocumentsContract.deleteDocument(context.contentResolver, uri.toUri())
            if (deleted) {
                FileResult.Success(Unit)
            } else {
                FileResult.Error(FileError.IOFailed(uri, IllegalStateException("无法删除附件")))
            }
        } catch (e: SecurityException) {
            Timber.e(e, "AttachmentRepository: delete permission denied uri=%s", uri)
            FileResult.Error(FileError.PermissionDenied(uri))
        } catch (e: Exception) {
            Timber.e(e, "AttachmentRepository: delete failed uri=%s", uri)
            FileResult.Error(FileError.IOFailed(uri, e))
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

    private fun queryDisplayName(sourceUri: Uri): String? {
        return context.contentResolver.query(
            sourceUri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    private fun resolveExtension(displayName: String?, mimeType: String): String {
        val fromName = displayName?.substringAfterLast('.', "")?.lowercase()?.takeIf { it.matches(EXTENSION_PATTERN) }
        return fromName ?: MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "jpg"
    }

    companion object {
        const val ATTACHMENTS_DIRECTORY = "attachments"
        private val EXTENSION_PATTERN = Regex("[a-z0-9]{1,8}")
    }
}

internal object AttachmentFileNameGenerator {
    fun generate(
        displayName: String?,
        extension: String,
        timestamp: LocalDateTime = LocalDateTime.now(),
        suffix: String = UUID.randomUUID().toString().take(4)
    ): String {
        val originalBaseName = displayName
            ?.substringBeforeLast('.', displayName)
            ?.replace(INVALID_FILE_NAME_CHARS, "-")
            ?.trim(' ', '.', '-')
            ?.take(MAX_ORIGINAL_NAME_LENGTH)
            ?.takeIf { it.isNotBlank() }
            ?: "image"
        val formattedTimestamp = timestamp.format(FILE_NAME_TIME_FORMAT)
        return "$originalBaseName-$formattedTimestamp-$suffix.$extension"
    }

    private const val MAX_ORIGINAL_NAME_LENGTH = 80
    private val FILE_NAME_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    private val INVALID_FILE_NAME_CHARS = Regex("[\\\\/:*?\"<>|]")
}
