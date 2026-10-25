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
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.model.ChordProLine.Lyrics.Formatting
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_format_bold
import com.pandulapeter.campfire.presentation.resources.ic_format_italic
import com.pandulapeter.campfire.presentation.resources.ic_format_underlined
import com.pandulapeter.campfire.presentation.resources.song_editor_format_bold
import com.pandulapeter.campfire.presentation.resources.song_editor_format_italic
import com.pandulapeter.campfire.presentation.resources.song_editor_format_underline
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
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_more_details
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
import com.pandulapeter.campfire.presentation.ui.chords.chordShapeInstruments
import com.pandulapeter.campfire.presentation.ui.instrument.chordInstrument
import com.pandulapeter.campfire.presentation.ui.instrument.instrumentLabel
import com.pandulapeter.campfire.presentation.ui.components.bounceHorizontalScroll
import com.pandulapeter.campfire.presentation.ui.tabs.tabInstruments
import org.jetbrains.compose.resources.painterResource

/**
 * The insertion rows of the editor's app bar: a button for every directive the parser understands, so that a format
 * whose vocabulary is only ever spelled out in its own documentation can be written without knowing any of it by
 * heart.
 *
 * There are two of them because the two halves are reached for at different moments: the second holds what goes into
 * nearly every line (its chords, the words' formatting, its sections), and the first everything a song needs now and
 * then: what it *is* and how it is played (its title, its key and tempo and the changes of them further down, its
 * tags), then the tabs, grids, and comments a song holds a few of at most. The two are kept about as long as each
 * other, so that neither is the one always scrolled through, and what is reached for less often waits behind a menu
 * rather than lengthening its row.
 *
 * Each row scrolls horizontally rather than wrapping, because the alternative on a phone is four rows of buttons
 * over an editor that is two lines tall. The buttons take no focus ([Modifier.focusProperties]): a toolbar that
 * takes the caret out of the field closes the keyboard under itself on every tap, and the insertion the tap asked
 * for would land wherever the caret used to be.
 *
 * @param chordShapes What the Chord shape menu writes a definition with for the instrument picked from it
 *   ([chordShapeInstruments]), see `chordShapeInsertion`.
 * @param tabStaff The empty staff the Tab button writes where nothing is selected for the instrument picked from its
 *   menu, or for none, its strings named.
 * @param onValueToType Called after a write that left a value to type ([leavesValueToType]), so that the keyboard comes
 *   up for it even where the user had put it away. Never for the formatting buttons: markup is toggled around what is
 *   there, and with nothing selected its empty pair waits for words as much as a section does.
 */
@Composable
internal fun EditorToolbar(
    modifier: Modifier = Modifier,
    textFieldState: TextFieldState,
    contentPadding: PaddingValues,
    chordShapes: EditorChordShapes,
    tabStaff: (UserPreferences.Instrument?) -> List<String>,
    onValueToType: () -> Unit,
) {
    val onInsert = { insertion: EditorInsertion ->
        val textBefore = textFieldState.text.toString()
        val selectionBefore = textFieldState.selection
        val instrument = insertion.chordShapeInstrument
        if (instrument == null) textFieldState.insert(insertion) else textFieldState.insertChordShape(chordShapes, instrument)
        if (insertion.formatting == null && leavesValueToType(textBefore, selectionBefore, textFieldState.text.toString(), textFieldState.selection)) {
            onValueToType()
        }
    }
    Column(
        modifier = modifier.padding(bottom = TOOLBAR_PADDING),
        verticalArrangement = Arrangement.spacedBy(TOOLBAR_GAP),
    ) {
        EditorToolbarRow(
            groups = metadataInsertions() + lineKindInsertions(tabStaff),
            contentPadding = contentPadding,
            onInsert = onInsert,
        )
        EditorToolbarRow(
            groups = contentInsertions(),
            contentPadding = contentPadding,
            onInsert = onInsert,
        )
    }
}

/**
 * @param groups Rendered in order with a divider between them, which is the only thing that tells them apart.
 */
@Composable
private fun EditorToolbarRow(
    modifier: Modifier = Modifier,
    groups: List<List<EditorInsertion>>,
    contentPadding: PaddingValues,
    onInsert: (EditorInsertion) -> Unit,
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
                        icon = insertion.icon?.let { painterResource(it) },
                        onClick = { onInsert(insertion) },
                    )
                } else {
                    EditorToolbarMenuButton(
                        label = insertion.label,
                        choices = insertion.choices.map { it.label },
                        onChoiceSelected = { index -> onInsert(insertion.choices[index]) },
                    )
                }
            }
        }
    }
}

