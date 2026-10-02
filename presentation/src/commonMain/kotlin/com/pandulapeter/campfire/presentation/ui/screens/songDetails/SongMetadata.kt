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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.model.ChordProLink
import com.pandulapeter.campfire.chordpro.model.ChordProMetadata
import com.pandulapeter.campfire.presentation.localization.pluralStringResource
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_add
import com.pandulapeter.campfire.presentation.resources.ic_edit
import com.pandulapeter.campfire.presentation.resources.ic_info
import com.pandulapeter.campfire.presentation.resources.ic_label
import com.pandulapeter.campfire.presentation.resources.ic_language
import com.pandulapeter.campfire.presentation.resources.ic_link
import com.pandulapeter.campfire.presentation.resources.song_details_album
import com.pandulapeter.campfire.presentation.resources.song_details_capo
import com.pandulapeter.campfire.presentation.resources.song_details_composer
import com.pandulapeter.campfire.presentation.resources.song_details_duration
import com.pandulapeter.campfire.presentation.resources.song_details_info_add
import com.pandulapeter.campfire.presentation.resources.song_details_info_details
import com.pandulapeter.campfire.presentation.resources.song_details_info_languages
import com.pandulapeter.campfire.presentation.resources.song_details_info_links
import com.pandulapeter.campfire.presentation.resources.song_details_info_tags
import com.pandulapeter.campfire.presentation.resources.song_details_languages_edit
import com.pandulapeter.campfire.presentation.resources.song_details_links_edit
import com.pandulapeter.campfire.presentation.resources.song_details_lyricist
import com.pandulapeter.campfire.presentation.resources.song_details_metadata_edit
import com.pandulapeter.campfire.presentation.resources.song_details_song_info
import com.pandulapeter.campfire.presentation.resources.song_details_tags_manage
import com.pandulapeter.campfire.presentation.resources.song_details_tempo
import com.pandulapeter.campfire.presentation.resources.song_details_time
import com.pandulapeter.campfire.presentation.resources.song_details_year
import com.pandulapeter.campfire.presentation.resources.songs_key
import com.pandulapeter.campfire.presentation.ui.components.TagFlowRow
import com.pandulapeter.campfire.presentation.ui.components.TagPill
import com.pandulapeter.campfire.presentation.ui.components.languageLabel
import com.pandulapeter.campfire.presentation.ui.components.sortedAlphabeticallyBy
import com.pandulapeter.campfire.presentation.ui.components.textResource
import com.pandulapeter.campfire.presentation.ui.theme.LocalSecondAccentColor
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.painterResource

/**
 * What the song says about itself is the first section of its own grid, so it shares the rows, columns, stepping and
 * motion of the lyrics rather than taking their full width away above them: the card of what the song is, where it is
 * shown there, and under it the line of how it is played.
 *
 * @param shouldShowChords False for lyrics-only mode, which leaves out the key, capo, tempo and time along with the
 * chords: they are what is played, and say nothing to somebody who is only singing.
 * @param isSongInfoShown Whether the card of what the song is belongs to the section: it does in the editor's preview,
 * which shows everything being typed, and not on the song details screen, whose app bar opens it as a sheet instead.
 * @param isSongInfoEditable Whether that card has edit buttons, which puts it there for a song that says nothing about
 * itself yet too: it is where its first tag or link is added.
 */
internal fun withMetadataSection(
    sections: List<RenderSection>,
    metadata: ChordProMetadata,
    shouldShowChords: Boolean,
    isSongInfoShown: Boolean,
    isSongInfoEditable: Boolean = false,
): List<RenderSection> {
    val shownMetadata = if (shouldShowChords) metadata else metadata.copy(key = null, capo = null, tempo = null, time = null)
    return if ((isSongInfoShown && (isSongInfoEditable || shownMetadata.hasSongInfo)) || shownMetadata.hasPlayingValues) {
        listOf(RenderSection.Metadata(shownMetadata)) + sections
    } else {
        sections
    }
}

private val ChordProMetadata.hasInfoRows
    get() = listOf(album, year, composer, lyricist, duration).any { !it.isNullOrBlank() }

/** Whether the song says anything about itself beyond how it is played, which is what the card and the sheet hold. */
internal val ChordProMetadata.hasSongInfo
    get() = hasInfoRows || tags.isNotEmpty() || languages.isNotEmpty() || links.isNotEmpty()

