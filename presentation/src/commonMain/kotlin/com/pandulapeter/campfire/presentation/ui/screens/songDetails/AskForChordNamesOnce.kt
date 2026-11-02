/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.playing.leadingChordNames
import com.pandulapeter.campfire.presentation.ui.playing.playsWithoutCapo
import com.pandulapeter.campfire.presentation.ui.playing.songPlaybackOf
import com.pandulapeter.campfire.presentation.ui.playing.soundingTransposition
import com.pandulapeter.campfire.presentation.ui.rendering.SongRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Asks how a capoed song's chords are to be named ([DialogType.ChordNames]) the first time the answer changes what the
 * reader sees: a song opened (or paged to) with a capo on, or its capo stepped while it is read, to none included. Only
 * once the layout question is behind ([isChordNamesQuestionOwed]), only while this screen is on top with nothing over
 * it, so that it follows that question, What's new, or an import's questions rather than stacking on them. Asked once
 * per song until its Confirm answers it: a sheet put away without it asks again over the next song it matters on.
 */
@Composable
internal fun AskForChordNamesOnce(
    viewModel: CampfireViewModel,
    song: Song?,
    destination: CampfireDestination.SongDetails,
) {
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val overrides by viewModel.overrides.playingOverrides.collectAsStateWithLifecycle()
    val inferredKeys by viewModel.songTextStore.inferredKeys.keys.collectAsStateWithLifecycle()
    val songTexts by viewModel.songTextStore.songTexts.collectAsStateWithLifecycle()
    val isPerformanceModeEnabled by viewModel.preferencesController.isPerformanceModeEnabled.collectAsStateWithLifecycle()
    val visibleDialog by viewModel.dialogHost.visibleDialog.collectAsStateWithLifecycle()
    val isUncovered = visibleDialog == null && viewModel.navigator.backStack.lastOrNull() == destination
    val playback = song?.let { songPlaybackOf(song = it, setlistFileName = destination.setlistFileName, overrides = overrides) }
    val capo = playback?.capo?.fret ?: 0
    // The capo the song had when this screen first showed it, so that stepping it back to none still counts as a change.
    val openedCapo = remember(song?.fileName) { capo }
    // Put up over this song already: a sheet put away unanswered uncovers the screen, which would otherwise ask again at
    // once, rather than at the next song it matters on.
    var isAskedOverSong by remember(song?.fileName) { mutableStateOf(false) }
    val isOwed = isChordNamesQuestionOwed(
        preferences = userPreferences,
        hasChords = song?.hasChords == true,
        capo = capo,
        isCapoChanged = capo != openedCapo,
        isPerformanceModeEnabled = isPerformanceModeEnabled,
    )
    // The example needs the song's text, which is read as the screen opens: the question waits the few frames that takes.
    val text = song?.let { songTexts[it.fileName] }
    LaunchedEffect(isOwed, isUncovered, text != null, capo) {
        if (!isOwed || !isUncovered || isAskedOverSong || song == null || playback == null || text == null) return@LaunchedEffect
        // A capo stepped from none to the third fret passes the first two: asked at once, the example would be a capo
        // the reader never meant. Each step restarts this effect, so the question waits for the stepper to rest.
        if (capo != openedCapo) delay(CAPO_SETTLE_MILLIS)
        // A capo just stepped to none names nothing differently, so the example is the capo it was stepped from.
        val exampleCapo = if (capo != 0) capo else openedCapo
        val dialog = withContext(Dispatchers.Default) {
            chordNamesQuestionOf(
                renderer = viewModel.songRenderer,
                song = song,
                text = text,
                transposition = playback.transposition,
                capo = exampleCapo,
                spelling = userPreferences?.chordSpelling ?: UserPreferences.ChordSpelling.Default,
                inferredKey = inferredKeys[song.fileName],
            )
        }
        if (viewModel.dialogHost.showIfNoneIsShown(dialog)) isAskedOverSong = true
    }
}

/**
 * The question for [song] played at [transposition] under [capo]: its first chords named both ways, by the shapes
 * ([capo] below what sounds, as `SongPlayback.namingTransposition` names them) and as they sound.
 */
internal fun chordNamesQuestionOf(
    renderer: SongRenderer,
    song: Song,
    text: String,
    transposition: Int,
    capo: Int,
    spelling: UserPreferences.ChordSpelling,
    inferredKey: String?,
): DialogType.ChordNames {
    val sounding = soundingTransposition(transposition, capo)
    val shapes = sounding - capo
    fun chords(transposition: Int) = renderer.leadingChordNames(
        song = song,
        text = text,
        transposition = transposition,
        spelling = spelling,
        inferredKey = inferredKey,
        count = EXAMPLE_CHORD_COUNT,
    )
    return DialogType.ChordNames(
        capo = capo,
        shapesChords = chords(shapes),
        soundingChords = chords(sounding),
    )
}

/** How long the capo stepper rests before a change of capo is taken as the one meant, held repeats included. */
private const val CAPO_SETTLE_MILLIS = 1_500L

/** Four chords say a song by both names without the cards outgrowing a line on a phone. */
private const val EXAMPLE_CHORD_COUNT = 4

/**
 * Whether the question about chord names is to be asked over a song with [capo] on, [isCapoChanged] while it is read: not
 * before the preferences are read, nor once it was answered (or a name picked in Preferences), nor where the answer
 * would change nothing on screen (chords off, the keyboard, which always names what sounds, a song without chords, a
 * capo that is none and was not moved), nor in performance mode, which is for playing rather than for questions. Never
 * while the layout question is still owed, which the first song opened asks: the two are never put up together, and
 * this one comes at the next moment it fits.
 */
internal fun isChordNamesQuestionOwed(
    preferences: UserPreferences?,
    hasChords: Boolean,
    capo: Int,
    isCapoChanged: Boolean,
    isPerformanceModeEnabled: Boolean,
) = preferences != null &&
    !preferences.isChordNamesChosen &&
    preferences.isSongLayoutChosen &&
    preferences.areChordsEnabled &&
    !preferences.playsWithoutCapo &&
    hasChords &&
    !isPerformanceModeEnabled &&
    (capo != 0 || isCapoChanged)
