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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.ChordProDuration
import com.pandulapeter.campfire.chordpro.model.ChordProLink
import com.pandulapeter.campfire.chordpro.model.ChordProMetadata
import com.pandulapeter.campfire.presentation.localization.pluralStringResource
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_edit
import com.pandulapeter.campfire.presentation.resources.ic_label
import com.pandulapeter.campfire.presentation.resources.ic_language
import com.pandulapeter.campfire.presentation.resources.ic_link
import com.pandulapeter.campfire.presentation.resources.song_details_album
import com.pandulapeter.campfire.presentation.resources.song_details_change_cover_art
import com.pandulapeter.campfire.presentation.resources.song_details_composer
import com.pandulapeter.campfire.presentation.resources.song_details_duration
import com.pandulapeter.campfire.presentation.resources.song_details_info_languages
import com.pandulapeter.campfire.presentation.resources.song_details_info_links
import com.pandulapeter.campfire.presentation.resources.song_details_info_tags
import com.pandulapeter.campfire.presentation.resources.song_details_languages_edit
import com.pandulapeter.campfire.presentation.resources.song_details_links_edit
import com.pandulapeter.campfire.presentation.resources.song_details_lyricist
import com.pandulapeter.campfire.presentation.resources.song_details_tags_manage
import com.pandulapeter.campfire.presentation.resources.song_details_year
import com.pandulapeter.campfire.presentation.ui.components.CoverArtImage
import com.pandulapeter.campfire.presentation.ui.components.TagFlowRow
import com.pandulapeter.campfire.presentation.ui.components.TagPill
import com.pandulapeter.campfire.presentation.ui.components.languageLabel
import com.pandulapeter.campfire.presentation.ui.components.sortedAlphabeticallyBy
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.painterResource

/**
 * What the card and the sheet of what the song is hold: the cover, then the album, the year and the people behind the
 * song as small label-over-value tiles beside it, untitled since the card or the sheet around them already says what
 * they are, then the song's defaults where [defaults] are given, then a group of chips for each of its tags, its
 * languages and its links. Where [editing] is given, each category ends with a Manage chip, or an Add chip with a plus
 * icon when empty. Details are edited from the card or sheet header.
 *
 * @param coverArtUrl The cover to draw at the start of the details, null where there is none or the cover art setting
 * is off. It opens the cover search where [editing] has a way to it, the same as the header's cover button.
 * @param defaults What the file declares for the four values the song is played by, which only the sheet shows: the
 * editor's preview card sits over the line of text that already reads them.
 * @param onOpenLink Follows a link; null leaves the links as plain chips.
 */
