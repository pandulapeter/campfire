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

import androidx.compose.foundation.layout.size
import com.pandulapeter.campfire.data.model.domain.Setlist

/** Where each page of a setlist's pager sits in the setlist, see [SongPagerControls]. */
internal data class SetlistSlots(
    val slotByPage: List<Int>,
    val entryCount: Int,
)

/** A missing page invalidates the numbering, while absent entries still count toward the total. */
internal fun buildSetlistSlots(entries: List<Setlist.Entry>, songFileNames: List<String>): SetlistSlots? {
    val entryIndexByFileName = mutableMapOf<String, Int>()
    entries.forEachIndexed { index, entry ->
        if (entry.songFileName !in entryIndexByFileName) entryIndexByFileName[entry.songFileName] = index
    }
    val slotByPage = songFileNames.map { entryIndexByFileName[it] ?: return null }
    return SetlistSlots(slotByPage = slotByPage, entryCount = entries.size)
}
