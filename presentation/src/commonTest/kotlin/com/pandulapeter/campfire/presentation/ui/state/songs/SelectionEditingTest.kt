/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.songs

import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.data.model.song.EditedSongs
import com.pandulapeter.campfire.domain.api.songs.EditSongsUseCase
import com.pandulapeter.campfire.presentation.ui.ChordProTagWriter
import com.pandulapeter.campfire.presentation.ui.messages.MessageSink
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The files are a map of texts that the edit is applied to as `SongRepository.editSongs` applies it, and the use cases
 * are stood in for by the `:chordpro` objects their implementations delegate to, folding tags as they fold them.
 */
class SelectionEditingTest {

    private val files = mutableMapOf<String, String>()

    @Test
    fun `a decomposed tag in a file is taken off by its composed key`() = runTest {
        // The decomposed letter is spelled as an escape, since a literal one would not survive a tool that normalizes text.
        files["a.cho"] = "{title: A}\n{tag: U\u0308nnep}\n{tag: Rock}"

        editing(backgroundScope).setSelectionTags(listOf("a.cho"), added = emptyList(), removed = setOf("ünnep"))
        runCurrent()

        assertEquals(listOf("Rock"), ChordProParser.parse(files.getValue("a.cho")).metadata.tags)
    }

    @Test
    fun `a bulk tag write carries on when the view model's scope is cancelled`() = runTest {
        files += listOf("a.cho", "b.cho", "c.cho").associateWith { "{title: ${it.first()}}" }
        val viewModelScope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val gate = CompletableDeferred<Unit>()

        editing(viewModelScope, gate).setSelectionTags(files.keys.toList(), added = listOf("Rock"), removed = emptySet())
        runCurrent()
        viewModelScope.cancel()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(List(3) { listOf("Rock") }, files.values.map { ChordProParser.parse(it).metadata.tags })
    }

    /** Edits [files], each file once [gate] lets it through, as the repository's lock would keep it waiting. */
    private fun editing(scope: CoroutineScope, gate: CompletableDeferred<Unit>? = null) = SelectionEditing(
        scope = scope,
        messageSink = MessageSink(scope),
        editSongs = object : EditSongsUseCase {
            override suspend fun invoke(fileNames: List<String>, edit: (text: String) -> String): EditedSongs {
                val changed = fileNames.filterTo(mutableSetOf()) { fileName ->
                    gate?.await()
                    val text = files.getValue(fileName)
                    edit(text).also { files[fileName] = it } != text
                }
                return EditedSongs(changedFileNames = changed, failedFileNames = emptyList())
            }
        },
        setChordProTag = ChordProTagWriter,
    )
}
