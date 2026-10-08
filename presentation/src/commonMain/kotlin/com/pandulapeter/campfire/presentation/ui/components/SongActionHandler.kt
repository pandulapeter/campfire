/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

import androidx.compose.runtime.Stable
import com.pandulapeter.campfire.data.model.domain.Song

/**
 * What the song menus ([SongActions], [SongEditingActions], [SetlistAssignmentsButton]) do with their song. One per
 * screen, remembered, whose methods take the song, so that a row composed again as a list scrolls is handed the same
 * object rather than new lambdas that would keep it from skipping.
 */
@Stable
internal interface SongActionHandler {

    fun edit(song: Song)

    fun updateFileName(song: Song)

    fun export(song: Song, setlistFileName: String?)

    fun delete(song: Song)

    /** Opens the setlist assignments sheet; [setlistFileName] is the setlist the song is read through, if any. */
    fun chooseSetlists(song: Song, setlistFileName: String?)
}
