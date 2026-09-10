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

interface TransposeChordProTextUseCase {

    /** Transposes the chords of a ChordPro document in place, leaving the rest of the text exactly as it was. */
    operator fun invoke(text: String, semitones: Int): String
}
