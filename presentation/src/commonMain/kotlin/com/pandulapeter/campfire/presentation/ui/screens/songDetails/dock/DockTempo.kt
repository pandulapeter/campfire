/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails.dock

import androidx.compose.runtime.Immutable
import com.pandulapeter.campfire.presentation.ui.songView.firstSection.SongTempoControl

/**
 * The tempo of one page as the dock's tempo chip says it: [bpm], the stretch the page is in (a later `{tempo}`'s, an
 * override of the opening one scaled into it), and [control], what the Tempo sheet sets, null in read only mode, whose
 * chip opens no sheet (it would write an override), the click still playing. [onClick] opens the sheet.
 */
@Immutable
internal data class DockTempo(
    val bpm: Int,
    val control: SongTempoControl?,
    val onClick: (() -> Unit)?,
)
