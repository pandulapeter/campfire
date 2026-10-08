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

import com.pandulapeter.campfire.data.model.domain.SongContent
import com.pandulapeter.campfire.domain.api.useCases.GetSongContentInvalidationsUseCase
import com.pandulapeter.campfire.domain.api.useCases.GetSongContentUseCase
import com.pandulapeter.campfire.domain.api.useCases.SaveSongContentUseCase
import com.pandulapeter.campfire.presentation.ui.messages.MessageSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The song files a [SongTextStore] reads and writes, by name, refusing a write whose expected text the file no longer
 * holds as the repository does.
 *
 * @param changeBeforeEveryWrite Stands in for another writer that gets to the file between every read and write.
 */
internal class FakeSongFiles(
    initial: Map<String, String> = emptyMap(),
    private val changeBeforeEveryWrite: ((String) -> String)? = null,
) : GetSongContentUseCase,
    SaveSongContentUseCase,
    GetSongContentInvalidationsUseCase {

    val files = initial.toMutableMap()

    /** Every text that reached a file. */
    val writes = mutableListOf<String>()

    override suspend fun invoke(fileName: String) = files[fileName]?.let { SongContent(fileName = fileName, text = it) }

    override suspend fun invoke(content: SongContent, expectedText: String?): Boolean {
        changeBeforeEveryWrite?.let { change -> files[content.fileName]?.let { files[content.fileName] = change(it) } }
        if (expectedText != null && files[content.fileName] != expectedText) return false
        files[content.fileName] = content.text
        writes += content.text
        return true
    }

    override fun invoke() = MutableStateFlow(0L)

    fun songTextStore(scope: CoroutineScope, messageSink: MessageSink) = SongTextStore(
        scope = scope,
        backStack = emptyList(),
        editorDraftFileName = { null },
        messageSink = messageSink,
        getSongContent = this,
        saveSongContent = this,
        getSongContentInvalidations = this,
    )
}
