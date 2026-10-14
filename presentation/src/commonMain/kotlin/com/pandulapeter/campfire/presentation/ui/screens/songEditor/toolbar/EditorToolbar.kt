/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songEditor.toolbar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.insert
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.edit.ChordProHeader
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_album
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_annotation
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_artist
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_capo
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_chord
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_chord_shape
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_chorus_recall
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_comment
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_comment_box
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_comment_italic
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_composer
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_cover_art
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_duration
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_key
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_key_change
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_language
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_link
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_lyricist
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_subtitle
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_tag
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_tempo
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_time
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_title
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_year
import com.pandulapeter.campfire.presentation.resources.song_editor_section_bridge
import com.pandulapeter.campfire.presentation.resources.song_editor_section_chorus
import com.pandulapeter.campfire.presentation.resources.song_editor_section_grid
import com.pandulapeter.campfire.presentation.resources.song_editor_section_intro
import com.pandulapeter.campfire.presentation.resources.song_editor_section_outro
import com.pandulapeter.campfire.presentation.resources.song_editor_section_pre_chorus
import com.pandulapeter.campfire.presentation.resources.song_editor_section_solo
import com.pandulapeter.campfire.presentation.resources.song_editor_section_tab
import com.pandulapeter.campfire.presentation.resources.song_editor_section_verse
import com.pandulapeter.campfire.presentation.resources.song_editor_tab_any_instrument
import com.pandulapeter.campfire.presentation.ui.chords.chordShapeInsertion
import com.pandulapeter.campfire.presentation.ui.instrument.instrumentLabel
import com.pandulapeter.campfire.presentation.ui.components.bounceHorizontalScroll
import com.pandulapeter.campfire.presentation.ui.tabs.tabInstruments

/**
 * The insertion rows of the editor's app bar: a button for every directive the parser understands, so that a format
 * whose vocabulary is only ever spelled out in its own documentation can be written without knowing any of it by
 * heart.
 *
 * There are two of them because the two halves are reached for at different moments: the first writes what the song
 * *is* (its title, its key, its tags), which is what a file opens with and what is usually filled in once, and the
 * second writes the song itself (its chords, its sections, its comments, and the changes of key, tempo and time
 * signature further down), which is the rest of the work. Keeping them apart means the row being scrolled through is
 * always the short one.
 *
 * Each row scrolls horizontally rather than wrapping, because the alternative on a phone is four rows of buttons
 * over an editor that is two lines tall. The buttons take no focus ([Modifier.focusProperties]): a toolbar that
 * takes the caret out of the field closes the keyboard under itself on every tap, and the insertion the tap asked
 * for would land wherever the caret used to be.
 *
 * @param text The text of [textFieldState] as one string, which the screen copies once per edit for everything that
 *   follows it rather than each of them copying it again.
 * @param chordShapes What the Chord shape button, the last of the first row, writes a definition with, see
 *   [chordShapeInsertion].
 * @param tabStaff The empty staff the Tab button writes where nothing is selected for the instrument picked from its
 *   menu, or for none, its strings named.
 */
@Composable
internal fun EditorToolbar(
    modifier: Modifier = Modifier,
    textFieldState: TextFieldState,
    text: State<String>,
    contentPadding: PaddingValues,
    chordShapes: EditorChordShapes,
    tabStaff: (UserPreferences.Instrument?) -> List<String>,
) {
    // What the song already says about itself follows the text rather than being read once, so that a directive
    // typed by hand takes its button out of reach exactly as one inserted from the toolbar does. The cache is what
    // keeps that off the keystroke path: only an edit that changes what a line declares counts the whole text again,
    // and the derived state recomposes the toolbar only when the set itself changes.
    val declaredMetadataCache = remember(text) { ChordProHeader.DeclaredMetadataCache() }
    val declaredMetadata by remember(text) {
        derivedStateOf { declaredMetadataCache.declaredMetadataOf(text.value) }
    }
    Column(
        modifier = modifier.padding(bottom = TOOLBAR_PADDING),
        verticalArrangement = Arrangement.spacedBy(TOOLBAR_GAP),
    ) {
        EditorToolbarRow(
            groups = metadataInsertions(),
            declaredMetadata = declaredMetadata,
            textFieldState = textFieldState,
            contentPadding = contentPadding,
        ) {
            // A song defines as many chords as it has, so this one is never out of reach, like Tag and Language.
            EditorToolbarDivider()
            EditorToolbarButton(
                label = stringResource(Res.string.song_editor_insert_chord_shape),
                isEnabled = true,
                onClick = { textFieldState.insertChordShape(chordShapes) },
            )
        }
        EditorToolbarRow(
            groups = contentInsertions(tabStaff),
            declaredMetadata = declaredMetadata,
            textFieldState = textFieldState,
            contentPadding = contentPadding,
        )
    }
}

