/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.songCard

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow

/**
 * The title remains one line, except in a narrow window: there the cover and the trailing buttons leave a phone's card
 * so little of its width that a title cut to one line often could not be told from the next song's, so it may take a
 * second.
 */
@Composable
internal fun ListItemHeadline(
    title: String,
) = Text(text = title, maxLines = if (isNarrowSongCardWindow) 2 else 1, overflow = TextOverflow.Ellipsis)
