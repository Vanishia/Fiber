package com.bird.fiber.ui.screens.editor

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bird.fiber.ui.theme.ErrorDark
import com.bird.fiber.ui.theme.ErrorLight
import com.bird.fiber.ui.theme.SuccessDark
import com.bird.fiber.ui.theme.SuccessLight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    fileUri: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    initialPreviewMode: Boolean = true,
    viewModel: EditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val renderState by viewModel.renderState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val statusBarTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navigationBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val overlayTopInset = statusBarTopPadding + 68.dp

    var showUnsavedDialog by remember { mutableStateOf(false) }
    var pendingClose by remember { mutableStateOf(false) }
    var wasSaving by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isSaving) {
        if (wasSaving && !uiState.isSaving) {
            if (pendingClose) {
                pendingClose = false
                Toast.makeText(context, "修改已保存", Toast.LENGTH_SHORT).show()
                onClose()
            } else {
                Toast.makeText(context, "修改已保存", Toast.LENGTH_SHORT).show()
            }
        }
        wasSaving = uiState.isSaving
    }

    LaunchedEffect(fileUri, initialPreviewMode) {
        viewModel.loadFile(fileUri)
        viewModel.setInitialPreviewMode(initialPreviewMode)
    }

    fun handleClose() {
        if (viewModel.hasUnsavedChanges()) {
            showUnsavedDialog = true
            pendingClose = true
        } else {
            onClose()
        }
    }

    BackHandler(enabled = true) { handleClose() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            EditorContentHost(
                uiState = uiState,
                renderedMarkdown = renderState.renderedMarkdown,
                isRendering = renderState.isRendering,
                onContentChange = viewModel::onContentChange,
                topContentInset = overlayTopInset,
                bottomContentInset = navigationBarBottomPadding,
                modifier = Modifier.fillMaxSize()
            )

            EditorTopBar(
                fileName = uiState.fileName,
                isLoading = uiState.isLoading,
                isSaving = uiState.isSaving,
                isPreviewMode = uiState.isPreviewMode,
                onBackClick = ::handleClose,
                onTogglePreviewMode = viewModel::togglePreviewMode,
                onSaveClick = viewModel::saveFile,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = statusBarTopPadding)
            )
        }
    }

    if (showUnsavedDialog) {
        val isDarkTheme = isSystemInDarkTheme()
        val successColor = if (isDarkTheme) SuccessDark else SuccessLight
        val errorColor = if (isDarkTheme) ErrorDark else ErrorLight

        UnsavedChangesDialog(
            onDismiss = {
                showUnsavedDialog = false
                pendingClose = false
            },
            onSaveAndExit = {
                viewModel.saveFile()
                showUnsavedDialog = false
            },
            onExitWithoutSaving = {
                showUnsavedDialog = false
                pendingClose = false
                onClose()
            },
            successColor = successColor,
            errorColor = errorColor
        )
    }
}