private val ChordProMetadata.hasPlayingValues
    get() = !key.isNullOrBlank() || (capo ?: 0) != 0 || !tempo.isNullOrBlank() || !time.isNullOrBlank()

/**
 * The first section of the song: the card of what the song is, where [isSongInfoShown] and the song says anything for
 * it or [songInfoEditing] lets it be given something, then the key, capo, tempo and time, which are always on the page,
 * since they are what is played. A whole, uncuttable section.
 *
 * @param titleStyle The style of the section titles of the lyrics, which the card's own title follows as the text is
 * scaled; what the card holds scales with [fontScale] the way the lyrics do.
 */
@Composable
internal fun SongMetadataSection(
    modifier: Modifier = Modifier,
    metadata: ChordProMetadata,
    isSongInfoShown: Boolean,
    songInfoEditing: SongInfoEditing?,
    titleStyle: TextStyle,
    fontScale: Float,
) = Column(modifier = modifier) {
    val hasSongInfoCard = isSongInfoShown && (songInfoEditing != null || metadata.hasSongInfo)
    if (hasSongInfoCard) {
        SongInfoCard(
            metadata = metadata,
            editing = songInfoEditing,
            titleStyle = titleStyle,
            fontScale = fontScale,
        )
    }
    SongPlayingMetadata(
        modifier = Modifier.padding(top = if (hasSongInfoCard) 12.dp else 0.dp),
        metadata = metadata,
        fontScale = fontScale,
    )
}

@Composable
private fun SongPlayingMetadata(
    modifier: Modifier = Modifier,
    metadata: ChordProMetadata,
    fontScale: Float,
) {
    val values = listOfNotNull(
        metadata.key?.takeIf { it.isNotBlank() }?.let { textResource(Res.string.songs_key, it) },
        metadata.capo?.takeIf { it != 0 }?.let { stringResource(Res.string.song_details_capo, it) },
        metadata.tempo?.takeIf { it.isNotBlank() }?.let { textResource(Res.string.song_details_tempo, it) },
        metadata.time?.takeIf { it.isNotBlank() }?.let { textResource(Res.string.song_details_time, it) },
    )
    if (values.isNotEmpty()) {
        Text(
            modifier = modifier.padding(horizontal = 12.dp),
            text = values.joinToString("  •  "),
            style = MaterialTheme.typography.labelLarge.scaled(fontScale),
            color = LocalSecondAccentColor.current,
        )
    }
}

/**
 * The card of what the song is in the editor's preview, as wide as its column whatever it holds, so that its edit buttons
 * stay where they are as the groups fill up and empty. Its links are not followed, since the preview is there to show
 * what is being typed.
 */
@Composable
private fun SongInfoCard(
    metadata: ChordProMetadata,
    editing: SongInfoEditing?,
    titleStyle: TextStyle,
    fontScale: Float,
) = Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surfaceContainer,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                modifier = Modifier.size(FOLD_CHEVRON_SIZE * fontScale),
                painter = painterResource(Res.drawable.ic_info),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                modifier = Modifier.weight(1f).padding(start = 8.dp),
                text = stringResource(Res.string.song_details_song_info),
                style = titleStyle,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        SongInfoBody(
            modifier = Modifier.padding(bottom = 12.dp),
            metadata = metadata,
            fontScale = fontScale,
            horizontalPadding = 12.dp,
            editing = editing,
            onOpenLink = null,
        )
    }
}

/** What each group of [SongInfoBody] is edited with, from the buttons next to their titles, see [rememberSongInfoEditing]. */
@Immutable
internal class SongInfoEditing(
    val onEditMetadata: () -> Unit,
    val onEditTags: () -> Unit,
    val onEditLanguages: () -> Unit,
    val onEditLinks: () -> Unit,
)

/**
 * What the card and the sheet of what the song is hold: the album, the year and the people behind the song as small
 * label-over-value tiles, then a group of chips for each of its tags, its languages and its links, each under a title
 * that ends in a button that edits it where [editing] is given. A group the song has nothing for is not shown as an
 * empty one: with [editing] it is one outlined chip of a last group that adds it instead, which is what a song nobody
 * has said anything about yet comes down to, and where it gets its first tag or link from the song details screen.
 *
 * @param horizontalPadding Given to each child rather than taken off the whole, so that the edit buttons reach closer to
 * the edge than the text does.
 * @param onOpenLink Follows a link; null leaves the links as plain chips.
 */
