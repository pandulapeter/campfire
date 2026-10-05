/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire

import com.pandulapeter.campfire.metronome.api.Metronome
import com.pandulapeter.campfire.metronome.api.model.MetronomePlayback
import com.pandulapeter.campfire.presentation.ui.platform.MetronomeNotification
import com.pandulapeter.campfire.presentation.ui.platform.MetronomeNotifier
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import platform.MediaPlayer.MPMediaItemPropertyArtist
import platform.MediaPlayer.MPMediaItemPropertyTitle
import platform.MediaPlayer.MPNowPlayingInfoCenter
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackRate
import platform.MediaPlayer.MPRemoteCommand
import platform.MediaPlayer.MPRemoteCommandCenter
import platform.MediaPlayer.MPRemoteCommandHandlerStatusSuccess

/**
 * The lock screen's and Control Center's face of a playing click: Now Playing with the song and the tempo, in the
 * language chosen in the app, and the remote commands, every one of which stops the click, since there is no paused
 * state to come back to. It only draws and answers: the audio session, the background audio and the interruptions are
 * the audio output's (see :metronome:implementation), so a click stops for a call whether or not this is around. It
 * follows the engine itself for taking Now Playing down, since the composition stops as the app leaves the front.
 *
 * One per process, created after Koin.
 */
class IosMetronomeNotifier(private val metronome: Metronome) : MetronomeNotifier {

    private val scope = MainScope()
    private val commandTargets = mutableListOf<Pair<MPRemoteCommand, Any>>()

    init {
        scope.launch { metronome.playback.collect { if (it !is MetronomePlayback.Playing) clear() } }
    }

    override fun onMetronomeNotificationChanged(notification: MetronomeNotification?) {
        if (notification == null || metronome.playback.value !is MetronomePlayback.Playing) {
            clear()
            return
        }
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = mapOf<Any?, Any?>(
            MPMediaItemPropertyTitle to notification.title,
            MPMediaItemPropertyArtist to notification.body,
            MPNowPlayingInfoPropertyPlaybackRate to 1.0,
        )
        if (commandTargets.isEmpty()) {
            val center = MPRemoteCommandCenter.sharedCommandCenter()
            center.playCommand.enabled = false
            listOf(center.pauseCommand, center.stopCommand, center.togglePlayPauseCommand).forEach { command ->
                command.enabled = true
                commandTargets += command to command.addTargetWithHandler {
                    metronome.stop()
                    MPRemoteCommandHandlerStatusSuccess
                }
            }
        }
    }

    private fun clear() {
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = null
        commandTargets.forEach { (command, target) -> command.removeTarget(target) }
        commandTargets.clear()
    }
}
