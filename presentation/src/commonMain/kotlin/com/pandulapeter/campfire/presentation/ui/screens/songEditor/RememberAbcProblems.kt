/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songEditor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.pandulapeter.campfire.abc.api.AbcMessage
import com.pandulapeter.campfire.abc.api.AbcRenderer
import com.pandulapeter.campfire.abc.api.AbcRendering
import com.pandulapeter.campfire.chordpro.edit.ChordProAbcTunes
import com.pandulapeter.campfire.presentation.ui.notation.LocalAbcRenderer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/**
 * What the notation engine reports about the tunes of the editor's [text], checked [CHECK_DELAY_MILLIS] after typing
 * stops, read as snapshot state by the field's output transformation ([AbcProblems]); null before the first check and
 * where nothing renders notation.
 *
 * Each tune is engraved for its messages alone, apart from the preview, whose tunes are the reader's (a song title left
 * out, one part of a score): the positions reported have to be of the text as typed. The messages of the last tunes are
 * kept by their text, so an edit outside a tune engraves nothing again. It is remembered where the editor is, not in a
 * pane, which is composed afresh whenever Edit, Preview, and Split change places.
 */
@Composable
internal fun rememberAbcProblems(text: State<String>): () -> AbcProblems? {
    val renderer = LocalAbcRenderer.current
    var problems by remember { mutableStateOf<AbcProblems?>(null) }
    LaunchedEffect(renderer, text) {
        if (renderer == null) return@LaunchedEffect
        val messagesByTune = LinkedHashMap<String, List<AbcMessage>>()
        snapshotFlow { text.value }.collectLatest { current ->
            delay(CHECK_DELAY_MILLIS)
            val tunes = ChordProAbcTunes.of(current)
            val messages = tunes.associate { tune ->
                tune.text to (
                    messagesByTune[tune.text] ?: when (val rendering = renderer.render(tune = tune.text, width = AbcRenderer.TEXT_LINE_WIDTH, transposition = 0)) {
                        is AbcRendering.Systems -> rendering.messages
                        is AbcRendering.Failed -> rendering.messages
                    }
                )
            }
            messagesByTune.clear()
            messagesByTune.putAll(messages)
            problems = AbcProblems.of(text = current, tunes = tunes, messagesOf = { messages[it.text].orEmpty() })
        }
    }
    return remember { { problems } }
}

private const val CHECK_DELAY_MILLIS = 500L
