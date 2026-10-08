/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.rendering

import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.chords.ChordProNotation
import com.pandulapeter.campfire.chordpro.chords.ChordProTransposer
import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.data.model.domain.UserPreferences.Notation
import com.pandulapeter.campfire.domain.api.useCases.ConvertChordProNotationUseCase
import com.pandulapeter.campfire.domain.api.useCases.ConvertChordProTextNotationUseCase
import com.pandulapeter.campfire.domain.api.useCases.NormalizeLanguageCodeUseCase
import com.pandulapeter.campfire.domain.api.useCases.NormalizeSearchTextUseCase
import com.pandulapeter.campfire.domain.api.useCases.NormalizeTextUseCase
import com.pandulapeter.campfire.domain.api.useCases.ParseChordProUseCase
import com.pandulapeter.campfire.domain.api.useCases.PrettifyChordProUseCase
import com.pandulapeter.campfire.domain.api.useCases.TransposeChordProTextUseCase
import com.pandulapeter.campfire.domain.api.useCases.TransposeChordProUseCase
import com.pandulapeter.campfire.presentation.ui.chords.toChordNotation

/** A [SongRenderer] whose use cases are stood in for by the `:chordpro` objects their implementations delegate to, which this module sees. */
internal fun testSongRenderer() = SongRenderer(
    parseChordPro = object : ParseChordProUseCase {
        override fun invoke(text: String, notation: Notation) = ChordProParser.parse(text, notation.toChordNotation())
    },
    transposeChordPro = object : TransposeChordProUseCase {
        override fun invoke(song: ChordProSong, semitones: Int, accidentals: UserPreferences.Accidentals) =
            ChordProTransposer.transpose(song, semitones, accidentals.preferFlats)
    },
    transposeChordProText = object : TransposeChordProTextUseCase {
        override fun invoke(text: String, semitones: Int, accidentals: UserPreferences.Accidentals) =
            ChordProTransposer.transposeText(text, semitones, accidentals.preferFlats)
    },
    convertChordProNotation = object : ConvertChordProNotationUseCase {
        override fun invoke(song: ChordProSong, spelling: UserPreferences.ChordSpelling) =
            ChordProNotation.toNotation(song, spelling.notation.toChordNotation())
    },
    convertChordProTextNotation = object : ConvertChordProTextNotationUseCase {
        override fun invoke(text: String, from: Notation, to: Notation) =
            ChordProNotation.convertText(text, from.toChordNotation(), to.toChordNotation())
    },
    prettifyChordPro = object : PrettifyChordProUseCase {
        override fun invoke(text: String) = text
    },
    normalizeText = object : NormalizeTextUseCase {
        override fun invoke(text: String) = text.lowercase()
    },
    normalizeSearchText = object : NormalizeSearchTextUseCase {
        override fun invoke(text: String) = text.lowercase()
    },
    normalizeLanguageCode = object : NormalizeLanguageCodeUseCase {
        override fun invoke(value: String) = value.lowercase()
    },
)

private val UserPreferences.Accidentals.preferFlats
    get() = when (this) {
        UserPreferences.Accidentals.ORIGINAL -> null
        UserPreferences.Accidentals.FLATS -> true
        UserPreferences.Accidentals.SHARPS -> false
    }
