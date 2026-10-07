@file:Suppress("MagicNumber") // Vector path coordinates on the 24-unit grid.

package com.rolla.musicplayer.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.unit.dp

private const val VIEWPORT = 24f
private const val LINE_WIDTH = 1.6f
private const val NOTE_LINE_WIDTH = 2.0f

private inline fun icon(
    name: String,
    autoMirror: Boolean = false,
    block: ImageVector.Builder.() -> Unit,
): ImageVector =
    ImageVector.Builder(
        name = "Rolla.$name",
        defaultWidth = VIEWPORT.dp,
        defaultHeight = VIEWPORT.dp,
        viewportWidth = VIEWPORT,
        viewportHeight = VIEWPORT,
        autoMirror = autoMirror,
    ).apply(block).build()

private fun ImageVector.Builder.line(strokeWidth: Float = LINE_WIDTH, path: PathBuilder.() -> Unit) {
    addPath(
        pathData = PathData(path),
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = strokeWidth,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
}

private fun ImageVector.Builder.solid(path: PathBuilder.() -> Unit) {
    addPath(pathData = PathData(path), fill = SolidColor(Color.Black))
}

private fun PathBuilder.circle(cx: Float, cy: Float, radius: Float) {
    moveTo(cx + radius, cy)
    arcTo(radius, radius, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = cx - radius, y1 = cy)
    arcTo(radius, radius, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = cx + radius, y1 = cy)
    close()
}

private fun PathBuilder.roundedRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float) {
    moveTo(left + radius, top)
    lineTo(right - radius, top)
    arcTo(radius, radius, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = right, y1 = top + radius)
    lineTo(right, bottom - radius)
    arcTo(radius, radius, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = right - radius, y1 = bottom)
    lineTo(left + radius, bottom)
    arcTo(radius, radius, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = left, y1 = bottom - radius)
    lineTo(left, top + radius)
    arcTo(radius, radius, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = left + radius, y1 = top)
    close()
}

private fun PathBuilder.heart() {
    moveTo(12f, 20f)
    curveTo(12f, 20f, 3f, 14.5f, 3f, 8.8f)
    curveTo(3f, 6.1f, 5.1f, 4f, 7.7f, 4f)
    curveTo(9.5f, 4f, 11f, 5f, 12f, 6.5f)
    curveTo(13f, 5f, 14.5f, 4f, 16.3f, 4f)
    curveTo(18.9f, 4f, 21f, 6.1f, 21f, 8.8f)
    curveTo(21f, 14.5f, 12f, 20f, 12f, 20f)
    close()
}

private fun PathBuilder.repeatLoop() {
    moveTo(17f, 4.5f)
    lineTo(20f, 7.5f)
    lineTo(17f, 10.5f)
    moveTo(20f, 7.5f)
    lineTo(8f, 7.5f)
    curveTo(5.8f, 7.5f, 4f, 9.3f, 4f, 11.5f)
    lineTo(4f, 12f)
    moveTo(7f, 19.5f)
    lineTo(4f, 16.5f)
    lineTo(7f, 13.5f)
    moveTo(4f, 16.5f)
    lineTo(16f, 16.5f)
    curveTo(18.2f, 16.5f, 20f, 14.7f, 20f, 12.5f)
    lineTo(20f, 12f)
}

/**
 * The One UI icon set (spec §5.5), hand-authored on a 24×24 grid. Line icons use a 1.6 stroke with round
 * caps and joins; transport glyphs are solid. Tint them through Icon(tint = …).
 * Owned by m3-design-system-agent.
 */
object RollaIcons {
    val Search: ImageVector by lazy {
        icon("Search") {
            line { circle(cx = 10.5f, cy = 10.5f, radius = 6.5f) }
            line {
                moveTo(15.3f, 15.3f)
                lineTo(20f, 20f)
            }
        }
    }

    val More: ImageVector by lazy {
        icon("More") {
            solid {
                circle(cx = 12f, cy = 5.5f, radius = 1.6f)
                circle(cx = 12f, cy = 12f, radius = 1.6f)
                circle(cx = 12f, cy = 18.5f, radius = 1.6f)
            }
        }
    }

    val Add: ImageVector by lazy {
        icon("Add") {
            line {
                moveTo(12f, 5f)
                lineTo(12f, 19f)
                moveTo(5f, 12f)
                lineTo(19f, 12f)
            }
        }
    }

    /** Down arrow with a single left barb, then three lines that get shorter going down (the "⇅ Name" sort glyph). */
    val Sort: ImageVector by lazy {
        icon("Sort", autoMirror = true) {
            line {
                moveTo(6f, 4f)
                lineTo(6f, 20f)
                lineTo(3f, 17f)
                moveTo(10f, 5.5f)
                lineTo(21f, 5.5f)
                moveTo(10f, 12f)
                lineTo(18f, 12f)
                moveTo(10f, 18.5f)
                lineTo(14f, 18.5f)
            }
        }
    }

