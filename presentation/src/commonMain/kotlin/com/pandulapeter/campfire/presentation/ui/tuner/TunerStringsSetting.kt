/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.tuner

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.tuner_strings
import com.pandulapeter.campfire.presentation.resources.tuner_strings_description
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.chrome.TOOL_GROUP_GAP
import com.pandulapeter.campfire.presentation.ui.settingsPage.MAX_COLUMN_WIDTH
import com.pandulapeter.campfire.presentation.ui.settingsPage.PAGE_TOP_PADDING
import com.pandulapeter.campfire.presentation.ui.settingsPage.SettingsSubsection
import com.pandulapeter.campfire.tuner.api.model.TunerConfig

/**
 * The strings [config] listens for ([TunerStrings]), the same on the tab and in the sheet over a song, and nothing for an
 * instrument with no strings to tune (the keyboard, Other), which is read note by note.
 *
 * Another instrument's strings fade in over the last one's, and the strings fade in and out as they come and go, while
 * the room they take grows or shrinks around them. That room is not a clip, so no row is ever cut through while it
 * changes; the fade is what hides what reaches past it.
 *
 * @param isTitled Whether they stand under their title and what a tap does, as a setting among the sheet's rows; the tab
 * sets them centered under its display, as the instrument the display is listening to, with what a tap does under them.
 */
@Composable
internal fun TunerStringsSetting(
    config: TunerConfig,
    notation: ChordNotation,
    tone: Int?,
    heardNote: Int?,
    tunedNotes: Set<Int>,
    isTitled: Boolean,
    onToggleTone: (Int) -> Unit,
) {
    // Read through states, since the content of a row leaving is not sure to be recomposed with the newest parameters.
    val currentConfig by rememberUpdatedState(config)
    val currentTone by rememberUpdatedState(tone)
    // A row leaving with its instrument is drawn as it was but plays nothing: only a string the tuner listens for now
    // starts a tone, and the one sounding can always be stopped.
    val onToggleString: (Int) -> Unit = remember(onToggleTone) {
        { note -> if (note == currentTone || isStringOf(currentConfig, note)) onToggleTone(note) }
    }
    AnimatedContent(
        targetState = config.tuning?.let { it to config.partners },
        transitionSpec = { fadeIn() togetherWith fadeOut() using SizeTransform(clip = false) },
        label = "tunerStrings",
    ) { shown ->
        val (shownTuning, partners) = shown ?: return@AnimatedContent
        val strings = @Composable { stringsModifier: Modifier ->
            TunerStrings(
                modifier = stringsModifier,
                tuning = shownTuning,
                partners = partners,
                notation = notation,
                tone = tone,
                heardNote = heardNote,
                tunedNotes = tunedNotes,
                onToggleTone = onToggleString,
            )
        }
        if (isTitled) {
            SettingsSubsection(
                title = stringResource(Res.string.tuner_strings),
                description = stringResource(Res.string.tuner_strings_description),
            ) {
                strings(Modifier)
            }
        } else {
            // A tool's group gap under the display (TOOL_GROUP_GAP), the page's own top padding counted.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = TOOL_GROUP_GAP - PAGE_TOP_PADDING, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                strings(Modifier.widthIn(max = MAX_COLUMN_WIDTH))
                Text(
                    modifier = Modifier.widthIn(max = MAX_COLUMN_WIDTH).padding(start = 16.dp, top = 8.dp, end = 16.dp),
                    text = stringResource(Res.string.tuner_strings_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