@Composable
internal fun SongInfoBody(
    modifier: Modifier = Modifier,
    metadata: ChordProMetadata,
    fontScale: Float = 1f,
    horizontalPadding: Dp,
    editing: SongInfoEditing?,
    onOpenLink: ((String) -> Unit)?,
) {
    val rows = listOfNotNull(
        metadata.album?.takeIf { it.isNotBlank() }?.let { stringResource(Res.string.song_details_album) to it },
        metadata.year?.takeIf { it.isNotBlank() }?.let { stringResource(Res.string.song_details_year) to it },
        metadata.composer?.takeIf { it.isNotBlank() }?.let { stringResource(Res.string.song_details_composer) to it },
        metadata.lyricist?.takeIf { it.isNotBlank() }?.let { stringResource(Res.string.song_details_lyricist) to it },
        metadata.duration?.takeIf { it.isNotBlank() }?.let { stringResource(Res.string.song_details_duration) to it },
    )
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(SONG_INFO_GROUP_GAP * fontScale),
    ) {
        if (rows.isNotEmpty()) {
            SongInfoGroup(
                title = stringResource(Res.string.song_details_info_details),
                count = 0,
                fontScale = fontScale,
                horizontalPadding = horizontalPadding,
                editLabel = stringResource(Res.string.song_details_metadata_edit),
                onEdit = editing?.onEditMetadata,
            ) {
                MetadataTiles(rows = rows, fontScale = fontScale)
            }
        }
        if (metadata.tags.isNotEmpty()) {
            SongInfoChipGroup(
                title = Res.plurals.song_details_info_tags,
                count = metadata.tags.size,
                fontScale = fontScale,
                horizontalPadding = horizontalPadding,
                editLabel = stringResource(Res.string.song_details_tags_manage),
                onEdit = editing?.onEditTags,
            ) {
                val tags = remember(metadata.tags) { metadata.tags.sortedAlphabeticallyBy { it } }
                tags.forEach { TagPill(text = it, leadingIcon = painterResource(Res.drawable.ic_label), fontScale = fontScale) }
            }
        }
        if (metadata.languages.isNotEmpty()) {
            SongInfoChipGroup(
                title = Res.plurals.song_details_info_languages,
                count = metadata.languages.size,
                fontScale = fontScale,
                horizontalPadding = horizontalPadding,
                editLabel = stringResource(Res.string.song_details_languages_edit),
                onEdit = editing?.onEditLanguages,
            ) {
                metadata.languages.map { languageLabel(it) }.sortedAlphabeticallyBy { it }.forEach { label ->
                    TagPill(text = label, leadingIcon = painterResource(Res.drawable.ic_language), fontScale = fontScale)
                }
            }
        }
        if (metadata.links.isNotEmpty()) {
            SongInfoChipGroup(
                title = Res.plurals.song_details_info_links,
                count = metadata.links.size,
                fontScale = fontScale,
                horizontalPadding = horizontalPadding,
                editLabel = stringResource(Res.string.song_details_links_edit),
                onEdit = editing?.onEditLinks,
            ) {
                // Unlike the tags and the languages, the links stay in the order the file and the dialog that
                // edits them give them, since that order is one the user chose.
                metadata.links.forEach { link ->
                    TagPill(
                        text = linkLabel(link),
                        onClick = onOpenLink?.let { { it(link.url) } },
                        leadingIcon = painterResource(Res.drawable.ic_link),
                        fontScale = fontScale,
                    )
                }
            }
        }
        if (editing != null) {
            val tag = pluralStringResource(Res.plurals.song_details_info_tags, 1)
            val language = pluralStringResource(Res.plurals.song_details_info_languages, 1)
            val link = pluralStringResource(Res.plurals.song_details_info_links, 1)
            val additions = listOfNotNull(
                if (rows.isEmpty()) stringResource(Res.string.song_details_info_details) to editing.onEditMetadata else null,
                if (metadata.tags.isEmpty()) tag to editing.onEditTags else null,
                if (metadata.languages.isEmpty()) language to editing.onEditLanguages else null,
                if (metadata.links.isEmpty()) link to editing.onEditLinks else null,
            )
            if (additions.isNotEmpty()) {
                SongInfoGroup(
                    title = stringResource(Res.string.song_details_info_add),
                    count = 0,
                    fontScale = fontScale,
                    horizontalPadding = horizontalPadding,
                    editLabel = null,
                    onEdit = null,
                ) {
                    TagFlowRow {
                        additions.forEach { (label, onAdd) ->
                            TagPill(
                                text = label,
                                isAction = true,
                                onClick = onAdd,
                                leadingIcon = painterResource(Res.drawable.ic_add),
                                fontScale = fontScale,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The album, the year and the people behind the song, each a label over its value, flowing side by side where they fit:
 * as a table of two columns they left most of a wide sheet empty between a short label and a short value.
 */
@Composable
private fun MetadataTiles(
    modifier: Modifier = Modifier,
    rows: List<Pair<String, String>>,
    fontScale: Float,
) = FlowRow(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(24.dp * fontScale),
    verticalArrangement = Arrangement.spacedBy(12.dp * fontScale),
) {
    rows.forEach { (label, value) ->
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.scaled(fontScale),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge.scaled(fontScale),
            )
        }
    }
}

/**
 * A [SongInfoGroup] of chips, titled in the singular or the plural. A group of several also says how many; a single
 * chip, which is what most songs have for a language, is named by the title alone.
 */
@Composable
private fun SongInfoChipGroup(
    title: PluralStringResource,
    count: Int,
    fontScale: Float,
    horizontalPadding: Dp,
    editLabel: String,
    onEdit: (() -> Unit)?,
    chips: @Composable FlowRowScope.() -> Unit,
) = SongInfoGroup(
    title = pluralStringResource(title, count),
    count = count,
    fontScale = fontScale,
    horizontalPadding = horizontalPadding,
    editLabel = editLabel,
    onEdit = onEdit,
) {
    TagFlowRow(content = chips)
}

/**
 * One group of [SongInfoBody] under its title, which ends in a button that edits the group where [onEdit] is given.
 * [count] is said next to the title where it is more than one.
 *
 * The title row is as tall with the button as without it, so that the groups are evenly spaced in performance mode too:
 * the button keeps its whole touch target, which reaches past the row into the gaps around it rather than pushing the
 * title away from what it names.
 */
@Composable
private fun SongInfoGroup(
    title: String,
    count: Int,
    fontScale: Float,
    horizontalPadding: Dp,
    editLabel: String?,
    onEdit: (() -> Unit)?,
    content: @Composable () -> Unit,
) = Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(4.dp * fontScale),
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SONG_INFO_TITLE_HEIGHT * fontScale)
            .padding(start = horizontalPadding, end = if (onEdit == null) horizontalPadding else EDIT_BUTTON_END_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.scaled(fontScale),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (count > 1) {
            Text(
                modifier = Modifier.padding(start = 8.dp),
                text = count.toString(),
                style = MaterialTheme.typography.titleSmall.scaled(fontScale),
                color = MaterialTheme.colorScheme.outline,
            )
        }
        if (onEdit != null) {
            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                modifier = Modifier.requiredSize(EDIT_BUTTON_SIZE),
                onClick = onEdit,
            ) {
                Icon(
                    modifier = Modifier.size(EDIT_ICON_SIZE * fontScale),
                    painter = painterResource(Res.drawable.ic_edit),
                    contentDescription = editLabel,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
    Box(modifier = Modifier.padding(horizontal = horizontalPadding)) {
        content()
    }
}

/** A supplied name takes precedence; unnamed links keep the host label used for plain URL directives. */
internal fun linkLabel(link: ChordProLink): String = link.name?.takeIf { it.isNotBlank() } ?: linkLabel(link.url)

/**
 * The host without `www.`, or the address itself if there is no host. A backslash ends the authority just as it does
 * in a browser, so `https://evil.example\\@youtube.com` cannot be labelled as a site the address does not open.
 */
internal fun linkLabel(url: String): String = url
    .substringAfter("://")
    .takeWhile { it != '/' && it != '\\' && it != '?' && it != '#' }
    .substringAfterLast('@')
    .substringBefore(':')
    .lowercase()
    .removePrefix("www.")
    .ifEmpty { url }

private val EDIT_ICON_SIZE = 18.dp
private val EDIT_BUTTON_SIZE = 48.dp
private val EDIT_BUTTON_END_PADDING = 4.dp
private val SONG_INFO_TITLE_HEIGHT = 32.dp
private val SONG_INFO_GROUP_GAP = 16.dp