    val Shuffle: ImageVector by lazy {
        icon("Shuffle") {
            line {
                moveTo(3f, 7f)
                lineTo(6.5f, 7f)
                curveTo(9f, 7f, 10.5f, 8.5f, 12f, 12f)
                curveTo(13.5f, 15.5f, 15f, 17f, 17.5f, 17f)
                lineTo(20.5f, 17f)
                moveTo(3f, 17f)
                lineTo(6.5f, 17f)
                curveTo(9f, 17f, 10.5f, 15.5f, 12f, 12f)
                curveTo(13.5f, 8.5f, 15f, 7f, 17.5f, 7f)
                lineTo(20.5f, 7f)
                moveTo(18f, 4.5f)
                lineTo(20.5f, 7f)
                lineTo(18f, 9.5f)
                moveTo(18f, 14.5f)
                lineTo(20.5f, 17f)
                lineTo(18f, 19.5f)
            }
        }
    }

    /** Three lines (the top one runs full width) with a note at the bottom right. */
    val Queue: ImageVector by lazy {
        icon("Queue", autoMirror = true) {
            line {
                moveTo(3f, 5.5f)
                lineTo(21f, 5.5f)
                moveTo(3f, 11f)
                lineTo(12f, 11f)
                moveTo(3f, 16.5f)
                lineTo(9f, 16.5f)
                moveTo(17.4f, 17.3f)
                lineTo(17.4f, 9.5f)
                curveTo(18.8f, 9.7f, 20f, 10.6f, 20.5f, 11.8f)
            }
            solid { circle(cx = 15.2f, cy = 17.3f, radius = 2.3f) }
        }
    }

    val ChevronBack: ImageVector by lazy {
        icon("ChevronBack", autoMirror = true) {
            line {
                moveTo(15f, 4.5f)
                lineTo(8f, 12f)
                lineTo(15f, 19.5f)
            }
        }
    }

    val ChevronDown: ImageVector by lazy {
        icon("ChevronDown") {
            line {
                moveTo(4.5f, 8.5f)
                lineTo(12f, 15.5f)
                lineTo(19.5f, 8.5f)
            }
        }
    }

    val Volume: ImageVector by lazy {
        icon("Volume", autoMirror = true) {
            line {
                moveTo(4f, 9.5f)
                lineTo(7.5f, 9.5f)
                lineTo(12f, 5.5f)
                lineTo(12f, 18.5f)
                lineTo(7.5f, 14.5f)
                lineTo(4f, 14.5f)
                close()
                moveTo(15f, 9f)
                curveTo(16.3f, 10.6f, 16.3f, 13.4f, 15f, 15f)
                moveTo(17.8f, 6.5f)
                curveTo(20.6f, 9.8f, 20.6f, 14.2f, 17.8f, 17.5f)
            }
        }
    }

    /** Five bottom-aligned bars of varying height (Now Playing's equaliser entry, queue "now playing" marker). */
    val EqualizerBars: ImageVector by lazy {
        icon("EqualizerBars") {
            line {
                moveTo(4f, 9f)
                lineTo(4f, 19.5f)
                moveTo(8f, 4.5f)
                lineTo(8f, 19.5f)
                moveTo(12f, 8.5f)
                lineTo(12f, 19.5f)
                moveTo(16f, 12f)
                lineTo(16f, 19.5f)
                moveTo(20f, 14.5f)
                lineTo(20f, 19.5f)
            }
        }
    }

    val Heart: ImageVector by lazy { icon("Heart") { line { heart() } } }

    val HeartFilled: ImageVector by lazy { icon("HeartFilled") { solid { heart() } } }

    /** One UI's "play in order" glyph: an "A" above a right arrow. It is the repeat-OFF state. */
    val PlayOrder: ImageVector by lazy {
        icon("PlayOrder") {
            line {
                moveTo(8.5f, 12.5f)
                lineTo(12f, 3.5f)
                lineTo(15.5f, 12.5f)
                moveTo(9.6f, 9.6f)
                lineTo(14.4f, 9.6f)
                moveTo(4f, 18f)
                lineTo(20f, 18f)
                moveTo(17f, 15f)
                lineTo(20f, 18f)
                lineTo(17f, 21f)
            }
        }
    }

    val Repeat: ImageVector by lazy { icon("Repeat") { line { repeatLoop() } } }

    val RepeatOne: ImageVector by lazy {
        icon("RepeatOne") {
            line {
                repeatLoop()
                moveTo(11.2f, 10.8f)
                lineTo(12.6f, 9.8f)
                lineTo(12.6f, 14.2f)
            }
        }
    }

    val Play: ImageVector by lazy {
        icon("Play") {
            solid {
                moveTo(7.5f, 5.2f)
                curveTo(7.5f, 4.4f, 8.4f, 3.9f, 9.1f, 4.4f)
                lineTo(19f, 11.1f)
                curveTo(19.6f, 11.5f, 19.6f, 12.5f, 19f, 12.9f)
                lineTo(9.1f, 19.6f)
                curveTo(8.4f, 20.1f, 7.5f, 19.6f, 7.5f, 18.8f)
                close()
            }
        }
    }

