package com.rolla.musicplayer.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rolla.musicplayer.core.data.repository.FolderRepository
import com.rolla.musicplayer.core.model.Folder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The Home pager's Folders tab: every folder that holds songs, by display name. null until Room answers. */
@HiltViewModel
class FoldersTabViewModel @Inject constructor(
    folderRepository: FolderRepository,
) : ViewModel() {

    val folders: StateFlow<List<Folder>?> = folderRepository.observeFolders()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
            initialValue = null,
        )
}
