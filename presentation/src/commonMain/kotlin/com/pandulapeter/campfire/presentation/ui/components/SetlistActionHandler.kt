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
import com.pandulapeter.campfire.data.model.domain.Setlist

/** What [SetlistActions] does with its setlist, one per screen and remembered, as [SongActionHandler] is. */
@Stable
internal interface SetlistActionHandler {

    fun edit(setlist: Setlist)

    fun chooseSongs(setlist: Setlist)

    fun duplicate(setlist: Setlist)

    fun setArchived(setlist: Setlist, isArchived: Boolean)

    fun export(setlist: Setlist)

    fun delete(setlist: Setlist)
}