/**
 * @param groups Rendered in order with a divider between them, which is the only thing that tells them apart.
 * @param declaredMetadata What the song already declares, which decides what is left for a button to write.
 */
@Composable
private fun EditorToolbarRow(
    modifier: Modifier = Modifier,
    groups: List<List<EditorInsertion>>,
    declaredMetadata: Set<String>,
    textFieldState: TextFieldState,
    contentPadding: PaddingValues,
    trailing: @Composable () -> Unit = {},
) {
    val layoutDirection = LocalLayoutDirection.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .bounceHorizontalScroll(rememberScrollState())
            .padding(
                start = contentPadding.calculateStartPadding(layoutDirection) + TOOLBAR_PADDING,
                end = contentPadding.calculateEndPadding(layoutDirection) + TOOLBAR_PADDING,
            ),
        horizontalArrangement = Arrangement.spacedBy(TOOLBAR_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        groups.forEachIndexed { index, group ->
            if (index > 0) EditorToolbarDivider()
            group.forEach { insertion ->
                if (insertion.choices.isEmpty()) {
                    EditorToolbarButton(
                        label = insertion.label,
                        isEnabled = insertion.isEnabled(declaredMetadata),
                        onClick = { textFieldState.insert(insertion) },
                    )
                } else {
                    EditorToolbarMenuButton(
                        label = insertion.label,
                        choices = insertion.choices.map { it.label },
                        onChoiceSelected = { index -> textFieldState.insert(insertion.choices[index]) },
                    )
                }
            }
        }
        trailing()
    }
}

/**
 * What goes into the song itself: what is written inside a line, every section the app has a name for, the two ways
 * of writing lines down, the three kinds of comment, and the changes a song makes from where they stand — of key, of
 * tempo and of time signature.
 *
 * Tablature and grids are a group of their own rather than two more sections, because that is what they are: a
 * `{start_of_tab}` says how the next few lines are written and can open inside a solo or a verse without breaking
 * it in two, see [SectionType][com.pandulapeter.campfire.chordpro.model.SectionType].
 *
 * `{new_page}` and `{column_break}` would belong here and are deliberately missing, though the parser still reads
 * them without complaint: they belong to a renderer that paginates, and Campfire flows the sections into columns
 * itself at whatever size the window happens to be, so a break written by hand would decide nothing.
 *
 * The key change is `{transpose}`, which from the caret on moves the chords by the semitones typed into it, counted
 * from the song as it is written, and which the viewer names the new key by. It goes to the caret like the rest, so
 * one written above the song's first line is what the directive means there: the transposition of all of it.
 *
 * The tempo and the time signature stand next to it although they are metadata, since after the header has named them
 * once, every further tap writes a change at the caret's line, which is what the row is reached for while the song is
 * being written; the first one still goes into the header, see [metadataInsertions].
 *
 * The Tab button first asks what the tab is for: any instrument, which writes a plain `{start_of_tab}`, or one of the
 * instruments with frets ([tabInstruments]), whose id becomes its selector (`{start_of_tab-ukulele}`), so that of the
 * tabs written one after the other for several instruments each player is shown theirs. A tab with nothing selected
 * starts as an empty staff of one bar ([tabStaff]) — the player's strings for their own instrument or for any, the
 * standard tuning's for another — named, so that the file says what it is written in for whoever reads it in another
 * tuning or on another instrument; with something selected, which is most likely a tab pasted in, it only puts the
 * environment around it.
 */
