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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_label
import com.pandulapeter.campfire.presentation.resources.ic_language
import com.pandulapeter.campfire.presentation.resources.ic_select
import com.pandulapeter.campfire.presentation.resources.ic_setlists
import com.pandulapeter.campfire.presentation.resources.ic_setlists_outline
import org.jetbrains.compose.resources.painterResource

/**
 * The icons every card of a song list draws, built once for the list and handed to each card: a painter of a vector is
 * an image to build and cache of its own, and a list that asked for its own on every card scrolled in or composed by a
 * pop would build several per card. [selectSongs] is the icon of the Select multiple entry in each card's menu.
 */
@Immutable
internal class SongCardPainters(
    val setlistStar: Painter,
    val setlistStarOutline: Painter,
    val tag: Painter,
    val language: Painter,
    val selectSongs: Painter,
)

@Composable
internal fun rememberSongCardPainters(): SongCardPainters {
    val setlistStar = painterResource(Res.drawable.ic_setlists)
    val setlistStarOutline = painterResource(Res.drawable.ic_setlists_outline)
    val tag = painterResource(Res.drawable.ic_label)
    val language = painterResource(Res.drawable.ic_language)
    val selectSongs = painterResource(Res.drawable.ic_select)
    return remember(setlistStar, setlistStarOutline, tag, language, selectSongs) {
        SongCardPainters(setlistStar = setlistStar, setlistStarOutline = setlistStarOutline, tag = tag, language = language, selectSongs = selectSongs)
    }
}
