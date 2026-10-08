/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.playing

import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.domain.api.useCases.UpdateSetlistUseCase
import com.pandulapeter.campfire.domain.api.useCases.UpdateUserPreferencesUseCase
import com.pandulapeter.campfire.metronome.api.model.MetronomePattern
import com.pandulapeter.campfire.presentation.ui.messages.Message
import com.pandulapeter.campfire.presentation.ui.messages.MessageSink
import com.pandulapeter.campfire.presentation.ui.state.asState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine

/**
 * How every song is played where it is opened - its transposition, tempo and capo - as the preferences and the setlists
 * hold them, with the tempos and capos set here and still on their way to the store over them, and the steppers that
 * set all three.
 *
 * @param songsByFileName The library by file name as it is now, which the steppers read the song's own values from.
 * @param updateEditableSetlist A setlist changed unless it is archived, see `CampfireViewModel.updateEditableSetlist`.
 * @param writeDelayMillis How long a stepper has to hold still before its value is written.
 */
internal class PlayingOverrides(
    private val scope: CoroutineScope,
    userPreferences: StateFlow<UserPreferences?>,
    setlists: StateFlow<List<Setlist>>,
    private val songsByFileName: () -> Map<String, Song>,
    private val updateUserPreferences: UpdateUserPreferencesUseCase,
    private val updateEditableSetlist: suspend (fileName: String, transform: (Setlist) -> Setlist) -> Setlist?,
    private val messageSink: MessageSink,
    writeDelayMillis: Long,
) {

    /**
     * Where a song's transposition is kept depends on how it was opened, so both places are folded into one lookup:
     * a song opened from a setlist reads the setlist's entry, one opened from the library reads the preferences.
     */
    val transpositions = combine(userPreferences, setlists) { userPreferences, setlists ->
        Transpositions(SongOverrides.of(userPreferences?.transpositions.orEmpty(), setlists) { it.transposition.takeIf { semitones -> semitones != 0 } })
    }.asState(scope, Transpositions())

    /** The tempo overrides as the preferences and the setlists hold them, folded the way [transpositions] are. */
    private val storedTempos = combine(userPreferences, setlists) { userPreferences, setlists ->
        SongOverrides.of(userPreferences?.tempos.orEmpty(), setlists) { it.tempo }
    }.asState(scope, Tempos())

    /** The tempos set here whose writes are waiting or still on their way back, see [changeTempo]. */
    private val tempoOverrides = PendingOverrides(
        scope = scope,
        delayMillis = writeDelayMillis,
        stored = storedTempos,
        name = "tempo",
        write = { place, bpm ->
            writeOverride(
                place = place,
                value = bpm,
                library = { it.tempos },
                withLibrary = { preferences, tempos -> preferences.copy(tempos = tempos) },
                withEntry = { entry, value -> entry.copy(tempo = value) },
            )
        },
        onFailed = { messageSink.sendMessage(Message.OperationFailed) },
    )

    /** Every song's tempo override, as the screens and the click read it. */
    val tempos = tempoOverrides.effective.asState(scope, Tempos())

    /** The capo overrides as the preferences and the setlists hold them, folded the way [tempos] are. */
    private val storedCapos = combine(userPreferences, setlists) { userPreferences, setlists ->
        SongOverrides.of(userPreferences?.capos.orEmpty(), setlists) { it.capo }
    }.asState(scope, Capos())

    /** The capos set here whose writes are waiting or still on their way back, exactly as [tempoOverrides] are. */
    private val capoOverrides = PendingOverrides(
        scope = scope,
        delayMillis = writeDelayMillis,
        stored = storedCapos,
        name = "capo",
        write = { place, fret ->
            writeOverride(
                place = place,
                value = fret,
                library = { it.capos },
                withLibrary = { preferences, capos -> preferences.copy(capos = capos) },
                withEntry = { entry, value -> entry.copy(capo = value) },
            )
        },
        onFailed = { messageSink.sendMessage(Message.OperationFailed) },
    )

    /** Every song's capo override, as the song details screen and the lists that name a sounding key read it. */
    val capos = capoOverrides.effective.asState(scope, Capos())

    /** The three overrides of how a song is played as one value, for the screens that read all three. */
    val playingOverrides = combine(transpositions, capos, tempos, ::PlayingOverridesSnapshot).asState(scope, PlayingOverridesSnapshot())

    /**
     * One step of the transposition stepper. A song opened from a setlist transposes inside that setlist; one opened from
     * the library, in the preferences.
     */
    fun stepTransposition(songFileName: String, setlistFileName: String?, semitones: Int) =
        changeTransposition(songFileName = songFileName, setlistFileName = setlistFileName) { it + semitones }

    /** The stepper's value tapped: the song goes back to the key its file is written in. */
    fun resetTransposition(songFileName: String, setlistFileName: String?) =
        changeTransposition(songFileName = songFileName, setlistFileName = setlistFileName) { 0 }

    /**
     * Applies [change] to the transposition the store holds when the write runs rather than to the one the stepper was
     * drawn with. A setlist's entry only reaches the screen once its write has been round tripped through the
     * repository, and every tap inside that round trip reads the same number off the stepper, so an absolute value would
     * turn five quick taps into two. The setlist's transform is handed the latest version of the file, one write at a
     * time ([UpdateSetlistUseCase]), and the preferences' is applied to what the repository holds when it runs
     * ([UpdateUserPreferencesUseCase]): [userPreferences] is a few hops downstream of it and may not have the previous
     * tap yet.
     *
     * A setlist that is gone by now is not brought back, and saying nothing would leave a stepper that does nothing.
     *
     * The result is wrapped around the octave ([wrapTransposition]), so the stepper never runs into an end.
     */
    private fun changeTransposition(songFileName: String, setlistFileName: String?, change: (Int) -> Int) = messageSink.launchLibraryChange {
        if (setlistFileName == null) {
            updateUserPreferences { preferences ->
                val transposition = wrapTransposition(change(preferences.transpositions[songFileName] ?: 0))
                preferences.copy(
                    transpositions = if (transposition == 0) {
                        preferences.transpositions - songFileName
                    } else {
                        preferences.transpositions + (songFileName to transposition)
                    }
                )
            }
        } else {
            updateEditableSetlist(setlistFileName) { setlist ->
                setlist.withEntry(songFileName) { it.copy(transposition = wrapTransposition(change(it.transposition))) } ?: setlist
            } ?: messageSink.sendMessage(Message.OperationFailed)
        }
    }

    /** The tempo a song plays at where it is opened, the override waiting to be written included. */
    fun effectiveTempoOf(songFileName: String, setlistFileName: String?) =
        effectiveTempo(song = songsByFileName()[songFileName], setlistFileName = setlistFileName, tempos = tempos.value, songFileName = songFileName)

    /** One step of the tempo stepper, in the setlist the song was opened from or in the preferences, like a transposition. */
    fun stepTempo(songFileName: String, setlistFileName: String?, delta: Int) =
        changeTempo(songFileName = songFileName, setlistFileName = setlistFileName) { it + delta }

    /** A tempo tapped in. */
    fun setTempo(songFileName: String, setlistFileName: String?, bpm: Int) =
        changeTempo(songFileName = songFileName, setlistFileName = setlistFileName) { bpm }

    /**
     * The stepper's value tapped: the override is removed rather than set to the file's number, so that a `{tempo}`
     * edited later shows through. Written at once, and a step still waiting to be written is dropped, so that it cannot
     * land after the reset.
     */
    fun resetTempo(songFileName: String, setlistFileName: String?) {
        tempoOverrides.reset(SongPlace(songFileName = songFileName, setlistFileName = setlistFileName))
    }

    /**
     * Sets the override to [change] of the tempo on screen, and writes it once the stepper has held still: a setlist
     * write is a sync run, and a held stepper is a step every few frames. Unlike [changeTransposition] the value is
     * absolute, which is safe here because while a value is pending nothing reads the store's instead. A value equal to
     * the song's own removes the override.
     */
    private fun changeTempo(songFileName: String, setlistFileName: String?, change: (Int) -> Int) {
        val effective = effectiveTempoOf(songFileName, setlistFileName)
        val bpm = MetronomePattern.coerceBpm(change(effective.bpm))
        tempoOverrides.set(SongPlace(songFileName = songFileName, setlistFileName = setlistFileName), bpm.takeIf { it != effective.songBpm })
    }

    /**
     * Writes a tempo or capo override, null removing it: into the setlist the song was opened from, or into the
     * preferences for one opened from the library. Answers whether it was written and is expected to come back from the
     * store, see [PendingOverrides].
     */
    private suspend fun writeOverride(
        place: SongPlace,
        value: Int?,
        library: (UserPreferences) -> Map<String, Int>,
        withLibrary: (UserPreferences, Map<String, Int>) -> UserPreferences,
        withEntry: (Setlist.Entry, Int?) -> Setlist.Entry,
    ): Boolean {
        val setlistFileName = place.setlistFileName
        return if (setlistFileName == null) {
            updateUserPreferences { preferences ->
                val overrides = library(preferences)
                withLibrary(preferences, if (value == null) overrides - place.songFileName else overrides + (place.songFileName to value))
            }
            true
        } else {
            // A song a sync run took out of the setlist while its screen stayed open has no entry to hold the value,
            // and nothing would ever settle it. A reset has nothing left to clear there, so that one still counts.
            var hasEntry = false
            updateEditableSetlist(setlistFileName) { setlist ->
                setlist.withEntry(place.songFileName) { withEntry(it, value) }.also { hasEntry = it != null } ?: setlist
            }?.takeUnless { it.isArchived } != null && (hasEntry || value == null)
        }
    }

    /** The fret a song is capoed at where it is opened, the override waiting to be written included. */
    fun effectiveCapoOf(songFileName: String, setlistFileName: String?) =
        effectiveCapo(song = songsByFileName()[songFileName], setlistFileName = setlistFileName, capos = capos.value, songFileName = songFileName)

    /** One step of the capo stepper, in the setlist the song was opened from or in the preferences, like a tempo. */
    fun stepCapo(songFileName: String, setlistFileName: String?, delta: Int) =
        changeCapo(songFileName = songFileName, setlistFileName = setlistFileName) { it + delta }

    /**
     * The stepper's value tapped: the override is removed rather than set to the file's fret, so that a `{capo}` edited
     * later shows through, and a step still waiting to be written is dropped, exactly as [resetTempo] does it.
     */
    fun resetCapo(songFileName: String, setlistFileName: String?) {
        capoOverrides.reset(SongPlace(songFileName = songFileName, setlistFileName = setlistFileName))
    }

    /** [changeTempo] for a fret: absolute, debounced, and a value equal to the song's own removing the override. */
    private fun changeCapo(songFileName: String, setlistFileName: String?, change: (Int) -> Int) {
        val effective = effectiveCapoOf(songFileName, setlistFileName)
        val fret = change(effective.fret).coerceIn(Song.CAPO_RANGE)
        capoOverrides.set(SongPlace(songFileName = songFileName, setlistFileName = setlistFileName), fret.takeIf { it != effective.songFret })
    }

    /**
     * Takes the tempo and capo overrides still waiting for their debounce out of it and returns their writes, for a view
     * model or a process that is about to go, see [PendingOverrides.takeWaiting].
     */
    fun takeWaitingOverrideWrites(): suspend () -> Unit {
        val waitingTempos = tempoOverrides.takeWaiting()
        val waitingCapos = capoOverrides.takeWaiting()
        return {
            waitingTempos()
            waitingCapos()
        }
    }

    /** Lets the tempos and capos set here go once the store says the same, see [PendingOverrides.start]. */
    fun startSettling() {
        tempoOverrides.start()
        capoOverrides.start()
    }
}
