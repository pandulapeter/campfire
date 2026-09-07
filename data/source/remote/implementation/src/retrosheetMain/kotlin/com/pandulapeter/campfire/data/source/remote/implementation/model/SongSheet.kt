package com.pandulapeter.campfire.data.source.remote.implementation.model

import io.github.theapache64.retrosheet.core.RetrosheetConfig

/**
 * Registers the columns of [SongResponse] with Retrosheet, which needs them to build the sheet query.
 */
internal fun RetrosheetConfig.Builder.addSongSheet() = addSheet(
    SongResponse.SHEET_NAME,
    SongResponse.KEY_ID,
    SongResponse.KEY_URL,
    SongResponse.KEY_TITLE,
    SongResponse.KEY_ARTIST,
    SongResponse.KEY_KEY,
    SongResponse.KEY_HAS_CHORDS
)
