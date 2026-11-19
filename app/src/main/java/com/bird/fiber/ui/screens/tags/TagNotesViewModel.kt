package com.bird.fiber.ui.screens.tags

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.map
import com.bird.fiber.data.local.TagRepository
import com.bird.fiber.data.local.TagBackfillCoordinator
import com.bird.fiber.data.local.library.toMarkdownFileMeta
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class TagNotesViewModel @Inject constructor(
    savedState: SavedStateHandle,
    tags: TagRepository,
    backfill: TagBackfillCoordinator
) : ViewModel() {
    val path: String = checkNotNull(savedState["tagPath"])
    val count = tags.count(path).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val progress = backfill.state
    val displayPath = tags.displayPath(path)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), path)
    val pager = Pager(PagingConfig(pageSize = 30, enablePlaceholders = false)) {
        tags.notes(path)
    }.flow.map { data -> data.map { it.toMarkdownFileMeta() } }.cachedIn(viewModelScope)
}
