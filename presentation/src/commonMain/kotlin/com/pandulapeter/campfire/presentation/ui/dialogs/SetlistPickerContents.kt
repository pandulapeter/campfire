/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs

import com.pandulapeter.campfire.data.model.domain.Setlist

/**
 * Whether the setlist picker for [songFileName] would list any setlist at all as it opens: an archived one only counts
 * while it holds the song, the way the sheet's own rows leave the other archived ones out.
 */
internal fun hasListableSetlist(setlists: List<Setlist>, songFileName: String) =
    setlists.any { setlist -> !setlist.isArchived || setlist.entries.any { it.songFileName == songFileName } }
