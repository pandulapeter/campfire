/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs.pickers

import com.pandulapeter.campfire.data.model.setlist.Setlist

/**
 * Whether the setlist picker for [songFileNames] - one song, or the songs ticked in the selection mode - would list any
 * setlist at all as it opens: an archived one only counts while it holds one of the songs, the way the sheet's own rows
 * leave the other archived ones out.
 */
internal fun hasListableSetlist(setlists: List<Setlist>, songFileNames: Collection<String>) =
    setlists.any { setlist -> !setlist.isArchived || setlist.entries.any { it.songFileName in songFileNames } }
