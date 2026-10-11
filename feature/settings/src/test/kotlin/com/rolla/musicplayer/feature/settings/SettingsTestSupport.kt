package com.rolla.musicplayer.feature.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult

/**
 * The resolved colour a Text node draws with, read through its TextLayoutResult (the merged style, so a colour that
 * comes from LocalContentColor, e.g. a TextButton's contentColor, shows up too). Unmerged, because a row merges its
 * texts into one node.
 */
internal fun SemanticsNodeInteractionsProvider.textColorOf(text: String): Color {
    val layouts = mutableListOf<TextLayoutResult>()
    onNodeWithText(text, useUnmergedTree = true)
        .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
    return layouts.single().layoutInput.style.color
}
