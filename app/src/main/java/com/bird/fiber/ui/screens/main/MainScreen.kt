package com.bird.fiber.ui.screens.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bird.fiber.ui.screens.filelist.FileListScreen
import com.bird.fiber.ui.screens.quicknote.QuickNoteUiState
import com.bird.fiber.ui.screens.quicknote.QuickNoteViewModel
import com.bird.fiber.ui.screens.sidebar.SidebarContent
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreenContainer(
    visible: Boolean = true,
    onFileClick: (String, Boolean) -> Unit = { _, _ -> },
    onSelectFolder: () -> Unit,
    onAddLibrary: () -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = hiltViewModel(),
    quickNoteViewModel: QuickNoteViewModel = hiltViewModel()
) {
    if (!visible) return

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val quickNoteState by quickNoteViewModel.uiState.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current

    MainScreenRoute(
        modifier = modifier,
        drawerState = drawerState,
        selectedLibraryId = uiState.selectedLibraryId,
        currentLibraryName = uiState.currentLibraryName,
        quickNoteState = quickNoteState,
        onFileClick = onFileClick,
        onSelectFolder = onSelectFolder,
        onAddLibrary = onAddLibrary,
        onSearchClick = onSearchClick,
        onSettingsClick = onSettingsClick,
        onLibrarySelected = { libraryId ->
            viewModel.onLibrarySelected(libraryId)
            scope.launch { drawerState.close() }
        },
        onOpenDrawer = { scope.launch { drawerState.open() } },
        onCopyContent = { clipboardManager.setText(AnnotatedString(it)) },
        onListScroll = { focusManager.clearFocus() },
        onQuickNoteContentChange = { newValue ->
            Timber.d("MainScreen: quick note content changed")
            quickNoteViewModel.onContentChange(newValue)
        },
        onQuickNoteDismissError = { quickNoteViewModel.clearError() },
        onQuickNoteSave = { quickNoteViewModel.saveNote() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreenRoute(
    drawerState: DrawerState,
    selectedLibraryId: String?,
    currentLibraryName: String?,
    quickNoteState: QuickNoteUiState,
    onFileClick: (String, Boolean) -> Unit,
    onSelectFolder: () -> Unit,
    onAddLibrary: () -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onLibrarySelected: (String) -> Unit,
    onOpenDrawer: () -> Unit,
    onCopyContent: (String) -> Unit,
    onListScroll: () -> Unit,
    onQuickNoteContentChange: (String) -> Unit,
    onQuickNoteDismissError: () -> Unit,
    onQuickNoteSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shouldComposeDrawer =
        drawerState.currentValue != DrawerValue.Closed ||
            drawerState.targetValue != DrawerValue.Closed

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f),
        drawerContent = {
            MainDrawerContent(
                selectedLibraryId = selectedLibraryId,
                onLibrarySelected = onLibrarySelected,
                onAddLibrary = onAddLibrary,
                onSettingsClick = onSettingsClick,
                shouldComposeContent = shouldComposeDrawer
            )
        }
    ) {
        MainScreenLayout(
            modifier = modifier,
            currentLibraryName = currentLibraryName,
            quickNoteState = quickNoteState,
            onFileClick = onFileClick,
            onSelectFolder = onSelectFolder,
            onSearchClick = onSearchClick,
            onOpenDrawer = onOpenDrawer,
            onCopyContent = onCopyContent,
            onListScroll = onListScroll,
            onQuickNoteContentChange = onQuickNoteContentChange,
            onQuickNoteDismissError = onQuickNoteDismissError,
            onQuickNoteSave = onQuickNoteSave
        )
    }
}

@Composable
private fun MainScreenLayout(
    currentLibraryName: String?,
    quickNoteState: QuickNoteUiState,
    onFileClick: (String, Boolean) -> Unit,
    onSelectFolder: () -> Unit,
    onSearchClick: () -> Unit,
    onOpenDrawer: () -> Unit,
    onCopyContent: (String) -> Unit,
    onListScroll: () -> Unit,
    onQuickNoteContentChange: (String) -> Unit,
    onQuickNoteDismissError: () -> Unit,
    onQuickNoteSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = paddingValues.calculateStartPadding(LayoutDirection.Ltr),
                    end = paddingValues.calculateEndPadding(LayoutDirection.Ltr)
                )
        ) {
            FileListScreen(
                onFileClick = onFileClick,
                onSelectFolder = onSelectFolder,
                onChangeFolder = {},
                onCopyContent = onCopyContent,
                onMenuClick = onOpenDrawer,
                onSearchClick = onSearchClick,
                currentLibraryName = currentLibraryName,
                onListScroll = onListScroll,
                modifier = Modifier.weight(1f)
            )

            QuickNoteBar(
                content = quickNoteState.content,
                isSaving = quickNoteState.isSaving,
                error = quickNoteState.error,
                onContentChange = onQuickNoteContentChange,
                onDismissError = onQuickNoteDismissError,
                onSaveClick = onQuickNoteSave
            )
        }
    }
}

@Composable
private fun MainDrawerContent(
    selectedLibraryId: String?,
    onLibrarySelected: (String) -> Unit,
    onAddLibrary: () -> Unit,
    onSettingsClick: () -> Unit,
    shouldComposeContent: Boolean
) {
    ModalDrawerSheet(
        modifier = Modifier.width(280.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface
    ) {
        if (shouldComposeContent) {
            SidebarContent(
                selectedLibraryId = selectedLibraryId,
                onLibrarySelected = onLibrarySelected,
                onAddLibrary = onAddLibrary,
                onSettingsClick = onSettingsClick
            )
        } else {
            Spacer(modifier = Modifier.fillMaxSize())
        }
    }
}
