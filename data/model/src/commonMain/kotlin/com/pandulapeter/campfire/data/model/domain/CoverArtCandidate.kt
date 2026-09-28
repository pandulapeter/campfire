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
 * One record the cover search found: a release group, the one entry that stands for every edition of an album or a
 * single, whose front cover the Cover Art Archive picks from its releases.
 */
data class CoverArtCandidate(
    /** The MusicBrainz identifier of the release group, unique among the candidates of one search. */
    val id: String,
    val title: String,
    /** The artist credit as MusicBrainz spells it, `feat.` and all; empty where it has none. */
    val artist: String,
    /** The year of the group's first release, null where MusicBrainz does not know it. */
    val year: String?,
    /** The group's primary type as MusicBrainz names it (`Album`, `Single`, `EP`), null where it has none. */
    val type: String?,
    /** The address written into the song when this one is chosen, and the one its thumbnail is loaded from. */
    val coverArtUrl: String,
)
