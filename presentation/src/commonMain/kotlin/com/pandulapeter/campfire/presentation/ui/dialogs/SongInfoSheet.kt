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

import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.CampfireBottomSheet
import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.sheetContainerColor
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.chordpro.ChordProTempo
import com.pandulapeter.campfire.chordpro.model.displayTitle
import com.pandulapeter.campfire.metronome.api.model.MetronomePattern
import com.pandulapeter.campfire.metronome.api.model.TimeSignature
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_song_info
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.platform.bounceVerticalScroll
import com.pandulapeter.campfire.presentation.ui.songInfo.SongDefaults
import com.pandulapeter.campfire.presentation.ui.songInfo.SongInfoBody
import com.pandulapeter.campfire.presentation.ui.songInfo.rememberSongInfoEditing

/**
 * What a song says about itself, read from its text as it is now — the file's, or over the editor ([DialogType.SongInfo]'s
 * target) the text being typed: its details, the defaults its file declares for how it is played — with a line saying
 * where the song is being played differently from them, which is what the steppers on the page show — and its tags,
 * languages and links. It is the one place all of that is edited: outside read only mode every group is a tile that
 * opens its own sheet on top of this one, and the cover is what changes it; read only mode shows only what the song has.
 */
@Composable
internal fun SongInfoSheet(
    viewModel: CampfireViewModel,
    dialog: DialogType.SongInfo,
    urlOpener: (String) -> Unit,
) {
    val isDraft = dialog.target is SongEditTarget.EditorDraft
    val songs by viewModel.allSongs.collectAsStateWithLifecycle()
    val songTexts by viewModel.songTexts.collectAsStateWithLifecycle()
    val editorDraft by viewModel.editorDraft.collectAsStateWithLifecycle()
    val isPerformanceModeEnabled by viewModel.isPerformanceModeEnabled.collectAsStateWithLifecycle()
    val setlists by viewModel.setlists.collectAsStateWithLifecycle()
    // The editor's own song is the text being typed, which neither has the overrides of a setlist nor is read only.
    val setlistFileName = if (isDraft) null else (viewModel.backStack.lastOrNull() as? CampfireDestination.SongDetails)?.setlistFileName
    val isReadOnly = !isDraft && (isPerformanceModeEnabled || setlists.any { it.fileName == setlistFileName && it.isArchived })
    val text = if (isDraft) editorDraft?.takeIf { it.fileName == dialog.song.fileName }?.text else songTexts[dialog.song.fileName]
    val metadata = remember(text) { text?.let(viewModel::songMetadataOf) }
    // Over the editor the header names the song the way the editor's title does, from the text being typed.
    val song = if (isDraft) {
        metadata?.let { dialog.song.copy(title = it.displayTitle(fallback = dialog.song.title), artist = it.artist.orEmpty()) } ?: dialog.song
    } else {
        songs.firstOrNull { it.fileName == dialog.song.fileName } ?: dialog.song
    }
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val shouldShowChords = userPreferences?.areChordsEnabled != false
    val shouldShowTempo = userPreferences?.isMetronomeEnabled != false
    val editing = rememberSongInfoEditing(viewModel = viewModel, song = song, setlistFileName = setlistFileName, target = dialog.target)
    val overrides = songPlayingOverrides(viewModel = viewModel, song = song, setlistFileName = setlistFileName)
    CampfireBottomSheet(
        title = stringResource(Res.string.song_details_song_info),
        subtitle = songLabel(song),
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
                        // The editor's text is in the reader's notation already, and the file's in the standard one.
                        key = metadata.key?.takeIf { shouldShowChords && it.isNotBlank() }?.let { if (isDraft) it else viewModel.songRenderer.editorKeyOf(it, viewModel.editorNotation) },
                        capo = metadata.capo?.takeIf { shouldShowChords },
                        // The click's own tempo and time where the file names none, as if they were written there.
                        tempo = (ChordProTempo.parse(metadata.tempo) ?: MetronomePattern.DEFAULT_BPM).takeIf { shouldShowTempo },
                        time = (metadata.time?.takeIf { it.isNotBlank() } ?: TimeSignature.COMMON_TIME.toString()).takeIf { shouldShowTempo },
                        overrides = if (isDraft) emptyList() else overrides.labels,
                        isReadFromSetlist = setlistFileName != null,
                        onEdit = editing.onEditDefaults.takeUnless { isReadOnly },
                    )
                } else {
                    null
                },
                onOpenLink = urlOpener,
            )
        }
    }
}
