/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.chordpro.edit.ChordProCoverArt
import com.pandulapeter.campfire.data.model.domain.CoverArtQuery
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.cover_art_address_mode
import com.pandulapeter.campfire.presentation.resources.cover_art_search
import com.pandulapeter.campfire.presentation.resources.cover_art_search_remove
import com.pandulapeter.campfire.presentation.resources.done
import com.pandulapeter.campfire.presentation.resources.ic_delete
import com.pandulapeter.campfire.presentation.resources.save
import com.pandulapeter.campfire.presentation.resources.song_details_change_cover_art
import com.pandulapeter.campfire.presentation.resources.song_details_set_cover_art
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.SegmentedChoice
import org.jetbrains.compose.resources.painterResource

/**
 * The cover search: the song's artist, album and title as fields, and under them the records MusicBrainz and iTunes
 * know by those, each with its front cover, one of which is made the song's cover by Save. The other tab takes the
 * address of an image instead, for a cover neither catalogue has.
 *
 * The search is asked by the view model as the sheet is put up, with the fields as the file fills them, and after that
 * only when it is asked for — the keyboard's search key or the button — never per keystroke, since MusicBrainz allows
 * one request a second from the whole app. Its state is the view model's ([CampfireViewModel.coverArtSearch]), so that
 * it outlives the Android activity being recreated, and goes with the sheet.
 *
 * The sheet opens at its full height whatever it holds, since what it holds changes after it has opened: a sheet that
 * grew from a line saying the search is running to a grid of covers moved while it was still sliding up, and again
 * with every catalogue that answered.
 *
 * A record whose thumbnail does not load is taken off the grid rather than left as an empty square: the archive only
 * says that a record has no cover by answering its address with nothing, and a cover that cannot be seen cannot be
 * chosen either — so one that was selected while it was still loading is let go of as it leaves, or Save would write an
 * address that answers nothing. A new search gives every record back its place, since a thumbnail may have failed only
 * for the moment.
 */
