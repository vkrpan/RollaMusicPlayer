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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rolla.musicplayer.core.designsystem.icon.RollaIcons
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.nowPlayingGradient

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
        Row(Modifier.padding(start = RollaDimens.listThumbStart)) {
            ArtworkPlaceholder(modifier = Modifier.size(RollaDimens.listThumb))
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
