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

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_add
import com.pandulapeter.campfire.presentation.resources.ic_subtract
import com.pandulapeter.campfire.presentation.resources.library_key_down
import com.pandulapeter.campfire.presentation.resources.library_key_up
import com.pandulapeter.campfire.presentation.ui.components.controls.Stepper
import org.jetbrains.compose.resources.painterResource

/**
 * The key of the Chords and the Scales tabs, stepped a semitone at a time in the pill the editor transposes a song with,
 * before the search. The twelve keys go round, B stepping up to C, so that no key is more than six taps away, and the pill
 * takes the room of the widest key in the reader's notation, so that it keeps one length while it is stepped.
 */
@Composable
internal fun LibraryKeyStepper(
    modifier: Modifier = Modifier,
    keys: List<String>,
    selectedIndex: Int,
    notation: ChordNotation,
    onSelected: (Int) -> Unit,
) {
    val index = selectedIndex.coerceIn(keys.indices)
    Stepper(
        modifier = modifier,
        value = shownChordName(keys[index], notation),
        widestValues = keys.map { shownChordName(it, notation) },
        isDefault = true,
        decreaseIcon = painterResource(Res.drawable.ic_subtract),
        decreaseLabel = stringResource(Res.string.library_key_down),
        canDecrease = true,
        onDecrease = { onSelected((index - 1).mod(keys.size)) },
        increaseIcon = painterResource(Res.drawable.ic_add),
        increaseLabel = stringResource(Res.string.library_key_up),
        canIncrease = true,
        onIncrease = { onSelected((index + 1).mod(keys.size)) },
        resetLabel = null,
        onReset = null,
    )
}
