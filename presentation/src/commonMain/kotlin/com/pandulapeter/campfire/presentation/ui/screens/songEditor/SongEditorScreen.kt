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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.close
import com.pandulapeter.campfire.presentation.resources.ic_error
import com.pandulapeter.campfire.presentation.resources.retry
import com.pandulapeter.campfire.presentation.resources.song_details_no_data
import com.pandulapeter.campfire.presentation.resources.song_details_no_data_hint
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.DelayedLoadingIndicator
import com.pandulapeter.campfire.presentation.ui.components.EmptyState
import com.pandulapeter.campfire.presentation.ui.components.EmptyStateAction
import com.pandulapeter.campfire.presentation.ui.components.WindowSize
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import org.jetbrains.compose.resources.painterResource

/**
 * The raw ChordPro text of one song, with what it will look like next to it. Wide windows show both at once, narrow
 * ones one at a time.
 *
 * The text lives in a [TextFieldState] here rather than in the view model: it is the field's own state, undo history
 * included, and it lives through a configuration change whole, in the view model's keeping, and through process death
 * as its text and caret, see [EditorFieldSaver]. Nothing is ever written on its own: the file changes when the user
 * saves it, and leaving with something unsaved asks first (see [CampfireViewModel.navigateBack]). What the screen does report as it is typed is the text itself, which is
 * what lets that question be answered - and the save be finished - after the screen is gone.
 */
@Composable
internal fun SongEditorScreen(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    destination: CampfireDestination.SongEditor,
    windowSize: WindowSize,
    contentPadding: PaddingValues,
    urlOpener: (String) -> Unit,
    onBack: () -> Unit,
) {
    val songTexts by viewModel.songTexts.collectAsStateWithLifecycle()
    val failedSongFileNames by viewModel.failedSongFileNames.collectAsStateWithLifecycle()
    // The text the field starts from, taken once. What the file holds afterwards is only ever compared with what has
    // been typed: an editor that followed songTexts would be taken apart, the field and the draft with it, by a
    // sync run deleting the file or a rescan failing to read it - the two moments the draft is the only copy left.
    //
    // An editor that is composed again over a file that has gone - after a rotation, or in a process the system
    // restored - still has its field to come back to, retained by the view model or saved with the screen, so it
    // opens on that rather than waiting for a file that is not coming back. Whether it had opened is saved for the
    // same reason: in a new process that flag is all there is to tell a draft worth restoring from a song that was
    // never loaded. The empty text it opens with is only what the field is compared with, which is what the file
    // holds now.
    //
    // The field is in the editor's notation, so every text of the file it is compared with or replaced by is too.
    var hasOpened by rememberSaveable(destination.fileName) { mutableStateOf(false) }
    val notation = viewModel.editorNotation
    val editorTextOf = remember(notation) { { text: String -> viewModel.songRenderer.editorTextOf(text, notation) } }
    var initialText by remember(destination.fileName) {
        mutableStateOf(
            viewModel.songTexts.value[destination.fileName]?.let(editorTextOf)
                ?: viewModel.retainedEditorField(destination.fileName)?.let { "" }
        )
    }
    LaunchedEffect(destination.fileName) {
        viewModel.loadSongContent(destination.fileName).join()
        if (initialText == null && hasOpened) initialText = viewModel.songTexts.value[destination.fileName]?.let(editorTextOf) ?: ""
        initialText = initialText ?: viewModel.songTexts.mapNotNull { it[destination.fileName] }.first().let(editorTextOf)
        hasOpened = true
    }
    AnimatedContent(
        modifier = modifier.fillMaxSize(),
        targetState = initialText,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        contentKey = { it != null },
    ) { text ->
        if (text == null) {
            SongNotLoadedPane(
                contentPadding = contentPadding,
                hasFailed = destination.fileName in failedSongFileNames,
                onRetry = { viewModel.loadSongContent(destination.fileName) },
                onClose = onBack,
            )
        } else {
            LoadedSongEditor(
                viewModel = viewModel,
                destination = destination,
                initialText = text,
                hasSavedText = songTexts[destination.fileName] != null,
                windowSize = windowSize,
                contentPadding = contentPadding,
                urlOpener = urlOpener,
                onBack = onBack,
            )
        }
    }
}

/**
 * What the editor shows until it has a text to open with. A read that failed says so and offers the way out as
 * well as the retry, because this state has no app bar to leave by.
 */
@Composable
private fun SongNotLoadedPane(
    contentPadding: PaddingValues,
    hasFailed: Boolean,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) = Box(
    modifier = Modifier.fillMaxSize().padding(contentPadding),
    contentAlignment = Alignment.Center,
) {
    if (hasFailed) {
        EmptyState(
            icon = painterResource(Res.drawable.ic_error),
            title = stringResource(Res.string.song_details_no_data),
            hint = stringResource(Res.string.song_details_no_data_hint),
            actions = listOf(
                EmptyStateAction(text = stringResource(Res.string.retry), onClick = onRetry),
                EmptyStateAction(text = stringResource(Res.string.close), onClick = onClose),
            ),
        )
    } else {
        DelayedLoadingIndicator()
    }
}
