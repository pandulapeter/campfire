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

/**
 * A music catalogue the app asks, for a song's cover and for its details. Each knows what the other does not —
 * MusicBrainz is thorough about old, independent and non-English records and every edition of them, Apple's store about
 * whatever is sold today — so a search asks all of them and offers what each found.
 */
enum class Catalogue {
    /** MusicBrainz: its release groups with the Cover Art Archive's front cover of each, its recordings and works. */
    MUSIC_BRAINZ,

    /** The iTunes Search API: the albums, singles and songs Apple sells, with the artwork it keeps for each. */
    ITUNES,
}
