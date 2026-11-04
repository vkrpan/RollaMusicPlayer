package com.rolla.musicplayer.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import com.rolla.musicplayer.core.designsystem.icon.RollaIcons
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens

/**
 * The One UI checkbox (Phase 7, 6b ruling P4): a round [RollaDimens.checkboxSize] box, a primary disc with an
 * onPrimary check when [checked], else a [RollaDimens.checkboxStroke] onSurfaceVariant ring (outline is only 2.60:1 on
 * the panel; onSurfaceVariant is 6.45:1).
 *
 * Pass onCheckedChange = null when a row owns the click and the selected semantics (SongListItem's selection mode):
 * the checkbox is then purely visual and occupies exactly its box. With a handler it is a [Role.Checkbox] toggle with a
 * [RollaDimens.minTouchTarget] target, the box centred in it.
 */
@Composable
fun OneUiCheckbox(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.checkboxInteraction(checked, onCheckedChange),
        contentAlignment = Alignment.Center,
    ) {
        val box = Modifier.size(RollaDimens.checkboxSize).clip(CircleShape)
        if (checked) {
            Box(box.background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = RollaIcons.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(RollaDimens.checkboxGlyph),
                )
            }
        } else {
            Box(box.border(RollaDimens.checkboxStroke, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape))
        }
    }
}

private fun Modifier.checkboxInteraction(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?): Modifier =
    if (onCheckedChange == null) {
        this
    } else {
        minimumInteractiveComponentSize()
            .clip(CircleShape)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
    }
