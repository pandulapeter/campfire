/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songEditor

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.insert
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.ChordProHeader
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_album
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_annotation
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_artist
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_capo
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_chord
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_chorus_recall
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_comment
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_comment_box
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_comment_italic
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_composer
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_duration
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_key
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_language
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

/**
 * The insertion rows of the editor's app bar: a button for every directive the parser understands, so that a format
 * whose vocabulary is only ever spelled out in its own documentation can be written without knowing any of it by
 * heart.
 *
 * There are two of them because the two halves are reached for at different moments: the first writes what the song
 * *is* (its title, its key, its tempo), which is what a file opens with and what is usually filled in once, and the
 * second writes the song itself (its chords, its sections, its comments), which is the rest of the work. Keeping
 * them apart means the row being scrolled through is always the short one.
 *
 * Each row scrolls horizontally rather than wrapping, because the alternative on a phone is four rows of buttons
 * over an editor that is two lines tall. The buttons take no focus ([Modifier.focusProperties]): a toolbar that
 * takes the caret out of the field closes the keyboard under itself on every tap, and the insertion the tap asked
 * for would land wherever the caret used to be.
 */
@Composable
internal fun EditorToolbar(
    modifier: Modifier = Modifier,
    textFieldState: TextFieldState,
    contentPadding: PaddingValues,
) {
    // What the song already says about itself follows the text rather than being read once, so that a directive
    // typed by hand takes its button out of reach exactly as one inserted from the toolbar does. The derived state
    // is what keeps that off the keystroke path: the set only changes when a directive is added or removed.
    val declaredMetadata by remember(textFieldState) {
        derivedStateOf { ChordProHeader.declaredMetadata(textFieldState.text.toString()) }
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
        )
        EditorToolbarRow(
            groups = contentInsertions(),
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
) {
    val layoutDirection = LocalLayoutDirection.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
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
                EditorToolbarButton(
                    label = insertion.label,
                    isEnabled = insertion.isEnabled(declaredMetadata),
                    onClick = { textFieldState.insert(insertion) },
                )
            }
        }
    }
}

/**
 * What goes into the song itself: what is written inside a line, every section the app has a name for, the two ways
 * of writing lines down, and the three kinds of comment.
 *
 * Tablature and grids are a group of their own rather than two more sections, because that is what they are: a
 * `{start_of_tab}` says how the next few lines are written and can open inside a solo or a verse without breaking
 * it in two, see [SectionType][com.pandulapeter.campfire.chordpro.model.SectionType].
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
        EditorInsertion.section(stringResource(Res.string.song_editor_section_tab), name = "tab"),
        EditorInsertion.section(stringResource(Res.string.song_editor_section_grid), name = "grid"),
    ),
    listOf(
        EditorInsertion.directive(stringResource(Res.string.song_editor_insert_comment), name = "comment"),
        EditorInsertion.directive(stringResource(Res.string.song_editor_insert_comment_italic), name = "comment_italic"),
        EditorInsertion.directive(stringResource(Res.string.song_editor_insert_comment_box), name = "comment_box"),
    ),
)

/**
 * What the song says about itself: every metadata directive the parser reads and the app then shows somewhere, in
 * the order the header of a file tends to list them.
 *
 * This is also the order they are written in, since none of these goes to the caret: they belong to the header and
 * are put there, see [insertIntoHeader]. A directive that can only be true once — a song has one title and came out
 * in one year — is offered until the file carries it and then no longer, which leaves the tags and the languages of
 * a song as the two that can be added again and again.
 *
 * `{new_song}` is left out, since it splits an imported file into several songs and the editor is only ever looking
 * at one of them. `{transpose}` is left out on purpose too: the renderer does honor it, but transposition here is
 * something the reader picks on the details screen or writes into the chords with the editor's own transpose action,
 * and a directive that silently shifts every chord away from what the file says is not worth offering a shortcut to.
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
        EditorInsertion.metadata(stringResource(Res.string.song_editor_insert_year), name = "year"),
        EditorInsertion.metadata(stringResource(Res.string.song_editor_insert_key), name = "key"),
        EditorInsertion.metadata(stringResource(Res.string.song_editor_insert_capo), name = "capo"),
        EditorInsertion.metadata(stringResource(Res.string.song_editor_insert_tempo), name = "tempo"),
        EditorInsertion.metadata(stringResource(Res.string.song_editor_insert_time), name = "time"),
        EditorInsertion.metadata(stringResource(Res.string.song_editor_insert_duration), name = "duration"),
        EditorInsertion.metadata(stringResource(Res.string.song_editor_insert_tag), name = "tag"),
        EditorInsertion.meta(stringResource(Res.string.song_editor_insert_language), key = "language"),
    ),
)

/**
 * One thing a toolbar button writes around the selection, or around the caret where there is none.
 *
 * @param isOwnLine True for everything in braces, since ChordPro only reads a directive as one when it is alone on
 *   its line; the two bracketed insertions belong inside a line of lyrics instead.
 * @param metadataName The kind of metadata this describes the song with, for the insertions that go into the header
 *   instead of to the caret, under the name `:chordpro` knows it by. Null for everything that is part of the song.
 */
