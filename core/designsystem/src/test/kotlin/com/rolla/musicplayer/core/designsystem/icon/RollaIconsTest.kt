package com.rolla.musicplayer.core.designsystem.icon

import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** icon() names every vector "Rolla.<Name>". */
private const val NAME_PREFIX = "Rolla."
private const val LINE_WIDTH = 1.6f
private const val NOTE_LINE_WIDTH = 2.0f

private val CONTRACT_NAMES = setOf(
    "Search", "More", "Add", "Sort", "Shuffle", "Queue", "ChevronBack", "ChevronDown", "Volume", "EqualizerBars",
    "Heart", "HeartFilled", "PlayOrder", "Repeat", "RepeatOne", "Play", "Pause", "SkipPrevious", "SkipNext",
    "MusicNote", "FolderBadge", "Close", "Edit", "Check", "PlaylistAdd", "Album", "Person",
)
private val DIRECTIONAL_NAMES = setOf("ChevronBack", "Sort", "Queue", "PlaylistAdd", "Volume")
private val SOLID_ONLY_NAMES = setOf("More", "Play", "Pause", "SkipPrevious", "SkipNext", "HeartFilled")

class RollaIconsTest {

    @Test
    fun everyIconBuildsOnThe24GridWithAtLeastOnePath() {
        RollaIcons.all.forEach { icon ->
            assertEquals(icon.name, 24f, icon.viewportWidth, 0f)
            assertEquals(icon.name, 24f, icon.viewportHeight, 0f)
            assertTrue("${icon.name} has no paths", icon.root.size > 0)
        }
    }

    @Test
    fun iconSetIsCompleteAndUniquelyNamed() {
        val names = RollaIcons.all.map { it.name }
        assertEquals(27, names.size)
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun iconNamesMatchTheContract() {
        assertEquals(
            CONTRACT_NAMES.map { NAME_PREFIX + it }.toSet(),
            RollaIcons.all.map { it.name }.toSet(),
        )
    }

    @Test
    fun onlyDirectionalIconsAutoMirror() {
        assertEquals(
            DIRECTIONAL_NAMES.map { NAME_PREFIX + it }.toSet(),
            RollaIcons.all.filter { it.autoMirror }.map { it.name }.toSet(),
        )
    }

    @Test
    fun strokeStyleMatchesSpec() {
        RollaIcons.all.forEach { icon ->
            val shortName = icon.name.removePrefix(NAME_PREFIX)
            val expectedWidth = if (shortName == "MusicNote") NOTE_LINE_WIDTH else LINE_WIDTH
            val paths = icon.root.paths()
            paths.forEach { path ->
                if (path.stroke != null) {
                    assertLinePath(icon.name, path, expectedWidth)
                } else {
                    assertNotNull("${icon.name}: path has neither stroke nor fill", path.fill)
                }
            }
            if (shortName in SOLID_ONLY_NAMES) {
                assertTrue("${icon.name}: must contain only solid paths", paths.all { it.stroke == null })
            } else {
                assertTrue("${icon.name}: must contain at least one line path", paths.any { it.stroke != null })
            }
        }
    }

    private fun assertLinePath(iconName: String, path: VectorPath, expectedWidth: Float) {
        assertNull("$iconName: line path must not have a fill", path.fill)
        assertEquals("$iconName: line cap", StrokeCap.Round, path.strokeLineCap)
        assertEquals("$iconName: line join", StrokeJoin.Round, path.strokeLineJoin)
        assertEquals("$iconName: stroke width", expectedWidth, path.strokeLineWidth, 0f)
    }
}

/** Every VectorPath under this group, descending into nested VectorGroups. */
private fun VectorGroup.paths(): List<VectorPath> = flatMap { node ->
    when (node) {
        is VectorGroup -> node.paths()
        is VectorPath -> listOf(node)
    }
}
