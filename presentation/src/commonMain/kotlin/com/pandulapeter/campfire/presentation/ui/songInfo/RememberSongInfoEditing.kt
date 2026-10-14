/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songInfo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.dialogs.SongEditTarget
import com.pandulapeter.campfire.presentation.ui.metronome.timeSignatureOrDefault
import com.pandulapeter.campfire.presentation.ui.playing.SongPlayback
import com.pandulapeter.campfire.presentation.ui.playing.readsCapoAsTransposition
import com.pandulapeter.campfire.presentation.ui.playing.shownFor

/**
 * What the tiles of the About the song sheet open: the dialog of each group. Over the editor (a
 * [SongEditTarget.EditorDraft] [target]) they edit the text being typed rather than the file, see [DialogType.SongEdit],
 * and the latest [song] is what a tile opens its dialog on; [setlistFileName] is the setlist it is read through, whose
 * overrides the Song defaults sheet names.
 */
@Composable
internal fun rememberSongInfoEditing(
    viewModel: CampfireViewModel,
    song: Song,
    setlistFileName: String?,
    target: SongEditTarget,
): SongInfoEditing {
    val latestSong by rememberUpdatedState(song)
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val isCoverArtEnabled = userPreferences?.isCoverArtEnabled == true
    return remember(viewModel, setlistFileName, target, isCoverArtEnabled) {
        SongInfoEditing(
            onEditCoverArt = if (isCoverArtEnabled) {
                { viewModel.songMetadataEditing.showSongCoverArtDialog(song = latestSong, target = target) }
            } else null,
            onEditMetadata = { viewModel.songMetadataEditing.showSongMetadataDialog(song = latestSong, target = target) },
            onEditDefaults = { viewModel.songMetadataEditing.showSongPlayingDialog(song = latestSong, setlistFileName = setlistFileName, target = target) },
            onEditTags = { viewModel.songMetadataEditing.showSongTagsDialog(song = latestSong, target = target) },
            onEditLanguages = { viewModel.songMetadataEditing.showSongLanguagesDialog(song = latestSong, target = target) },
            onEditLinks = { viewModel.songMetadataEditing.showSongLinksDialog(song = latestSong, target = target) },
        )
    }
}
