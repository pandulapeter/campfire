/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.chords.ChordProNotation
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.ui.chords.toChordNotation
import com.pandulapeter.campfire.presentation.ui.theme.LocalSecondAccentColor

/**
 * Two lines of a song with its chords over the words, the way a song page sets them, which is what the notation being
 * picked is shown on as it is made. A picture rather than content, so it is left out of the semantics tree; the choices beside it say what it shows.
 */
@Composable
internal fun OnboardingSongPreview(
    modifier: Modifier = Modifier,
    notation: UserPreferences.Notation?,
) {
    val chordNotation = (notation ?: UserPreferences.Notation.STANDARD).toChordNotation()
    val shownNames = remember(chordNotation) { ChordProNotation.shownNames(ChordProParser.parse(PREVIEW_SONG), chordNotation) }
    val chordColor = LocalSecondAccentColor.current
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp).clearAndSetSemantics { },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PREVIEW_LINES.forEach { line ->
                FlowRow {
                    line.forEach { (chord, words) ->
                        Column {
                            Text(
                                text = chord?.let { shownNames[it] ?: it } ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = chordColor,
                            )
                            Text(
                                text = words,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The key [PREVIEW_LINES] are in, in the standard notation: E, whose chords read differently in every notation the app
 * has, its fifth being the B the German notation calls H. Never shown, only counted from by a numbering.
 */
private const val PREVIEW_KEY = "E"

/**
 * The opening of Amazing Grace, which is in the public domain and known in most of the world, each line as the chord
 * played on a stretch of it, if any, and the words of that stretch. Sample content rather than interface text, so it is
 * not translated, as the demo songs are not.
 */
private val PREVIEW_LINES = listOf(
    listOf("E" to "Amazing ", "E7" to "grace, how ", "A" to "sweet the ", "E" to "sound"),
    listOf(null to "That ", "E" to "saved a wretch like ", "B" to "me"),
)

/** The same lines as a song, which is what the notations are worked out from: a numbering counts from its key. */
private val PREVIEW_SONG = "{key: $PREVIEW_KEY}\n" + PREVIEW_LINES.joinToString("\n") { line ->
    line.joinToString("") { (chord, words) -> chord?.let { "[$it]" }.orEmpty() + words }
}