/**
 * What goes into nearly every line of the song: what goes inside a line (chords, annotations, the definition of how
 * a chord is fingered, and the markup that sets words in bold, italic, or underlined, which Ctrl / Cmd + B, I, and U
 * toggle too), and every section the app has a name for, in the order a song tends to run through them (the chorus
 * recall right after the chorus it repeats).
 *
 * The Chord shape menu sits with the chords although it writes into the header: what it writes is how the chord at
 * the caret is played, which is what a player looks for next to the Chord button. The formatting buttons are icons,
 * the ones every text editor shares, since three words there would make the busiest row longer still.
 *
 * `{new_page}` and `{column_break}` would belong here and are deliberately missing, though the parser still reads
 * them without complaint: they belong to a renderer that paginates, and Campfire flows the sections into columns
 * itself at whatever size the window happens to be, so a break written by hand would decide nothing.
 */
@Composable
private fun contentInsertions(): List<List<EditorInsertion>> = listOf(
    listOf(
        EditorInsertion(stringResource(Res.string.song_editor_insert_chord), prefix = "[", suffix = "]", isOwnLine = false),
        EditorInsertion(stringResource(Res.string.song_editor_insert_annotation), prefix = "[*", suffix = "]", isOwnLine = false),
        EditorInsertion.menu(
            label = stringResource(Res.string.song_editor_insert_chord_shape),
            choices = chordShapeInstruments.mapNotNull { instrument ->
                instrument.chordInstrument?.let { EditorInsertion.chordShape(instrumentLabel(instrument), it) }
            },
        ),
    ),
    listOf(
        EditorInsertion.formatting(stringResource(Res.string.song_editor_format_bold), Formatting.Style.BOLD, Res.drawable.ic_format_bold),
        EditorInsertion.formatting(stringResource(Res.string.song_editor_format_italic), Formatting.Style.ITALIC, Res.drawable.ic_format_italic),
        EditorInsertion.formatting(stringResource(Res.string.song_editor_format_underline), Formatting.Style.UNDERLINE, Res.drawable.ic_format_underlined),
    ),
    listOf(
        EditorInsertion.section(stringResource(Res.string.song_editor_section_intro), name = "intro"),
        EditorInsertion.section(stringResource(Res.string.song_editor_section_verse), name = "verse"),
        EditorInsertion.section(stringResource(Res.string.song_editor_section_pre_chorus), name = "pre-chorus"),
        EditorInsertion.section(stringResource(Res.string.song_editor_section_chorus), name = "chorus"),
        EditorInsertion.directive(stringResource(Res.string.song_editor_insert_chorus_recall), name = "chorus", hasValue = false),
        EditorInsertion.section(stringResource(Res.string.song_editor_section_bridge), name = "bridge"),
        EditorInsertion.section(stringResource(Res.string.song_editor_section_solo), name = "solo"),
        EditorInsertion.section(stringResource(Res.string.song_editor_section_outro), name = "outro"),
    ),
)

/**
 * What the song says about itself and how it is played: every metadata directive the parser reads and the app then
 * shows somewhere, and the changes of key, tempo, and time signature further down.
 *
 * The metadata does not go to the caret: it belongs to the header and is put there, see `insertIntoHeader`. The title
 * and the artist, which nearly every song names, are buttons of their own; the credits and the rest that some songs
 * name (the subtitle, the composer, the lyricist, the album, the year, the duration, and the cover) wait in one menu
 * after them, in the order a header tends to list them. The directives that can only be true once - a song has one
 * title and came out in one year - take the caret to the line once the file carries one, instead of writing a second
 * (see `ChordProHeader.write`).
 *
 * How the song is played is a group of its own: the key and the capo, the key change, and the tempo and the time
 * signature. The key change is `{transpose}`, which from the caret on moves the chords by the semitones typed into it,
 * counted from the song as it is written, and which the viewer names the new key by; it goes to the caret, so one
 * written above the song's first line is what the directive means there: the transposition of all of it. The tempo
 * and the time signature go into the header the first time, and every later tap writes a change at the caret's line,
 * which the song details screen starts a page with. A `{key}` is not offered that way, since nothing in the app follows
 * a key change written as one; that is what the key change is for.
 *
 * The tags, the languages and the links of a song, which can be added again and again, are a group of their own after
 * them, and the row ends in the [lineKindInsertions].
 * `{new_song}` is left out, since it splits an imported file into several songs and the editor is only ever looking
 * at one of them.
 */
