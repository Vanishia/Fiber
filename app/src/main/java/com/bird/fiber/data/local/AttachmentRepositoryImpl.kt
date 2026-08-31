package com.bird.fiber.data.local

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import androidx.core.net.toUri
import com.bird.fiber.data.local.library.LibraryRepository
import com.bird.fiber.data.local.library.MarkdownFileDao
import com.bird.fiber.data.model.Attachment
import com.bird.fiber.data.model.AttachmentDeletionSummary
import com.bird.fiber.data.model.AttachmentReference
import com.bird.fiber.data.model.FileError
import com.bird.fiber.data.model.FileResult
import com.bird.fiber.data.model.LibraryTarget
import com.bird.fiber.data.model.ManagedAttachment
import com.bird.fiber.data.repository.AttachmentRepository
import com.bird.fiber.data.local.library.toTarget
import com.bird.fiber.utils.MarkdownUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AttachmentRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val libraryRepository: LibraryRepository,
    private val markdownFileDao: MarkdownFileDao
) : AttachmentRepository {

    // 附件相对路径解析缓存：SAF DocumentFile 逐级 findFile 代价高，
    // 预览每次重渲染都会对同一批图片路径全量解析一遍，这里按笔记缓存结果
    // （空串表示解析失败的负缓存）；附件增删时整体清空，避免脏数据
    private val resolveUriCache = ConcurrentHashMap<String, String>()

    // attachments 目录清单缓存（key 为库根 treeUri，value 为 文件名 -> 文件 uri）：
    // 一次 listFiles 即可解析整篇笔记的全部平铺附件，
    // 避免每张图片各自逐级 findFile，显著加快含图笔记的首次渲染
    private val attachmentListingCache = ConcurrentHashMap<String, Map<String, String>>()

    private fun clearResolveCaches() {
        resolveUriCache.clear()
        attachmentListingCache.clear()
    }

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
            clearResolveCaches()
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
                clearResolveCaches()
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

    override suspend fun listForLibrary(
        libraryId: String
    ): FileResult<List<ManagedAttachment>> = withContext(Dispatchers.IO) {
        try {
            val library = libraryRepository.getLibraryById(libraryId)
                ?: return@withContext FileResult.Error(FileError.NotFound(libraryId))
            val root = DocumentFile.fromTreeUri(context, library.folderUri.toUri())
                ?: return@withContext FileResult.Error(FileError.NotFound(library.folderUri))
            val attachmentsDirectory = root.findFile(ATTACHMENTS_DIRECTORY)
                ?: return@withContext FileResult.Success(emptyList())
            if (!attachmentsDirectory.isDirectory) {
                return@withContext FileResult.Error(
                    FileError.IOFailed(
                        library.folderUri,
                        IllegalStateException("attachments 不是文件夹")
                    )
                )
            }

            val attachments = attachmentsDirectory.listFiles().mapNotNull { file ->
                if (!file.isFile) return@mapNotNull null
                val name = file.name ?: return@mapNotNull null
                val relativePath = "$ATTACHMENTS_DIRECTORY/$name"
                val dimensions = readImageDimensions(file.uri)
                val mimeType = file.type
                if (mimeType?.startsWith("image/") != true && dimensions.first <= 0) {
                    return@mapNotNull null
                }

                ManagedAttachment(
                    displayName = name,
                    relativePath = relativePath,
                    uri = file.uri.toString(),
                    mimeType = mimeType,
                    size = file.length(),
                    lastModified = file.lastModified(),
                    width = dimensions.first,
                    height = dimensions.second,
                    referencedBy = emptyList()
                )
            }.sortedWith(
                compareByDescending<ManagedAttachment> { it.lastModified }
                    .thenBy { it.displayName.lowercase() }
            )

            FileResult.Success(attachments)
        } catch (e: SecurityException) {
            Timber.e(e, "AttachmentRepository: list permission denied library=%s", libraryId)
            FileResult.Error(FileError.PermissionDenied(libraryId))
        } catch (e: Exception) {
            Timber.e(e, "AttachmentRepository: list failed library=%s", libraryId)
            FileResult.Error(FileError.IOFailed(libraryId, e))
        }
    }

    override suspend fun loadReferencesForLibrary(
        libraryId: String,
        attachments: List<ManagedAttachment>
    ): FileResult<Map<String, List<AttachmentReference>>> = withContext(Dispatchers.IO) {
        if (attachments.isEmpty()) return@withContext FileResult.Success(emptyMap())

        try {
            // 每个附件的旧式引用正则只编译一次
            val matchers = attachments.map { attachment ->
                AttachmentReferenceMatcher(
                    attachment = attachment,
                    legacyPattern = legacyReferencePattern(attachment.relativePath)
                )
            }
            val references = attachments.associate { it.uri to mutableListOf<AttachmentReference>() }

            // 笔记全文已由 FileIndexer 同步进索引库（content_text），
            // 直接查库做内存匹配，避免遍历文件系统逐篇读取；
            // content_text 为空的行（读取失败/空文件）查不出来，与旧逻辑读失败跳过行为一致
            markdownFileDao.getImageNoteContentsByLibrary(libraryId).forEach { file ->
                val content = file.contentText
                val destinations = MarkdownUtils.extractImageDestinations(content)
                    .mapNotNull(::normalizeAttachmentPath)
                    .toSet()
                matchers.forEach { matcher ->
                    if (matcher.attachment.relativePath in destinations ||
                        matcher.legacyPattern.containsMatchIn(content)
                    ) {
                        references.getValue(matcher.attachment.uri).add(
                            AttachmentReference(fileUri = file.uri, fileName = file.name)
                        )
                    }
                }
            }

            FileResult.Success(references)
        } catch (e: SecurityException) {
            Timber.e(e, "AttachmentRepository: references permission denied library=%s", libraryId)
            FileResult.Error(FileError.PermissionDenied(libraryId))
        } catch (e: Exception) {
            Timber.e(e, "AttachmentRepository: references failed library=%s", libraryId)
            FileResult.Error(FileError.IOFailed(libraryId, e))
        }
    }

    override suspend fun deleteOrphans(
        libraryId: String,
        uris: Set<String>
    ): FileResult<AttachmentDeletionSummary> = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) {
            return@withContext FileResult.Success(AttachmentDeletionSummary(0, 0, 0))
        }

        when (val currentResult = listForLibrary(libraryId)) {
            is FileResult.Error -> FileResult.Error(currentResult.error)
            is FileResult.Loading -> FileResult.Loading
            is FileResult.Success -> {
                when (val referencesResult = loadReferencesForLibrary(libraryId, currentResult.data)) {
                    is FileResult.Error -> FileResult.Error(referencesResult.error)
                    is FileResult.Loading -> FileResult.Loading
                    is FileResult.Success -> {
                        val requested = currentResult.data.filter { it.uri in uris }
                        var deletedCount = 0
                        var skippedReferencedCount = 0
                        var failedCount = uris.size - requested.size

                        requested.forEach { attachment ->
                            if (referencesResult.data[attachment.uri].orEmpty().isNotEmpty()) {
                                skippedReferencedCount++
                                return@forEach
                            }

                            try {
                                if (DocumentsContract.deleteDocument(context.contentResolver, attachment.uri.toUri())) {
                                    deletedCount++
                                } else {
                                    failedCount++
                                }
                            } catch (e: Exception) {
                                failedCount++
                                Timber.e(e, "AttachmentRepository: orphan delete failed uri=%s", attachment.uri)
                            }
                        }

                        if (deletedCount > 0) {
                            clearResolveCaches()
                        }
                        FileResult.Success(
                            AttachmentDeletionSummary(
                                deletedCount = deletedCount,
                                skippedReferencedCount = skippedReferencedCount,
                                failedCount = failedCount
                            )
                        )
                    }
                }
            }
        }
    }

    override fun resolveUri(markdownFileUri: String, relativePath: String): String? {
        val normalized = relativePath.removePrefix("./").replace('\\', '/')
        if (!normalized.startsWith("$ATTACHMENTS_DIRECTORY/") || ".." in normalized.split('/')) {
            return null
        }

        val cacheKey = "$markdownFileUri|$normalized"
        resolveUriCache[cacheKey]?.let { return it.ifEmpty { null } }

        val resolved = runCatching {
            val noteUri = markdownFileUri.toUri()
            val rootDocumentId = DocumentsContract.getTreeDocumentId(noteUri)
            val treeUri = DocumentsContract.buildTreeDocumentUri(noteUri.authority, rootDocumentId)
            val fileName = normalized.substringAfter("$ATTACHMENTS_DIRECTORY/")
            // 平铺附件优先走目录清单缓存：一次 listFiles 解析整篇笔记的全部图片；
            // 清单不可用（权限/异常）或带子目录的路径才回退逐级 findFile
            if ('/' !in fileName) {
                attachmentListing(treeUri)?.let { return@runCatching it[fileName] }
            }
            resolveByTraversal(treeUri, normalized)
        }.getOrElse {
            Timber.w(it, "AttachmentRepository: resolve failed path=%s", relativePath)
            null
        }

        if (resolveUriCache.size >= RESOLVE_CACHE_MAX_ENTRIES) {
            resolveUriCache.clear()
        }
        resolveUriCache[cacheKey] = resolved.orEmpty()
        return resolved
    }

    /**
     * 列出库根目录下 attachments 的 文件名 -> uri 清单并缓存；
     * 返回 null 表示列举失败（调用方应回退逐级查找），空 map 表示没有附件目录
     */
    private fun attachmentListing(treeUri: Uri): Map<String, String>? {
        val key = treeUri.toString()
        attachmentListingCache[key]?.let { return it }

        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        val directory = root.findFile(ATTACHMENTS_DIRECTORY) ?: return emptyMap<String, String>().also {
            attachmentListingCache[key] = it
        }
        if (!directory.isDirectory) return null

        val listing = directory.listFiles()
            .filter { it.isFile }
            .mapNotNull { file -> file.name?.let { name -> name to file.uri.toString() } }
            .toMap()
        attachmentListingCache[key] = listing
        return listing
    }

    private fun resolveByTraversal(treeUri: Uri, normalizedPath: String): String? {
        var current = DocumentFile.fromTreeUri(context, treeUri)
        for (segment in normalizedPath.split('/')) {
            current = current?.findFile(segment)
        }
        return current?.uri?.toString()
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

    private fun readImageDimensions(uri: Uri): Pair<Int, Int> {
        return runCatching {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
            options.outWidth to options.outHeight
        }.getOrDefault(0 to 0)
    }

    companion object {
        const val ATTACHMENTS_DIRECTORY = "attachments"
        private val EXTENSION_PATTERN = Regex("[a-z0-9]{1,8}")
        private const val RESOLVE_CACHE_MAX_ENTRIES = 2000
    }
}

