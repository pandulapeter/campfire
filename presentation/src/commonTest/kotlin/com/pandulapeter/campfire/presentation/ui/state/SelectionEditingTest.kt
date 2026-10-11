/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state

import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.edit.ChordProLanguages
import com.pandulapeter.campfire.chordpro.edit.ChordProTags
import com.pandulapeter.campfire.data.model.domain.EditedSongs
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.data.model.domain.normalizedToNfc
import com.pandulapeter.campfire.domain.api.useCases.EditSongsUseCase
import com.pandulapeter.campfire.domain.api.useCases.ParseChordProUseCase
import com.pandulapeter.campfire.domain.api.useCases.SetChordProLanguagesUseCase
import com.pandulapeter.campfire.domain.api.useCases.SetChordProTagUseCase
import com.pandulapeter.campfire.presentation.ui.chords.toChordNotation
import com.pandulapeter.campfire.presentation.ui.messages.MessageSink
import kotlinx.coroutines.test.TestScope
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

        editing().setSelectionTags(listOf("a.cho"), added = emptyList(), removed = setOf("ünnep"))
        runCurrent()

        assertEquals(listOf("Rock"), ChordProParser.parse(files.getValue("a.cho")).metadata.tags)
    }

    private fun TestScope.editing() = SelectionEditing(
        scope = backgroundScope,
        messageSink = MessageSink(backgroundScope),
        editSongs = object : EditSongsUseCase {
            override suspend fun invoke(fileNames: List<String>, edit: (text: String) -> String): EditedSongs {
                val changed = fileNames.filterTo(mutableSetOf()) { fileName ->
                    val text = files.getValue(fileName)
                    edit(text).also { files[fileName] = it } != text
                }
                return EditedSongs(changedFileNames = changed, failedFileNames = emptyList())
            }
        },
        parseChordPro = object : ParseChordProUseCase {
            override fun invoke(text: String, notation: UserPreferences.Notation) = ChordProParser.parse(text, notation.toChordNotation())
        },
        setChordProTag = object : SetChordProTagUseCase {
            override fun invoke(text: String, tag: String, isSelected: Boolean) = if (isSelected) {
                ChordProTags.addTag(text, tag, fold = String::normalizedToNfc)
            } else {
                ChordProTags.removeTag(text, tag, fold = String::normalizedToNfc)
            }
        },
        setChordProLanguages = object : SetChordProLanguagesUseCase {
            override fun invoke(text: String, codes: List<String>) = ChordProLanguages.setLanguages(text, codes)
        },
    )
}
