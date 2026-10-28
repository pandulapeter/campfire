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
 * it). Main thread only, so it takes no lock: it is read in composition and written by the rendering effect once its
 * work off the main thread has returned.
 */
internal class NotationCache(
    private val maxTunes: Int = MAX_CACHED_TUNES,
    private val maxWords: Int = MAX_CACHED_WORDS,
) {
    /** In the order last used: common Kotlin's `LinkedHashMap` keeps insertion order only, so a read puts an entry back last. */
    private val renderings = LinkedHashMap<NotationTuneKey, NotationRendering>()
    private var words = 0

    operator fun get(key: NotationTuneKey): NotationRendering? = renderings.remove(key)?.also { renderings[key] = it }

    operator fun set(key: NotationTuneKey, rendering: NotationRendering) {
        renderings.remove(key)?.let { words -= it.words }
        if (rendering.words > maxWords) return
        renderings[key] = rendering
        words += rendering.words
        while (renderings.size > maxTunes || words > maxWords) {
            val oldest = renderings.keys.first()
            words -= renderings.remove(oldest)!!.words
        }
    }

    /** Every tune of [request] rendered, or null where any one of them is not kept. */
    fun renderedFor(request: NotationRequest): RenderedNotations? {
        val found = request.tunes.associateWith { get(request.keyOf(it)) ?: return null }
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
    }
}

/** How many words [systems] hold laid out, which is what a painted tune's size mostly is. */
internal fun notationWordsOf(systems: List<NotationSystemPaint>) = systems.sumOf { it.texts.size }
