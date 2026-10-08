/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_info_defaults
import com.pandulapeter.campfire.presentation.resources.song_details_playing_edit
import com.pandulapeter.campfire.presentation.resources.song_details_playing_override_library
import com.pandulapeter.campfire.presentation.resources.song_details_playing_override_setlist
import com.pandulapeter.campfire.presentation.resources.song_details_tempo
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_capo
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_key
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_tempo
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_time
import com.pandulapeter.campfire.presentation.ui.components.scaled
import com.pandulapeter.campfire.presentation.ui.components.textResource

/** What the song's file declares for the four values it is played by, for the "About the song" sheet, see [SongInfoBody]. */
@Immutable
internal class SongDefaults(
    /** In the reader's notation, the way the "Song defaults" sheet's field shows it. */
    val key: String?,
    val capo: Int?,
    val tempo: Int?,
    val time: String?,
    /** How the song is played differently from these where it is read, see `songPlayingOverrides`. */
    val overrides: List<String>,
    /** Whether [overrides] belong to a setlist rather than to the library on this device. */
    val isReadFromSetlist: Boolean,
    /** Opens the "Song defaults" sheet; null in read only mode, which changes nothing about a song. */
    val onEdit: (() -> Unit)?,
) {
    val hasValues get() = !key.isNullOrBlank() || capo != null || tempo != null || !time.isNullOrBlank()
}

/**
 * The song's defaults as label-over-value tiles, the way its details are shown, with a pencil next to the title that
 * opens the "Song defaults" sheet. Where the song is being played differently from them a line under the tiles says so, in the
 * color the steppers on the page draw an overridden value in: the sheet would otherwise name values the page does not
 * show, and that difference is the one the "Song defaults" sheet exists to explain.
 */
@Composable
internal fun SongDefaultsGroup(
    defaults: SongDefaults,
    fontScale: Float,
    horizontalPadding: Dp,
) = SongInfoGroup(
    title = stringResource(Res.string.song_details_info_defaults),
    count = 0,
    fontScale = fontScale,
    horizontalPadding = horizontalPadding,
    action = defaults.onEdit?.let { onEdit ->
        {
            SongInfoAction(
                hasValues = defaults.hasValues,
                contentDescription = stringResource(Res.string.song_details_playing_edit),
                fontScale = fontScale,
                onClick = onEdit,
            )
        }
    },
    isContentShown = defaults.hasValues || defaults.overrides.isNotEmpty(),
) {
    SongDefaultsValues(defaults = defaults, fontScale = fontScale)
}

/** The tiles of [SongDefaultsGroup], and under them the line naming the overrides while there are any. */
@Composable
private fun SongDefaultsValues(
    defaults: SongDefaults,
    fontScale: Float,
) {
    val rows = listOfNotNull(
        defaults.key?.takeIf { it.isNotBlank() }?.let { stringResource(Res.string.song_editor_insert_key) to it },
        defaults.capo?.let { stringResource(Res.string.song_editor_insert_capo) to it.toString() },
        defaults.tempo?.let { stringResource(Res.string.song_editor_insert_tempo) to textResource(Res.string.song_details_tempo, it.toString()) },
        defaults.time?.takeIf { it.isNotBlank() }?.let { stringResource(Res.string.song_editor_insert_time) to it },
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp * fontScale)) {
        if (rows.isNotEmpty()) {
            MetadataTiles(
                rows = rows,
                fontScale = fontScale,
            )
        }
        AnimatedVisibility(
            visible = defaults.overrides.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            // The last overrides named stay while a reset folds the line away.
            var shownOverrides by remember { mutableStateOf(defaults.overrides) }
            if (defaults.overrides.isNotEmpty()) shownOverrides = defaults.overrides
            Column {
                Text(
                    text = stringResource(
                        if (defaults.isReadFromSetlist) Res.string.song_details_playing_override_setlist else Res.string.song_details_playing_override_library,
                    ),
                    style = MaterialTheme.typography.labelMedium.scaled(fontScale),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = shownOverrides.joinToString("  •  "),
                    style = MaterialTheme.typography.bodyMedium.scaled(fontScale),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
