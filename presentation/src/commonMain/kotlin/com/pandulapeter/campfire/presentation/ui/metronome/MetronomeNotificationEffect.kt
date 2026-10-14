/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.metronome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.metronome.api.model.MetronomePlayback
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.metronome
import com.pandulapeter.campfire.presentation.resources.metronome_notification_channel
import com.pandulapeter.campfire.presentation.resources.metronome_stop
import com.pandulapeter.campfire.presentation.resources.song_details_tempo
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.KEY_SEPARATOR
import com.pandulapeter.campfire.presentation.ui.platform.LocalMetronomeNotifier
import com.pandulapeter.campfire.presentation.ui.platform.MetronomeNotification
import com.pandulapeter.campfire.presentation.ui.platform.MetronomeNotifier

/**
 * Hands a playing click to the shell in the language chosen in the app, see [MetronomeNotifier], and says it is over only
 * once it has said it began: reported on the first frame of every composition, "nothing to show" would reach the Android
 * shell of an activity recreated over a click that is still playing.
 */
@Composable
internal fun MetronomeNotificationEffect(viewModel: CampfireViewModel) {
    val notifier = LocalMetronomeNotifier.current
    val playback by viewModel.metronomePlayback.collectAsStateWithLifecycle()
    val songsByFileName by viewModel.songsByFileName.collectAsStateWithLifecycle()
    val pattern = (playback as? MetronomePlayback.Playing)?.pattern
    val songTitle = (viewModel.metronomeContext as? MetronomeContext.Song)?.let { songsByFileName[it.songFileName]?.title }
    val notification = pattern?.let {
        MetronomeNotification(
            channelName = stringResource(Res.string.metronome_notification_channel),
            title = songTitle ?: stringResource(Res.string.metronome),
            body = "${stringResource(Res.string.song_details_tempo, it.bpm.toString())} $KEY_SEPARATOR ${it.timeSignature}",
            stopLabel = stringResource(Res.string.metronome_stop),
        )
    }
    var hasShown by remember { mutableStateOf(false) }
    LaunchedEffect(notification) {
        if (notification != null) {
            hasShown = true
            notifier.onMetronomeNotificationChanged(notification)
        } else if (hasShown) {
            hasShown = false
            notifier.onMetronomeNotificationChanged(null)
        }
    }
}
