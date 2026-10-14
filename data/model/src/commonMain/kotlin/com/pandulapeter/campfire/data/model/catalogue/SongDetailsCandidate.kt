/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.catalogue

import kotlin.time.Duration

/**
 * One recording of a song the details lookup found, with what it would fill the song's header with. On MusicBrainz a
 * recording, on iTunes a song as the store sells it. A catalogue that lists the same recording once for every edition
 * of it gives one candidate for all of them where they would fill in the same values.
 */
data class SongDetailsCandidate(
    /** The catalogue that found it. */
    val catalogue: Catalogue,
    /** The recording's identifier in [catalogue], unique among that catalogue's candidates of one search. */
    val id: String,
    val title: String,
    /** The artist credit as the catalogue spells it, `feat.` and all; empty where it has none. */
    val artist: String,
    /** The record it is best known from, null where the catalogue names none. */
    val album: String?,
    /** The year it was first released, null where the catalogue does not know it. */
    val year: String?,
    /** How long it plays, null where the catalogue does not say. */
    val duration: Duration?,
) {
    /** What tells this candidate apart from every other of one search, whichever catalogue found it. */
    val key get() = "${catalogue.name}:$id"
}
