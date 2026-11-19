package com.bird.fiber.ui.screens.notelist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.platform.LocalContext
import com.bird.fiber.data.model.MarkdownFileMeta
import com.bird.fiber.ui.screens.filelist.components.DeleteConfirmDialog
import com.bird.fiber.ui.screens.filelist.components.LongPressMenuBottomSheet
import com.bird.fiber.ui.screens.filelist.components.RenameFileDialog
import com.bird.fiber.utils.ShareHelper

/** Shared note actions for all cross-library browsing surfaces. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NoteListItemActionOverlays(
    selectedFile: MarkdownFileMeta?,
    showMenu: Boolean,
    onDismissMenu: () -> Unit,
    directDeleteFile: MarkdownFileMeta? = null,
    onDismissDirectDelete: () -> Unit = {},
    actions: NoteListActionsViewModel
) {
    val context = LocalContext.current
    var deleteFile by remember { mutableStateOf<MarkdownFileMeta?>(null) }
    var renameFile by remember { mutableStateOf<MarkdownFileMeta?>(null) }
    var renameName by remember { mutableStateOf("") }

    LongPressMenuBottomSheet(
        isVisible = showMenu,
        onDismiss = onDismissMenu,
        onEdit = { selectedFile?.let { actions.openFile(it, true) } },
        onCopy = { selectedFile?.let(actions::copyFile) },
        onShare = {
            selectedFile?.let {
                ShareHelper.shareMarkdownFile(context, it.uri, it.name.removeSuffix(".md"))
            }
        },
        onRename = {
            selectedFile?.let {
                renameName = it.name.removeSuffix(".md")
                renameFile = it
            }
        },
        onDelete = { deleteFile = selectedFile }
    )

    (deleteFile ?: directDeleteFile)?.let { file ->
        DeleteConfirmDialog(
            fileName = file.name.removeSuffix(".md"),
            onDismiss = {
                deleteFile = null
                onDismissDirectDelete()
            },
            onConfirm = {
                actions.deleteFile(file)
                deleteFile = null
                onDismissDirectDelete()
            }
        )
    }

    renameFile?.let { file ->
        RenameFileDialog(
            currentName = file.name.removeSuffix(".md"),
            newName = renameName,
            onNewNameChange = { renameName = it },
            onDismiss = { renameFile = null },
            onConfirm = {
                actions.renameFile(file, renameName)
                renameFile = null
            }
        )
    }
}
