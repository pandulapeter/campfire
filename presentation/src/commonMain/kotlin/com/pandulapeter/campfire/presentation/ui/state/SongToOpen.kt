/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state

import com.pandulapeter.campfire.data.model.domain.ImportPlan
import com.pandulapeter.campfire.data.model.domain.ImportResult

/**
 * The song an import of [plan] that ended in [result] opens, or null where it opens none: only one asked to open
 * one ([shouldOpenSong]), of a single song that is neither converted from a document nor travelling with a setlist.
 */
internal fun songToOpen(plan: ImportPlan, result: ImportResult, shouldOpenSong: Boolean) =
    // A song that was already in the library is opened as well: it is still the song that was asked for, under
    // the name the library has for it. One that the answer to the conflicts left out is in neither list, and
    // the library's own file under that name is a different song.
    if (shouldOpenSong && plan.songs.size == 1 && !plan.songs.single().isConverted && plan.setlists.isEmpty()) {
        (result.importedSongFileNames + result.duplicateFileNames).singleOrNull()
    } else {
        null
    }
