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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_add
import com.pandulapeter.campfire.presentation.resources.ic_check
import com.pandulapeter.campfire.presentation.resources.ic_cloud
import com.pandulapeter.campfire.presentation.resources.ic_settings
import com.pandulapeter.campfire.presentation.resources.ic_songs
import com.pandulapeter.campfire.presentation.resources.metronome
import com.pandulapeter.campfire.presentation.resources.onboarding_done_message
import com.pandulapeter.campfire.presentation.resources.onboarding_done_setup
import com.pandulapeter.campfire.presentation.resources.onboarding_done_sync
import com.pandulapeter.campfire.presentation.resources.onboarding_done_tip_add
import com.pandulapeter.campfire.presentation.resources.onboarding_done_tip_demo
import com.pandulapeter.campfire.presentation.resources.onboarding_done_tip_settings
import com.pandulapeter.campfire.presentation.resources.onboarding_done_tips
import com.pandulapeter.campfire.presentation.resources.onboarding_done_title
import com.pandulapeter.campfire.presentation.resources.onboarding_done_lyrics_only
import com.pandulapeter.campfire.presentation.resources.setlists
import com.pandulapeter.campfire.presentation.resources.settings_chords
import com.pandulapeter.campfire.presentation.resources.tuner
import org.jetbrains.compose.resources.painterResource

/**
 * The last page: what the answers added up to, as a short list a newcomer can check at a glance (the features left on,
 * or that the songs will be lyrics only, and the cloud folder where one was connected), then where to go from here. The
 * look, the instrument and the notation are not repeated, since the app is already wearing them.
 */
@Composable
internal fun DoneStep(
    modifier: Modifier = Modifier,
    features: OnboardingFeatures,
    isSyncConnected: Boolean,
    isCompact: Boolean,
) = OnboardingPage(
    modifier = modifier,
    icon = painterResource(Res.drawable.ic_check),
    title = stringResource(Res.string.onboarding_done_title),
    message = stringResource(Res.string.onboarding_done_message),
    isCompact = isCompact,
) {
    DoneCard(title = stringResource(Res.string.onboarding_done_setup)) {
        val check = painterResource(Res.drawable.ic_check)
        if (features.areChordsEnabled) DoneRow(icon = check, text = stringResource(Res.string.settings_chords))
        if (!features.areChordsEnabled) DoneRow(icon = check, text = stringResource(Res.string.onboarding_done_lyrics_only))
        if (features.areSetlistsEnabled) DoneRow(icon = check, text = stringResource(Res.string.setlists))
        if (features.isMetronomeEnabled) DoneRow(icon = check, text = stringResource(Res.string.metronome))
        if (features.isTunerEnabled) DoneRow(icon = check, text = stringResource(Res.string.tuner))
        if (isSyncConnected) DoneRow(icon = painterResource(Res.drawable.ic_cloud), text = stringResource(Res.string.onboarding_done_sync))
    }
    DoneCard(title = stringResource(Res.string.onboarding_done_tips)) {
        DoneRow(icon = painterResource(Res.drawable.ic_songs), text = stringResource(Res.string.onboarding_done_tip_demo))
        DoneRow(icon = painterResource(Res.drawable.ic_add), text = stringResource(Res.string.onboarding_done_tip_add))
        DoneRow(icon = painterResource(Res.drawable.ic_settings), text = stringResource(Res.string.onboarding_done_tip_settings))
    }
}

@Composable
private fun DoneCard(
    title: String,
    content: @Composable () -> Unit,
) = Surface(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surfaceContainerLow,
) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            modifier = Modifier.semantics { heading() },
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        content()
    }
}

@Composable
private fun DoneRow(
    icon: Painter,
    text: String,
) = Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
) {
    Icon(
        painter = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
    )
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
    )
}