    val Pause: ImageVector by lazy {
        icon("Pause") {
            solid {
                roundedRect(left = 6.5f, top = 5f, right = 10f, bottom = 19f, radius = 1f)
                roundedRect(left = 14f, top = 5f, right = 17.5f, bottom = 19f, radius = 1f)
            }
        }
    }

    /** A bar plus two solid triangles pointing left (|◀◀). */
    val SkipPrevious: ImageVector by lazy {
        icon("SkipPrevious") {
            solid {
                roundedRect(left = 3.5f, top = 5.5f, right = 5.5f, bottom = 18.5f, radius = 1f)
                moveTo(13f, 6.5f)
                lineTo(13f, 17.5f)
                lineTo(6.2f, 12f)
                close()
                moveTo(20.5f, 6.5f)
                lineTo(20.5f, 17.5f)
                lineTo(13.2f, 12f)
                close()
            }
        }
    }

    /** Two solid triangles pointing right plus a bar (▶▶|). */
    val SkipNext: ImageVector by lazy {
        icon("SkipNext") {
            solid {
                roundedRect(left = 18.5f, top = 5.5f, right = 20.5f, bottom = 18.5f, radius = 1f)
                moveTo(11f, 6.5f)
                lineTo(11f, 17.5f)
                lineTo(17.8f, 12f)
                close()
                moveTo(3.5f, 6.5f)
                lineTo(3.5f, 17.5f)
                lineTo(10.8f, 12f)
                close()
            }
        }
    }

    /** The placeholder note: a round head, a stem, and a flag that curls right. */
    val MusicNote: ImageVector by lazy {
        icon("MusicNote") {
            line(strokeWidth = NOTE_LINE_WIDTH) {
                circle(cx = 9f, cy = 17f, radius = 3.2f)
                moveTo(12.2f, 17f)
                lineTo(12.2f, 5f)
                curveTo(15.5f, 5f, 17.5f, 7.2f, 17.5f, 10.2f)
            }
        }
    }

    val FolderBadge: ImageVector by lazy {
        icon("FolderBadge") {
            line {
                moveTo(3.5f, 6.5f)
                lineTo(9f, 6.5f)
                lineTo(11f, 8.5f)
                lineTo(20.5f, 8.5f)
                lineTo(20.5f, 18.5f)
                lineTo(3.5f, 18.5f)
                close()
            }
        }
    }

    val Close: ImageVector by lazy {
        icon("Close") {
            line {
                moveTo(6f, 6f)
                lineTo(18f, 18f)
                moveTo(18f, 6f)
                lineTo(6f, 18f)
            }
        }
    }

    val Edit: ImageVector by lazy {
        icon("Edit") {
            line {
                moveTo(4f, 20f)
                lineTo(4.6f, 16.4f)
                lineTo(15.8f, 5.2f)
                curveTo(16.6f, 4.4f, 17.8f, 4.4f, 18.6f, 5.2f)
                lineTo(18.8f, 5.4f)
                curveTo(19.6f, 6.2f, 19.6f, 7.4f, 18.8f, 8.2f)
                lineTo(7.6f, 19.4f)
                close()
                moveTo(14f, 7f)
                lineTo(17f, 10f)
            }
        }
    }

    val Check: ImageVector by lazy {
        icon("Check") {
            line {
                moveTo(5f, 12.5f)
                lineTo(10f, 17.5f)
                lineTo(19f, 7f)
            }
        }
    }

    val PlaylistAdd: ImageVector by lazy {
        icon("PlaylistAdd", autoMirror = true) {
            line {
                moveTo(3.5f, 6f)
                lineTo(16f, 6f)
                moveTo(3.5f, 11f)
                lineTo(16f, 11f)
                moveTo(3.5f, 16f)
                lineTo(10f, 16f)
                moveTo(17f, 13f)
                lineTo(17f, 21f)
                moveTo(13f, 17f)
                lineTo(21f, 17f)
            }
        }
    }

    val Album: ImageVector by lazy {
        icon("Album") {
            line {
                circle(cx = 12f, cy = 12f, radius = 8.5f)
                circle(cx = 12f, cy = 12f, radius = 2.5f)
            }
        }
    }

    val Person: ImageVector by lazy {
        icon("Person") {
            line {
                circle(cx = 12f, cy = 8f, radius = 3.5f)
                moveTo(5f, 20f)
                curveTo(5f, 16.4f, 8.1f, 14f, 12f, 14f)
                curveTo(15.9f, 14f, 19f, 16.4f, 19f, 20f)
            }
        }
    }

    /** Every icon, for RollaIconsTest and the kit preview. */
    internal val all: List<ImageVector>
        get() = listOf(
            Search, More, Add, Sort, Shuffle, Queue, ChevronBack, ChevronDown, Volume, EqualizerBars, Heart,
            HeartFilled, PlayOrder, Repeat, RepeatOne, Play, Pause, SkipPrevious, SkipNext, MusicNote, FolderBadge,
            Close, Edit, Check, PlaylistAdd, Album, Person,
        )
}
