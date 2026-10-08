/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_add
import com.pandulapeter.campfire.presentation.resources.ic_subtract
import com.pandulapeter.campfire.presentation.resources.song_editor_transpose_text_down
import com.pandulapeter.campfire.presentation.resources.song_editor_transpose_text_up
import com.pandulapeter.campfire.presentation.ui.components.Stepper
import org.jetbrains.compose.resources.painterResource

/**
 * The same stepper for the editor, where transposing rewrites the file instead of changing how it is read. There is
 * no amount to show and nothing to reset to, so the value is the key the song is in - or a question mark, since a
 * file that declares no `{key}` still has chords that move.
 *
 * @param isEnabled False for a song with no chords, where both buttons would rewrite nothing.
 */
@Composable
internal fun TextTranspositionControls(
    modifier: Modifier = Modifier,
    key: String?,
    isEnabled: Boolean,
    onTransposed: (semitones: Int) -> Unit,
) = Stepper(
    modifier = modifier,
    value = key?.takeIf { it.isNotBlank() } ?: UNKNOWN_KEY,
    isDefault = true,
    decreaseIcon = painterResource(Res.drawable.ic_subtract),
    decreaseLabel = stringResource(Res.string.song_editor_transpose_text_down),
    canDecrease = isEnabled,
    onDecrease = { onTransposed(-1) },
    increaseIcon = painterResource(Res.drawable.ic_add),
    increaseLabel = stringResource(Res.string.song_editor_transpose_text_up),
    canIncrease = isEnabled,
    onIncrease = { onTransposed(1) },
    resetLabel = null,
    onReset = null,
)

/** What the editor's stepper shows for a song whose file names no key. */
private const val UNKNOWN_KEY = "?"
