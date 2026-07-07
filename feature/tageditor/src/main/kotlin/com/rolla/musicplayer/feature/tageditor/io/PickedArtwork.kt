package com.rolla.musicplayer.feature.tageditor.io

/**
 * A locally-picked artwork image, fully loaded into memory and ready to embed: raw encoded
 * [bytes] (written verbatim into the tag -- never re-encoded), the [mimeType] the tag frame
 * declares, and the decoded pixel [width]/[height] (FLAC's picture block stores dimensions
 * explicitly; other formats ignore them).
 *
 * Deliberately a plain class, not a data class: a [ByteArray] property would make generated
 * `equals`/`hashCode` compare array identity rather than content, which is a footgun -- and
 * nothing needs value equality on this type.
 *
 * Produced by [ArtworkLoader] from a PhotoPicker/document uri -- always a LOCAL image, per the
 * app's offline contract; there is no online artwork lookup anywhere.
 */
class PickedArtwork(
    val bytes: ByteArray,
    val mimeType: String,
    val width: Int,
    val height: Int,
)