@Composable
private fun metadataInsertions(): List<List<EditorInsertion>> = listOf(
    listOf(
        EditorInsertion.metadata(stringResource(Res.string.song_editor_insert_title), name = "title"),
        EditorInsertion.metadata(stringResource(Res.string.song_editor_insert_artist), name = "artist"),
        EditorInsertion.menu(
            label = stringResource(Res.string.song_editor_insert_more_details),
            choices = listOf(
                EditorInsertion.metadata(stringResource(Res.string.song_editor_insert_subtitle), name = "subtitle"),
                EditorInsertion.metadata(stringResource(Res.string.song_editor_insert_composer), name = "composer"),
                EditorInsertion.metadata(stringResource(Res.string.song_editor_insert_lyricist), name = "lyricist"),
                EditorInsertion.metadata(stringResource(Res.string.song_editor_insert_album), name = "album"),
                EditorInsertion.metadata(stringResource(Res.string.song_editor_insert_year), name = "year"),
                EditorInsertion.metadata(stringResource(Res.string.song_editor_insert_duration), name = "duration"),
                EditorInsertion.meta(stringResource(Res.string.song_editor_insert_cover_art), key = "cover"),
            ),
        ),
    ),
    listOf(
        EditorInsertion.metadata(stringResource(Res.string.song_editor_insert_key), name = "key"),
        EditorInsertion.metadata(stringResource(Res.string.song_editor_insert_capo), name = "capo"),
        EditorInsertion.directive(stringResource(Res.string.song_editor_insert_key_change), name = "transpose"),
        EditorInsertion.metadata(stringResource(Res.string.song_editor_insert_tempo), name = "tempo"),
        EditorInsertion.metadata(stringResource(Res.string.song_editor_insert_time), name = "time"),
    ),
    listOf(
        EditorInsertion.metadata(stringResource(Res.string.song_editor_insert_tag), name = "tag"),
        EditorInsertion.meta(stringResource(Res.string.song_editor_insert_language), key = "language"),
        EditorInsertion.meta(stringResource(Res.string.song_editor_insert_link), key = "link"),
    ),
)

/**
 * The other ways of writing lines down, which a song holds a few of at most and so belong to the first row: tablature
 * and grids, and the three kinds of comment as one menu, a plain comment being the one usually wanted.
 *
 * Tablature and grids are a group of their own rather than two more sections, because that is what they are: a
 * `{start_of_tab}` says how the next few lines are written and can open inside a solo or a verse without breaking
 * it in two, see [SectionType][com.pandulapeter.campfire.chordpro.model.SectionType].
 *
 * The Tab button first asks what the tab is for: any instrument, which writes a plain `{start_of_tab}`, or one of the
 * instruments with frets ([tabInstruments]), whose id becomes its selector (`{start_of_tab-ukulele}`), so that of the
 * tabs written one after the other for several instruments each player is shown theirs. A tab with nothing selected
 * starts as an empty staff of one bar ([tabStaff]) — the picked instrument's strings in standard tuning, the player's
 * own instrument's for any — named, so that the file says what it is written in for whoever reads it in another
 * tuning or on another instrument; with something selected, which is most likely a tab pasted in, it only puts the
 * environment around it.
 */
@Composable
private fun lineKindInsertions(tabStaff: (UserPreferences.Instrument?) -> List<String>): List<List<EditorInsertion>> = listOf(
    listOf(
        EditorInsertion.menu(
            label = stringResource(Res.string.song_editor_section_tab),
            choices = listOf(EditorInsertion.tab(stringResource(Res.string.song_editor_tab_any_instrument), selector = null, staff = tabStaff(null))) +
                tabInstruments.map { instrument -> EditorInsertion.tab(instrumentLabel(instrument), selector = instrument.id, staff = tabStaff(instrument)) },
        ),
        EditorInsertion.section(stringResource(Res.string.song_editor_section_grid), name = "grid"),
        EditorInsertion.menu(
            label = stringResource(Res.string.song_editor_insert_comment),
            choices = listOf(
                EditorInsertion.directive(stringResource(Res.string.song_editor_insert_comment), name = "comment"),
                EditorInsertion.directive(stringResource(Res.string.song_editor_insert_comment_italic), name = "comment_italic"),
                EditorInsertion.directive(stringResource(Res.string.song_editor_insert_comment_box), name = "comment_box"),
            ),
        ),
    ),
)

@Composable
private fun EditorToolbarDivider() = VerticalDivider(modifier = Modifier.height(DIVIDER_HEIGHT).padding(horizontal = TOOLBAR_GAP))

private val TOOLBAR_PADDING = 16.dp
private val TOOLBAR_GAP = 4.dp
private val DIVIDER_HEIGHT = 24.dp
