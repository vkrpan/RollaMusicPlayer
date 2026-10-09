package com.rolla.musicplayer.core.designsystem.theme

import androidx.compose.ui.text.style.LineHeightStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class TypographyTokensTest {

    private val expected = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

    @Test
    fun everyMaterialRoleUsesCenteredUntrimmedLineHeight() {
        val t = RollaTypography
        listOf(
            t.displayLarge, t.displayMedium, t.displaySmall, t.headlineLarge, t.headlineMedium, t.headlineSmall,
            t.titleLarge, t.titleMedium, t.titleSmall, t.bodyLarge, t.bodyMedium, t.bodySmall,
            t.labelLarge, t.labelMedium, t.labelSmall,
        ).forEachIndexed { index, style -> assertEquals("role #$index", expected, style.lineHeightStyle) }
    }
}
