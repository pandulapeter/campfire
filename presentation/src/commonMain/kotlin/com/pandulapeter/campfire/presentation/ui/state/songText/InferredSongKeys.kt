/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.songText

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * The key each song read so far is played in where its file names none, as its chords tell it, for the song details
 * screen's app bar to name. Worked out by [SongTextStore] on [dispatcher] before it hands the text on, so that the key is
 * there in the frame the lyrics are, and kept by file name for as long as the view model lives rather than for as long as
 * the text is held: a song opened again names its key from the first frame, before its text is read again. A file
 * edited elsewhere meanwhile is named by its old key until that read. One short entry per song opened, never pruned.
 *
 * @param inferKey The key of a song's text, null where its file names one or none can be told; the scale walk, so never
 *   on the main thread.
 */
internal class InferredSongKeys(
    private val inferKey: (text: String) -> String?,
    private val dispatcher: CoroutineDispatcher,
) {

    /** The text each entry of [keys] was worked out from, so that a text read again unchanged is not walked again. */
    private val inferredTexts = mutableMapOf<String, String>()

    private val _keys = MutableStateFlow(emptyMap<String, String>())
    val keys: StateFlow<Map<String, String>> = _keys.asStateFlow()

    /** Works out the key of every text of [contents] (file name to text) not already worked out from that very text. */
    suspend fun inferFor(contents: List<Pair<String, String>>) {
        val changed = contents.filter { (fileName, text) -> inferredTexts[fileName] != text }
        if (changed.isEmpty()) return
        val inferred = withContext(dispatcher) { changed.map { (fileName, text) -> Triple(fileName, text, inferKey(text)) } }
        inferred.forEach { (fileName, text, _) -> inferredTexts[fileName] = text }
        _keys.update { keys -> inferred.fold(keys) { updated, (fileName, _, key) -> if (key == null) updated - fileName else updated + (fileName to key) } }
    }
}
