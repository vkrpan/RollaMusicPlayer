package com.rolla.musicplayer.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import com.rolla.musicplayer.core.designsystem.icon.RollaIcons
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.accentText

/**
 * The One UI search field (spec §7.3, §8.7; Phase 6b ruling 1): a [RollaDimens.minTouchTarget]-tall
 * surfaceContainerHigh pill (CircleShape) with a leading Search glyph and, while [query] is empty, a
 * [placeholder], both in onSurfaceVariant. While [query] is not empty a trailing "Clear search" button shows, which
 * calls onQueryChange(""). There is no indicator line, the cursor is accentText, and the IME action is Search, which
 * calls [onSearch]. Contrast on surfaceContainerHigh (ContrastTest): query onSurface 13.41:1 dark / 15.41:1 light,
 * placeholder onSurfaceVariant 4.95:1 / 4.82:1.
 *
 * Usage (the Search screen's bar, beside its Navigate up button):
 * ```
 * OneUiSearchField(
 *     query = query,
 *     onQueryChange = onQueryChange,
 *     placeholder = "Search",
 *     modifier = Modifier.weight(1f).focusRequester(focusRequester),
 *     onSearch = onSubmit,
 * )
 * ```
 */
@Composable
fun OneUiSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSearch: () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val textStyle = MaterialTheme.typography.bodyLarge
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .heightIn(min = RollaDimens.minTouchTarget)
            .clip(CircleShape)
            .background(colors.surfaceContainerHigh),
        textStyle = textStyle.copy(color = colors.onSurface),
        singleLine = true,
        cursorBrush = SolidColor(colors.accentText),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        decorationBox = { innerTextField ->
            SearchFieldDecoration(
                query = query,
                placeholder = placeholder,
                textStyle = textStyle,
                onClear = { onQueryChange("") },
                innerTextField = innerTextField,
            )
        },
    )
}

/** Search glyph · query (or placeholder) · Clear button; the end keeps [RollaDimens.searchFieldStart] when empty. */
@Composable
private fun SearchFieldDecoration(
    query: String,
    placeholder: String,
    textStyle: TextStyle,
    onClear: () -> Unit,
    innerTextField: @Composable () -> Unit,
) {
    val gray = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RollaDimens.minTouchTarget)
            .padding(start = RollaDimens.searchFieldStart),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = RollaIcons.Search,
            contentDescription = null,
            tint = gray,
            modifier = Modifier.size(RollaDimens.iconSize),
        )
        Spacer(Modifier.width(RollaDimens.searchFieldGlyphGap))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(placeholder, style = textStyle, color = gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            innerTextField()
        }
        SearchFieldEnd(showClear = query.isNotEmpty(), onClear = onClear)
    }
}

/** The trailing "Clear search" button while there is a query; otherwise [RollaDimens.searchFieldStart] of space. */
@Composable
private fun SearchFieldEnd(showClear: Boolean, onClear: () -> Unit) {
    if (showClear) {
        OneUiIconButton(
            icon = RollaIcons.Close,
            contentDescription = "Clear search",
            onClick = onClear,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Spacer(Modifier.width(RollaDimens.searchFieldStart))
    }
}