private data class EditorInsertion(
    val label: String,
    val prefix: String,
    val suffix: String,
    val isOwnLine: Boolean,
    val metadataName: String? = null,
) {

    companion object {

        /** A `{start_of_x}` … `{end_of_x}` environment around what is selected, which becomes its first lines. */
        fun section(label: String, name: String) = EditorInsertion(
            label = label,
            prefix = "{start_of_$name}\n",
            suffix = "\n{end_of_$name}",
            isOwnLine = true,
        )

        /**
         * @param hasValue False for the directives that are a word on their own (`{chorus}`), which leave nothing
         *   to type after them and so have no closing half to put the caret in front of.
         */
        fun directive(label: String, name: String, hasValue: Boolean = true) = EditorInsertion(
            label = label,
            prefix = if (hasValue) "{$name: " else "{$name}",
            suffix = if (hasValue) "}" else "",
            isOwnLine = true,
        )

        /** A directive that says what the song *is*, and so belongs in its header rather than at the caret. */
        fun metadata(label: String, name: String) = EditorInsertion(
            label = label,
            prefix = "{$name: ",
            suffix = "}",
            isOwnLine = true,
            metadataName = name,
        )

        /**
         * A custom metadata item, which is how ChordPro carries what it has no directive of its own for — the
         * language of a song being the one Campfire reads, see `ChordProSyntax.language`. Part of the header like
         * every other [metadata] item, whatever it is spelled as.
         */
        fun meta(label: String, key: String) = EditorInsertion(
            label = label,
            prefix = "{meta: $key ",
            suffix = "}",
            isOwnLine = true,
            metadataName = key,
        )
    }
}

/**
 * Whether an insertion still has anything to write: a song is only ever in one key and only came out in one year,
 * so the button that says so is offered until the file says it, and then no longer. The ones a song may repeat
 * ([ChordProHeader.repeatableMetadata]) and everything that is part of the song rather than about it stay.
 */
private fun EditorInsertion.isEnabled(declaredMetadata: Set<String>) =
    metadataName == null || metadataName in ChordProHeader.repeatableMetadata || metadataName !in declaredMetadata

@Composable
private fun EditorToolbarButton(
    modifier: Modifier = Modifier,
    label: String,
    isEnabled: Boolean,
    onClick: () -> Unit,
) = CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
    Surface(
        modifier = modifier.focusProperties { canFocus = false }.alpha(if (isEnabled) 1f else DISABLED_ALPHA),
        onClick = onClick,
        enabled = isEnabled,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            modifier = Modifier.padding(horizontal = BUTTON_HORIZONTAL_PADDING, vertical = BUTTON_VERTICAL_PADDING),
            text = label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
    }
}

@Composable
private fun EditorToolbarDivider() = VerticalDivider(modifier = Modifier.height(DIVIDER_HEIGHT).padding(horizontal = TOOLBAR_GAP))

/** Writes [insertion] where it belongs: into the song's header when it describes the song, at the caret otherwise. */
private fun TextFieldState.insert(insertion: EditorInsertion) {
    val metadataName = insertion.metadataName
    if (metadataName == null) insertAtSelection(insertion) else insertIntoHeader(insertion, metadataName)
}

/**
 * Writes a directive that describes the song into its header, wherever the caret happens to be at the time.
 *
 * ChordPro reads a `{title}` as the title of the song from anywhere in the file, so the one thing the caret cannot
 * decide here is where the directive goes: a title written into the middle of a verse is valid, invisible in the
 * rendered song, and nowhere near the rest of what the file says about itself. `:chordpro` picks the line, keeping
 * the header in the order it lists metadata in and leaving one the user has arranged otherwise alone; the caret
 * follows it there, since a directive inserted from the toolbar is one the value is about to be typed into.
 */
private fun TextFieldState.insertIntoHeader(insertion: EditorInsertion, metadataName: String) = edit {
    val header = ChordProHeader.insert(
        text = originalText.toString(),
        name = metadataName,
        prefix = insertion.prefix,
        suffix = insertion.suffix,
    )
    insert(header.offset, header.text)
    selection = TextRange(header.caretOffset)
}

/**
 * Puts the two halves of [insertion] around the selection, or around the caret when there is none.
 *
 * A directive only counts as one when nothing shares its line, so the halves grow a line break of their own where
 * the text on either side would otherwise run into them.
 */
private fun TextFieldState.insertAtSelection(insertion: EditorInsertion) = edit {
    val start = minOf(selection.start, selection.end)
    val end = maxOf(selection.start, selection.end)
    val selected = originalText.substring(start, end)
    val opening = if (insertion.isOwnLine && start > 0 && originalText[start - 1] != '\n') "\n${insertion.prefix}" else insertion.prefix
    val closing = if (insertion.isOwnLine && end < originalText.length && originalText[end] != '\n') "${insertion.suffix}\n" else insertion.suffix
    delete(start, end)
    insert(start, opening + selected + closing)
    // With nothing selected the caret lands between the two halves, which is where the next thing typed belongs.
    selection = if (selected.isEmpty()) {
        TextRange(start + opening.length)
    } else {
        TextRange(start + opening.length, start + opening.length + selected.length)
    }
}

private const val DISABLED_ALPHA = 0.5f
private val TOOLBAR_PADDING = 16.dp
private val TOOLBAR_GAP = 4.dp
private val DIVIDER_HEIGHT = 24.dp
private val BUTTON_HORIZONTAL_PADDING = 12.dp
private val BUTTON_VERTICAL_PADDING = 8.dp
