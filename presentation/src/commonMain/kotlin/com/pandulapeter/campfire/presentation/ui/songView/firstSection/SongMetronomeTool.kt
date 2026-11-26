/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView.firstSection

import androidx.compose.runtime.Immutable

/**
 * What song details' tempo chip says and opens: [bpm], the tempo of the stretch the page is in, and [onTempoClicked], the
 * Tempo sheet, null in read only mode, whose chip opens nothing. It is there in read only mode too, since the click
 * changes nothing about a song.
 */
@Immutable
internal class SongMetronomeTool(
    val bpm: Int,
    val onTempoClicked: (() -> Unit)?,
)
