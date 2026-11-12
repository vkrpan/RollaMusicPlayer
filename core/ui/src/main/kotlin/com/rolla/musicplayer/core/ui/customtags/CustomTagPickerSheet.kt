@file:Suppress("FunctionNaming")

package com.rolla.musicplayer.core.ui.customtags

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.rolla.musicplayer.core.designsystem.component.InsetDivider
import com.rolla.musicplayer.core.designsystem.component.OneUiBottomSheet
import com.rolla.musicplayer.core.designsystem.component.OneUiCheckbox
import com.rolla.musicplayer.core.designsystem.component.OneUiPillButton
import com.rolla.musicplayer.core.designsystem.component.OneUiSearchField
import com.rolla.musicplayer.core.designsystem.component.OneUiTabRow
import com.rolla.musicplayer.core.designsystem.component.OneUiTextButton
import com.rolla.musicplayer.core.designsystem.component.SheetActionRow
import com.rolla.musicplayer.core.designsystem.icon.RollaIcons
import com.rolla.musicplayer.core.designsystem.motion.rememberReducedMotion
import com.rolla.musicplayer.core.designsystem.motion.rollaSpringOrSnap
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.emptyState
import com.rolla.musicplayer.core.designsystem.theme.songTitle
import com.rolla.musicplayer.core.model.CustomTag
import com.rolla.musicplayer.core.model.CustomTagIds
import com.rolla.musicplayer.core.model.CustomTagKind
import kotlinx.coroutines.flow.filter

/**
 * The custom tag picker (custom tags phase 2): a full-height One UI sheet with a "Tags" · "Subtags" tab row over a
 * two-page pager. Each page has its search field, a "New tag"/"New subtag" row first, then its list filtered by the
 * page's query, each entry a row that toggles as a whole (TalkBack: "Techno, checked"); an empty list says "No tags
 * yet", a query that matches nothing "No matches". "Done" closes it, and so does dismissing the sheet: every tick has
 * already reached the owner through [onToggle].
 *
 * Stateless: [state] says what to show. It opens on [CustomTagPickerState.page] (the owner sets it to the tapped
 * row's page), a tab tap or the page the pager comes to rest on reports the new page through [onPageChange], and each
 * later change of `state.page` moves the pager there (a spring, or a snap under reduced motion). [onCreateClick]
 * passes the page's trimmed query, which prefills the name dialog.
 *
 * When [onManageClick] is set (custom tags phase 3), a "Manage tags" text link sits under the list, above "Done"; the
 * owner closes the sheet and opens Manage tags. Without it there is no link.
 *
 * Insets: the kit sheet pads the status bar and the navigation bar; the content adds [imePadding] so the list and
 * "Done" stay above the keyboard while searching.
 */
// Seven parameters: the state, its five events and the modifier, fixed by the phase 2 plan's Task 3 contract, plus
// phase 3's optional Manage tags link.
@Suppress("LongParameterList")
@Composable
fun CustomTagPickerSheet(
    state: CustomTagPickerState,
    onPageChange: (CustomTagKind) -> Unit,
    onQueryChange: (CustomTagKind, String) -> Unit,
    onToggle: (CustomTagKind, Long) -> Unit,
    onCreateClick: (CustomTagKind, String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    onManageClick: (() -> Unit)? = null,
) {
    OneUiBottomSheet(onDismissRequest = onDone, modifier = modifier, skipPartiallyExpanded = true) {
        Column(modifier = Modifier.imePadding()) {
            val pagerState = rememberPagerState(initialPage = PICKER_PAGES.indexOf(state.page)) { PICKER_PAGES.size }
            PickerPagerSync(pagerState = pagerState, page = state.page, onPageChange = onPageChange)
            OneUiTabRow(
                titles = PICKER_TAB_TITLES,
                pagerState = pagerState,
                onTabClick = { index -> onPageChange(PICKER_PAGES[index]) },
            )
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { index ->
                val kind = PICKER_PAGES[index]
                PickerPage(
                    kind = kind,
                    state = state,
                    onQueryChange = { onQueryChange(kind, it) },
                    onToggle = { id -> onToggle(kind, id) },
                    onCreateClick = { onCreateClick(kind, it) },
                )
            }
            if (onManageClick != null) {
                ManageTagsLink(onClick = onManageClick)
            }
            DoneButton(onClick = onDone)
        }
    }
}

/** The "Manage tags" text link under the list, centred above "Done" (custom tags phase 3). */
@Composable
private fun ColumnScope.ManageTagsLink(onClick: () -> Unit) {
    OneUiTextButton(
        text = "Manage tags",
        onClick = onClick,
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .padding(top = RollaDimens.editorFieldGap),
    )
}

/** The sheet's full-width filled "Done" pill, inset like the list rows. */
@Composable
private fun DoneButton(onClick: () -> Unit) {
    OneUiPillButton(
        text = "Done",
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = RollaDimens.listThumbStart, vertical = RollaDimens.settingsSectionGap),
    )
}

