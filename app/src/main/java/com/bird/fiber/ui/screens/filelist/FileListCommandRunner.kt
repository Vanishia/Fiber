package com.bird.fiber.ui.screens.filelist

import com.bird.fiber.data.model.FileResult
import com.bird.fiber.data.model.MarkdownFileMeta
import com.bird.fiber.data.repository.FileRepository
import javax.inject.Inject

class FileListCommandRunner @Inject constructor(
    private val repository: FileRepository
) {
    suspend fun createFile(folderUri: String, fileName: String): FileResult<MarkdownFileMeta> {
        return repository.createMarkdownFile(folderUri, fileName)
    }

    suspend fun deleteFile(fileUri: String): FileResult<Unit> {
        return repository.deleteFile(fileUri)
    }

    suspend fun renameFile(fileUri: String, newName: String): FileResult<Unit> {
        return repository.renameFile(fileUri, newName)
    }

    suspend fun readFileContent(fileUri: String): FileResult<String> {
        return repository.readFileContent(fileUri)
    }
}
