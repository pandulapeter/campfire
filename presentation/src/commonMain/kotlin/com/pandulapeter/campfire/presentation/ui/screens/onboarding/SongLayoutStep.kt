/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.onboarding

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.preferences.SongLayout
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.onboarding_song_layout_message
import com.pandulapeter.campfire.presentation.resources.song_layout_title
import com.pandulapeter.campfire.presentation.ui.songDisplay.SongLayoutChoices

/**
 * "How do you read songs?": pages turned by the reader, or one column that scrolls by itself, the same two cards the
 * first song opened asks with where this page was never reached. Being on the page answers it, with the pages shown as
 * picked until the other card is.
 */
@Composable
internal fun SongLayoutStep(
    modifier: Modifier = Modifier,
    markModifier: Modifier,
    selected: SongLayout?,
    isCompact: Boolean,
    onSelected: (SongLayout) -> Unit,
) = OnboardingPage(
    modifier = modifier,
    markModifier = markModifier,
    title = stringResource(Res.string.song_layout_title),
    message = stringResource(Res.string.onboarding_song_layout_message),
    isCompact = isCompact,
) {
    SongLayoutChoices(
        modifier = Modifier.padding(horizontal = 16.dp),
        selected = selected,
        onSelected = onSelected,
    )
}
