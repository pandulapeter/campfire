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

import androidx.compose.runtime.saveable.listSaver
import com.pandulapeter.campfire.chordpro.ChordProMetadataFields.Field
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_playing_tempo
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_album
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_artist
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_capo
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_composer
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_duration
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_key
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_lyricist
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_subtitle
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_time
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_title
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_year
import org.jetbrains.compose.resources.StringResource

/** What a field is called in the forms that edit it, the metadata ones and the "Song defaults" sheet alike. */
internal val Field.label: StringResource
    get() = when (this) {
        Field.TITLE -> Res.string.song_editor_insert_title
        Field.SUBTITLE -> Res.string.song_editor_insert_subtitle
        Field.ARTIST -> Res.string.song_editor_insert_artist
        Field.COMPOSER -> Res.string.song_editor_insert_composer
        Field.LYRICIST -> Res.string.song_editor_insert_lyricist
        Field.ALBUM -> Res.string.song_editor_insert_album
        Field.YEAR -> Res.string.song_editor_insert_year
        Field.DURATION -> Res.string.song_editor_insert_duration
        Field.KEY -> Res.string.song_editor_insert_key
        Field.CAPO -> Res.string.song_editor_insert_capo
        Field.TEMPO -> Res.string.song_details_playing_tempo
        Field.TIME -> Res.string.song_editor_insert_time
    }

/**
 * The fields the New song and Edit song details forms ask for, in the order they ask for them. Not every
 * [Field] there is: how the song is played is written from the "Song defaults" sheet, so it is left out of both forms
 * and of what they save.
 */
internal val SONG_METADATA_FIELDS = listOf(
    Field.TITLE,
    Field.SUBTITLE,
    Field.ARTIST,
    Field.ALBUM,
    Field.COMPOSER,
    Field.LYRICIST,
    Field.YEAR,
    Field.DURATION,
)

internal val songMetadataSaver = listSaver<Map<Field, String>, String>(
    save = { values -> SONG_METADATA_FIELDS.map { values[it].orEmpty() } },
    restore = { saved -> SONG_METADATA_FIELDS.zip(saved).toMap() },
)
