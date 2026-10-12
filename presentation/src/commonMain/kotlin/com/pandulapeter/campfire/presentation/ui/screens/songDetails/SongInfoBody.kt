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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.ChordProDuration
import com.pandulapeter.campfire.chordpro.model.ChordProLink
import com.pandulapeter.campfire.chordpro.model.ChordProMetadata
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_album
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
import com.pandulapeter.campfire.presentation.resources.song_details_links_add
import com.pandulapeter.campfire.presentation.resources.song_details_links_edit
import com.pandulapeter.campfire.presentation.resources.song_details_languages_add
import com.pandulapeter.campfire.presentation.resources.song_details_lyricist
import com.pandulapeter.campfire.presentation.resources.song_details_metadata_edit
import com.pandulapeter.campfire.presentation.resources.song_details_set_cover_art
import com.pandulapeter.campfire.presentation.resources.song_details_tags_manage
import com.pandulapeter.campfire.presentation.resources.song_details_tags_add
import com.pandulapeter.campfire.presentation.resources.song_details_year
import com.pandulapeter.campfire.presentation.ui.components.CoverArtImage
import com.pandulapeter.campfire.presentation.ui.components.TagFlowRow
import com.pandulapeter.campfire.presentation.ui.components.TagPill
import com.pandulapeter.campfire.presentation.ui.components.languageLabel
import com.pandulapeter.campfire.presentation.ui.components.sortedAlphabeticallyBy
import com.pandulapeter.campfire.presentation.ui.components.tagColor
import org.jetbrains.compose.resources.painterResource

