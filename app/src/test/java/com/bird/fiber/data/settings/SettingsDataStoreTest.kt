package com.bird.fiber.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.bird.fiber.ui.screens.settings.ListViewMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsDataStoreTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun `未配置库及无激活库时默认条目视图`() = runTest {
        val file = temporaryFolder.root.resolve("defaults.preferences_pb")
        val settings = SettingsDataStore(PreferenceDataStoreFactory.create(scope = backgroundScope) { file })
        val state = settings.settingsFlow.first()
        assertEquals(ListViewMode.LIST, state.listViewModeForLibrary("new-library"))
        assertEquals(ListViewMode.LIST, state.listViewModeForLibrary(null))
    }

    @Test
    fun `逐库保存互不覆盖且切回条目只移除当前库`() = runTest {
        val file = temporaryFolder.root.resolve("independent.preferences_pb")
        val settings = SettingsDataStore(PreferenceDataStoreFactory.create(scope = backgroundScope) { file })
        settings.saveLibraryListViewMode("a", ListViewMode.GRID)
        settings.saveLibraryListViewMode("b", ListViewMode.GRID)
        settings.saveLibraryListViewMode("a", ListViewMode.LIST)

        val state = settings.settingsFlow.first()
        assertEquals(ListViewMode.LIST, state.listViewModeForLibrary("a"))
        assertEquals(ListViewMode.GRID, state.listViewModeForLibrary("b"))
        assertEquals(ListViewMode.LIST, state.listViewModeForLibrary("c"))
        assertEquals(setOf("b"), state.waterfallLibraryIds)
    }

    @Test
    fun `并发保存多个库不丢失设置`() = runTest {
        val file = temporaryFolder.root.resolve("concurrent.preferences_pb")
        val settings = SettingsDataStore(PreferenceDataStoreFactory.create(scope = backgroundScope) { file })
        (1..20).map { id -> async { settings.saveLibraryListViewMode("library-$id", ListViewMode.GRID) } }.awaitAll()
        assertEquals((1..20).map { "library-$it" }.toSet(), settings.settingsFlow.first().waterfallLibraryIds)
    }

    @Test
    fun `重新打开持久化文件仍恢复各库视图`() = runTest {
        val file = temporaryFolder.root.resolve("persisted.preferences_pb")
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val firstSettings = SettingsDataStore(PreferenceDataStoreFactory.create(scope = firstScope) { file })
        try {
            firstSettings.saveLibraryListViewMode("library-id", ListViewMode.GRID)
        } finally {
            firstScope.coroutineContext[Job]!!.cancelAndJoin()
        }
        val reopened = SettingsDataStore(PreferenceDataStoreFactory.create(scope = backgroundScope) { file })
        assertTrue(file.isFile)
        assertEquals(ListViewMode.GRID, reopened.settingsFlow.first().listViewModeForLibrary("library-id"))
    }
}
