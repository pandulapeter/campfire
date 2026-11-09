/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.notation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import com.pandulapeter.campfire.data.model.preferences.UserPreferences.Instrument

/** The player's instrument, which picks the part of a score engraved for them (see [NotationPartChoices]). */
internal val LocalNotationInstrument = compositionLocalOf<Instrument?> { null }

/**
 * Which part of each score of one song is engraved. A score with a part for the player's instrument shows that part and
 * offers no choice: the instrument is picked from the song's first section already. Any other shows the part picked
 * here, its first until one is. Remembered by the tune's text for as long as the song is on screen, never stored: it is
 * how one reading goes, and the instrument is what a reader keeps.
 */
@Stable
internal class NotationPartChoices {

    private val chosen = mutableStateMapOf<String, Int>()
    private val parsed = HashMap<String, List<AbcPart>>()

    private fun partsOf(tune: String) = parsed.getOrPut(tune) { abcPartsOf(tune) }

    /** The part of [tune] engraved for [instrument], null for the whole score. */
    fun shownPartOf(tune: String, instrument: Instrument?): AbcPart? {
        val parts = partsOf(tune)
        val index = shownAbcPartIndex(parts, matched = abcPartFor(parts, instrument), chosen = chosen[tune]) ?: return null
        return parts[index]
    }

    /** What the picker beside [tune]'s heading offers, null where it has none. */
    fun pickerOf(tune: String, instrument: Instrument?): NotationPartPicker? {
        val parts = partsOf(tune)
        if (parts.isEmpty() || abcPartFor(parts, instrument) != null) return null
        return NotationPartPicker(
            parts = parts,
            selected = shownAbcPartIndex(parts, matched = null, chosen = chosen[tune]),
            onSelected = { chosen[tune] = it ?: ALL_PARTS },
        )
    }
}

/**
 * The parts a score can be shown in and the one it is.
 *
 * @param selected The index of the part shown, null for the whole score.
 * @param onSelected Takes the index of a part, or null for the whole score.
 */
internal class NotationPartPicker(
    val parts: List<AbcPart>,
    val selected: Int?,
    val onSelected: (Int?) -> Unit,
)

/**
 * The index of the part of [parts] shown, null for the whole score: the one [matched] the instrument, else the one
 * [chosen] ([ALL_PARTS] for the whole score), else the first, which is the melody in most scores.
 */
internal fun shownAbcPartIndex(parts: List<AbcPart>, matched: Int?, chosen: Int?): Int? = when {
    parts.isEmpty() -> null
    matched != null -> matched
    chosen == ALL_PARTS -> null
    chosen != null && chosen in parts.indices -> chosen
    else -> 0
}

internal const val ALL_PARTS = -1
