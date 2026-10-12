/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.implementation.songDetails

import com.pandulapeter.campfire.data.model.domain.SongDetailsCandidate

/**
 * [this] with only the first of the candidates that would fill the song's header with the same values, the duration
 * compared to the second, which is all a `{duration}` holds. A catalogue lists a recording once for every remaster and
 * edition of it, and a dozen rows that fill in the same thing are only noise.
 */
internal fun List<SongDetailsCandidate>.withoutRepeats() = distinctBy { candidate ->
    listOf(candidate.title, candidate.artist, candidate.album, candidate.year, candidate.duration?.inWholeSeconds)
}