@Composable
private fun contentInsertions(tabStaff: (UserPreferences.Instrument?) -> List<String>): List<List<EditorInsertion>> = listOf(
    listOf(
        EditorInsertion(stringResource(Res.string.song_editor_insert_chord), prefix = "[", suffix = "]", isOwnLine = false),
        EditorInsertion(stringResource(Res.string.song_editor_insert_annotation), prefix = "[*", suffix = "]", isOwnLine = false),
    ),
    listOf(
        EditorInsertion.section(stringResource(Res.string.song_editor_section_intro), name = "intro"),
        EditorInsertion.section(stringResource(Res.string.song_editor_section_verse), name = "verse"),
        EditorInsertion.section(stringResource(Res.string.song_editor_section_pre_chorus), name = "pre-chorus"),
        EditorInsertion.section(stringResource(Res.string.song_editor_section_chorus), name = "chorus"),
        EditorInsertion.section(stringResource(Res.string.song_editor_section_bridge), name = "bridge"),
        EditorInsertion.section(stringResource(Res.string.song_editor_section_solo), name = "solo"),
        EditorInsertion.section(stringResource(Res.string.song_editor_section_outro), name = "outro"),
        EditorInsertion.directive(stringResource(Res.string.song_editor_insert_chorus_recall), name = "chorus", hasValue = false),
    ),
    listOf(
        EditorInsertion.menu(
            label = stringResource(Res.string.song_editor_section_tab),
            choices = listOf(EditorInsertion.tab(stringResource(Res.string.song_editor_tab_any_instrument), selector = null, staff = tabStaff(null))) +
                tabInstruments.map { instrument -> EditorInsertion.tab(instrumentLabel(instrument), selector = instrument.id, staff = tabStaff(instrument)) },
        ),
        EditorInsertion.section(stringResource(Res.string.song_editor_section_grid), name = "grid"),
    ),
    listOf(
        EditorInsertion.directive(stringResource(Res.string.song_editor_insert_comment), name = "comment"),
        EditorInsertion.directive(stringResource(Res.string.song_editor_insert_comment_italic), name = "comment_italic"),
        EditorInsertion.directive(stringResource(Res.string.song_editor_insert_comment_box), name = "comment_box"),
    ),
    listOf(
        EditorInsertion.directive(stringResource(Res.string.song_editor_insert_key_change), name = "transpose"),
        EditorInsertion.metadata(stringResource(Res.string.song_editor_insert_tempo), name = "tempo"),
        EditorInsertion.metadata(stringResource(Res.string.song_editor_insert_time), name = "time"),
    ),
)

/**
 * What the song says about itself: every metadata directive the parser reads and the app then shows somewhere, in
 * the order the header of a file tends to list them.
 *
 * None of these goes to the caret: they belong to the header and are put there, see `insertIntoHeader`. The directives
 * that can only be true once — a song has one title and came out in one year — come first, each offered until the file
 * carries it and then no longer, so the start of the row empties as the header fills in. The tags, the languages and
 * the links of a song, which can be added again and again, are a group of their own after them. The tempo and the time
 * signature are not here, though they are header directives too: only the first of each goes into the header, and
 * every later one at the caret's line, as a change from there on that the song details screen starts a page with, so
 * they are among the [contentInsertions], next to the key change. A `{key}` is not offered that way, since nothing in
 * the app follows a key change written as one; that is what the key change is for.
 *
 * `{new_song}` is left out, since it splits an imported file into several songs and the editor is only ever looking
 * at one of them. `{transpose}` is not here either: at the top of a file it shifts every chord away from what the file
 * says, which the reader does on the details screen or writes into the chords with the editor's own transpose action.
 * It is among the [contentInsertions] instead, as the key change it is once it stands further down.
 */
@Composable
private fun metadataInsertions(): List<List<EditorInsertion>> = listOf(
    listOf(
        EditorInsertion.metadata(stringResource(Res.string.song_editor_insert_title), name = "title"),
        EditorInsertion.metadata(stringResource(Res.string.song_editor_insert_subtitle), name = "subtitle"),
        EditorInsertion.metadata(stringResource(Res.string.song_editor_insert_artist), name = "artist"),
        EditorInsertion.metadata(stringResource(Res.string.song_editor_insert_composer), name = "composer"),
        EditorInsertion.metadata(stringResource(Res.string.song_editor_insert_lyricist), name = "lyricist"),
        EditorInsertion.metadata(stringResource(Res.string.song_editor_insert_album), name = "album"),
        EditorInsertion.meta(stringResource(Res.string.song_editor_insert_cover_art), key = "cover"),
        EditorInsertion.metadata(stringResource(Res.string.song_editor_insert_year), name = "year"),
        EditorInsertion.metadata(stringResource(Res.string.song_editor_insert_key), name = "key"),
        EditorInsertion.metadata(stringResource(Res.string.song_editor_insert_capo), name = "capo"),
        EditorInsertion.metadata(stringResource(Res.string.song_editor_insert_duration), name = "duration"),
    ),
    listOf(
        EditorInsertion.metadata(stringResource(Res.string.song_editor_insert_tag), name = "tag"),
        EditorInsertion.meta(stringResource(Res.string.song_editor_insert_language), key = "language"),
        EditorInsertion.meta(stringResource(Res.string.song_editor_insert_link), key = "link"),
    ),
)

@Composable
private fun EditorToolbarDivider() = VerticalDivider(modifier = Modifier.height(DIVIDER_HEIGHT).padding(horizontal = TOOLBAR_GAP))

private val TOOLBAR_PADDING = 16.dp
private val TOOLBAR_GAP = 4.dp
private val DIVIDER_HEIGHT = 24.dp
