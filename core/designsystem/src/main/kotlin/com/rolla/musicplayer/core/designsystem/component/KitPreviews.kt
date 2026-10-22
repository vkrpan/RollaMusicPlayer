// Preview scaffolding sizes and sample slider values; not design tokens.
@file:Suppress("MagicNumber")

package com.rolla.musicplayer.core.designsystem.component

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rolla.musicplayer.core.designsystem.icon.RollaIcons
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.nowPlayingGradient
import com.rolla.musicplayer.core.designsystem.theme.songTitle

private val PreviewTabs = listOf("Favourites", "Playlists", "Tracks", "Albums", "Artists", "Folders")

@Suppress("UnusedPrivateMember")
@Preview(name = "Kit - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, heightDp = 1100)
@Preview(name = "Kit - Light", uiMode = Configuration.UI_MODE_NIGHT_NO, heightDp = 1100)
@Preview(name = "Kit - Large font", uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 1.5f, heightDp = 1300)
@Composable
private fun PreviewOneUiKit() {
    RollaMusicPlayerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column {
                KitHeaderSection()
                KitPanelSection()
                KitControlsSection()
                KitIconsSection()
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .nowPlayingBackground(MaterialTheme.colorScheme.nowPlayingGradient),
                )
            }
        }
    }
}

