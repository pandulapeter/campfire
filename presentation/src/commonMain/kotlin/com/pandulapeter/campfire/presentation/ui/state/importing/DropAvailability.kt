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

import com.pandulapeter.campfire.presentation.ui.CampfireViewModel

/**
 * Whether the window takes a drop at all, which every shell asks both as a drag starts and again as it ends (a drag
 * lasts long enough for the setting to change under it). Read only mode offers no way to add to the library, and a drop
 * is one; a file opened with Campfire or shared to it is still imported there, since the user sent it from outside. A
 * dropped text is a new song, asked [PastedSongController.isNewSongPasteAvailable] besides, which refuses it in read
 * only mode too, and with the songs switched off, since it needs the songs on screen. Dropped files are taken with the
 * songs off as well: they are an import like Settings' own, written into a library that is only out of sight.
 */
internal val CampfireViewModel.isDropAvailable
    get() = !preferencesController.isPerformanceModeEnabled.value