/**
 * Keeps the pager and [page] in step. The pager opens on [page] (its initial page); afterwards each new [page] the
 * owner sets moves the pager there. Whenever the pager comes to rest, the page it rests on is reported through
 * [onPageChange] if it differs from [page]. That covers a swipe, and also a move the user cuts short: a tab tap whose
 * animation is dragged back to the old page never settles anywhere new, so watching settledPage alone would leave
 * [page] on the tapped tab while the pager shows the other one, and later taps on that tab would change nothing (fix
 * round 1, M2). A page that already matches does nothing, so the two directions never echo each other.
 */
@Composable
private fun PickerPagerSync(pagerState: PagerState, page: CustomTagKind, onPageChange: (CustomTagKind) -> Unit) {
    val reducedMotion = rememberReducedMotion()
    // The page last seen from the owner: the pager starts there, so only a later change moves it.
    var followedPage by remember { mutableStateOf(page) }
    LaunchedEffect(page) {
        if (page == followedPage) {
            return@LaunchedEffect
        }
        followedPage = page
        val target = PICKER_PAGES.indexOf(page)
        if (pagerState.targetPage != target) {
            pagerState.animateScrollToPage(target, animationSpec = rollaSpringOrSnap(reducedMotion))
        }
    }
    val currentPage by rememberUpdatedState(page)
    val currentOnPageChange by rememberUpdatedState(onPageChange)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress to pagerState.settledPage }
            .filter { (scrolling, _) -> !scrolling }
            .collect { (_, settled) ->
                val settledKind = PICKER_PAGES[settled]
                if (settledKind != currentPage) currentOnPageChange(settledKind)
            }
    }
}

/** One page: the search field over the page's list (see [PickerList]). */
@Composable
private fun PickerPage(
    kind: CustomTagKind,
    state: CustomTagPickerState,
    onQueryChange: (String) -> Unit,
    onToggle: (Long) -> Unit,
    onCreateClick: (String) -> Unit,
) {
    val query = state.queryOf(kind)
    Column(modifier = Modifier.fillMaxSize()) {
        OneUiSearchField(
            query = query,
            onQueryChange = onQueryChange,
            placeholder = if (kind == CustomTagKind.TAG) "Search tags" else "Search subtags",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = RollaDimens.listThumbStart, vertical = RollaDimens.editorFieldGap),
        )
        PickerList(
            kind = kind,
            all = state.entriesOf(kind),
            query = query,
            selectedIds = state.selectedIdsOf(kind),
            onToggle = onToggle,
            onCreateClick = onCreateClick,
            modifier = Modifier.weight(1f),
        )
    }
}

/** The "New" row (handing over the trimmed [query]), then the entries [query] matches, or the empty state. */
// Seven inputs of one private list: its slice of the picker state, its two events and the modifier.
@Suppress("LongParameterList")
@Composable
private fun PickerList(
    kind: CustomTagKind,
    all: List<CustomTag>,
    query: String,
    selectedIds: Set<Long>,
    onToggle: (Long) -> Unit,
    onCreateClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown = remember(all, query) { filterCustomTags(all, query) }
    LazyColumn(modifier = modifier) {
        item(key = NEW_ENTRY_KEY) {
            Column {
                SheetActionRow(
                    icon = RollaIcons.Add,
                    label = kind.newEntryLabel(),
                    onClick = { onCreateClick(query.trim()) },
                )
                // A hairline only above entries; above an empty-state message the row is the list's last.
                if (shown.isNotEmpty()) {
                    InsetDivider(startInset = RollaDimens.listTextStart)
                }
            }
        }
        if (shown.isEmpty()) {
            item(key = EMPTY_STATE_KEY) { PickerEmptyState(text = emptyStateText(kind, all.isEmpty())) }
        }
        itemsIndexed(items = shown, key = { _, tag -> tag.id }) { index, tag ->
            PickerRow(
                name = tag.name,
                checked = tag.id in selectedIds,
                onToggle = { onToggle(tag.id) },
                showDivider = index < shown.lastIndex,
            )
        }
    }
}