// A 432 dp rail fits all 27 labels at fontScale 1; at 1.5 the letter slot grows to the line height and labels drop.
@Suppress("UnusedPrivateMember")
@Preview(name = "Fast scroll rail - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Fast scroll rail - Light", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Fast scroll rail - Large font", uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 1.5f)
@Composable
private fun PreviewFastScrollRail() {
    PreviewRail(height = 432.dp)
}

// A 200 dp rail has 7.4 dp slots, shorter than a letter's line: every 3rd label shows (at fontScale 1 and 1.5), each
// at its natural height, centred on its own slot and unclipped.
@Suppress("UnusedPrivateMember")
@Preview(name = "Fast scroll rail, short - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Fast scroll rail, short - Light", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Fast scroll rail, short - Large font", uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 1.5f)
@Composable
private fun PreviewFastScrollRailShort() {
    PreviewRail(height = 200.dp)
}

// The in-surface parts of the sheet/dialog kit, on the sheet's own surfaceContainerHigh.
@Suppress("UnusedPrivateMember")
@Preview(name = "Sheet kit - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Sheet kit - Light", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Sheet kit - Large font", uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 1.5f)
@Composable
private fun PreviewSheetKit() {
    RollaMusicPlayerTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column {
                SheetActionRow(icon = RollaIcons.PlaylistAdd, label = "Add to playlist", onClick = {})
                SheetActionRow(icon = RollaIcons.Edit, label = "Edit tags", onClick = {})
                SheetActionRow(
                    icon = RollaIcons.Delete,
                    label = "Remove from playlist",
                    onClick = {},
                    destructive = true,
                )
                OneUiMenuItem(text = "Go to album", onClick = {})
                SheetKitDialogControls()
            }
        }
    }
}

@Composable
private fun SheetKitDialogControls() {
    var name by remember { mutableStateOf("Road trip") }
    OneUiTextField(
        value = name,
        onValueChange = { name = it },
        label = "Playlist name",
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.padding(horizontal = RollaDimens.listThumbStart),
    )
    Row(Modifier.padding(16.dp)) {
        OneUiTextButton(text = "Cancel", onClick = {})
        OneUiTextButton(text = "Delete", onClick = {}, destructive = true)
        OneUiTextButton(text = "Save", onClick = {}, enabled = false)
    }
}

// The Search screen's field (Phase 6b): empty with its placeholder, then with a query and the Clear search button.
@Suppress("UnusedPrivateMember")
@Preview(name = "Search field - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Search field - Light", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Search field - Large font", uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 1.5f)
@Composable
private fun PreviewOneUiSearchField() {
    RollaMusicPlayerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OneUiSearchField(
                    query = "",
                    onQueryChange = {},
                    placeholder = "Search",
                    modifier = Modifier.fillMaxWidth(),
                )
                OneUiSearchField(
                    query = "Road trip",
                    onQueryChange = {},
                    placeholder = "Search",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// The tag editor's form parts (Phase 6b) on a surfaceContainer card: tonal and filled pills, a saving pill, a disabled
// pill, and a field in error with its destructiveText supporting text.
@Suppress("UnusedPrivateMember")
@Preview(name = "Pill buttons - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Pill buttons - Light", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Pill buttons - Large font", uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 1.5f)
@Composable
private fun PreviewOneUiPillButton() {
    RollaMusicPlayerTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OneUiPillButton(text = "Cancel", onClick = {}, tonal = true)
                    OneUiPillButton(text = "Save", onClick = {})
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OneUiPillButton(text = "Save", onClick = {}, loading = true, loadingDescription = "Saving")
                    OneUiPillButton(text = "Save", onClick = {}, enabled = false)
                }
                OneUiTextField(
                    value = "20255",
                    onValueChange = {},
                    label = "Year",
                    isError = true,
                    supportingText = "Enter a year such as 1999",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview(name = "Dialog - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Dialog - Light", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dialog - Large font", uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 1.5f)
@Composable
private fun PreviewOneUiDialog() {
    RollaMusicPlayerTheme {
        OneUiDialog(
            onDismissRequest = {},
            title = "Delete playlist?",
            confirmButton = { OneUiTextButton(text = "Delete", onClick = {}, destructive = true) },
            dismissButton = { OneUiTextButton(text = "Cancel", onClick = {}) },
            text = { Text("\"Road trip\" will be deleted. Its tracks stay in your library.") },
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(name = "Bottom sheet - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Bottom sheet - Light", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Bottom sheet - Large font", uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 1.5f)
@Composable
private fun PreviewOneUiBottomSheet() {
    RollaMusicPlayerTheme {
        OneUiBottomSheet(onDismissRequest = {}, title = "Add to playlist") {
            SheetActionRow(icon = RollaIcons.Add, label = "New playlist", onClick = {})
            InsetDivider(startInset = RollaDimens.listTextStart)
            PreviewTextRow("Road trip")
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview(name = "Dropdown menu - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, heightDp = 240)
@Preview(name = "Dropdown menu - Light", uiMode = Configuration.UI_MODE_NIGHT_NO, heightDp = 240)
@Preview(
    name = "Dropdown menu - Large font",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    fontScale = 1.5f,
    heightDp = 300,
)
@Composable
private fun PreviewOneUiDropdownMenu() {
    RollaMusicPlayerTheme {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Box {
                OneUiDropdownMenu(expanded = true, onDismissRequest = {}) {
                    OneUiMenuItem(text = "Edit tags", onClick = {})
                    OneUiMenuItem(text = "Go to album", onClick = {})
                    OneUiMenuItem(text = "Settings", onClick = {})
                }
            }
        }
    }
}

@Composable
private fun PreviewRail(height: Dp) {
    RollaMusicPlayerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            FastScrollRail(
                sections = buildFastScrollSections(listOf("Abba", "Mango", "Zed")),
                onSectionSelected = {},
                modifier = Modifier.height(height),
            )
        }
    }
}

// The container over a list that scrolls: 50 rows, "Alpha 0".."Alpha 24" then "Mango 0".."Mango 24", so A and M are
// populated. 432 dp fits all 27 labels at fontScale 1; at 1.5 labels drop. No bottom padding: there is no pill here.
@Suppress("UnusedPrivateMember")
@Preview(name = "Fast scroll container - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Fast scroll container - Light", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Fast scroll container - Large font", uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 1.5f)
@Composable
private fun PreviewFastScrollContainer() {
    val rows = remember { List(25) { "Alpha $it" } + List(25) { "Mango $it" } }
    val sections = remember(rows) { buildFastScrollSections(rows) }
    RollaMusicPlayerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            val listState = rememberLazyListState()
            FastScrollContainer(
                listState = listState,
                sections = sections,
                railBottomPadding = 0.dp,
                modifier = Modifier.fillMaxWidth().height(432.dp),
            ) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(rows, key = { it }) { row -> PreviewTextRow(row) }
                }
            }
        }
    }
}

@Composable
private fun PreviewTextRow(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(RollaDimens.singleLineRowHeight)
            .padding(start = RollaDimens.listThumbStart),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text = text, style = MaterialTheme.typography.songTitle, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun KitHeaderSection() {
    OneUiTopBar(title = "Rolla Music", windowInsets = WindowInsets(0)) {
        OneUiIconButton(icon = RollaIcons.Add, contentDescription = "Add", onClick = {})
        OneUiIconButton(icon = RollaIcons.Search, contentDescription = "Search", onClick = {})
        OneUiIconButton(icon = RollaIcons.More, contentDescription = "More", onClick = {})
    }
    OneUiTabRow(
        titles = PreviewTabs,
        pagerState = rememberPagerState(initialPage = 2) { PreviewTabs.size },
        onTabClick = {},
    )
    OneUiDetailTopBar(title = "Equaliser", onNavigateUp = {}, windowInsets = WindowInsets(0))
}

@Composable
private fun KitPanelSection() {
    ContentPanel(modifier = Modifier.height(170.dp)) {
        SortHeader(label = "Name") {
            CircleIconButton(icon = RollaIcons.Shuffle, contentDescription = "Shuffle", onClick = {})
            CircleIconButton(icon = RollaIcons.Play, contentDescription = "Play", onClick = {})
        }
        Row(
            Modifier.padding(start = RollaDimens.listThumbStart),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ArtworkPlaceholder(modifier = Modifier.size(RollaDimens.listThumb))
            // A folder thumbnail: the badge over the placeholder's bottom-end corner (FolderRow's anatomy).
            Box(Modifier.size(RollaDimens.listThumb)) {
                ArtworkPlaceholder(modifier = Modifier.matchParentSize())
                FolderBadge(Modifier.align(Alignment.BottomEnd).padding(RollaDimens.folderBadgeInset))
            }
        }
        InsetDivider(startInset = RollaDimens.listTextStart, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun KitControlsSection() {
    var checked by remember { mutableStateOf(true) }
    var speed by remember { mutableFloatStateOf(1f) }
    var gain by remember { mutableFloatStateOf(300f) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OneUiSwitch(checked = checked, onCheckedChange = { checked = it })
            OneUiSwitch(checked = !checked, onCheckedChange = { checked = !it })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(RollaDimens.chipGap)) {
            PillChip(label = "Balanced", selected = true, onClick = {}, modifier = Modifier.width(142.dp))
            PillChip(label = "Bass boost", selected = false, onClick = {}, modifier = Modifier.width(142.dp))
        }
        OneUiSlider(value = speed, onValueChange = { speed = it }, valueRange = 0.5f..2f)
        Box(Modifier.size(width = RollaDimens.eqColumnPitch, height = 160.dp)) {
            EqVerticalSlider(
                value = gain,
                onValueChange = { gain = it },
                valueRange = -1500f..1500f,
                contentDescription = "40 Hz gain",
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KitIconsSection() {
    FlowRow(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RollaIcons.all.forEach { icon ->
            Icon(imageVector = icon, contentDescription = icon.name, tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}
