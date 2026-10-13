/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.pandulapeter.campfire.chordpro.model.TabPlayer
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.tab_notice_hidden
import com.pandulapeter.campfire.presentation.resources.tab_notice_hidden_instrument
import com.pandulapeter.campfire.presentation.resources.tab_notice_not_moved
import com.pandulapeter.campfire.presentation.resources.tab_notice_not_moved_instrument
import com.pandulapeter.campfire.presentation.resources.tab_notice_octave_down
import com.pandulapeter.campfire.presentation.resources.tab_notice_octave_up
import com.pandulapeter.campfire.presentation.resources.tab_notice_other_instrument
import com.pandulapeter.campfire.presentation.ui.chords.toChordNotation
import com.pandulapeter.campfire.presentation.ui.components.textResource
import com.pandulapeter.campfire.presentation.ui.instrument.instrumentLabel
import com.pandulapeter.campfire.presentation.ui.instrument.instrumentTuning

/**
 * How the tabs of a song are shown to one reader: the [player] they are chosen for and moved to, and the [labels] what
 * was done to them is said in. Where [selects] is false (the editor's preview) every tab written for an instrument is
 * shown, since the preview shows the song as it is written.
 */
internal class TabReading(val player: TabPlayer, val labels: TabNoticeLabels, val selects: Boolean)

/**
 * The [TabReading] of the reader [preferences] describe, null until they are read. The same one is given back for as
 * long as the instrument, its tuning, the chord notation and the app's language stay what they are, since a song is
 * moved again whenever it changes.
 */
@Composable
internal fun rememberTabReading(preferences: UserPreferences?, isMoved: Boolean = true): TabReading? {
    val labels = rememberTabNoticeLabels()
    preferences ?: return null
    val instrument = preferences.instrument
    val tuning = preferences.instrumentTuning
    val notation = preferences.chordSpelling.notation.toChordNotation()
    return remember(instrument, tuning, notation, labels, isMoved) {
        TabReading(player = tabPlayerOf(instrument, tuning, notation, isMoved), labels = labels, selects = isMoved)
    }
}

/** The [TabNoticeLabels] in the app's language. */
@Composable
internal fun rememberTabNoticeLabels(): TabNoticeLabels {
    val instruments = UserPreferences.Instrument.entries.associate { instrument -> instrument.id to instrumentLabel(instrument) }
    val octaveUp = stringResource(Res.string.tab_notice_octave_up)
    val octaveDown = stringResource(Res.string.tab_notice_octave_down)
    val otherInstrument = textResource(Res.string.tab_notice_other_instrument, INSTRUMENT)
    val notMoved = stringResource(Res.string.tab_notice_not_moved)
    val notMovedInstrument = textResource(Res.string.tab_notice_not_moved_instrument, INSTRUMENT)
    val hidden = stringResource(Res.string.tab_notice_hidden)
    val hiddenInstrument = textResource(Res.string.tab_notice_hidden_instrument, INSTRUMENT)
    return remember(instruments, octaveUp, octaveDown, otherInstrument, notMoved, notMovedInstrument, hidden, hiddenInstrument) {
        TabNoticeLabels(
            octaveUp = octaveUp,
            octaveDown = octaveDown,
            otherInstrument = otherInstrument,
            notMoved = notMoved,
            notMovedInstrument = notMovedInstrument,
            hidden = hidden,
            hiddenInstrument = hiddenInstrument,
            instruments = instruments,
        )
    }
}
