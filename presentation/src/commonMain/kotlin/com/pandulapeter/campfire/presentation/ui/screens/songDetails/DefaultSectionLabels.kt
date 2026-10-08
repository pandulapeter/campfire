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

import androidx.compose.runtime.Composable
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.SectionType
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_section_bridge
import com.pandulapeter.campfire.presentation.resources.song_details_section_chorus
import com.pandulapeter.campfire.presentation.resources.song_details_section_grid
import com.pandulapeter.campfire.presentation.resources.song_details_section_tab
import com.pandulapeter.campfire.presentation.resources.song_editor_section_intro
import com.pandulapeter.campfire.presentation.resources.song_editor_section_outro
import com.pandulapeter.campfire.presentation.resources.song_editor_section_pre_chorus
import com.pandulapeter.campfire.presentation.resources.song_editor_section_solo
import com.pandulapeter.campfire.presentation.resources.song_editor_section_verse

/** The name of a fold whose environment was given no label, the one a section of nothing but it would be headed by. */
internal fun DefaultSectionLabels.labelOf(kind: FoldableKind) = when (kind) {
    FoldableKind.TAB -> tab
    FoldableKind.GRID -> grid
}

/** The fallback labels of the environments that have one, read here since they are string resources. */
@Composable
internal fun rememberDefaultSectionLabels(shouldNumberSections: Boolean = false) = DefaultSectionLabels(
    verse = stringResource(Res.string.song_editor_section_verse),
    chorus = stringResource(Res.string.song_details_section_chorus),
    bridge = stringResource(Res.string.song_details_section_bridge),
    tab = stringResource(Res.string.song_details_section_tab),
    grid = stringResource(Res.string.song_details_section_grid),
    intro = stringResource(Res.string.song_editor_section_intro),
    preChorus = stringResource(Res.string.song_editor_section_pre_chorus),
    solo = stringResource(Res.string.song_editor_section_solo),
    outro = stringResource(Res.string.song_editor_section_outro),
    shouldNumberSections = shouldNumberSections,
)

/**
 * The fallback names of the environments that have one: every kind of section the editor writes, since it writes them
 * without a label. Everything else is named by the file itself.
 */
internal data class DefaultSectionLabels(
    val verse: String,
    val chorus: String,
    val bridge: String,
    val tab: String,
    val grid: String,
    val intro: String,
    val preChorus: String,
    val solo: String,
    val outro: String,
    /** Whether the sections that have no label, or only their kind's name, are numbered, see [withNumberedSections]. */
    val shouldNumberSections: Boolean = false,
)

/**
 * What a section of a kind ChordPro leaves to the file heads it with where it has no label: the translated name of the
 * kinds the editor writes, and otherwise the file's own wording, only capitalised ("Interlude" for `interlude`).
 */
internal fun DefaultSectionLabels.labelOf(type: SectionType.Custom) = when (type.name.lowercase()) {
    "intro" -> intro
    "pre-chorus" -> preChorus
    "solo" -> solo
    "outro" -> outro
    else -> type.name.replace('_', ' ').replaceFirstChar { it.uppercaseChar() }
}

internal fun ChordProBlock.Section.header(defaultLabels: DefaultSectionLabels): String = (label ?: when (val sectionType = type) {
    SectionType.Verse -> defaultLabels.verse
    SectionType.Chorus -> defaultLabels.chorus
    SectionType.Bridge -> defaultLabels.bridge
    is SectionType.Custom -> defaultLabels.labelOf(sectionType)
    // A paragraph that is nothing but tablature or a grid is a bare `{start_of_tab}` / `{start_of_grid}` standing
    // on its own, and those name themselves even where the file gave them no label. One with lyrics around the run
    // is an ordinary paragraph that happens to hold some, and heading that "Tab" would be a lie - as would any other
    // name, since the file says nothing about what it is, so it is headed by its fold toggle alone.
    SectionType.Paragraph -> when {
        lines.areAll<ChordProLine.Tab>() -> defaultLabels.tab
        lines.areAll<ChordProLine.Grid>() -> defaultLabels.grid
        else -> UNNAMED_SECTION_HEADER
    }
}).withNumber(number)

/**
 * The header of a section the file gives no name and that has none of its own kind to fall back on — a paragraph of
 * lyrics, which is what a song pasted in as plain text with blank lines between its verses is made of, or the rest
 * of a section a chorus recall cut in two: a pill with nothing in it but the chevron that folds it, so that every
 * part of a song folds, and an empty one of the same size where nothing folds (the editor's preview).
 */
internal const val UNNAMED_SECTION_HEADER = ""
