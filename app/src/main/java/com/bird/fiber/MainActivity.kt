package com.bird.fiber

import android.net.Uri
import android.os.Bundle
import android.os.Build
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.bird.fiber.data.settings.SettingsDataStore
import com.bird.fiber.domain.sync.LibrarySyncManager
import com.bird.fiber.ui.navigation.FiberNavGraph
import com.bird.fiber.ui.screens.settings.DarkMode
import com.bird.fiber.ui.screens.settings.SettingsUiState
import com.bird.fiber.ui.theme.FiberTheme
import com.bird.fiber.utils.UriHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Main Activity
 *
 * 职责：
 * 1. 处理 SAF 文件夹选择
 * 2. 提供导航容器
 * 3. 启动时验证库有效性
 * 4. 启动时在后台同步文件到数据库
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var librarySyncManager: LibrarySyncManager

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )

            val folderUriString = uri.toString()
            val folderName = UriHelper.extractFolderName(uri)

            lifecycleScope.launch {
                librarySyncManager.addLibraryAndSync(
                    contentResolver = contentResolver,
                    folderName = folderName,
                    folderUriString = folderUriString
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        setContent {
            val settingsUiState by settingsDataStore.settingsFlow.collectAsState(
                initial = SettingsUiState()
            )

            val darkTheme = when (settingsUiState.darkMode) {
                DarkMode.SYSTEM -> isSystemInDarkTheme()
                DarkMode.LIGHT -> false
                DarkMode.DARK -> true
            }

            val fontSizeScale = SettingsUiState.FONT_SIZE_PERCENTAGES
                .getOrElse(settingsUiState.fontSizeLevel) { 100 } / 100f

            FiberTheme(
                darkTheme = darkTheme,
                dynamicColor = settingsUiState.isDynamicColorEnabled,
                colorSchemeType = settingsUiState.colorScheme,
                fontSizeScale = fontSizeScale
            ) {
                val navController = rememberNavController()

                FiberNavGraph(
                    navController = navController,
                    onSelectFolder = { folderPickerLauncher.launch(null) },
                    onAddLibrary = { folderPickerLauncher.launch(null) },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        schedulePostFirstDrawStartupWork()
    }

    override fun onResume() {
        super.onResume()
    }

    private fun schedulePostFirstDrawStartupWork() {
        window.decorView.post {
            lifecycleScope.launch {
                Timber.d("StartupTrace: validate libraries begin")
                val removedCount = librarySyncManager.validateAndCleanupInvalidLibraries(contentResolver)
                Timber.d("StartupTrace: validate libraries result removedCount=$removedCount")
                if (removedCount > 0) {
                    Timber.d("清理了 $removedCount 个无效的库")
                }

                delay(ACTIVE_LIBRARY_SYNC_DELAY_MS)
                Timber.d("StartupTrace: active library sync enqueue")
                librarySyncManager.syncActiveLibraryIfIdle(contentResolver)
                Timber.d("StartupTrace: active library sync finished")

                delay(INACTIVE_LIBRARIES_SYNC_DELAY_MS)
                Timber.d("StartupTrace: inactive libraries sync enqueue")
                librarySyncManager.syncInactiveLibrariesIfIdle(contentResolver)
                Timber.d("StartupTrace: inactive libraries sync finished")
            }
        }
    }

    private companion object {
        private const val ACTIVE_LIBRARY_SYNC_DELAY_MS = 750L
        private const val INACTIVE_LIBRARIES_SYNC_DELAY_MS = 2_000L
    }
}
