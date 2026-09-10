/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.api.useCases

import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.data.model.domain.UserPreferences

interface ConvertChordProNotationUseCase {

    /**
     * Rewrites the chords of a parsed song in the notation the reader prefers, which today means German notation or
     * nothing: with [UserPreferences.ChordSpelling.isGermanNotationEnabled] off, the song comes back untouched.
     *
     * The last step of rendering, run after [TransposeChordProUseCase]: transposing works in the notation the file is
     * written in, and this is what its result is then read as. There is deliberately no text-level counterpart to
     * [TransposeChordProTextUseCase] — that one rewrites the file, and the file stays in the app's own notation.
     */
    operator fun invoke(song: ChordProSong, spelling: UserPreferences.ChordSpelling): ChordProSong
}
