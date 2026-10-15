/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui

import com.pandulapeter.campfire.chordpro.edit.ChordProTags
import com.pandulapeter.campfire.data.model.library.normalizedToNfc
import com.pandulapeter.campfire.domain.api.songs.SetChordProTagUseCase
import com.pandulapeter.campfire.domain.api.text.NormalizeSearchTextUseCase

/** Search text folded by case only, which is all the tests of this module search by. */
internal object LowercaseSearchText : NormalizeSearchTextUseCase {
    override fun invoke(text: String) = text.lowercase()
}

/** Adds and removes a `{tags}` entry as the real use case does, matching a tag by its NFC form. */
internal object ChordProTagWriter : SetChordProTagUseCase {
    override fun invoke(text: String, tag: String, isSelected: Boolean) = if (isSelected) {
        ChordProTags.addTag(text, tag, fold = String::normalizedToNfc)
    } else {
        ChordProTags.removeTag(text, tag, fold = String::normalizedToNfc)
    }
}
