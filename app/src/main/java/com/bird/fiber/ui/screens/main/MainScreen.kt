package com.bird.fiber.ui.screens.main

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bird.fiber.ui.screens.filelist.FileListScreen
import com.bird.fiber.ui.screens.quicknote.QuickNoteEvent
import com.bird.fiber.ui.screens.quicknote.QuickNoteUiState
import com.bird.fiber.ui.screens.quicknote.QuickNoteViewModel
import com.bird.fiber.ui.theme.LocalFiberSurfaceColors
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
    onManageAttachments: (String) -> Unit = {},
    topBarModifier: Modifier = Modifier,
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

    // 快速笔记保存成功后收起键盘，输入框随内容清空自动收起
    LaunchedEffect(Unit) {
        quickNoteViewModel.events.collect { event ->
            when (event) {
                QuickNoteEvent.SaveSuccess -> focusManager.clearFocus()
            }
        }
    }
    fun openDrawerIfClosed() {
        if (
            drawerState.currentValue == DrawerValue.Closed &&
            drawerState.targetValue == DrawerValue.Closed
        ) {
            scope.launch { drawerState.open() }
        }
    }

    fun closeDrawerIfOpen() {
        if (
            drawerState.currentValue != DrawerValue.Closed ||
            drawerState.targetValue != DrawerValue.Closed
        ) {
            scope.launch { drawerState.close() }
        }
    }

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
        onManageAttachments = onManageAttachments,
        topBarModifier = topBarModifier,
        onLibrarySelected = { libraryId ->
            viewModel.onLibrarySelected(libraryId)
            closeDrawerIfOpen()
        },
        onOpenDrawer = ::openDrawerIfClosed,
        onCopyContent = { clipboardManager.setText(AnnotatedString(it)) },
        onListScroll = { focusManager.clearFocus() },
        onQuickNoteContentChange = { newValue ->
            Timber.d("MainScreen: quick note content changed")
            quickNoteViewModel.onContentChange(newValue)
        },
        onQuickNoteDismissError = { quickNoteViewModel.clearError() },
        onQuickNoteImageSelected = quickNoteViewModel::addImage,
        onQuickNoteRemoveAttachment = quickNoteViewModel::removeAttachment,
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
    onManageAttachments: (String) -> Unit,
    topBarModifier: Modifier,
    onLibrarySelected: (String) -> Unit,
    onOpenDrawer: () -> Unit,
    onCopyContent: (String) -> Unit,
    onListScroll: () -> Unit,
    onQuickNoteContentChange: (String) -> Unit,
    onQuickNoteDismissError: () -> Unit,
    onQuickNoteImageSelected: (String) -> Unit,
    onQuickNoteRemoveAttachment: (String) -> Unit,
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
                onManageAttachments = onManageAttachments,
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
            topBarModifier = topBarModifier,
            onOpenDrawer = onOpenDrawer,
            onCopyContent = onCopyContent,
            onListScroll = onListScroll,
            onQuickNoteContentChange = onQuickNoteContentChange,
            onQuickNoteDismissError = onQuickNoteDismissError,
            onQuickNoteImageSelected = onQuickNoteImageSelected,
            onQuickNoteRemoveAttachment = onQuickNoteRemoveAttachment,
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
    topBarModifier: Modifier,
    onOpenDrawer: () -> Unit,
    onCopyContent: (String) -> Unit,
    onListScroll: () -> Unit,
    onQuickNoteContentChange: (String) -> Unit,
    onQuickNoteDismissError: () -> Unit,
    onQuickNoteImageSelected: (String) -> Unit,
    onQuickNoteRemoveAttachment: (String) -> Unit,
    onQuickNoteSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        containerColor = LocalFiberSurfaceColors.current.pageBackground,
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
            // 快速笔记输入框聚焦时，给列表盖一层浅半透明遮罩，
            // 弱化背景内容，点一下即收起（与长按菜单/抽屉的遮罩手感一致）
            var isQuickNoteFocused by remember { mutableStateOf(false) }
            val scrimAlpha by animateFloatAsState(
                targetValue = if (isQuickNoteFocused) 1f else 0f,
                animationSpec = tween(150),
                label = "quickNoteScrimAlpha"
            )

            Box(modifier = Modifier.weight(1f)) {
                FileListScreen(
                    onFileClick = onFileClick,
                    onSelectFolder = onSelectFolder,
                    onChangeFolder = {},
                    onCopyContent = onCopyContent,
                    onMenuClick = onOpenDrawer,
                    onSearchClick = onSearchClick,
                    topBarModifier = topBarModifier,
                    currentLibraryName = currentLibraryName,
                    onListScroll = onListScroll,
                    modifier = Modifier.fillMaxSize()
                )

                if (scrimAlpha > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = scrimAlpha }
                            .background(
                                MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)
                            )
                            .clickable(enabled = isQuickNoteFocused) { onListScroll() },
                    )
                }
            }

            QuickNoteBar(
                content = quickNoteState.content,
                attachments = quickNoteState.attachments,
                isSaving = quickNoteState.isSaving,
                isAddingImage = quickNoteState.isAddingImage,
                error = quickNoteState.error,
                onContentChange = onQuickNoteContentChange,
                onImageSelected = onQuickNoteImageSelected,
                onRemoveAttachment = onQuickNoteRemoveAttachment,
                onDismissError = onQuickNoteDismissError,
                onSaveClick = onQuickNoteSave,
                onInputFocusChange = { isQuickNoteFocused = it }
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
    onManageAttachments: (String) -> Unit,
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
                onSettingsClick = onSettingsClick,
                onManageAttachments = onManageAttachments
            )
        } else {
            Spacer(modifier = Modifier.fillMaxSize())
        }
    }
}
