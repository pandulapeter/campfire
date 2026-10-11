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

import com.pandulapeter.campfire.domain.api.useCases.EditSongsUseCase
import com.pandulapeter.campfire.domain.api.useCases.ParseChordProUseCase
import com.pandulapeter.campfire.domain.api.useCases.SetChordProLanguagesUseCase
import com.pandulapeter.campfire.domain.api.useCases.SetChordProTagUseCase
import com.pandulapeter.campfire.presentation.ui.messages.Message
import com.pandulapeter.campfire.presentation.ui.messages.MessageSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * The tags and the languages of the songs ticked in the selection mode, written into all of them at once through
 * [EditSongsUseCase], which announces the change to the library once rather than once per file. Each file is edited as
 * it is on disk, by the rules the one-song sheets follow: a tag is compared without regard to case, a row the sheet
 * left half ticked is left alone in every file, and a label nobody offered - one another device synced in meanwhile -
 * stays where it is.
 */
internal class SelectionEditing(
    scope: CoroutineScope,
    private val messageSink: MessageSink,
    private val editSongs: EditSongsUseCase,
    private val parseChordPro: ParseChordProUseCase,
    private val setChordProTag: SetChordProTagUseCase,
    private val setChordProLanguages: SetChordProLanguagesUseCase,
) {

    private val runningEdits = MutableStateFlow(0)

    /** Whether a write to the ticked songs is still running, which disables the selection's actions meanwhile. */
    val isEditingSongs: StateFlow<Boolean> = runningEdits.map { it > 0 }.asState(scope, false)

    /**
     * Puts [added] on every song of [fileNames] and takes the tags of [removed] (folded by case) off every one of them.
     */
    fun setSelectionTags(fileNames: List<String>, added: List<String>, removed: Set<String>) = edit(fileNames) { text ->
        val removedTags = parseChordPro(text).metadata.tags.filter { it.lowercase() in removed }
        val withoutRemoved = removedTags.fold(text) { current, tag -> setChordProTag(text = current, tag = tag, isSelected = false) }
        added.fold(withoutRemoved) { current, tag -> setChordProTag(text = current, tag = tag, isSelected = true) }
    }

    /** Each song's own languages, less [removed] and with [added] after them. */
    fun setSelectionLanguages(fileNames: List<String>, added: Set<String>, removed: Set<String>) = edit(fileNames) { text ->
        val codes = parseChordPro(text).metadata.languages
        val edited = codes.filterNot { it in removed } + added.filterNot { it in codes }
        if (edited == codes) text else setChordProLanguages(text = text, codes = edited)
    }

    private fun edit(fileNames: List<String>, edit: (String) -> String) = messageSink.launchLibraryChange {
        runningEdits.update { it + 1 }
        try {
            val result = editSongs(fileNames = fileNames, edit = edit)
            messageSink.sendMessage(
                if (result.failedFileNames.isEmpty()) {
                    Message.SongsUpdated(fileNames.size)
                } else {
                    Message.SongsNotUpdated(result.failedFileNames.size)
                },
            )
        } finally {
            runningEdits.update { it - 1 }
        }
    }
}
