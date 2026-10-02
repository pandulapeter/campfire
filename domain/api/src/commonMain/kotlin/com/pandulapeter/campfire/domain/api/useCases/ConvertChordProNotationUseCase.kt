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
     * Rewrites the chords of a parsed song, which is in [UserPreferences.Notation.STANDARD], in the notation of
     * [spelling]; in the standard one, the song comes back untouched.
     *
     * The last step of rendering, run after [TransposeChordProUseCase], which works in the standard notation. Its
     * text-level counterpart is [ConvertChordProTextNotationUseCase].
     */
    operator fun invoke(song: ChordProSong, spelling: UserPreferences.ChordSpelling): ChordProSong
}