@Composable
internal fun SongInfoBody(
    modifier: Modifier = Modifier,
    metadata: ChordProMetadata,
    coverArtUrl: String?,
    fontScale: Float = 1f,
    horizontalPadding: Dp,
    editing: SongInfoEditing?,
    defaults: SongDefaults? = null,
    onOpenLink: ((String) -> Unit)?,
) {
    val rows = listOfNotNull(
        metadata.album?.takeIf { it.isNotBlank() }?.let { stringResource(Res.string.song_details_album) to it },
        metadata.year?.takeIf { it.isNotBlank() }?.let { stringResource(Res.string.song_details_year) to it },
        metadata.composer?.takeIf { it.isNotBlank() }?.let { stringResource(Res.string.song_details_composer) to it },
        metadata.lyricist?.takeIf { it.isNotBlank() }?.let { stringResource(Res.string.song_details_lyricist) to it },
        ChordProDuration.parse(metadata.duration)?.let { stringResource(Res.string.song_details_duration) to ChordProDuration.format(it) },
    )
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(SONG_INFO_GROUP_GAP * fontScale),
    ) {
        if (rows.isNotEmpty() || coverArtUrl != null) {
            SongInfoGroup(
                title = null,
                count = 0,
                fontScale = fontScale,
                horizontalPadding = horizontalPadding,
            ) {
                Row {
                    SongInfoCover(
                        url = coverArtUrl,
                        fontScale = fontScale,
                        onClick = editing?.onEditCoverArt,
                    )
                    MetadataTiles(
                        modifier = Modifier.weight(1f),
                        rows = rows,
                        fontScale = fontScale,
                    )
                }
            }
        }
        if (defaults != null && (defaults.hasValues || defaults.onEdit != null || defaults.overrides.isNotEmpty())) {
            SongDefaultsGroup(
                defaults = defaults,
                fontScale = fontScale,
                horizontalPadding = horizontalPadding,
            )
        }
        if (metadata.tags.isNotEmpty() || editing != null) {
            SongInfoChipGroup(
                title = Res.plurals.song_details_info_tags,
                count = metadata.tags.size,
                fontScale = fontScale,
                horizontalPadding = horizontalPadding,
                onEdit = editing?.onEditTags,
                editLabel = stringResource(Res.string.song_details_tags_manage),
            ) {
                val tags = remember(metadata.tags) { metadata.tags.sortedAlphabeticallyBy { it } }
                tags.forEach { TagPill(text = it, leadingIcon = painterResource(Res.drawable.ic_label), fontScale = fontScale) }
            }
        }
        if (metadata.languages.isNotEmpty() || editing != null) {
            SongInfoChipGroup(
                title = Res.plurals.song_details_info_languages,
                count = metadata.languages.size,
                fontScale = fontScale,
                horizontalPadding = horizontalPadding,
                onEdit = editing?.onEditLanguages,
                editLabel = stringResource(Res.string.song_details_languages_edit),
            ) {
                metadata.languages.map { languageLabel(it) }.sortedAlphabeticallyBy { it }.forEach { label ->
                    TagPill(text = label, leadingIcon = painterResource(Res.drawable.ic_language), fontScale = fontScale)
                }
            }
        }
        if (metadata.links.isNotEmpty() || editing != null) {
            SongInfoChipGroup(
                title = Res.plurals.song_details_info_links,
                count = metadata.links.size,
                fontScale = fontScale,
                horizontalPadding = horizontalPadding,
                onEdit = editing?.onEditLinks,
                editLabel = stringResource(Res.string.song_details_links_edit),
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
    }
}

/**
 * The cover in front of the details, a shortcut to the cover search where [onClick] is given, with a small pencil on it
 * then, since the sheet's header offers its cover button only to a song with no cover yet. Keyed on whether there is
 * a cover rather than on its address, so that a new cover crossfades in place of the old one, while a cover being set or
 * removed opens or closes its room.
 */
@Composable
private fun SongInfoCover(
    url: String?,
    fontScale: Float,
    onClick: (() -> Unit)?,
) = AnimatedContent(
    targetState = url,
    transitionSpec = { fadeIn() togetherWith fadeOut() },
    contentKey = { it != null },
) { shownUrl ->
    if (shownUrl != null) {
        val shape = MaterialTheme.shapes.medium
        Box(
            modifier = Modifier
                .padding(end = 16.dp * fontScale)
                .size(SONG_INFO_COVER_SIZE * fontScale)
                .clip(shape)
                .then(
                    if (onClick == null) Modifier else Modifier.clickable(
                        onClickLabel = stringResource(Res.string.song_details_change_cover_art),
                        role = Role.Button,
                        onClick = onClick,
                    ),
                ),
        ) {
            CoverArtImage(
                modifier = Modifier.matchParentSize(),
                url = shownUrl,
                shape = shape,
            )
            if (onClick != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(COVER_EDIT_BADGE_INSET * fontScale)
                        .size(COVER_EDIT_BADGE_SIZE * fontScale),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Icon(
                        modifier = Modifier.padding(COVER_EDIT_BADGE_INSET * fontScale),
                        painter = painterResource(Res.drawable.ic_edit),
                        contentDescription = null,
                    )
                }
            }
        }
    }
}

/**
 * A [SongInfoGroup] of chips, titled in the singular or the plural. A group of several also says how many; a single
 * chip, which is what most songs have for a language, is named by the title alone. A pencil next to the title edits the
 * group; an empty one offers a plus instead, and is the title row alone.
 */
@Composable
private fun SongInfoChipGroup(
    title: PluralStringResource,
    count: Int,
    fontScale: Float,
    horizontalPadding: Dp,
    onEdit: (() -> Unit)?,
    editLabel: String,
    chips: @Composable FlowRowScope.() -> Unit,
) = SongInfoGroup(
    title = pluralStringResource(title, count),
    count = count,
    fontScale = fontScale,
    horizontalPadding = horizontalPadding,
    action = onEdit?.let { onClick ->
        {
            SongInfoAction(
                hasValues = count > 0,
                contentDescription = editLabel,
                fontScale = fontScale,
                onClick = onClick,
            )
        }
    },
    isContentShown = count > 0,
) {
    TagFlowRow(content = chips)
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

private val SONG_INFO_GROUP_GAP = 16.dp

/** As tall as two rows of detail tiles, which is what a song with a few details fills beside it. */
private val SONG_INFO_COVER_SIZE = 96.dp
private val COVER_EDIT_BADGE_SIZE = 24.dp
private val COVER_EDIT_BADGE_INSET = 4.dp
