package com.bird.fiber.data.local

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import timber.log.Timber

class MarkdownFileScanner {

    fun scan(
        contentResolver: ContentResolver,
        folderUri: String,
        libraryId: String
    ): List<ScannedFile> {
        val treeUri = Uri.parse(folderUri)
        val files = mutableListOf<ScannedFile>()

        scanRecursive(
            contentResolver = contentResolver,
            treeUri = treeUri,
            parentDocumentId = DocumentsContract.getTreeDocumentId(treeUri),
            parentRelativeDir = "",
            libraryId = libraryId,
            out = files
        )

        return files
    }

    fun scanSingleFile(
        contentResolver: ContentResolver,
        libraryId: String,
        rootFolderUri: String,
        fileUri: String
    ): ScannedFile? {
        val uri = Uri.parse(fileUri)
        val treeUri = Uri.parse(rootFolderUri)

        contentResolver.query(
            uri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_SIZE
            ),
            null,
            null,
            null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return null

            val name = cursor.getString(
                cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            )
            val documentId = cursor.getString(
                cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            )
            val lastModified = cursor.getLong(
                cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            )
            val size = cursor.getLong(
                cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
            )

            return ScannedFile(
                uri = fileUri,
                name = name.removeSuffix(".md"),
                path = relativePathFromTree(
                    treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri),
                    documentId = documentId,
                    fallbackDisplayName = name
                ),
                lastModified = lastModified,
                size = size,
                libraryId = libraryId
            )
        }

        return null
    }

    private fun scanRecursive(
        contentResolver: ContentResolver,
        treeUri: Uri,
        parentDocumentId: String,
        parentRelativeDir: String,
        libraryId: String,
        out: MutableList<ScannedFile>
    ) {
        try {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
            val cursor = contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null,
                null,
                null
            ) ?: throw IllegalArgumentException("Unable to access folder children: $parentDocumentId")

            cursor.use {
                val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val lastModifiedColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                val sizeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                val mimeTypeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)

                while (cursor.moveToNext()) {
                    val displayName = cursor.getString(nameColumn)
                    val mimeType = cursor.getString(mimeTypeColumn)
                    val documentId = cursor.getString(idColumn)

                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        val nextDir = if (parentRelativeDir.isBlank()) displayName else "$parentRelativeDir/$displayName"
                        scanRecursive(contentResolver, treeUri, documentId, nextDir, libraryId, out)
                        continue
                    }

                    val isMarkdown = displayName.endsWith(".md", ignoreCase = true) || mimeType == "text/markdown"
                    if (!isMarkdown) continue

                    val relativePath = if (parentRelativeDir.isBlank()) displayName else "$parentRelativeDir/$displayName"
                    out += ScannedFile(
                        uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId).toString(),
                        name = displayName.removeSuffix(".md"),
                        path = relativePath,
                        lastModified = cursor.getLong(lastModifiedColumn),
                        size = cursor.getLong(sizeColumn),
                        libraryId = libraryId
                    )
                }
            }
        } catch (e: SecurityException) {
            throw e
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "FileIndexer: scan failed documentId=%s", parentDocumentId)
            throw e
        }
    }

    private fun relativePathFromTree(
        treeDocumentId: String,
        documentId: String,
        fallbackDisplayName: String
    ): String {
        if (documentId.startsWith(treeDocumentId)) {
            val suffix = documentId.removePrefix(treeDocumentId).trimStart('/')
            if (suffix.isNotBlank()) return suffix
        }
        return fallbackDisplayName
    }
}
