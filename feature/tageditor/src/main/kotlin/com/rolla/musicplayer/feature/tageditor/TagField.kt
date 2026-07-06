package com.rolla.musicplayer.feature.tageditor

/**
 * One editable field of [SongTags].
 *
 * Shared by both editing surfaces of this feature: [TagEditorViewModel.onFieldChanged] (single
 * song) and [BatchTagEditorViewModel.onFieldValueChanged]/[BatchTagEditorViewModel.onFieldApplyToggled]
 * (many songs at once). Lives in its own file, rather than nested in either ViewModel, so
 * `:feature:tageditor`'s `io` package ([com.rolla.musicplayer.feature.tageditor.io.TagWriter]) can
 * depend on it without depending on either ViewModel.
 */
enum class TagField { TITLE, ARTIST, ALBUM, ALBUM_ARTIST, GENRE, YEAR, TRACK_NUMBER, COMPOSER }