private fun CustomTagPickerState.entriesOf(kind: CustomTagKind): List<CustomTag> =
    if (kind == CustomTagKind.TAG) tags else subTags

private fun CustomTagPickerState.queryOf(kind: CustomTagKind): String =
    if (kind == CustomTagKind.TAG) tagQuery else subTagQuery

private fun CustomTagPickerState.selectedIdsOf(kind: CustomTagKind): Set<Long> =
    if (kind == CustomTagKind.TAG) selected.tagIds else selected.subTagIds

/**
 * One entry: the checkbox in the thumbnail column (in line with the "New" row's + icon), then the name at
 * listTextStart. The whole row is one Checkbox toggle, merging the name and the state for TalkBack; the checkbox is
 * purely visual.
 */
@Composable
private fun PickerRow(name: String, checked: Boolean, onToggle: () -> Unit, showDivider: Boolean) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = RollaDimens.singleLineRowHeight)
                .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
                .padding(horizontal = RollaDimens.listThumbStart),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(RollaDimens.listThumb), contentAlignment = Alignment.Center) {
                OneUiCheckbox(checked = checked, onCheckedChange = null)
            }
            Spacer(modifier = Modifier.width(RollaDimens.listThumbTextGap))
            Text(
                text = name,
                style = MaterialTheme.typography.songTitle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // No hairline under the list's last row.
        if (showDivider) {
            InsetDivider(startInset = RollaDimens.listTextStart)
        }
    }
}

/** A page's one-line empty state, in line with the entry names. */
@Composable
private fun PickerEmptyState(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.emptyState,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = RollaDimens.listTextStart, vertical = RollaDimens.editorFieldGap),
    )
}

/** "No tags yet"/"No subtags yet" for an empty list, else "No matches" (the query filtered everything out). */
private fun emptyStateText(kind: CustomTagKind, listIsEmpty: Boolean): String = when {
    !listIsEmpty -> "No matches"
    kind == CustomTagKind.TAG -> "No tags yet"
    else -> "No subtags yet"
}

/** The pager's pages, in tab order. */
private val PICKER_PAGES = listOf(CustomTagKind.TAG, CustomTagKind.SUBTAG)
private val PICKER_TAB_TITLES = listOf("Tags", "Subtags")

private const val NEW_ENTRY_KEY = "new_entry"
private const val EMPTY_STATE_KEY = "empty_state"

// One page without the ModalBottomSheet window, which previews cannot show.
@Suppress("UnusedPrivateMember")
@Preview(name = "Custom Tag Picker Page - Light")
@Preview(name = "Custom Tag Picker Page - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewCustomTagPickerPage() {
    RollaMusicPlayerTheme {
        PickerPage(
            kind = CustomTagKind.TAG,
            state = CustomTagPickerState(
                tags = listOf(
                    CustomTag(kind = CustomTagKind.TAG, id = 1L, name = "Acid"),
                    CustomTag(kind = CustomTagKind.TAG, id = 2L, name = "Techno"),
                    CustomTag(kind = CustomTagKind.TAG, id = 3L, name = "Minimal"),
                ),
                subTags = emptyList(),
                selected = CustomTagIds(tagIds = setOf(2L), subTagIds = emptySet()),
            ),
            onQueryChange = {},
            onToggle = {},
            onCreateClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(name = "Custom Tag Picker Page Empty - Light")
@Preview(name = "Custom Tag Picker Page Empty - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewCustomTagPickerPageEmpty() {
    RollaMusicPlayerTheme {
        PickerPage(
            kind = CustomTagKind.SUBTAG,
            state = CustomTagPickerState(tags = emptyList(), subTags = emptyList(), selected = CustomTagIds.EMPTY),
            onQueryChange = {},
            onToggle = {},
            onCreateClick = {},
        )
    }
}
