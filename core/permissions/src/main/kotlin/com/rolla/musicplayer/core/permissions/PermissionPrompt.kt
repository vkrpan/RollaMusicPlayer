package com.rolla.musicplayer.core.permissions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.rolla.musicplayer.core.designsystem.component.ArtworkPlaceholder
import com.rolla.musicplayer.core.designsystem.component.ContentPanel
import com.rolla.musicplayer.core.designsystem.component.OneUiPillButton
import com.rolla.musicplayer.core.designsystem.component.homeListBottomPadding
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.featureCard

/**
 * The explain-first prompt (Phase 6b ruling 8) inside a [ContentPanel], where Home's tab pages sit: a large music-note
 * placeholder at feature-card size, the privacy-forward [message] centred in bodyLarge onSurface, and a filled
 * [OneUiPillButton] labelled [buttonText]. Tokens only; the placeholder is decorative (no content description).
 *
 * Like Home's lists, the prompt pads its bottom by [homeListBottomPadding], so the button can always be scrolled clear
 * of the floating pill and the navigation bar. It scrolls when it does not fit (a short screen, a large font) and is
 * centred in the space above that padding when it does: the column is at least the panel's height.
 */
@Composable
internal fun PermissionPrompt(
    message: String,
    buttonText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ContentPanel(modifier = modifier) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(horizontal = RollaDimens.screenEdge)
                    .padding(bottom = homeListBottomPadding()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                PromptBody(message = message, buttonText = buttonText, onClick = onClick)
            }
        }
    }
}

/** The placeholder, the message and the button, settingsSectionGap apart, emitted into the prompt's column. */
@Composable
private fun PromptBody(message: String, buttonText: String, onClick: () -> Unit) {
    ArtworkPlaceholder(
        modifier = Modifier.size(RollaDimens.featureCardSize),
        shape = MaterialTheme.shapes.featureCard,
        large = true,
        glyphSize = RollaDimens.npArtGlyph,
    )
    Spacer(modifier = Modifier.height(RollaDimens.settingsSectionGap))
    Text(
        text = message,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(RollaDimens.settingsSectionGap))
    OneUiPillButton(text = buttonText, onClick = onClick)
}
