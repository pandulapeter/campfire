/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import androidx.compose.runtime.Immutable

/** Editing callbacks for the card or sheet header and the groups of [SongInfoBody], see [rememberSongInfoEditing]. */
@Immutable
internal class SongInfoEditing(
    val onEditCoverArt: (() -> Unit)?,
    val onEditMetadata: () -> Unit,
    val onEditTags: () -> Unit,
    val onEditLanguages: () -> Unit,
    val onEditLinks: () -> Unit,
)
