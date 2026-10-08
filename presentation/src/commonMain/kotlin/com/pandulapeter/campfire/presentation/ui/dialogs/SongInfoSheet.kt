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

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.chordpro.ChordProTempo
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_album
import com.pandulapeter.campfire.presentation.resources.song_details_set_cover_art
import com.pandulapeter.campfire.presentation.resources.ic_edit
import com.pandulapeter.campfire.presentation.resources.song_details_song_info
import com.pandulapeter.campfire.presentation.resources.song_details_metadata_edit
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.platform.bounceVerticalScroll
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.SongDefaults
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.SongInfoBody
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.rememberSongInfoEditing
import com.pandulapeter.campfire.presentation.ui.components.ACTION_BUTTON_OVERLAP
import com.pandulapeter.campfire.presentation.ui.components.overlappingAction
import org.jetbrains.compose.resources.painterResource

/**
 * What a song says about itself, read from its text as it is now: its details, the defaults its file declares for how
 * it is played — with a line saying where the song is being played differently from them, which is what the steppers on
 * the page show — and its tags, languages and links. Outside read only mode the header edits details and cover art and
 * the body edits the rest, opening their dialogs on top of the sheet — the cover art button there only while the song has
 * no cover, which is changed from the cover itself once it has one; read only mode shows only what the song has,
 * without editing controls.
 */
@Composable
internal fun SongInfoSheet(
    viewModel: CampfireViewModel,
    dialog: DialogType.SongInfo,
    urlOpener: (String) -> Unit,
) {
    val songs by viewModel.allSongs.collectAsStateWithLifecycle()
    val song = songs.firstOrNull { it.fileName == dialog.song.fileName } ?: dialog.song
    val songTexts by viewModel.songTexts.collectAsStateWithLifecycle()
    val isPerformanceModeEnabled by viewModel.isPerformanceModeEnabled.collectAsStateWithLifecycle()
    val setlists by viewModel.setlists.collectAsStateWithLifecycle()
    val setlistFileName = (viewModel.backStack.lastOrNull() as? CampfireDestination.SongDetails)?.setlistFileName
    val isReadOnly = isPerformanceModeEnabled || setlists.any { it.fileName == setlistFileName && it.isArchived }
    val text = songTexts[dialog.song.fileName]
    val metadata = remember(text) { text?.let(viewModel::songMetadataOf) }
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val shouldShowChords = userPreferences?.areChordsEnabled != false
    val shouldShowTempo = userPreferences?.isMetronomeEnabled != false
    val editing = rememberSongInfoEditing(viewModel = viewModel, song = song, isEditorDraft = false)
    val overrides = songPlayingOverrides(viewModel = viewModel, song = song, setlistFileName = setlistFileName)
    CampfireBottomSheet(
        title = stringResource(Res.string.song_details_song_info),
        subtitle = songLabel(song),
        actions = if (isReadOnly) null else {
            {
                // A song with a cover changes it from the cover itself, which the body draws with a pencil on it.
                val onSetCoverArt = editing.onEditCoverArt?.takeIf { metadata?.coverArt.isNullOrBlank() }
                onSetCoverArt?.let { onEditCoverArt ->
                    IconButton(onClick = onEditCoverArt, enabled = metadata != null) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_album),
                            contentDescription = stringResource(Res.string.song_details_set_cover_art),
                        )
                    }
                }
                IconButton(
                    modifier = if (onSetCoverArt != null) Modifier.overlappingAction(start = ACTION_BUTTON_OVERLAP, end = 0.dp) else Modifier,
                    onClick = editing.onEditMetadata,
                    enabled = metadata != null,
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_edit),
                        contentDescription = stringResource(Res.string.song_details_metadata_edit),
                    )
                }
            }
        },
        onDismiss = { viewModel.dismissSheet(dialog) },
    ) { contentPadding ->
        if (metadata != null) {
            val scrollState = rememberScrollState()
            SongInfoBody(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fadingTopEdge(scrollState, sheetContainerColor())
                    .bounceVerticalScroll(scrollState)
                    .padding(contentPadding)
                    .padding(vertical = 8.dp),
                metadata = metadata,
                coverArtUrl = metadata.coverArt.takeIf { userPreferences?.isCoverArtEnabled == true },
                horizontalPadding = 16.dp,
                editing = editing.takeUnless { isReadOnly },
                // Each value goes with its feature, and the group with both of them, see SongPlayingDialog.
                defaults = if (shouldShowChords || shouldShowTempo) {
                    SongDefaults(
                        key = metadata.key?.takeIf { shouldShowChords && it.isNotBlank() }?.let(viewModel::editorKeyOf),
                        capo = metadata.capo?.takeIf { shouldShowChords },
                        tempo = ChordProTempo.parse(metadata.tempo)?.takeIf { shouldShowTempo },
                        time = metadata.time?.takeIf { shouldShowTempo },
                        overrides = overrides.labels,
                        isReadFromSetlist = setlistFileName != null,
                        onEdit = if (isReadOnly) null else ({ viewModel.showSongPlayingDialog(song = song, setlistFileName = setlistFileName) }),
                    )
                } else {
                    null
                },
                onOpenLink = urlOpener,
            )
        }
    }
}
