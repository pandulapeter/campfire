/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.screenshots

import com.pandulapeter.campfire.data.model.library.ExportedFile
import com.pandulapeter.campfire.data.model.domain.ImportedFile
import com.pandulapeter.campfire.data.model.sync.SyncAccount
import com.pandulapeter.campfire.data.model.sync.SyncDeletionPolicy
import com.pandulapeter.campfire.data.model.sync.SyncOutcome
import com.pandulapeter.campfire.data.model.sync.SyncProgress
import com.pandulapeter.campfire.data.model.sync.SyncProviderId
import com.pandulapeter.campfire.data.model.sync.SyncState
import com.pandulapeter.campfire.data.model.sync.SyncSummary
import com.pandulapeter.campfire.data.repository.api.SyncRepository
import com.pandulapeter.campfire.data.model.sync.AuthorizationCompletionPage
import com.pandulapeter.campfire.metronome.api.Metronome
import com.pandulapeter.campfire.metronome.api.model.BeatLevel
import com.pandulapeter.campfire.metronome.api.model.MetronomeBeat
import com.pandulapeter.campfire.metronome.api.model.MetronomePattern
import com.pandulapeter.campfire.metronome.api.model.MetronomePlayback
import com.pandulapeter.campfire.metronome.api.model.MetronomeSound
import com.pandulapeter.campfire.presentation.ui.platform.FilePicker
import com.pandulapeter.campfire.tuner.api.Tuner
import com.pandulapeter.campfire.tuner.api.model.TunerConfig
import com.pandulapeter.campfire.tuner.api.model.TunerListening
import com.pandulapeter.campfire.tuner.api.model.TunerReading
import com.pandulapeter.campfire.tuner.api.model.TunerState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.dsl.module
import kotlin.time.Clock

/**
 * What the shots are taken with in place of the parts of the app that reach outside it, so that nothing a run does is
 * heard, sent or saved: the library being shot is a copy, and the account its sync section shows may hold another one.
 */
internal val fakes = module {
    single<SyncRepository> { ConnectedSyncRepository() }
    single<Metronome> { SilentMetronome() }
    single<Tuner> { ScriptedTuner() }
}

/**
 * Sync as it looks on a device that has been connected all along and synchronized a few minutes ago. It runs nothing
 * and connects nothing, whatever it is asked.
 */
private class ConnectedSyncRepository : SyncRepository {

    override val syncState = MutableStateFlow<SyncState>(
        SyncState.Connected(
            account = SyncAccount(
                providerId = SyncProviderId.DROPBOX,
                id = "screenshots",
                displayName = "Péter Pandula",
                email = null,
            ),
            progress = null,
            lastSyncedAt = Clock.System.now().toEpochMilliseconds() - LAST_SYNC_AGE_MILLIS,
            lastOutcome = SyncOutcome.Success(SyncSummary()),
        ),
    )

    override val availableProviders = listOf(SyncProviderId.DROPBOX)

    override suspend fun restore() = SyncRepository.RestoreResult(isConnected = true, didReturnFromAuthorization = false, wasInterrupted = false)

    override suspend fun connect(providerId: SyncProviderId, completionPage: AuthorizationCompletionPage) = false

    override suspend fun cancelConnection() = Unit

    override suspend fun disconnect() = Unit

    override suspend fun forgetStoredConnection() = Unit

    override fun synchronize(deletionPolicy: SyncDeletionPolicy) = false

    override fun scheduleSynchronization() = Unit

    override fun startScheduledSynchronization(): SyncProgress? = null

    override fun cancelSynchronization() = Unit
}

/**
 * A metronome that plays without a sound: it reports the pattern it was started with as playing, and a beat only when
 * a shot asks for one with [beat], so that the beat a shot shows lit is the one it chose rather than wherever a clock
 * had got to when the frame was taken.
 */
internal class SilentMetronome : Metronome {
    private val _playback = MutableStateFlow<MetronomePlayback>(MetronomePlayback.Stopped())
    private val _beats = MutableSharedFlow<MetronomeBeat>(extraBufferCapacity = 16)

    override val playback: StateFlow<MetronomePlayback> = _playback.asStateFlow()

    override val beats: SharedFlow<MetronomeBeat> = _beats.asSharedFlow()

    override fun start(pattern: MetronomePattern) {
        _playback.value = MetronomePlayback.Playing(pattern)
    }

    override fun update(pattern: MetronomePattern, fromNextBar: Boolean) {
        if (_playback.value is MetronomePlayback.Playing) _playback.value = MetronomePlayback.Playing(pattern)
    }

    override fun preview(sound: MetronomeSound, level: BeatLevel) = Unit

    override fun stop() {
        _playback.value = MetronomePlayback.Stopped()
    }

    override fun setStartable(isStartable: Boolean) = Unit

    /** Reports the beat at [beatIndex] of the bar as heard, which the beat row and the app bar's mark light up for. */
    fun beat(beatIndex: Int) {
        val pattern = (_playback.value as? MetronomePlayback.Playing)?.pattern ?: return
        _beats.tryEmit(
            MetronomeBeat(
                beatIndex = beatIndex,
                barIndex = 0,
                level = pattern.beatLevel(beatIndex),
                isSubdivision = false,
            ),
        )
    }
}

/**
 * A tuner without a microphone: it reports itself as listening once asked to, and hears only what a shot hands it with
 * [hear], so that the needle a recording shows moves the way the shot scripted rather than with whatever a room sounds
 * like. It plays no tone.
 */
internal class ScriptedTuner : Tuner {
    override val state = MutableStateFlow(TunerState())

    override fun listen(config: TunerConfig) {
        if (state.value.listening !is TunerListening.Hearing) state.value = TunerState(listening = TunerListening.Hearing())
    }

    override fun update(config: TunerConfig) = Unit

    override fun stopListening() {
        state.value = TunerState()
    }

    override fun playTone(note: Int, referencePitch: Int) = Unit

    override fun stopTone() = Unit

    override fun setStartable(isStartable: Boolean) = Unit

    /** Reports [reading] as what the microphone hears, or silence for null; ignored while not listening. */
    fun hear(reading: TunerReading?) {
        if (state.value.listening is TunerListening.Hearing) state.value = TunerState(listening = TunerListening.Hearing(reading = reading))
    }
}

/**
 * The file picker of the platform a shot is taken as, as far as anything on screen can tell: whether Share stands next
 * to Save. Nothing is ever picked or written.
 */
internal class ScreenshotFilePicker(override val canShare: Boolean) : FilePicker {

    override suspend fun pickFiles(): List<ImportedFile> = emptyList()

    override suspend fun saveFile(file: ExportedFile) = false

    override suspend fun shareFile(file: ExportedFile) = false
}

private const val LAST_SYNC_AGE_MILLIS = 4 * 60 * 1000L