@Composable
internal fun CoverArtSearchSheet(
    viewModel: CampfireViewModel,
    dialog: DialogType.CoverArtSearch,
) {
    val searchState by viewModel.coverArtSearch.collectAsStateWithLifecycle()
    val initialQuery = remember(dialog.song.fileName) { viewModel.coverArtQueryOf(song = dialog.song, isEditorDraft = dialog.isEditorDraft) }
    var mode by rememberSaveable { mutableStateOf(CoverArtSheetMode.SEARCH) }
    var artist by rememberSaveable { mutableStateOf(initialQuery.artist) }
    var album by rememberSaveable { mutableStateOf(initialQuery.album) }
    var title by rememberSaveable { mutableStateOf(initialQuery.title) }
    var address by rememberSaveable { mutableStateOf(dialog.song.coverArtUrl.orEmpty()) }
    var selectedUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var unavailableKeys by rememberSaveable { mutableStateOf(emptySet<String>()) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val query = CoverArtQuery(artist = artist, album = album, title = title)
    val search = {
        keyboardController?.hide()
        selectedUrl = null
        // A record that is really without a cover is answered at once from the repository's memory of the failure and
        // drops out again, while one that only failed for the moment — a timeout, a busy archive — gets another chance.
        unavailableKeys = emptySet()
        viewModel.searchCoverArt(query)
    }
    val usableAddress = ChordProCoverArt.usableUrl(address)
    CampfireBottomSheet(
        title = stringResource(if (dialog.song.coverArtUrl == null) Res.string.song_details_set_cover_art else Res.string.song_details_change_cover_art),
        subtitle = songLabel(dialog.song),
        sheetMaxWidth = SHEET_MAX_WIDTH,
        actions = { close ->
            CoverArtSearchActions(
                isEditorDraft = dialog.isEditorDraft,
                canRemove = dialog.song.coverArtUrl != null,
                canSave = when (mode) {
                    CoverArtSheetMode.SEARCH -> selectedUrl != null && selectedUrl != dialog.song.coverArtUrl
                    CoverArtSheetMode.ADDRESS -> usableAddress != null && usableAddress != dialog.song.coverArtUrl
                },
                onRemove = {
                    viewModel.showDialog(DialogType.RemoveSongCoverArt(song = dialog.song, isEditorDraft = dialog.isEditorDraft))
                },
                onSave = {
                    viewModel.setSongCoverArt(
                        fileName = dialog.song.fileName,
                        isEditorDraft = dialog.isEditorDraft,
                        url = when (mode) {
                            CoverArtSheetMode.SEARCH -> selectedUrl
                            CoverArtSheetMode.ADDRESS -> usableAddress
                        },
                    )
                    close()
                },
            )
        },
        onDismiss = { viewModel.dismissSheet(dialog) },
    ) { contentPadding ->
        // Choose the layout from the window, not the space above the keyboard. Moving a focused field between
        // the pinned area and a lazy grid item disposes that input as the IME opens and ends its editing session.
        // Small windows keep their fields in the scroll from the start; keyboard insets only change the padding.
        val windowSize = LocalWindowInfo.current.containerDpSize
        val pinFields = minOf(windowSize.width, windowSize.height) >= MIN_WINDOW_SIZE_FOR_PINNED_FIELDS
        val modeControls: @Composable () -> Unit = {
            SegmentedChoice(
                modifier = Modifier.padding(top = if (pinFields) 8.dp else 0.dp),
                shouldApplyPadding = pinFields,
                options = listOf(
                    CoverArtSheetMode.SEARCH to stringResource(Res.string.cover_art_search),
                    CoverArtSheetMode.ADDRESS to stringResource(Res.string.cover_art_address_mode),
                ),
                selected = mode,
                onSelected = {
                    keyboardController?.hide()
                    mode = it
                },
            )
        }
        if (pinFields) modeControls()
        AnimatedContent(
            modifier = Modifier.weight(1f),
            targetState = mode,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
        ) { currentMode ->
            when (currentMode) {
                CoverArtSheetMode.SEARCH -> CoverArtResults(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                    pinFields = pinFields,
                    scrollingControls = modeControls.takeUnless { pinFields },
                    state = searchState,
                    unavailableKeys = unavailableKeys,
                    selectedUrl = selectedUrl,
                    onSelected = { selectedUrl = it.coverArtUrl.takeUnless { url -> url == selectedUrl } },
                    onUnavailable = { candidate ->
                        unavailableKeys += candidate.key
                        if (candidate.coverArtUrl == selectedUrl) {
                            selectedUrl = null
                        }
                    },
                    onRetry = search,
                ) {
                    CoverArtQueryFields(
                        artist = artist,
                        album = album,
                        title = title,
                        onArtistChange = { artist = it },
                        onAlbumChange = { album = it },
                        onTitleChange = { title = it },
                        canSearch = query.isSearchable,
                        onSearch = search,
                    )
                }

                CoverArtSheetMode.ADDRESS -> CoverArtAddress(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                    pinFields = pinFields,
                    scrollingControls = modeControls.takeUnless { pinFields },
                    address = address,
                    usableAddress = usableAddress,
                    onAddressChange = { address = it },
                    onDone = { keyboardController?.hide() },
                )
            }
        }
    }
}

/** The two ways the sheet finds a cover: picking one of the records a search found, or typing the address of one. */
private enum class CoverArtSheetMode {
    SEARCH,
    ADDRESS,
}

/**
 * Save and, where the song has a cover to take off, Remove, at the end of the sheet's header rather than in a bar of
 * their own under the results: a bar held at the bottom of the sheet sat on the keyboard and took the room the covers
 * had left. Remove is the bin rather than a labelled button, since it is the rare one of the two and asks before it
 * does anything.
 */
@Composable
private fun CoverArtSearchActions(
    isEditorDraft: Boolean,
    canRemove: Boolean,
    canSave: Boolean,
    onRemove: () -> Unit,
    onSave: () -> Unit,
) = Row(
    verticalAlignment = Alignment.CenterVertically,
) {
    if (canRemove) {
        val isClosing = LocalIsSheetClosing.current
        IconButton(onClick = { if (!isClosing()) onRemove() }) {
            Icon(
                painter = painterResource(Res.drawable.ic_delete),
                contentDescription = stringResource(Res.string.cover_art_search_remove),
            )
        }
    }
    BottomSheetConfirmButton(
        enabled = canSave,
        onClick = onSave,
    ) {
        Text(stringResource(if (isEditorDraft) Res.string.done else Res.string.save))
    }
}

/** Wide enough for four or five covers side by side on a tablet or a desktop window, where a sheet is otherwise 640dp. */
private val SHEET_MAX_WIDTH = 840.dp

/** Small windows scroll the fields with the covers, even before the keyboard opens, so focusing never relocates them. */
private val MIN_WINDOW_SIZE_FOR_PINNED_FIELDS = 600.dp
