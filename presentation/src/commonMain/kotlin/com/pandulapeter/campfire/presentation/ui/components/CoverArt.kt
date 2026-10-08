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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.pandulapeter.campfire.domain.api.useCases.GetCoverArtUseCase
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/** What a cover image is asked of Coil by: the address a song names, fetched through [GetCoverArtUseCase]. */
internal data class CoverArt(val url: String)

/**
 * A cover address from text that is being typed, following it only once the typing has paused
 * ([COVER_ART_SETTLE_DELAY]): every half-typed address that happens to be a valid one would otherwise be a download of
 * its own, and one that failed is not asked again for a while. A cover taken out of the text goes at once, since that
 * asks nothing, and the first frame already has the address the text started with.
 */
@Composable
internal fun rememberSettledCoverArtUrl(url: String?): String? {
    var settledUrl by remember { mutableStateOf(url) }
    LaunchedEffect(url) {
        if (url != null) delay(COVER_ART_SETTLE_DELAY)
        settledUrl = url
    }
    return settledUrl
}

private val COVER_ART_SETTLE_DELAY = 500.milliseconds
