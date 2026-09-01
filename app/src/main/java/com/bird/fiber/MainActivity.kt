package com.bird.fiber

import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.OpenableColumns
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
import com.bird.fiber.data.importing.ImportShareManager
import com.bird.fiber.data.importing.PendingImport
import com.bird.fiber.domain.sync.LibrarySyncManager
import com.bird.fiber.ui.navigation.FiberNavGraph
import com.bird.fiber.ui.screens.settings.DarkMode
import com.bird.fiber.ui.screens.settings.SettingsUiState
import com.bird.fiber.ui.theme.FiberTheme
import com.bird.fiber.utils.UriHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    @Inject
    lateinit var importShareManager: ImportShareManager

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
                seedColor = settingsUiState.themeSeedColor,
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
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    /**
     * 处理其他应用打开/分享过来的 .md 文件
     *
     * intent 自带临时读权限，立即读取内容暂存到 [ImportShareManager]，
     * 由 UI 层的选库对话框决定保存到哪个库。处理后清空 intent 的 data，
     * 防止配置变更（如旋转屏幕）重建 Activity 时重复弹窗
     */
    private fun handleIncomingIntent(intent: android.content.Intent?) {
        if (intent == null) return
        val sharedUri = when (intent.action) {
            android.content.Intent.ACTION_VIEW -> intent.data
            android.content.Intent.ACTION_SEND -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(
                        android.content.Intent.EXTRA_STREAM,
                        Uri::class.java
                    )
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(android.content.Intent.EXTRA_STREAM)
                }
            }
            else -> null
        } ?: return

        Timber.d("收到外部文件: $sharedUri")
        // 标记已处理，重建时不再重复导入
        intent.data = null
        intent.removeExtra(android.content.Intent.EXTRA_STREAM)

        lifecycleScope.launch {
            val pending = withContext(Dispatchers.IO) { readSharedFile(sharedUri) }
            if (pending != null) {
                importShareManager.offer(pending)
            } else {
                Timber.w("外部文件读取失败: $sharedUri")
            }
        }
    }

    /**
     * 读取外部文件内容，超过 [MAX_IMPORT_CHARS] 字符截断
     */
    private fun readSharedFile(uri: Uri): PendingImport? = runCatching {
        val displayName = queryDisplayName(uri) ?: "导入笔记.md"
        val content = contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
            val buffer = CharArray(MAX_IMPORT_CHARS)
            val read = reader.read(buffer)
            if (read > 0) String(buffer, 0, read) else ""
        } ?: return null
        PendingImport(fileName = displayName, content = content)
    }.onFailure { e ->
        Timber.e(e, "读取外部文件失败: $uri")
    }.getOrNull()

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            } else {
                null
            }
        }
    }.getOrNull()

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

                // 数据库升级后摘要被清空、待重建索引达阈值时，一次性迁移所有库
                // （主界面显示全库聚合进度，期间切换库也保持进度页），
                // 迁移完成即全库索引已最新，跳过常规启动同步，避免逐库迁移的闪烁
                val migrated = librarySyncManager.reindexAllLibrariesIfNeeded(contentResolver)
                if (migrated) {
                    Timber.d("StartupTrace: db migration reindex done, skip regular startup sync")
                    return@launch
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

        /** 导入文件最大读取字符数（约 512KB 文本），超出截断 */
        private const val MAX_IMPORT_CHARS = 512 * 1024
    }
}
