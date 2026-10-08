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

import androidx.compose.runtime.Composable
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.songs_artist_and_title
import com.pandulapeter.campfire.presentation.ui.components.textResource

/**
 * How a dialog, a sheet or a screen about one song names it under its title: `Artist - Title (Subtitle)`, the subtitle
 * being part of [Song.title] already, or the title alone for a song that names no artist. Every such subtitle goes
 * through here, so that one song is named the same way wherever it is the subject.
 */
@Composable
internal fun songLabel(song: Song) = if (song.artist.isBlank()) song.title else textResource(Res.string.songs_artist_and_title, song.artist, song.title)
