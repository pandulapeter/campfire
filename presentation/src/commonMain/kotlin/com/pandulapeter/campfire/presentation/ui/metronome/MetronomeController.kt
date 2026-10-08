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

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import com.pandulapeter.campfire.data.model.domain.MetronomeSettings
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.domain.api.useCases.UpdateUserPreferencesUseCase
import com.pandulapeter.campfire.metronome.api.Metronome
import com.pandulapeter.campfire.metronome.api.model.BeatLevel
import com.pandulapeter.campfire.metronome.api.model.MetronomePattern
import com.pandulapeter.campfire.metronome.api.model.MetronomePlayback
import com.pandulapeter.campfire.metronome.api.model.MetronomeSound
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogHost
import com.pandulapeter.campfire.presentation.ui.messages.Message
import com.pandulapeter.campfire.presentation.ui.messages.MessageSink
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.playing.Tempos
import com.pandulapeter.campfire.presentation.ui.state.DebouncedPreference
import com.pandulapeter.campfire.presentation.ui.state.asState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The click: its settings, what it plays for (the song the pager is heading for, or the Metronome tab), starting and
 * stopping it from the keyboard, the panel and the screens, and the collectors that keep a playing click on the
 * pattern of what is on screen.
 *
 * @param currentSongFileName The song a details screen has settled on, which the pager's target stands in for.
 * @param songsBeingRenamed The songs being renamed, under their old names, see `CampfireViewModel.songsBeingRenamed`.
 * @param writeDelayMillis How long the settings have to hold still before they are written to the preferences.
 */
