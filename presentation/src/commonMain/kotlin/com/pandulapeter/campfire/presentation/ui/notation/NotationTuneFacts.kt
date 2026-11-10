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

/** What one tune's engraving reads of its text, worked out once per tune: the text engraved (the part shown, where one is). */
internal class TuneFacts(
    val engravedText: String,
)

/**
 * The [TuneFacts] of every tune of a [NotationRequest], built once per tunes and parts rather than on every effect
 * restart and recomposition, and immutable, so it is read from any thread. It follows from the request's tunes and
 * parts, which the request already compares, so it is equal to any other and hashes alike: the request's equality and
 * hash stay what its own fields make them, without a map of every tune's text hashed on each composition of a pinch.
 */
internal class NotationTuneFacts(
    private val byTune: Map<AbcTune, TuneFacts>,
) {
    operator fun get(tune: AbcTune) = byTune[tune]

    override fun equals(other: Any?) = other is NotationTuneFacts

    override fun hashCode() = 0
}

/** The facts of each of [tunes], engraved in its part of [parts] (by the tune's text) where it has one there. */
internal fun notationTuneFactsOf(tunes: Set<AbcTune>, parts: Map<String, AbcPart>) = NotationTuneFacts(
    tunes.associateWith { tune -> TuneFacts(engravedText = engravedTextOf(tune.text, parts[tune.text])) },
)

/** [tune] as engraved with only [part] shown, or whole where none is. */
internal fun engravedTextOf(tune: String, part: AbcPart?) = part?.let { abcTuneWithPart(tune, it) } ?: tune
