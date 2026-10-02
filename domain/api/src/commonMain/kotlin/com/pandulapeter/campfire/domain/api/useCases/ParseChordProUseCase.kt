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

interface ParseChordProUseCase {

    /**
     * The song [text] describes, its chords in [UserPreferences.Notation.STANDARD] whichever [notation] the text is
     * written in: a file's is the standard one, the editor's field the reader's own (see
     * [ConvertChordProTextNotationUseCase] for how the two are read).
     */
    operator fun invoke(text: String, notation: UserPreferences.Notation = UserPreferences.Notation.STANDARD): ChordProSong
}
