/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.chords

import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.settings_notation_german
import com.pandulapeter.campfire.presentation.resources.settings_notation_german_description
import com.pandulapeter.campfire.presentation.resources.settings_notation_latin
import com.pandulapeter.campfire.presentation.resources.settings_notation_latin_description
import com.pandulapeter.campfire.presentation.resources.settings_notation_nashville
import com.pandulapeter.campfire.presentation.resources.settings_notation_nashville_description
import com.pandulapeter.campfire.presentation.resources.settings_notation_roman
import com.pandulapeter.campfire.presentation.resources.settings_notation_roman_description
import com.pandulapeter.campfire.presentation.resources.settings_notation_standard
import com.pandulapeter.campfire.presentation.resources.settings_notation_standard_description

/** The name of the notation, as the chip in Settings and the sheet it opens write it. */
internal val UserPreferences.Notation.label
    get() = when (this) {
        UserPreferences.Notation.STANDARD -> Res.string.settings_notation_standard
        UserPreferences.Notation.GERMAN -> Res.string.settings_notation_german
        UserPreferences.Notation.LATIN -> Res.string.settings_notation_latin
        UserPreferences.Notation.NASHVILLE -> Res.string.settings_notation_nashville
        UserPreferences.Notation.ROMAN -> Res.string.settings_notation_roman
    }

/** The example written under the notation's name, which is what tells the notations apart. */
internal val UserPreferences.Notation.description
    get() = when (this) {
        UserPreferences.Notation.STANDARD -> Res.string.settings_notation_standard_description
        UserPreferences.Notation.GERMAN -> Res.string.settings_notation_german_description
        UserPreferences.Notation.LATIN -> Res.string.settings_notation_latin_description
        UserPreferences.Notation.NASHVILLE -> Res.string.settings_notation_nashville_description
        UserPreferences.Notation.ROMAN -> Res.string.settings_notation_roman_description
    }
