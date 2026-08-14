package com.bird.fiber.ui.screens.filelist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig as AndroidPagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.bird.fiber.data.config.PagingConfig
import com.bird.fiber.data.event.AppEvent
import com.bird.fiber.data.event.EventBus
import com.bird.fiber.data.local.PreviewCache
import com.bird.fiber.data.local.library.LibraryRepository
import com.bird.fiber.data.local.library.MarkdownFileDao
import com.bird.fiber.data.local.library.toMarkdownFileMeta
import com.bird.fiber.data.model.FileResult
import com.bird.fiber.data.model.MarkdownFileMeta
import com.bird.fiber.data.model.LibraryTarget
import com.bird.fiber.data.model.toUserMessage
import com.bird.fiber.data.repository.FileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
@HiltViewModel
class FileListViewModel @Inject constructor(
    private val commandRunner: FileListCommandRunner,
    private val eventBus: EventBus,
    private val markdownFileDao: MarkdownFileDao,
    private val libraryRepository: LibraryRepository,
    private val previewCache: PreviewCache,
    private val fileRepository: FileRepository
) : ViewModel() {

    enum class SearchScope { CURRENT_LIBRARY, ALL_LIBRARIES }
    enum class SearchSort { RELEVANCE, RECENT_MODIFIED }

    private val _uiState = MutableStateFlow(FileListUiState())
    val uiState: StateFlow<FileListUiState> = _uiState.asStateFlow()

    private val _fileCreatedEvents = MutableSharedFlow<MarkdownFileMeta>(extraBufferCapacity = 1)
    val fileCreatedEvents = _fileCreatedEvents.asSharedFlow()

    // 快速笔记保存成功后请求列表滚动到顶部，让新卡片出现在可视区域内
    private val _scrollToTopEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val scrollToTopEvents = _scrollToTopEvents.asSharedFlow()

    private val _currentSearchQuery = MutableStateFlow("")
    private val _currentLibraryId = MutableStateFlow<String?>(null)
    private val _refreshVersion = MutableStateFlow(0)

    // UI 输入的搜索词，经过 300ms 防抖后写入 _currentSearchQuery
    private val _searchQueryInput = MutableStateFlow("")
    private val _searchScope = MutableStateFlow(SearchScope.CURRENT_LIBRARY)
    private val _searchSort = MutableStateFlow(SearchSort.RELEVANCE)

    val searchScope: StateFlow<SearchScope> = _searchScope.asStateFlow()
    val searchSort: StateFlow<SearchSort> = _searchSort.asStateFlow()

    init {
        observeActiveLibrary()
        observeAppEvents()
        observeSearchInput()
    }

    val pager: Flow<PagingData<MarkdownFileMeta>> = combine(
        _currentLibraryId,
        _currentSearchQuery,
        _searchScope,
        _searchSort,
        _refreshVersion
    ) { libraryId, searchQuery, scope, sort, refreshVersion ->
        PagerParams(libraryId, searchQuery, scope, sort, refreshVersion)
    }
        .distinctUntilChanged()
        .flatMapLatest { params ->
            if (params.libraryId == null) {
                flowOf(androidx.paging.PagingData.empty())
            } else {
                createPager(params.libraryId, params.searchQuery, params.scope, params.sort)
            }
        }

    fun updateSearchQuery(query: String) {
        if (_searchQueryInput.value != query) {
            _searchQueryInput.value = query
        }
    }

    fun updateSearchScope(scope: SearchScope) { _searchScope.value = scope }

    fun updateSearchSort(sort: SearchSort) { _searchSort.value = sort }

    fun openSearchResult(file: MarkdownFileMeta, onReady: (String) -> Unit) {
        viewModelScope.launch {
            val libraryId = file.libraryId
            if (libraryId != null && libraryId != _currentLibraryId.value) {
                runCatching { libraryRepository.switchLibrary(libraryId) }
                    .onFailure { Timber.e(it, "Failed to switch library before opening search result") }
            }
            onReady(file.uri)
        }
    }

    /**
     * 随机抽取一条笔记（"随机漫步"）
     *
     * 抽取范围与搜索一致：全库或当前库，由搜索页的范围切换按钮控制；
     * 命中后读取全文，弹窗直接展示完整内容
     *
     * @param scope 抽取范围，当前库模式下没有选中库时回调 null
     * @param onResult 主线程回调，范围内没有可用笔记时返回 null
     */
    fun loadRandomMemo(scope: SearchScope, onResult: (RandomMemo?) -> Unit) {
        viewModelScope.launch {
            val meta = runCatching {
                if (scope == SearchScope.ALL_LIBRARIES) {
                    markdownFileDao.getRandomFileSummary()
                } else {
                    val libraryId = _currentLibraryId.value
                        ?: return@launch onResult(null)
                    markdownFileDao.getRandomFileSummaryByLibrary(libraryId)
                }
            }
                .onFailure { Timber.e(it, "Failed to load random memo") }
                .getOrNull()
                ?.toMarkdownFileMeta()
            if (meta == null) {
                onResult(null)
                return@launch
            }

            // 全文读取失败时退回预览，保证弹窗总有内容可显示
            val content = runCatching { fileRepository.readFileContent(meta.uri) }
                .onFailure { Timber.e(it, "Failed to read random memo content") }
                .getOrNull()
                ?.let { it as? FileResult.Success }
                ?.data
                ?: meta.preview

            onResult(RandomMemo(meta, content))
        }
    }

    fun onEvent(event: FileListEvent) {
        when (event) {
            is FileListEvent.SelectFolder -> {
                _uiState.value = _uiState.value.copy(
                    error = "请通过Activity选择文件夹"
                )
            }

            is FileListEvent.RefreshFiles -> triggerRefresh("manual")

            is FileListEvent.SelectFile -> {
                _uiState.value = _uiState.value.copy(selectedFile = event.file)
            }

            is FileListEvent.Search -> {
                _uiState.value = _uiState.value.copy(searchQuery = event.query)
                updateSearchQuery(event.query)
            }

            is FileListEvent.ClearError -> {
                _uiState.value = _uiState.value.copy(error = null)
            }

            is FileListEvent.CreateFile -> createFile(event.fileName)
            is FileListEvent.DeleteFile -> deleteFile(event.fileUri)
            is FileListEvent.RenameFile -> renameFile(event.fileUri, event.newName)
        }
    }

    fun readFileContent(fileUri: String, onResult: (FileResult<String>) -> Unit) {
        viewModelScope.launch {
            onResult(commandRunner.readFileContent(fileUri))
        }
    }

    val previewCacheVersion: StateFlow<Long> = previewCache.version

    fun getPreviewFromCache(fileUri: String): String? = previewCache.getPreview(fileUri)

    private fun observeActiveLibrary() {
        viewModelScope.launch {
            libraryRepository.getActiveLibrary().collect { library ->
                Timber.d("StartupTrace: active library emission id=${library?.id} folderSelected=${library != null}")
                if (library != null) {
                    _uiState.value = _uiState.value.copy(
                        currentFolderUri = library.folderUri,
                        hasResolvedInitialLibrary = true,
                        isFolderSelected = true
                    )
                    _currentLibraryId.value = library.id
                } else {
                    _uiState.value = _uiState.value.copy(
                        currentFolderUri = null,
                        hasResolvedInitialLibrary = true,
                        isFolderSelected = false
                    )
                    _currentLibraryId.value = null
                }
            }
        }
    }

    private fun observeAppEvents() {
        viewModelScope.launch {
            eventBus.events.collect { event ->
                Timber.d("FileListViewModel: received event $event")
                Timber.d("StartupTrace: FileListViewModel received event=${event::class.simpleName}")
                when (event) {
                    is AppEvent.FileCreated -> {
                        triggerRefresh("event=${event::class.simpleName}")
                        // 新建的文件排在列表最前，滚动到顶部让新卡片滑入可视区域
                        _scrollToTopEvents.tryEmit(Unit)
                    }

                    is AppEvent.RefreshFileList,
                    is AppEvent.FileDeleted,
                    is AppEvent.FileUpdated -> triggerRefresh("event=${event::class.simpleName}")

                    is AppEvent.SyncStarted -> {
                        Timber.d("FileListViewModel: sync started ${event.libraryId}")
                        _uiState.value = _uiState.value.copy(
                            isSyncing = true,
                            syncProcessed = 0,
                            syncTotal = null
                        )
                    }

                    is AppEvent.SyncProgress -> {
                        _uiState.value = _uiState.value.copy(
                            syncProcessed = event.processed,
                            syncTotal = event.total
                        )
                    }

                    is AppEvent.SyncCompleted -> {
                        Timber.d("FileListViewModel: sync completed ${event.libraryId}")
                        _uiState.value = _uiState.value.copy(
                            isSyncing = false,
                            syncProcessed = 0,
                            syncTotal = null
                        )
                    }
                }
            }
        }
    }

    private fun observeSearchInput() {
        viewModelScope.launch {
            _searchQueryInput
                .debounce(300)
                .distinctUntilChanged()
                .collect { query ->
                    _currentSearchQuery.value = query
                }
        }
    }

    private fun createPager(
        libraryId: String?,
        searchQuery: String,
        scope: SearchScope,
        sort: SearchSort
    ): Flow<PagingData<MarkdownFileMeta>> {
        return Pager(
            config = AndroidPagingConfig(
                pageSize = PagingConfig.PAGE_SIZE,
                enablePlaceholders = false,
                prefetchDistance = PagingConfig.PREFETCH_DISTANCE,
                initialLoadSize = PagingConfig.INITIAL_LOAD_SIZE
            ),
            pagingSourceFactory = {
                if (searchQuery.isBlank() && libraryId != null) {
                    markdownFileDao.getFilesByLibrarySummary(libraryId)
                } else if (scope == SearchScope.ALL_LIBRARIES && searchQuery.isNotBlank()) {
                    if (sort == SearchSort.RELEVANCE) markdownFileDao.searchAllFilesByRelevance(searchQuery)
                    else markdownFileDao.searchAllFilesByModified(searchQuery)
                } else if (libraryId != null) {
                    if (sort == SearchSort.RELEVANCE) markdownFileDao.searchFilesByRelevance(libraryId, searchQuery)
                    else markdownFileDao.searchFilesSummary(libraryId, searchQuery)
                } else {
                    markdownFileDao.searchAllFilesByModified(searchQuery)
                }
            }
        ).flow.map { pagingData ->
            pagingData.map { summary -> summary.toMarkdownFileMeta() }
        }
    }

    private fun createFile(fileName: String) {
        viewModelScope.launch {
            val libraryId = _currentLibraryId.value
            val folderUri = _uiState.value.currentFolderUri
            if (libraryId == null || folderUri == null) {
                _uiState.value = _uiState.value.copy(error = "请先选择文件夹")
                return@launch
            }

            _uiState.value = _uiState.value.copy(isLoading = true)

            when (val result = commandRunner.createFile(LibraryTarget(libraryId, folderUri), fileName)) {
                is FileResult.Success -> {
                    _fileCreatedEvents.tryEmit(result.data)
                    _uiState.value = _uiState.value.copy(isLoading = false, error = null)
                }

                is FileResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.error.toUserMessage()
                    )
                }

                is FileResult.Loading -> {
                    _uiState.value = _uiState.value.copy(isLoading = true)
                }
            }
        }
    }

    private fun deleteFile(fileUri: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            when (val result = commandRunner.deleteFile(fileUri)) {
                is FileResult.Success -> {
                    triggerRefresh("delete")
                    _uiState.value = _uiState.value.copy(isLoading = false, error = null)
                }

                is FileResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.error.toUserMessage()
                    )
                }

                is FileResult.Loading -> {
                    _uiState.value = _uiState.value.copy(isLoading = true)
                }
            }
        }
    }

    private fun renameFile(fileUri: String, newName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            when (val result = commandRunner.renameFile(fileUri, newName)) {
                is FileResult.Success -> {
                    triggerRefresh("rename")
                    _uiState.value = _uiState.value.copy(isLoading = false, error = null)
                }

                is FileResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.error.toUserMessage()
                    )
                }

                is FileResult.Loading -> {
                    _uiState.value = _uiState.value.copy(isLoading = true)
                }
            }
        }
    }

    private fun triggerRefresh(source: String) {
        Timber.d(
            "FileListViewModel: trigger refresh from $source, version=${_refreshVersion.value} -> ${_refreshVersion.value + 1}"
        )
        Timber.d("StartupTrace: triggerRefresh source=$source version=${_refreshVersion.value} next=${_refreshVersion.value + 1}")
        _refreshVersion.value = _refreshVersion.value + 1
    }

    private data class PagerParams(
        val libraryId: String?,
        val searchQuery: String,
        val scope: SearchScope,
        val sort: SearchSort,
        val refreshVersion: Int
    )
}

/**
 * "随机漫步"命中的笔记
 *
 * @property meta 笔记元信息（含所属库，供跨库打开编辑器使用）
 * @property content 文件全文；读取失败时退回为预览摘要
 */
data class RandomMemo(
    val meta: MarkdownFileMeta,
    val content: String
)
