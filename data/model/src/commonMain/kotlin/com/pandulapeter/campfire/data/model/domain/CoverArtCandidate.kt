/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.domain

/**
 * One record the cover search found: on MusicBrainz a release group, the one entry that stands for every edition of an
 * album or a single, whose front cover the Cover Art Archive picks from its releases; on iTunes an album or a single
 * as the store sells it.
 */
data class CoverArtCandidate(
    /** The catalogue that found it. */
    val service: CoverArtService,
    /** The record's identifier in [service]'s catalogue, unique among that service's candidates of one search. */
    val id: String,
    val title: String,
    /** The artist credit as the service spells it, `feat.` and all; empty where it has none. */
    val artist: String,
    /** The year of the record's first release, null where the service does not know it. */
    val year: String?,
    /** What kind of record it is as the service names it (`Album`, `Single`, `EP`), null where it says nothing. */
    val type: String?,
    /** The address written into the song when this one is chosen, and the one its thumbnail is loaded from. */
    val coverArtUrl: String,
) {
    /** What tells this candidate apart from every other of one search, whichever service found it. */
    val key get() = "${service.name}:$id"
}
