/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.setlists

import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.model.domain.Song

/**
 * One setlist as a list shows it: every entry it has, since a setlist is read as the list somebody wrote down
 * rather than as a view of the library. An entry whose file is not in the library any more (deleted from
 * outside the app) is kept as [Entry.Missing] rather than dropped, so that the user can see it and remove it.
 */
data class SetlistWithSongs(
    val setlist: Setlist,
    val entries: List<Entry>,
) {

    /** The songs that can actually be opened, which is what the pager of the song details screen gets. */
    val songs get() = entries.mapNotNull { (it as? Entry.Present)?.song }

    sealed interface Entry {

        /** The entry's place in the setlist, which is the number its row carries. */
        val index: Int

        val songFileName: String

        data class Present(override val index: Int, val song: Song) : Entry {
            override val songFileName get() = song.fileName
        }

        data class Missing(override val index: Int, override val songFileName: String) : Entry
    }
}
