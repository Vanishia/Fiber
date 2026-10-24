package com.bird.fiber.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bird.fiber.ui.theme.LocalFiberSurfaceColors

@Composable
fun HomeViewSettingsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }
    HomeViewSettingsContent(
        uiState = uiState,
        onBackClick = onBackClick,
        onModeChange = viewModel::setLibraryViewMode,
        snackbarHostState = snackbarHostState,
        modifier = modifier
    )
}

/** 无框表格：库名为行，两种视图为列，每个库独立单选。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeViewSettingsContent(
    uiState: HomeViewSettingsUiState,
    onBackClick: () -> Unit,
    onModeChange: (String, ListViewMode) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = LocalFiberSurfaceColors.current.pageBackground,
        topBar = {
            TopAppBar(
                title = { Text("主页视图设置") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp)
        ) {
            Text(
                text = "为每个已导入库单独设置，默认使用条目视图，选择后自动保存。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 24.dp)
            )
            when {
                uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                uiState.libraries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无已导入库，请先在主页导入库", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("已导入库", Modifier.weight(LIBRARY_COLUMN_WEIGHT), style = MaterialTheme.typography.labelLarge)
                        ListViewMode.entries.forEach { mode ->
                            Text(
                                text = mode.label,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelLarge,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(uiState.libraries, key = { it.libraryId }) { library ->
                            Row(
                                modifier = Modifier.fillMaxWidth().selectableGroup(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = library.libraryName,
                                    modifier = Modifier.weight(LIBRARY_COLUMN_WEIGHT).padding(end = 8.dp),
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                ListViewMode.entries.forEach { mode ->
                                    Box(
                                        modifier = Modifier.weight(1f)
                                            .sizeIn(minWidth = 48.dp, minHeight = 56.dp)
                                            .semantics { contentDescription = "${library.libraryName}，${mode.label}" }
                                            .selectable(
                                                selected = library.viewMode == mode,
                                                role = Role.RadioButton,
                                                onClick = { onModeChange(library.libraryId, mode) }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        RadioButton(selected = library.viewMode == mode, onClick = null)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val LIBRARY_COLUMN_WEIGHT = 1.4f
