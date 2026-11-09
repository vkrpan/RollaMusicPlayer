package com.rolla.musicplayer.core.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil.Coil
import coil.ImageLoader
import coil.decode.DataSource
import coil.intercept.Interceptor
import coil.request.SuccessResult
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.artworkPlaceholderLarge
import com.rolla.musicplayer.core.model.Album
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.model.songArtworkUri
import com.rolla.musicplayer.core.testing.drawRoot
import com.rolla.musicplayer.core.testing.isColorNear
import com.rolla.musicplayer.core.testing.pixelsIn
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Per-track artwork on screen (spec goals 1-3, Review Focus 5). Coil's singleton loader is swapped for one whose only
 * interceptor records each request's data and answers it with a solid colour chosen by that data, so nothing touches
 * a real file (the MiniPlayerArtPixelTest technique). The answer claims the memory cache as its source, so Coil skips
 * the crossfade and the first settled frame is the final one. Drawn with drawRoot: nothing here is a shared element.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TrackArtworkPixelTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // Coil runs interceptors on the main thread here, but the list is read from the test thread.
    private val requested = CopyOnWriteArrayList<String>()

    @Before
    fun installSolidColourLoader() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Coil.setImageLoader(
            ImageLoader.Builder(context)
                .components {
                    add(
                        Interceptor { chain ->
                            val data = chain.request.data.toString()
                            requested += data
                            val colour = PICTURES[data] ?: UNKNOWN_PICTURE
                            val bitmap = Bitmap.createBitmap(PICTURE_PX, PICTURE_PX, Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(colour.toArgb())
                            SuccessResult(
                                drawable = BitmapDrawable(chain.request.context.resources, bitmap),
                                request = chain.request,
                                dataSource = DataSource.MEMORY_CACHE,
                            )
                        },
                    )
                }
                .build(),
        )
    }

    @After
    fun restore() {
        Coil.setImageLoader(ImageLoader(ApplicationProvider.getApplicationContext<Context>()))
    }

    // Spec goal 1 / §2: two tracks that MediaStore files under one album show their own pictures. Control: both rows'
    // values reached the loader. Mutation: SongListItem's thumbnail loads the album's MediaStore art
    // ("content://media/external/audio/albumart/{albumId}", the pre-fix value) → both rows request one uri and draw
    // one colour → RED.
    @Test
    fun sameAlbumRows_drawTheirOwnPictures() {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                Column(Modifier.width(360.dp)) {
                    SongListItem(song = song(FIRST_ID, version = 1L), onClick = {})
                    SongListItem(song = song(SECOND_ID, version = 1L), onClick = {})
                }
            }
        }
        val centres = thumbnailCentres()
        assertEquals("two rows, two thumbnails", 2, centres.size)
        assertFalse(
            "rows of one album draw different pictures: ${centres.map(Integer::toHexString)}",
            centres[0] == centres[1],
        )
        assertTrue("track $FIRST_ID draws its own picture (red)", isColorNear(RED, centres[0]))
        assertTrue("track $SECOND_ID draws its own picture (blue)", isColorNear(BLUE, centres[1]))
        assertEquals(
            "control: each row's own value reached the loader",
            setOf(songArtworkUri(FIRST_ID, 1L), songArtworkUri(SECOND_ID, 1L)),
            requested.toSet(),
        )
    }

    // Review Focus 5: a re-synced Song carries a new v=, and the composed row loads it. Control: v=1 draws red first.
    // Mutation: ArtworkThumbnail's remember(artworkUri) → remember { } (the request outlives the value) → stays red →
    // RED.
    @Test
    fun songRow_newVersion_drawsTheNewPicture() {
        var current by mutableStateOf(song(FIRST_ID, version = 1L))
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                Column(Modifier.width(360.dp)) {
                    SongListItem(song = current, onClick = {})
                }
            }
        }
        val before = thumbnailCentres().single()
        assertTrue("control: v=1 draws red, was ${Integer.toHexString(before)}", isColorNear(RED, before))
        composeRule.runOnIdle { current = song(FIRST_ID, version = 2L) }
        val after = thumbnailCentres().single()
        assertTrue("v=2 draws the new picture (blue), was ${Integer.toHexString(after)}", isColorNear(BLUE, after))
    }

    // Spec goal 3: an album card always shows the placeholder and never asks the loader for anything. Control (same
    // composition): a song row's load reaches the loader, so an empty request log is not a dead loader. The probe is a
    // square of the art box left of the centred note glyph. Mutation: AlbumCardArt loads the album's MediaStore art
    // ("content://media/external/audio/albumart/{id}") → a second request is recorded and the box draws it → RED.
    @Test
    fun albumCard_drawsThePlaceholderAndRequestsNothing() {
        var placeholder = Color.Unspecified
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                placeholder = MaterialTheme.colorScheme.artworkPlaceholderLarge
                Column(Modifier.width(360.dp)) {
                    SongListItem(song = song(FIRST_ID, version = 1L), onClick = {})
                    AlbumCard(
                        album = Album(id = ALBUM_ID, title = "albumm", artist = "Artist", songCount = 2),
                        onClick = {},
                        modifier = Modifier.width(RollaDimens.featureCardSize),
                    )
                }
            }
        }
        val pixels = albumCardArtLeftOfTheNote()
        assertEquals(
            "only the song row asked the loader for a picture; the album card asked for none",
            setOf(songArtworkUri(FIRST_ID, 1L)),
            requested.toSet(),
        )
        assertTrue(
            "the album card draws the placeholder: ${pixels.distinct().map(Integer::toHexString)}",
            pixels.all { isColorNear(placeholder, it) },
        )
    }

    /** A 4 dp square of the album card's art box, centred vertically, halfway between its left edge and the note. */
    private fun albumCardArtLeftOfTheNote(): List<Int> {
        val bitmap = composeRule.drawRoot()
        val art = composeRule.onNodeWithTag(ALBUM_CARD_ART_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val noteLeft = art.left + (art.right - art.left - RollaDimens.featureCardGlyph) / 2
        val probeX = art.left + (noteLeft - art.left) / 2
        val centreY = (art.top + art.bottom) / 2
        return composeRule.pixelsIn(bitmap, DpRect(probeX - 2.dp, centreY - 2.dp, probeX + 2.dp, centreY + 2.dp))
    }

    /** The centre pixel of each song row's thumbnail, top to bottom. */
    private fun thumbnailCentres(): List<Int> {
        val bitmap = composeRule.drawRoot()
        return composeRule.onAllNodesWithTag(SONG_ROW_ARTWORK_TEST_TAG, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .map { node ->
                // boundsInRoot is in pixels, the bitmap's own unit.
                val centre = node.boundsInRoot.center
                bitmap.getPixel(centre.x.toInt(), centre.y.toInt())
            }
    }

    private fun song(id: String, version: Long) = Song(
        id = id,
        title = "Track $id",
        artist = "Artist",
        album = "albumm",
        albumId = ALBUM_ID,
        durationMs = 180_000L,
        trackNumber = null,
        year = null,
        contentUri = "content://media/external/audio/media/$id",
        artworkUri = songArtworkUri(id, version),
    )

    private companion object {
        // The device evidence (spec §2): two tracks MediaStore filed under one album id.
        const val ALBUM_ID = 7372593584554650381L
        const val FIRST_ID = "9540"
        const val SECOND_ID = "9545"
        const val PICTURE_PX = 8

        val RED = Color(0xFFFF0000)
        val BLUE = Color(0xFF0000FF)

        // Any data the test did not plan for (e.g. an album-art uri) answers green, so it can never pass for red/blue.
        val UNKNOWN_PICTURE = Color(0xFF00FF00)

        val PICTURES = mapOf(
            songArtworkUri(FIRST_ID, 1L) to RED,
            songArtworkUri(SECOND_ID, 1L) to BLUE,
            songArtworkUri(FIRST_ID, 2L) to BLUE,
        )
    }
}
