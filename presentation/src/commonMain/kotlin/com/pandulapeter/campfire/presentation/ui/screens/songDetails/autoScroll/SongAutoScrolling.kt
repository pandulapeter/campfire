/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails.autoScroll

import androidx.compose.runtime.Composable
import com.pandulapeter.campfire.data.model.preferences.SongLayout
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The song details screen's scrolling by itself, for the song on screen ([scroller], which its page hands up): whether it
 * is playing, and the speed it plays at, the one stored for the song (`UserPreferences.autoScrollSpeeds`) or the one the
 * slider is being dragged to. It plays only once asked, and stops only when asked, at the end of the song, on another
 * song, with the app out of sight, or with the song laid out in pages again: a drag or a pedal only moves the reader.
 */
internal class SongAutoScrolling(private val coroutineScope: CoroutineScope) {

    var isPlaying by mutableStateOf(false)
        private set

    /** The current page's scroller, null before it has handed one up. */
    var scroller by mutableStateOf<SongAutoScroller?>(null)

    /** The file name of the song on screen and its stored speed, written by [rememberSongAutoScrolling]. */
    internal var song by mutableStateOf<Pair<String, Int>?>(null)

    /** The speed the slider is being dragged to, by the file name of the song it was dragged on. */
    private var draggedSpeed by mutableStateOf<Pair<String, Int>?>(null)

    /** The speed the song on screen plays at, in percent of its own pace. */
    val speed get() = song?.let { (fileName, stored) -> draggedSpeed?.takeIf { it.first == fileName }?.second ?: stored } ?: SongLayout.DEFAULT_AUTO_SCROLL_SPEED

    /** Starts playing where the song is, where there is anything left to play, and stops it if it is playing. */
    fun togglePlaying() {
        isPlaying = !isPlaying && scroller?.canPlay == true
    }

    fun pause() {
        isPlaying = false
    }

    /** Moves the song a little in [direction], answering false where it is already at that end. */
    fun nudge(direction: Int): Boolean {
        val currentScroller = scroller ?: return false
        if (!currentScroller.canNudge(direction)) return false
        coroutineScope.launch { currentScroller.nudge(direction) }
        return true
    }

    fun dragSpeed(speed: Int) {
        val fileName = song?.first ?: return
        draggedSpeed = fileName to speed
    }

    /** The speed the slider was dragged to on the song on screen, by its file name, to be written once the drag ends. */
    fun speedToWrite() = draggedSpeed?.takeIf { it.first == song?.first }

    internal fun settleDraggedSpeed() {
        val (fileName, stored) = song ?: return
        if (draggedSpeed?.let { it.first != fileName || it.second == stored } == true) draggedSpeed = null
    }
}

/**
 * The [SongAutoScrolling] of a song details screen, playing [songFileName]'s page at its stored speed while the songs
 * are laid out in one column. The page being changed ([currentPage]), anything drawn over the screen ([isUncovered]
 * false: a sheet, a dialog, another screen), and the app leaving it (`ON_STOP`, which a window minimised or a tab hidden
 * also is) stop it, as the song being laid out in pages again does: nobody reads a song moving under something else.
 */
@Composable
internal fun rememberSongAutoScrolling(
    userPreferences: UserPreferences?,
    isUncovered: Boolean,
    currentPage: Int,
    songFileName: String?,
): SongAutoScrolling {
    val isContinuous = userPreferences?.songLayout == SongLayout.CONTINUOUS
    val storedSpeed = songFileName?.let { userPreferences?.autoScrollSpeeds?.get(it) } ?: SongLayout.DEFAULT_AUTO_SCROLL_SPEED
    val coroutineScope = rememberCoroutineScope()
    val autoScrolling = remember(coroutineScope) { SongAutoScrolling(coroutineScope) }
    autoScrolling.song = songFileName?.let { it to storedSpeed }
    LaunchedEffect(currentPage, isContinuous) { autoScrolling.pause() }
    LaunchedEffect(isUncovered) { if (!isUncovered) autoScrolling.pause() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { autoScrolling.pause() }
    LaunchedEffect(songFileName, storedSpeed) { autoScrolling.settleDraggedSpeed() }
    val scroller = autoScrolling.scroller
    LaunchedEffect(autoScrolling.isPlaying, scroller) {
        if (!autoScrolling.isPlaying || scroller == null) return@LaunchedEffect
        scroller.play { autoScrolling.speed }
        autoScrolling.pause()
    }
    return autoScrolling
}