internal class MetronomeController(
    private val scope: CoroutineScope,

    private val metronome: Metronome,
    private val backStack: List<CampfireDestination>,

    private val currentSongFileName: (CampfireDestination.SongDetails) -> String?,
    private val dialogHost: DialogHost,

    private val userPreferences: StateFlow<UserPreferences?>,
    private val tempos: StateFlow<Tempos>,

    private val songsByFileName: StateFlow<Map<String, Song>>,
    private val songsBeingRenamed: StateFlow<Map<String, Song>>,

    private val messageSink: MessageSink,
    private val updateUserPreferences: UpdateUserPreferencesUseCase,

    private val writeDelayMillis: Long,
) {

    /** Saved like [pendingPrintSettings]: a dragged slider is a new value every frame. */
    val metronomeSettingsPreference = DebouncedPreference<MetronomeSettings> { copy(metronomeSettings = it) }

    val metronomeSettings = combine(userPreferences, metronomeSettingsPreference.pending) { userPreferences, pending ->
        pending ?: userPreferences?.metronomeSettings ?: MetronomeSettings()
    }.asState(scope, MetronomeSettings())

    val metronomePlayback = metronome.playback

    /** One item per click as it is heard, which the flash and the haptics are driven from and nothing else. */
    val metronomeBeats = metronome.beats

    /**
     * The song each song details screen's pager is heading for, by [CampfireDestination.SongDetails.id]: its target page
     * rather than the one it settles on, so that a click paged on to the next song has the new tempo while the page is
     * still sliding in. Reported by the screen, see [onSongDetailsPageChanged].
     */
    val songDetailsTargetSongs = mutableStateMapOf<String, String>()

    /**
     * The stretch of the song in [songDetailsTargetSongs] each song details screen's page is headed for, by the same
     * id, where it changes its tempo or time signature further down: what the click follows inside one song.
     */
    val songDetailsTargetTimings = mutableStateMapOf<String, SongTiming>()

    /**
     * The renames of the song a click plays for, old name to new, from the moment [updateSongFileName] knows the new
     * name until the click's context has followed it: the context moving from one to the other is the same song under
     * another name, which keeps the bar going instead of restarting it. Read and written on the main thread only.
     */
    val metronomeRenames = mutableMapOf<String, String>()

    /** What a click plays for, derived from the back stack, see [metronomeContextOf]; snapshot state, observable. */
    val metronomeContext
        get() = metronomeContextOf(
            backStack = backStack,
            currentSongOf = { destination -> songDetailsTargetSongs[destination.id] ?: currentSongFileName(destination) },
            currentTimingOf = { destination -> songDetailsTargetTimings[destination.id]?.takeIf { songDetailsTargetSongs[destination.id] != null } },
        )

    private var silentClickStopJob: Job? = null

    /**
     * Reported by the song details screen whenever its pager heads for a page, or the page it heads for for another
     * stretch of its song, see [songDetailsTargetSongs] and [songDetailsTargetTimings].
     */
    fun onSongDetailsPageChanged(destination: CampfireDestination.SongDetails, songFileName: String, timing: SongTiming?) {
        if (backStack.any { it is CampfireDestination.SongDetails && it.id == destination.id }) {
            // One snapshot, so that the click never reads the new song with the stretch of the one before it.
            Snapshot.withMutableSnapshot {
                songDetailsTargetSongs[destination.id] = songFileName
                if (timing == null) songDetailsTargetTimings.remove(destination.id) else songDetailsTargetTimings[destination.id] = timing
            }
        }
    }

    /** The pattern a click started now would play, for [metronomeContext]. */
    private fun currentMetronomePattern(context: MetronomeContext) = metronomePatternOf(
        context = context,
        settings = metronomeSettings.value,
        songOf = { songsByFileName.value[it] ?: songsBeingRenamed.value[it] },
        tempos = tempos.value,
    )

    /**
     * The keyboard's way to [toggleMetronome], asked from the desktop window and the web page: Space on the Metronome tab
     * and M on the song details screen, while nothing is drawn over either. Pedals send only arrows, so this is for
     * keyboards. Answers whether it did, so that anywhere else the key is left alone.
     */
    fun toggleMetronomeByKey(isSpace: Boolean): Boolean {
        if (dialogHost.visibleDialog.value != null || dialogHost.overlayState.isAnyMenuOpen) return false
        val top = backStack.lastOrNull()
        if (if (isSpace) top != CampfireDestination.Metronome else top !is CampfireDestination.SongDetails || userPreferences.value?.isMetronomeEnabled == false) return false
        toggleMetronome()
        return true
    }

    /**
     * Starts a click for whatever is on screen - the song, or the Metronome tab's own pattern - or stops the one
     * playing. A click started while a song is being read opens the panel with it, so that stopping it again from there
     * leaves the instrument up, see [toggleMetronomePanel].
     */
    fun toggleMetronome() {
        if (metronome.playback.value is MetronomePlayback.Playing) {
            metronome.stop()
        } else {
            val context = metronomeContext
            metronome.start(currentMetronomePattern(context))
            if (backStack.lastOrNull() is CampfireDestination.SongDetails) showMetronomePanel(isShown = true)
        }
    }

    /**
     * The song details screen's metronome button, which is about the panel rather than about the click: the panel is
     * what the click is started and stopped from there, so stopping one leaves the instrument up for the next and only
     * this takes it away. Opening it starts nothing, and closing it stops a click that is playing, since a click is
     * never left with nothing on screen to stop it with.
     *
     * Whether it is up is a preference (`MetronomeSettings.isSongPanelShown`), so a player who reads to a click finds
     * the panel on the next song as well; a click that is playing is only ever playing with the panel up.
     */
    fun toggleMetronomePanel() {
        if (metronomeSettings.value.isSongPanelShown || metronome.playback.value is MetronomePlayback.Playing) {
            showMetronomePanel(isShown = false)
            metronome.stop()
        } else {
            showMetronomePanel(isShown = true)
        }
    }

    private fun showMetronomePanel(isShown: Boolean) = updateMetronomeSettings { copy(isSongPanelShown = isShown) }

    fun stopMetronome() = metronome.stop()

    fun previewMetronomeSound(sound: MetronomeSound) = metronome.preview(sound, BeatLevel.ACCENT)

    fun updateMetronomeSettings(change: MetronomeSettings.() -> MetronomeSettings) =
        metronomeSettingsPreference.update { (it ?: metronomeSettings.value).change() }

    /**
     * Called whenever the app is out of sight (ON_STOP). A click that cannot sound - the volume at zero, every beat
     * muted - is only there for the flash and the haptics. The flash never reaches a screen nobody sees, and keeping the
     * click up for nothing would keep the phone's background audio (iOS's audio mode, Android's media playback service)
     * going for silence, which is not what either platform allows it for. The haptics do reach a pocket where
     * [areBeatsFeltInBackground] (Android, with a vibrator) and the Vibrate switch is on, so a click with a beat left to
     * feel is kept there. A few seconds' grace, since an Android activity recreated in front (a system theme or language
     * change) stops, and is only started again once its new composition is up, and a click playing on its own screen
     * is not to be stopped by that.
     */
    fun onAppStopped(areBeatsFeltInBackground: Boolean) {
        silentClickStopJob?.cancel()
        silentClickStopJob = scope.launch {
            delay(SILENT_CLICK_GRACE_MILLIS)
            val playing = metronome.playback.value as? MetronomePlayback.Playing ?: return@launch
            val isFelt = areBeatsFeltInBackground && metronomeSettings.value.isHapticBeatEnabled && playing.pattern.hasUnmutedBeat
            if (!playing.pattern.canSound && !isFelt) {
                metronome.stop()
                messageSink.sendMessage(Message.SilentMetronomeStopped)
            }
        }
    }

    /** Called whenever the app is in sight again (ON_START), which takes back a stop [onAppStopped] has not made yet. */
    fun onAppStarted() {
        silentClickStopJob?.cancel()
        silentClickStopJob = null
    }

    /** Writes the settings once they have held still, see [metronomeSettingsPreference]. */
    fun startSettingsWriter() = metronomeSettingsPreference.start(scope, writeDelayMillis, updateUserPreferences::invoke)

    /** Follows what a playing click plays, see the comment inside. */
    fun startFollowingPattern() = scope.launch {
        // One collector for everything that changes what a playing click plays, so that a context change and the
        // pattern it brings are one update: paging to another song moves the click to it from beat one, and
        // anything else - a tempo stepped, a setting, a song's {tempo} saved or synced - is applied from the next
        // beat. Leaving the song is not among them, since a click does not outlive the screen it is played from.
        // The first value is only remembered: a view model built again while a click plays (an activity recreated)
        // starts from a back stack it has not moved. What was applied is compared rather than the values repeating,
        // since a pattern that changed while the click was held is emitted again unchanged once the hold ends.
        var previous: MetronomeContext? = null
        var applied: MetronomePattern? = null
        combine(
            snapshotFlow { metronomeContext },
            tempos,
            metronomeSettings,
            songsByFileName,
            songsBeingRenamed,
        ) { context, tempos, settings, songs, renaming ->
            Triple(
                context,
                metronomePatternOf(context = context, settings = settings, songOf = { songs[it] ?: renaming[it] }, tempos = tempos),
                renaming.keys,
            )
        }.collect { (context, pattern, renaming) ->
            // Held while the click's own song is being renamed: its overrides move to the new name before the screen
            // does, and the screen follows a moment after the rename is over. Whatever changed meanwhile is applied
            // once it has.
            if (context is MetronomeContext.Song && (context.songFileName in renaming || context.songFileName in metronomeRenames)) {
                return@collect
            }
            val last = previous
            previous = context
            if (last != null) {
                val isMoved = isMetronomeContextMoved(last = last, context = context, renames = metronomeRenames)
                if (last is MetronomeContext.Song && context != last) metronomeRenames.remove(last.songFileName)
                if (context != last || pattern != applied) metronome.update(pattern, restartBar = isMoved)
            }
            applied = pattern
        }
    }

    /** Says why a click stopped on its own. */
    fun startReportingStops() = scope.launch {
        // Only what happens from here on: the engine outlives the view model, and a reason from before it was built
        // was said by the one before it.
        metronome.playback.drop(1).collect { playback ->
            (playback as? MetronomePlayback.Stopped)?.reason?.let { messageSink.sendMessage(Message.MetronomeStopped(it)) }
        }
    }

    /** Tells the engine whether a screen that can start a click is on top, see [isMetronomeStartable]. */
    fun startStartableRule() = scope.launch {
        // Where the web's audio output listens for the presses that allow a page's audio to start, so that tapping
        // around the rest of the app never opens the audio device; a no-op on the other platforms. The screen
        // composes before the tap that starts a click, so the listener is there in time.
        combine(
            snapshotFlow { backStack.lastOrNull() },
            userPreferences.map { it?.isMetronomeEnabled != false },
            ::isMetronomeStartable,
        ).distinctUntilChanged().collect(metronome::setStartable)
    }

    private companion object {
        const val SILENT_CLICK_GRACE_MILLIS = 3_000L
    }
}
