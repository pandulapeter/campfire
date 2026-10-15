/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.importing

import com.pandulapeter.campfire.data.model.importing.ImportPlan

/** The one song a plan comes to, where it is nothing but that song: no setlist with it and nothing left out. */
internal val ImportPlan.loneSong: ImportPlan.SongEntry?
    get() = songs.singleOrNull()?.takeIf {
        setlists.isEmpty() && skippedFileNames.isEmpty() && oversizedFileNames.isEmpty() && unreadableDocumentFileNames.isEmpty()
    }

/**
 * The new song the plan of a pasted, dropped or shared text opens the editor on, or null where the plan is one song the
 * library already holds, which is opened rather than made again, or anything but one song, which is the import it would
 * be as a file. A song whose name is taken is a new song all the same: its first Save numbers the name.
 */
internal fun ImportPlan.newSongOf(): ImportPlan.SongEntry? = loneSong?.takeIf { it.status != ImportPlan.Status.IDENTICAL }
