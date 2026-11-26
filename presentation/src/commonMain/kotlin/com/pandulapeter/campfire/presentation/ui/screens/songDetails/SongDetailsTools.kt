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

import com.pandulapeter.campfire.data.model.preferences.UserPreferences

/**
 * Which of the tools song details has: its click in the dock ([isMetronomeEnabled]), the tuner at all ([isTunerEnabled]:
 * the Playing sheet's, the U key's, and the instrument card's or the menu's), and the tuner in the dock's instrument card
 * wherever the card is shown ([isTunerInCard]).
 */
internal data class SongDetailsTools(
    val isMetronomeEnabled: Boolean,
    val isTunerEnabled: Boolean,
    val isTunerInCard: Boolean,
) {
    /**
     * Whether the song's menu holds the tuner, the dock showing its instrument card or not ([hasInstrumentCard]): only
     * where the card does not hold it, so that the screen offers it once (the Playing sheet aside, behind the key chip),
     * and wherever the card is not shown whatever the Dock's switch says, so that it is never out of reach.
     */
    fun isTunerInMenu(hasInstrumentCard: Boolean) = isTunerEnabled && !(isTunerInCard && hasInstrumentCard)
}

/**
 * The tools of song details under [userPreferences]: the Metronome feature less what the Song page's Dock section leaves
 * out of this screen alone (the tab keeps it), and the Tuner feature, in the dock's instrument card where the Dock
 * section's Tuner switch puts it, else in the song's menu; never an import preview's, which has no tuner.
 */
internal fun songDetailsToolsOf(userPreferences: UserPreferences?, isImportPreview: Boolean): SongDetailsTools {
    val isTunerEnabled = userPreferences?.isTunerEnabled != false && !isImportPreview
    return SongDetailsTools(
        isMetronomeEnabled = userPreferences?.isMetronomeEnabled != false && userPreferences?.isDockMetronomeEnabled != false,
        isTunerEnabled = isTunerEnabled,
        isTunerInCard = isTunerEnabled && userPreferences?.isDockTunerEnabled != false,
    )
}
