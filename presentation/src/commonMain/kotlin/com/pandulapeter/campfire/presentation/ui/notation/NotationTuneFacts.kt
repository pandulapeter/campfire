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

import com.pandulapeter.campfire.abc.api.AbcTablature

/**
 * What one tune's engraving reads of its text, worked out once per tune: the text engraved (the part shown, where one
 * is), whether it has a tab voice, so the reader's tab fretting changes it, and whether it is named as the song, so the
 * song's title does. The last two are decided on the whole score, which a part that fails is engraved as.
 */
internal class TuneFacts(
    val engravedText: String,
    val hasTabVoice: Boolean,
    val namesSongTitle: Boolean,
)

/**
 * The [TuneFacts] of every tune of a [NotationRequest], built once per tunes, parts, and song title rather than on every
 * effect restart and recomposition, and immutable, so it is read from any thread. It follows from the request's tunes,
 * parts, and title (null in a request only where no tune is named as the song, which these facts then all say), which
 * the request already compares, so it is equal to any other and hashes alike: the request's equality and hash stay what
 * its own fields make them, without a map of every tune's text hashed on each composition of a pinch.
 */
internal class NotationTuneFacts(
    private val byTune: Map<AbcTune, TuneFacts>,
) {
    /** Whether any tune has a tab voice: a song with none is engraved alike under any tab fretting. */
    val hasTabVoice = byTune.values.any { it.hasTabVoice }

    /** Whether any tune is named as the song: a song with none is engraved alike under any title. */
    val namesSongTitle = byTune.values.any { it.namesSongTitle }

    operator fun get(tune: AbcTune) = byTune[tune]

    override fun equals(other: Any?) = other is NotationTuneFacts

    override fun hashCode() = 0
}

/** The facts of each of [tunes], engraved in its part of [parts] (by the tune's text) where it has one there. */
internal fun notationTuneFactsOf(tunes: Set<AbcTune>, parts: Map<String, AbcPart>, songTitle: String?) = NotationTuneFacts(
    tunes.associateWith { tune -> tuneFactsOf(tune.text, parts[tune.text], songTitle) },
)

/** The facts of [tune] engraved with only [part] shown, or whole where none is, in a song titled [songTitle]. */
internal fun tuneFactsOf(tune: String, part: AbcPart?, songTitle: String?) = TuneFacts(
    engravedText = engravedTextOf(tune, part),
    hasTabVoice = AbcTablature.isReadBy(tune),
    namesSongTitle = abcNamesSongTitle(tune, songTitle),
)

/** [tune] as engraved with only [part] shown, or whole where none is. */
internal fun engravedTextOf(tune: String, part: AbcPart?) = part?.let { abcTuneWithPart(tune, it) } ?: tune