/**
 * What the sheet and the editor preview's card of what the song is hold, as the tiles of one grouped list: the cover
 * with the album, the year and the people behind the song as small label-over-value tiles beside it, then the song's
 * defaults where [defaults] are given, then a group of chips for each of its tags and its languages, and one of buttons
 * for its links ([SongInfoLinkButtons]). Where [editing] is given every tile is what edits its group, an empty one offering its
 * editor rather than being left out, and the cover (or the empty place for one) is what changes it: a tile as large as
 * what it changes is found without a button next to every title.
 *
 * @param coverArtUrl The cover to draw at the start of the details, null where there is none or the cover art setting
 * is off.
 * @param defaults What the file declares for the four values the song is played by.
 * @param onOpenLink Follows a link; null leaves the links as plain chips, and with no buttons left in them the tiles
 * compact (see [SongInfoGroup]'s `isCompact`).
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
    val onSetCoverArt = editing?.onEditCoverArt
    val isCompact = onOpenLink == null
    val groups = songInfoGroupsOf(
        hasDetails = rows.isNotEmpty() || coverArtUrl != null || editing != null,
        hasDefaults = defaults != null && (defaults.hasValues || defaults.onEdit != null || defaults.overrides.isNotEmpty()),
        hasTags = metadata.tags.isNotEmpty() || editing != null,
        hasLanguages = metadata.languages.isNotEmpty() || editing != null,
        hasLinks = metadata.links.isNotEmpty() || editing != null,
    )
    fun shapeOf(group: SongInfoGroupKind) = songInfoTileShape(groups.indexOf(group), groups.size)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(SONG_INFO_TILE_GAP),
    ) {
        if (SongInfoGroupKind.DETAILS in groups) {
            SongInfoGroup(
                // Untitled: the cover and the labelled values say what they are, and the empty tile names its editor.
                title = null,
                count = 0,
                fontScale = fontScale,
                horizontalPadding = horizontalPadding,
                tileShape = shapeOf(SongInfoGroupKind.DETAILS),
                isCompact = isCompact,
                onClick = editing?.onEditMetadata,
                onClickLabel = stringResource(Res.string.song_details_metadata_edit),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SongInfoCover(
                        url = coverArtUrl,
                        fontScale = fontScale,
                        onClick = onSetCoverArt,
                    )
                    if (rows.isEmpty() && editing != null) {
                        SongInfoAction(
                            text = stringResource(Res.string.song_details_metadata_edit),
                            fontScale = fontScale,
                        )
                    } else {
                        MetadataTiles(
                            modifier = Modifier.weight(1f),
                            rows = rows,
                            fontScale = fontScale,
                        )
                    }
                }
            }
        }
        if (defaults != null && SongInfoGroupKind.DEFAULTS in groups) {
            SongDefaultsGroup(
                defaults = defaults,
                fontScale = fontScale,
                horizontalPadding = horizontalPadding,
                tileShape = shapeOf(SongInfoGroupKind.DEFAULTS),
                isCompact = isCompact,
            )
        }
        if (SongInfoGroupKind.TAGS in groups) {
            SongInfoChipGroup(
                title = stringResource(Res.string.song_details_info_tags),
                count = metadata.tags.size,
                fontScale = fontScale,
                horizontalPadding = horizontalPadding,
                tileShape = shapeOf(SongInfoGroupKind.TAGS),
                isCompact = isCompact,
                onEdit = editing?.onEditTags,
                editLabel = stringResource(Res.string.song_details_tags_manage),
                emptyLabel = stringResource(Res.string.song_details_tags_add),
            ) {
                val tags = remember(metadata.tags) { metadata.tags.sortedAlphabeticallyBy { it } }
                tags.forEach { tag ->
                    TagPill(
                        text = tag,
                        leadingIcon = painterResource(Res.drawable.ic_label),
                        leadingIconFill = tagColor(tag)?.color() ?: Color.Unspecified,
                        fontScale = fontScale,
                    )
                }
            }
        }
        if (SongInfoGroupKind.LANGUAGES in groups) {
            SongInfoChipGroup(
                title = stringResource(Res.string.song_details_info_languages),
                count = metadata.languages.size,
                fontScale = fontScale,
                horizontalPadding = horizontalPadding,
                tileShape = shapeOf(SongInfoGroupKind.LANGUAGES),
                isCompact = isCompact,
                onEdit = editing?.onEditLanguages,
                editLabel = stringResource(Res.string.song_details_languages_edit),
                emptyLabel = stringResource(Res.string.song_details_languages_add),
            ) {
                metadata.languages.map { languageLabel(it) }.sortedAlphabeticallyBy { it }.forEach { label ->
                    TagPill(text = label, leadingIcon = painterResource(Res.drawable.ic_language), fontScale = fontScale)
                }
            }
        }
        if (SongInfoGroupKind.LINKS in groups) {
            SongInfoChipGroup(
                title = stringResource(Res.string.song_details_info_links),
                count = metadata.links.size,
                fontScale = fontScale,
                horizontalPadding = horizontalPadding,
                tileShape = shapeOf(SongInfoGroupKind.LINKS),
                isCompact = isCompact,
                onEdit = editing?.onEditLinks,
                editLabel = stringResource(Res.string.song_details_links_edit),
                emptyLabel = stringResource(Res.string.song_details_links_add),
            ) {
                if (onOpenLink == null) {
                    // The links stay in the order the file gives them, as the buttons keep it.
                    metadata.links.forEach { link ->
                        TagPill(
                            text = linkLabel(link),
                            leadingIcon = painterResource(Res.drawable.ic_link),
                            fontScale = fontScale,
                        )
                    }
                } else {
                    SongInfoLinkButtons(links = metadata.links, fontScale = fontScale, onOpenLink = onOpenLink)
                }
            }
        }
    }
}

/**
 * The cover in front of the details. Where [onClick] is given it is the way to the cover search, and a song with no
 * cover gets an empty place for one with the album icon in it, which is how a first cover is set. Keyed on whether
 * there is a cover rather than on its address, so that a new cover crossfades in place of the old one, while a cover
 * being set or removed opens or closes its room.
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
    if (shownUrl != null || onClick != null) {
        val shape = MaterialTheme.shapes.medium
        Box(
            modifier = Modifier
                .padding(end = 16.dp * fontScale)
                .size(SONG_INFO_COVER_SIZE * fontScale)
                .clip(shape)
                .then(
                    if (onClick == null) Modifier else Modifier.clickable(
                        onClickLabel = stringResource(if (shownUrl == null) Res.string.song_details_set_cover_art else Res.string.song_details_change_cover_art),
                        role = Role.Button,
                        onClick = onClick,
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (shownUrl != null) {
                CoverArtImage(
                    modifier = Modifier.matchParentSize(),
                    url = shownUrl,
                    shape = shape,
                )
            } else {
                Surface(
                    modifier = Modifier.matchParentSize(),
                    shape = shape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_album),
                            contentDescription = null,
                        )
                    }
                }
            }
        }
    }
}

/**
 * A [SongInfoGroup] of chips, always titled in the plural: the title names a kind of thing the song can have any number
 * of, like a form's label, and one that changed its wording as the first chip arrived or the second one left would move
 * under the finger that did it. A group of several also says how many.
 */
@Composable
private fun SongInfoChipGroup(
    title: String,
    count: Int,
    fontScale: Float,
    horizontalPadding: Dp,
    tileShape: Shape,
    isCompact: Boolean,
    onEdit: (() -> Unit)?,
    editLabel: String,
    emptyLabel: String,
    chips: @Composable FlowRowScope.() -> Unit,
) = SongInfoGroup(
    title = title,
    count = count,
    fontScale = fontScale,
    horizontalPadding = horizontalPadding,
    tileShape = tileShape,
    isCompact = isCompact,
    onClick = onEdit,
    onClickLabel = editLabel,
    emptyLabel = emptyLabel,
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

private val SONG_INFO_TILE_GAP = 2.dp

/** As tall as two rows of detail tiles, which is what a song with a few details fills beside it. */
private val SONG_INFO_COVER_SIZE = 72.dp
