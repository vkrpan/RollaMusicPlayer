package com.rolla.musicplayer.core.media

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

/**
 * Declares the [PlaybackUpdateHook] multibinding so `Set<PlaybackUpdateHook>` legally resolves to an
 * empty set when no `:feature:*` module contributes an implementation. Without a [Multibinds]
 * declaration Hilt cannot provide the set at all unless something elsewhere binds into it with
 * `@IntoSet`/`@ElementsIntoSet` -- `:core:media` must build and be injectable on its own regardless
 * of whether `:feature:widget` (or any future consumer) is present.
 *
 * See [PlaybackUpdateHook]'s KDoc for who implements the interface and why the dependency runs
 * feature -> core instead of the other way around.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PlaybackUpdateModule {

    @Multibinds
    abstract fun bindPlaybackUpdateHooks(): Set<PlaybackUpdateHook>
}