internal fun normalizeAttachmentPath(destination: String): String? {
    val decoded = runCatching {
        URLDecoder.decode(
            destination.replace("+", "%2B"),
            StandardCharsets.UTF_8.name()
        )
    }.getOrDefault(destination)
    val normalized = decoded
        .trim()
        .removePrefix("<")
        .removeSuffix(">")
        .replace('\\', '/')
        .removePrefix("./")
    return normalized.takeIf {
        it.startsWith("${AttachmentRepositoryImpl.ATTACHMENTS_DIRECTORY}/") &&
            ".." !in it.split('/')
    }
}

internal fun legacyReferencePattern(relativePath: String): Regex = Regex(
    """!\[[^]]*]\(\s*<?(?:\./)?${Regex.escape(relativePath)}>?(?:\s+[\"'][^\"']*[\"'])?\s*\)"""
)

internal fun findAttachmentReferences(
    relativePath: String,
    filesWithDestinations: List<MarkdownAttachmentSource>
): List<AttachmentReference> {
    val legacyPattern = legacyReferencePattern(relativePath)
    return filesWithDestinations.mapNotNull { file ->
        if (relativePath in file.destinations || legacyPattern.containsMatchIn(file.content)) {
            AttachmentReference(fileUri = file.fileUri, fileName = file.fileName)
        } else {
            null
        }
    }
}

internal data class MarkdownAttachmentSource(
    val fileUri: String,
    val fileName: String,
    val content: String,
    val destinations: Set<String>
)

private data class AttachmentReferenceMatcher(
    val attachment: ManagedAttachment,
    val legacyPattern: Regex
)

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
