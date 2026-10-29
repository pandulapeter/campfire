/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.notation

import androidx.compose.runtime.staticCompositionLocalOf

/** The app's [NotationCache], provided once by `CampfireApp`; where nothing provides one, every tune is rendered anew. */
internal val LocalNotationCache = staticCompositionLocalOf<NotationCache?> { null }

/** Everything one tune's rendering depends on, so that a tune edited or added beside it does not miss the others. */
internal data class NotationTuneKey(
    val tune: String,
    val chords: NotationChordSpelling,
    val width: Int,
    val unitSize: Float,
    val songTitle: String?,
    val painting: NotationPainting,
)

/** The key of [tune] in this request. */
internal fun NotationRequest.keyOf(tune: AbcTune) = NotationTuneKey(
    tune = tune.text,
    chords = chords.movedBy(tune.offset),
    width = width,
    unitSize = unitSize,
    songTitle = songTitle,
    painting = painting,
)

/**
 * The renderings made last, app-wide, so that a tune seen before at the same width, size, and transposition is notation
 * on the first frame its song is opened again. Least recently used out first, bounded twice, whichever is reached
 * first: [maxTunes], and [maxWords] words laid out, since a painted tune's size is mostly its text layouts and one long
 * tune with words holds thousands of them. A rendering heavier than the whole bound is not kept (its page still shows
 * it). A tune that failed to render is remembered too, by the same key, the last [maxFailedTunes] of them, so that a
 * resize or a transposition back to it draws it as its text at once instead of holding the single engraving thread for
 * as long as the failure took again; a failure from a device too busy at that moment stays until the key changes too.
 * Main thread only, so it takes no lock: it is read in composition and written by the rendering effect once its work
 * off the main thread has returned.
 */
internal class NotationCache(
    private val maxTunes: Int = MAX_CACHED_TUNES,
    private val maxWords: Int = MAX_CACHED_WORDS,
    private val maxFailedTunes: Int = MAX_FAILED_TUNES,
) {
    /** In the order last used: common Kotlin's `LinkedHashMap` keeps insertion order only, so a read puts an entry back last. */
    private val renderings = LinkedHashMap<NotationTuneKey, NotationRendering>()
    private var words = 0

    /** In the order last used, as [renderings] is. */
    private val failed = LinkedHashMap<NotationTuneKey, Unit>()

    operator fun get(key: NotationTuneKey): NotationRendering? = renderings.remove(key)?.also { renderings[key] = it }

    operator fun set(key: NotationTuneKey, rendering: NotationRendering) {
        failed.remove(key)
        renderings.remove(key)?.let { words -= it.words }
        if (rendering.words > maxWords) return
        renderings[key] = rendering
        words += rendering.words
        while (renderings.size > maxTunes || words > maxWords) {
            val oldest = renderings.keys.first()
            words -= renderings.remove(oldest)!!.words
        }
    }

    /** Whether the tune of [key] failed to render, last time it was tried. */
    fun isFailed(key: NotationTuneKey) = (failed.remove(key) != null).also { if (it) failed[key] = Unit }

    fun markFailed(key: NotationTuneKey) {
        failed.remove(key)
        failed[key] = Unit
        if (failed.size > maxFailedTunes) failed.remove(failed.keys.first())
    }

    /** Whether the tune of [key] needs no rendering: it is kept, or it failed. */
    fun isKept(key: NotationTuneKey) = get(key) != null || isFailed(key)

    /**
     * Every tune of [request] rendered, a failed one left out (it is drawn as its text), or null where any one of them is
     * neither kept nor failed.
     */
    fun renderedFor(request: NotationRequest): RenderedNotations? {
        val found = request.tunes.mapNotNull { tune ->
            val key = request.keyOf(tune)
            get(key)?.let { tune to it } ?: if (isFailed(key)) null else return null
        }.toMap()
        return RenderedNotations(
            request = request,
            renderings = found,
            rerenderedTunes = emptySet(),
            // The page's first frame: nothing has a place to move from.
            arrivingTunes = found.keys,
        )
    }

    private val NotationRendering.words get() = notationWordsOf(systems)

    private companion object {
        const val MAX_CACHED_TUNES = 16
        const val MAX_CACHED_WORDS = 4_000
        const val MAX_FAILED_TUNES = 64
    }
}

/** How many words [systems] hold laid out, which is what a painted tune's size mostly is. */
internal fun notationWordsOf(systems: List<NotationSystemPaint>) = systems.sumOf { it.texts.size }
