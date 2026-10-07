package com.rolla.musicplayer.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * One UI shape scale (spec §5.3). `large` and `extraLarge` share the measured 26 dp panel radius. Stock M3 dialogs
 * and sheets default to `extraLarge`, so they match the panels without overrides. Owned by m3-design-system-agent.
 */
val RollaShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(11.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(RollaDimens.panelRadius),
    extraLarge = RoundedCornerShape(RollaDimens.panelRadius),
)

private val FeatureCardShape = RoundedCornerShape(20.dp)
private val PanelTopShape = RoundedCornerShape(topStart = RollaDimens.panelRadius, topEnd = RollaDimens.panelRadius)

/** Playlist feature cards and album-grid cards (143 dp, 20 dp radius). */
val Shapes.featureCard: Shape
    get() = FeatureCardShape

/** The content panel: rounded top corners only; it runs to the bottom edge of the screen. */
val Shapes.panelTop: Shape
    get() = PanelTopShape
