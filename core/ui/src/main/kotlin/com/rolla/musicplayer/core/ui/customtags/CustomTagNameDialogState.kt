package com.rolla.musicplayer.core.ui.customtags

import androidx.compose.runtime.Immutable
import com.rolla.musicplayer.core.model.CustomTagKind

/**
 * The open "New tag"/"New subtag" dialog ([CustomTagNameDialog]): which list it adds to, the name it opens with (the
 * picker's search text) and the owner's [error] for the last attempt ("That name is already in the list"), or null.
 */
@Immutable
data class CustomTagNameDialogState(
    val kind: CustomTagKind,
    val initialName: String,
    val error: String? = null,
)
