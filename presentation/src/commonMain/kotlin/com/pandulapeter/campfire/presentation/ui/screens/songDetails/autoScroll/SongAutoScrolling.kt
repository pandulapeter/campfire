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
import com.pandulapeter.campfire.presentation.ui.state.importing.isImportPreviewFileName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The song details screen's scrolling by itself, for the song on screen ([scroller], which its page hands up): whether it
 * is playing, and the speed it plays at, the one stored for the song (`UserPreferences.autoScrollSpeeds`) or the one the
 * slider is being dragged to. It plays only once asked, and stops only when asked, at the end of the song, on another
 * song, with the app out of sight, or with the song laid out in pages again: a drag or a pedal only moves the reader.
 * A speed dragged to is written once the drag ends, except on an import preview's page, which is no song of the library
 * yet: there it holds for as long as the page is read, and becomes the song's once the preview is imported as it.
 */
internal class SongAutoScrolling(private val coroutineScope: CoroutineScope) {

    var isPlaying by mutableStateOf(false)
        private set

    /** The current page's scroller, null before it has handed one up. */
    var scroller by mutableStateOf<SongAutoScroller?>(null)

    /** The file name of the song on screen and its stored speed, written by [rememberSongAutoScrolling]. */
    internal var song by mutableStateOf<Pair<String, Int>?>(null)

    /** Stores a song's speed, by its file name, written by [rememberSongAutoScrolling]. */
    internal var writeSpeed: (songFileName: String, speed: Int) -> Unit = { _, _ -> }

    /** The speed the slider is being dragged to, by the file name of the song it was dragged on. */
    private var draggedSpeed by mutableStateOf<Pair<String, Int>?>(null)

    /** The speed the song on screen plays at, in percent of its own pace. */
    val speed get() = song?.let { (fileName, stored) -> draggedSpeed?.takeIf { it.first == fileName }?.second ?: stored } ?: SongLayout.DEFAULT_AUTO_SCROLL_SPEED

    /** Starts playing where the song is, where there is anything left to play, and stops it if it is playing. */
    fun togglePlaying() {
        isPlaying = !isPlaying && scroller?.canPlay == true
    }

    /**
     * Stops playing, writing a speed still being dragged to: the slider leaves with playing, and a drag cut short that
     * way never ends on its own.
     */
    fun pause() {
        isPlaying = false
        writeDraggedSpeed()
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

    /**
     * Writes the speed the slider was dragged to on the song on screen, unless that is a preview's page. A drag that ends
     * on the stored speed writes nothing, so nothing would settle it either: it is let go of at once, and a speed synced
     * from another device later is the one played.
     */
    fun writeDraggedSpeed() {
        val (fileName, speed) = draggedSpeed?.takeIf { it.first == song?.first } ?: return
        if (isImportPreviewFileName(fileName)) return
        if (speed == song?.second) draggedSpeed = null else writeSpeed(fileName, speed)
    }

    /**
     * Hands the speed dragged on the preview's page on screen over to [fileName], the song that page is now the screen of:
     * a preview of one song is rewritten in place into the screen of the song it was imported as
     * (`ImportReportState.followImportedSong`).
     */
    internal fun followImportedPreview(fileName: String?) {
        val (draggedOn, speed) = draggedSpeed ?: return
        if (fileName == null || song?.first != draggedOn || !isImportPreviewFileName(draggedOn) || isImportPreviewFileName(fileName)) return
        draggedSpeed = fileName to speed
    }

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
 * [writeSpeed] stores a song's speed once a drag of the slider ends.
 */
@Composable
internal fun rememberSongAutoScrolling(
    userPreferences: UserPreferences?,
    isUncovered: Boolean,
    currentPage: Int,
    songFileName: String?,
    writeSpeed: (songFileName: String, speed: Int) -> Unit,
): SongAutoScrolling {
    val isContinuous = userPreferences?.songLayout == SongLayout.CONTINUOUS
    val storedSpeed = songFileName?.let { userPreferences?.autoScrollSpeeds?.get(it) } ?: SongLayout.DEFAULT_AUTO_SCROLL_SPEED
    val coroutineScope = rememberCoroutineScope()
    val autoScrolling = remember(coroutineScope) { SongAutoScrolling(coroutineScope) }
    autoScrolling.writeSpeed = writeSpeed
    // Handed over while composing, so that the frame the preview becomes its song in already plays, and shows, that speed.
    autoScrolling.followImportedPreview(songFileName)
    autoScrolling.song = songFileName?.let { it to storedSpeed }
    // Only a speed handed over from a preview is still unwritten as another song comes on screen.
    LaunchedEffect(songFileName) { autoScrolling.writeDraggedSpeed() }
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
