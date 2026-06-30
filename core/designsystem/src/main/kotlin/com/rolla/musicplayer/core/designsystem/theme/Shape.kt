package com.rolla.musicplayer.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Shape tokens for RollaMusicPlayer.
 *
 * Implements `.claude/rules/ui-style-guide.md` §4. Soft, heavily-rounded surfaces.
 * Owned by m3-design-system-agent. Use `MaterialTheme.shapes.*`; for pills/circles use
 * `CircleShape` directly (chips, the mini-player container, the round Play button).
 *
 * Mapping:
 *  - extraSmall (8dp)   small inner elements
 *  - small      (12dp)  list/thumbnail artwork
 *  - medium     (16dp)  inline cards, dialogs
 *  - large      (24dp)  content surface panel, settings group cards
 *  - extraLarge (28dp)  big feature cards, now-playing artwork
 */
val RollaShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
