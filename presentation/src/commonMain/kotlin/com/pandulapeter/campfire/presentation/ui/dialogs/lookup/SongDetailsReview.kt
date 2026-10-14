/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs.lookup

import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.dialogs.forms.SONG_METADATA_FIELDS
import com.pandulapeter.campfire.presentation.ui.dialogs.forms.durationDigitsOf
import com.pandulapeter.campfire.presentation.ui.dialogs.forms.durationTextOf
import com.pandulapeter.campfire.presentation.ui.dialogs.forms.toSongMetadataDraft
import com.pandulapeter.campfire.chordpro.ChordProDuration
import com.pandulapeter.campfire.chordpro.edit.ChordProMetadataFields.Field
import com.pandulapeter.campfire.data.model.catalogue.SongCredits
import com.pandulapeter.campfire.data.model.catalogue.SongDetailsCandidate

/**
 * One value a recording the song details lookup found would fill into the Edit song details form: what the form holds
 * for [field] now (empty for nothing) and what the recording suggests, both as the form shows them, the duration
 * spelled `m:ss`.
 */
internal data class SongDetailsReviewRow(
    val field: Field,
    val current: String,
    val suggested: String,
) {
    /**
     * Whether the row is ticked before the user touches it: where the form holds nothing for the field. A value the
     * user already has is only replaced on purpose, and the title never is, since a song always has one.
     */
    val isTickedByDefault get() = field != Field.TITLE && current.isBlank()
}

/**
 * What [candidate], and the [credits] its catalogue gave for it where they have arrived, would change in [draft], the
 * Edit song details form's draft in its own spelling (see `toSongMetadataDraft`): one row for every field it suggests a
 * value for that the form does not already hold, in the order the form asks for them. A suggestion is cleaned the way
 * typing into the field is, so that what is shown is what is filled in; durations are compared as durations, so a
 * `4:28` the form holds is not offered again as `04:28`.
 */
internal fun songDetailsReviewRows(
    draft: Map<Field, String>,
    candidate: SongDetailsCandidate,
    credits: SongCredits?,
): List<SongDetailsReviewRow> {
    val suggestions = mapOf(
        Field.TITLE to candidate.title,
        Field.ARTIST to candidate.artist,
        Field.ALBUM to candidate.album,
        Field.YEAR to candidate.year?.filter { it in '0'..'9' }?.takeIf { it.length == YEAR_LENGTH },
        Field.DURATION to candidate.duration?.let(ChordProDuration::format)?.takeIf { durationDigitsOf(it).isNotEmpty() },
        Field.COMPOSER to credits?.composer,
        Field.LYRICIST to credits?.lyricist,
    )
    return SONG_METADATA_FIELDS.mapNotNull { field ->
        val suggested = suggestions[field]?.cleanedForMetadata()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val current = draft[field].orEmpty().let { if (field == Field.DURATION) durationTextOf(it) else it.trim() }
        val isSame = if (field == Field.DURATION) ChordProDuration.parse(current) == ChordProDuration.parse(suggested) else current == suggested
        SongDetailsReviewRow(field = field, current = current, suggested = suggested).takeUnless { isSame }
    }
}

/** [this] draft with the suggestions of the [ticked] fields among [rows] filled in, in the draft's own spelling. */
internal fun Map<Field, String>.withSongDetails(rows: List<SongDetailsReviewRow>, ticked: Set<Field>) = this + rows
    .filter { it.field in ticked }
    .associate { row -> row.field to if (row.field == Field.DURATION) durationDigitsOf(row.suggested) else row.suggested }

/** The metadata form [this] lookup was put up over, holding [draft], as a hand-over of its own (see [DialogType.SongMetadata.fillCount]). */
internal fun DialogType.SongDetailsLookup.filledForm(draft: Map<Field, String>) = DialogType.SongMetadata(
    song = song,
    values = values,
    target = target,
    draft = draft,
    fillCount = formFillCount + 1,
)

/** A brace would end the directive early or open another one, and a line break would leave the rest in the song as lyrics. */
private fun String.cleanedForMetadata() = filterNot { it == '{' || it == '}' || it == '\n' || it == '\r' }.trim()

private const val YEAR_LENGTH = 4
