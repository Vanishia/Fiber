package com.bird.fiber.ui.screens.notelist

import app.cash.turbine.test
import com.bird.fiber.data.event.AppEvent
import com.bird.fiber.data.event.EventBus
import com.bird.fiber.data.local.library.LibraryEntity
import com.bird.fiber.data.local.library.LibraryRepository
import com.bird.fiber.data.local.library.toTarget
import com.bird.fiber.data.model.FileError
import com.bird.fiber.data.model.FileResult
import com.bird.fiber.data.model.MarkdownFileMeta
import com.bird.fiber.data.model.toUserMessage
import com.bird.fiber.data.repository.FileRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NoteListActionsViewModelTest {
    private val files = mockk<FileRepository>()
    private val libraries = mockk<LibraryRepository>()
    private val bus = mockk<EventBus>(relaxed = true)
    private val libraryA = LibraryEntity("a", "工作", "content://test/a", 0)
    private val libraryB = LibraryEntity("b", "生活", "content://test/b", 0)
    private val active = MutableStateFlow<LibraryEntity?>(libraryA)
    private val note = MarkdownFileMeta("content://test/b/note", "笔记", "note.md", 0, 50, "仅预览", libraryId = "b")
    private lateinit var actions: NoteListActionsViewModel

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { libraries.getActiveLibrary() } returns active
        coEvery { libraries.getLibraryById("b") } returns libraryB
        coEvery { libraries.switchLibrary(any()) } returns Unit
        actions = NoteListActionsViewModel(files, libraries, bus)
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `复制跨库笔记读取全文且不切库`() = runTest {
        val fullText = "完整内容".repeat(1_000)
        coEvery { files.readFileContent(note.uri) } returns FileResult.Success(fullText)
        actions.events.test {
            actions.copyFile(note)
            assertEquals(NoteListActionEvent.CopyContent(fullText), awaitItem())
        }
        coVerify(exactly = 0) { libraries.switchLibrary(any()) }
    }

    @Test fun `跨库编辑先切到条目所属库再打开原URI`() = runTest {
        actions.events.test {
            actions.openFile(note, true)
            assertEquals(NoteListActionEvent.OpenFile(note.uri, true), awaitItem())
        }
        coVerify(exactly = 1) { libraries.switchLibrary("b") }
    }

    @Test fun `本库查看不重复切库以免触发索引刷新`() = runTest {
        active.value = libraryB
        actions.events.test {
            actions.openFile(note, false)
            assertEquals(NoteListActionEvent.OpenFile(note.uri, false), awaitItem())
        }
        coVerify(exactly = 0) { libraries.switchLibrary(any()) }
    }

    @Test fun `切库失败不进入可能使用错误附件目录的编辑器`() = runTest {
        coEvery { libraries.switchLibrary("b") } throws IllegalStateException("切库失败")
        actions.events.test {
            actions.openFile(note, true)
            assertEquals(NoteListActionEvent.Message("切库失败"), awaitItem())
            expectNoEvents()
        }
    }

    @Test fun `删除失败不报成功也不发刷新通知且仍允许重试`() = runTest {
        val error = FileError.PermissionDenied(note.uri)
        coEvery { files.deleteFile(note.uri) } returns FileResult.Error(error) andThen FileResult.Success(Unit)
        actions.events.test {
            actions.deleteFile(note)
            assertEquals(NoteListActionEvent.Message(error.toUserMessage()), awaitItem())
            coVerify(exactly = 0) { bus.emit(any()) }
            actions.deleteFile(note)
            assertEquals(NoteListActionEvent.Message("「笔记」已删除"), awaitItem())
        }
        coVerify(exactly = 1) { bus.emit(AppEvent.FileDeleted(note.uri)) }
        coVerify(exactly = 0) { libraries.switchLibrary(any()) }
    }

    @Test fun `重命名跨库笔记保留目标URI并通知更新后的URI`() = runTest {
        val newUri = "content://test/b/renamed"
        coEvery { files.renameFile(note.uri, "新名称") } returns FileResult.Success(newUri)
        actions.events.test {
            actions.renameFile(note, "新名称")
            assertEquals(NoteListActionEvent.Message("已重命名为「新名称」"), awaitItem())
        }
        coVerify { bus.emit(AppEvent.FileUpdated(newUri)) }
        coVerify(exactly = 0) { libraries.switchLibrary(any()) }
    }

    @Test fun `历史回顾中新建使用确认的库且成功后直接进入编辑`() = runTest {
        coEvery { files.createMarkdownFile(libraryA.toTarget(), "回顾", "") } returns FileResult.Success(note)
        // 对话框打开后激活库变化，仍写入用户打开对话框时看到的工作库。
        active.value = libraryB
        actions.events.test {
            actions.createFile(libraryA, "回顾")
            assertEquals(NoteListActionEvent.OpenFile(note.uri, true), awaitItem())
        }
        coVerify { files.createMarkdownFile(libraryA.toTarget(), "回顾", "") }
        coVerify { bus.emit(AppEvent.FileCreated(note.uri)) }
        coVerify { libraries.switchLibrary("a") }
    }
}
