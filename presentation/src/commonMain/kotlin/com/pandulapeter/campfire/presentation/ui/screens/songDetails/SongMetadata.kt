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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.model.ChordProLink
import com.pandulapeter.campfire.chordpro.model.ChordProMetadata
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_language
import com.pandulapeter.campfire.presentation.resources.ic_link
import com.pandulapeter.campfire.presentation.resources.song_details_album
import com.pandulapeter.campfire.presentation.resources.song_details_capo
import com.pandulapeter.campfire.presentation.resources.song_details_composer
import com.pandulapeter.campfire.presentation.resources.song_details_duration
import com.pandulapeter.campfire.presentation.resources.song_details_lyricist
import com.pandulapeter.campfire.presentation.resources.song_details_tempo
import com.pandulapeter.campfire.presentation.resources.song_details_time
import com.pandulapeter.campfire.presentation.resources.song_details_year
import com.pandulapeter.campfire.presentation.resources.songs_key
import com.pandulapeter.campfire.presentation.ui.components.TagFlowRow
import com.pandulapeter.campfire.presentation.ui.components.TagPill
import com.pandulapeter.campfire.presentation.ui.components.languageLabel
import com.pandulapeter.campfire.presentation.ui.components.textResource
import com.pandulapeter.campfire.presentation.ui.theme.LocalSecondAccentColor
import org.jetbrains.compose.resources.painterResource

/**
 * Descriptive metadata is a section of the song's own grid, so it shares the rows, columns, stepping and motion of
 * the lyrics rather than taking their full width away above them. Performance metadata stays outside the card.
 */
internal fun withMetadataSection(sections: List<RenderSection>, metadata: ChordProMetadata): List<RenderSection> {
    val hasRows = listOf(metadata.album, metadata.year, metadata.composer, metadata.lyricist, metadata.duration).any { !it.isNullOrBlank() }
    val hasChips = metadata.tags.isNotEmpty() || metadata.languages.isNotEmpty() || metadata.links.isNotEmpty()
    return if (hasRows || hasChips) listOf(RenderSection.Metadata(metadata)) + sections else sections
}

/** Only the metadata read while playing grows with the lyrics; album information keeps the interface's size. */
@Composable
internal fun SongPlayingMetadata(
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
    // The caller measures this header to place the grid. An empty header still has to report its zero height when
    // the last playing field is removed, rather than leaving the grid with the previous measurement.
    Box(modifier = modifier) {
        if (values.isNotEmpty()) {
            Text(
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 16.dp),
                text = values.joinToString("  •  "),
                style = MaterialTheme.typography.labelLarge.scaled(fontScale),
                color = LocalSecondAccentColor.current,
            )
        }
    }
}

/** A whole, uncuttable section; links are only followed outside the editor's preview. */
@Composable
internal fun SongMetadataCard(
    modifier: Modifier = Modifier,
    metadata: ChordProMetadata,
    onOpenLink: ((String) -> Unit)?,
) = Surface(
    modifier = modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surfaceContainer,
) {
    val rows = listOfNotNull(
        metadata.album?.takeIf { it.isNotBlank() }?.let { stringResource(Res.string.song_details_album) to it },
        metadata.year?.takeIf { it.isNotBlank() }?.let { stringResource(Res.string.song_details_year) to it },
        metadata.composer?.takeIf { it.isNotBlank() }?.let { stringResource(Res.string.song_details_composer) to it },
        metadata.lyricist?.takeIf { it.isNotBlank() }?.let { stringResource(Res.string.song_details_lyricist) to it },
        metadata.duration?.takeIf { it.isNotBlank() }?.let { stringResource(Res.string.song_details_duration) to it },
    )
    val hasChips = metadata.tags.isNotEmpty() || metadata.languages.isNotEmpty() || metadata.links.isNotEmpty()
    Column(
        modifier = Modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (rows.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEach { (label, value) -> MetadataRow(label = label, value = value) }
            }
        }
        if (rows.isNotEmpty() && hasChips) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (hasChips) SongMetadataChips(metadata = metadata, onOpenLink = onOpenLink)
    }
}

@Composable
private fun MetadataRow(
    label: String,
    value: String,
) = Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
) {
    Text(
        modifier = Modifier.weight(0.32f),
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        modifier = Modifier.weight(0.68f),
        text = value,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun SongMetadataChips(
    metadata: ChordProMetadata,
    onOpenLink: ((String) -> Unit)?,
) = Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    if (metadata.tags.isNotEmpty()) {
        TagFlowRow { metadata.tags.forEach { TagPill(text = it) } }
    }
    if (metadata.languages.isNotEmpty()) {
        TagFlowRow {
            metadata.languages.forEach { code ->
                TagPill(text = languageLabel(code), leadingIcon = painterResource(Res.drawable.ic_language))
            }
        }
    }
    if (metadata.links.isNotEmpty()) {
        TagFlowRow {
            metadata.links.forEach { link ->
                TagPill(
                    text = linkLabel(link),
                    onClick = onOpenLink?.let { { it(link.url) } },
                    leadingIcon = painterResource(Res.drawable.ic_link),
                )
            }
        }
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
