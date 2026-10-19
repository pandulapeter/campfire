/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView.model

import androidx.compose.runtime.Immutable
import com.pandulapeter.campfire.chordpro.chords.ChordNotation

/**
 * What a song's Scales section needs beyond the song, null where the section is off (the Scales or the Scale
 * suggestions switch, or the chords): whether it is folded, one choice for every song, and what its header and its
 * chips do.
 *
 * @param onScaleClicked Opens the sheet of a scale, by its id and the note it is played on in the standard notation.
 *   Offered in read only mode too: the sheet changes nothing.
 * @param notation What the scales' roots are named in.
 */
@Immutable
internal data class ScaleSuggestions(
    val isFolded: Boolean,
    val onFoldToggled: () -> Unit,
    val onScaleClicked: (scaleId: String, rootName: String) -> Unit,
    val notation: ChordNotation,
)

/**
 * [sections] with the Scales section of [groups] after the Chords section, or after the metadata section where there is
 * none (the diagrams switched off, or an instrument without shapes), or first; unchanged where there are no groups.
 */
internal fun withScalesSection(sections: List<RenderSection>, groups: List<SongScaleGroup>, isFolded: Boolean): List<RenderSection> {
    if (groups.isEmpty()) return sections
    val after = sections.indexOfFirst { it is RenderSection.Chords }.takeIf { it >= 0 }
        ?: sections.indexOfFirst { it is RenderSection.Metadata }.takeIf { it >= 0 }
        ?: -1
    return sections.subList(0, after + 1) + RenderSection.Scales(groups = groups, isFolded = isFolded) + sections.subList(after + 1, sections.size)
}
