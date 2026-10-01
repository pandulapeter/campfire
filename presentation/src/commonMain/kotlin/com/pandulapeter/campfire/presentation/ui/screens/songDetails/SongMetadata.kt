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

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateBounds
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.model.ChordProLink
import com.pandulapeter.campfire.chordpro.model.ChordProMetadata
import com.pandulapeter.campfire.data.model.domain.UserPreferences.SongInfoSection
import com.pandulapeter.campfire.presentation.localization.pluralStringResource
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_info
import com.pandulapeter.campfire.presentation.resources.ic_label
import com.pandulapeter.campfire.presentation.resources.ic_language
import com.pandulapeter.campfire.presentation.resources.ic_link
import com.pandulapeter.campfire.presentation.resources.song_details_album
import com.pandulapeter.campfire.presentation.resources.song_details_capo
import com.pandulapeter.campfire.presentation.resources.song_details_composer
import com.pandulapeter.campfire.presentation.resources.song_details_duration
import com.pandulapeter.campfire.presentation.resources.song_details_info_languages
import com.pandulapeter.campfire.presentation.resources.song_details_info_links
import com.pandulapeter.campfire.presentation.resources.song_details_info_tags
import com.pandulapeter.campfire.presentation.resources.song_details_lyricist
import com.pandulapeter.campfire.presentation.resources.song_details_song_info
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
 * motion of the lyrics rather than taking their full width away above them: the card of what the song is, and under
 * it the line of how it is played.
 *
 * @param shouldShowChords False for lyrics-only mode, which leaves out the key, capo, tempo and time along with the
 * chords: they are what is played, and say nothing to somebody who is only singing.
 */
internal fun withMetadataSection(
    sections: List<RenderSection>,
    metadata: ChordProMetadata,
    shouldShowChords: Boolean,
): List<RenderSection> {
    val shownMetadata = if (shouldShowChords) metadata else metadata.copy(key = null, capo = null, tempo = null, time = null)
    return if (shownMetadata.hasInfoCard || shownMetadata.hasPlayingValues) {
        listOf(RenderSection.Metadata(shownMetadata)) + sections
    } else {
        sections
    }
}

private val ChordProMetadata.hasInfoRows
    get() = listOf(album, year, composer, lyricist, duration).any { !it.isNullOrBlank() }

private val ChordProMetadata.hasInfoCard
    get() = hasInfoRows || tags.isNotEmpty() || languages.isNotEmpty() || links.isNotEmpty()

private val ChordProMetadata.hasPlayingValues
    get() = !key.isNullOrBlank() || (capo ?: 0) != 0 || !tempo.isNullOrBlank() || !time.isNullOrBlank()

/**
 * How the info card folds, handed down by the song details screen and null where nothing folds (the editor's preview):
 * the whole card and each group in it, every one a single preference for every song, since whoever folds one has no use
 * for it on any song.
 */
@Immutable
internal class SongInfoFolding(
    val isCardFolded: Boolean,
    val foldedSections: Set<SongInfoSection>,
    val onCardToggled: () -> Unit,
    val onSectionToggled: (SongInfoSection) -> Unit,
)

/**
 * The first section of the song: the info card where the song says anything for it, then the key, capo, tempo and time,
 * which are never folded away, since they are what is played. A whole, uncuttable section; links are only followed
 * outside the editor's preview.
 *
 * @param titleStyle The style of the section titles of the lyrics, which the card's own title follows as the text is
 * scaled; what the card holds scales with [fontScale] the way the lyrics do.
 * @param lookaheadScope Where the card and its groups spring to their new size as something in them is folded, the way
 * the sections of the lyrics spring to their new place; null where the sections do not spring either.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun SongInfo(
    modifier: Modifier = Modifier,
    metadata: ChordProMetadata,
    folding: SongInfoFolding?,
    lookaheadScope: LookaheadScope?,
    titleStyle: TextStyle,
    fontScale: Float,
    onOpenLink: ((String) -> Unit)?,
) = Column(modifier = modifier) {
    val hasInfoCard = metadata.hasInfoCard
    if (hasInfoCard) {
        SongInfoCard(
            metadata = metadata,
            folding = folding,
            lookaheadScope = lookaheadScope,
            titleStyle = titleStyle,
            fontScale = fontScale,
            onOpenLink = onOpenLink,
        )
    }
    SongPlayingMetadata(
        modifier = Modifier.padding(top = if (hasInfoCard) 12.dp else 0.dp),
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
 * The card of what the song is. Its title row folds it down to itself, and each group of chips in it folds on its own, for a song filed under so many tags that they would push everything after them out of
 * sight. Like a chorus card it is as wide as what it holds, up to its column, so folded it narrows to its title.
 *
 * The card and its groups spring to their new size and place in [lookaheadScope] rather than being measured on the way
 * there: the column layout decides where every section goes from their intrinsic heights, which `animateBounds` answers
 * with the size it is going to, while a height animated in the layout itself would be measured halfway there. What is
 * unfolded also fades in the way a folded run of tablature does; only the folds made while the card is on screen, so
 * that a page opening onto an unfolded card does not fade it in.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SongInfoCard(
    metadata: ChordProMetadata,
    folding: SongInfoFolding?,
    lookaheadScope: LookaheadScope?,
    titleStyle: TextStyle,
    fontScale: Float,
    onOpenLink: ((String) -> Unit)?,
) = Surface(
    modifier = Modifier.springingBounds(lookaheadScope).width(IntrinsicSize.Max),
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surfaceContainer,
) {
    var isCardToggled by remember { mutableStateOf(false) }
    var toggledSections by remember { mutableStateOf(emptySet<SongInfoSection>()) }
    val isFolded = folding?.isCardFolded == true
    Column {
        SongInfoTitleRow(
            isFolded = isFolded,
            style = titleStyle,
            iconSize = FOLD_CHEVRON_SIZE * fontScale,
            onToggled = folding?.let {
                {
                    isCardToggled = true
                    it.onCardToggled()
                }
            },
        )
        // The body leaves the composition while the card is folded, so that unfolding it composes it afresh to fade in.
        if (!isFolded) {
            SongInfoBody(
                modifier = Modifier.fadingIn(isFadingIn = isCardToggled),
                metadata = metadata,
                foldedSections = folding?.foldedSections.orEmpty(),
                toggledSections = toggledSections,
                lookaheadScope = lookaheadScope,
                onSectionToggled = folding?.let {
                    { section ->
                        toggledSections += section
                        it.onSectionToggled(section)
                    }
                },
                fontScale = fontScale,
                onOpenLink = onOpenLink,
            )
        }
    }
}

/** The card's title, pressed as a whole where it folds the card, with the chevron at its far end like a chorus card's. */
@Composable
private fun SongInfoTitleRow(
    isFolded: Boolean,
    style: TextStyle,
    iconSize: Dp,
    onToggled: (() -> Unit)?,
) = Row(
    modifier = Modifier
        .fillMaxWidth()
        .then(if (onToggled == null) Modifier else Modifier.clickable(role = Role.Button, onClick = onToggled))
        .padding(12.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Icon(
        modifier = Modifier.size(iconSize),
        painter = painterResource(Res.drawable.ic_info),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
    )
    Text(
        modifier = Modifier.weight(1f).padding(start = 8.dp),
        text = stringResource(Res.string.song_details_song_info),
        style = style,
        color = MaterialTheme.colorScheme.primary,
    )
    if (onToggled != null) {
        FoldChevron(
            modifier = Modifier.padding(start = FOLD_CHEVRON_GAP).size(iconSize),
            kind = null,
            isExpanded = !isFolded,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun SongInfoBody(
    modifier: Modifier = Modifier,
    metadata: ChordProMetadata,
    foldedSections: Set<SongInfoSection>,
    toggledSections: Set<SongInfoSection>,
    lookaheadScope: LookaheadScope?,
    onSectionToggled: ((SongInfoSection) -> Unit)?,
    fontScale: Float,
    onOpenLink: ((String) -> Unit)?,
) {
    val rows = listOfNotNull(
        metadata.album?.takeIf { it.isNotBlank() }?.let { stringResource(Res.string.song_details_album) to it },
        metadata.year?.takeIf { it.isNotBlank() }?.let { stringResource(Res.string.song_details_year) to it },
        metadata.composer?.takeIf { it.isNotBlank() }?.let { stringResource(Res.string.song_details_composer) to it },
        metadata.lyricist?.takeIf { it.isNotBlank() }?.let { stringResource(Res.string.song_details_lyricist) to it },
        metadata.duration?.takeIf { it.isNotBlank() }?.let { stringResource(Res.string.song_details_duration) to it },
    )
    val hasChips = metadata.tags.isNotEmpty() || metadata.languages.isNotEmpty() || metadata.links.isNotEmpty()
    // Only the bottom is padded here, the sides by each child, so that a group's fold row is pressed across the whole
    // width of the card rather than inside its margins.
    Column(
        modifier = modifier.padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (rows.isNotEmpty()) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rows.forEach { (label, value) -> MetadataRow(label = label, value = value, fontScale = fontScale) }
            }
        }
        if (rows.isNotEmpty() && hasChips) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
        if (hasChips) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (metadata.tags.isNotEmpty()) {
                    SongInfoGroup(
                        section = SongInfoSection.TAGS,
                        title = Res.plurals.song_details_info_tags,
                        count = metadata.tags.size,
                        foldedSections = foldedSections,
                        toggledSections = toggledSections,
                        lookaheadScope = lookaheadScope,
                        onSectionToggled = onSectionToggled,
                        fontScale = fontScale,
                    ) {
                        val tags = remember(metadata.tags) { metadata.tags.sortedAlphabeticallyBy { it } }
                        tags.forEach { TagPill(text = it, leadingIcon = painterResource(Res.drawable.ic_label), fontScale = fontScale) }
                    }
                }
                if (metadata.languages.isNotEmpty()) {
                    SongInfoGroup(
                        section = SongInfoSection.LANGUAGES,
                        title = Res.plurals.song_details_info_languages,
                        count = metadata.languages.size,
                        foldedSections = foldedSections,
                        toggledSections = toggledSections,
                        lookaheadScope = lookaheadScope,
                        onSectionToggled = onSectionToggled,
                        fontScale = fontScale,
                    ) {
                        metadata.languages.map { languageLabel(it) }.sortedAlphabeticallyBy { it }.forEach { label ->
                            TagPill(text = label, leadingIcon = painterResource(Res.drawable.ic_language), fontScale = fontScale)
                        }
                    }
                }
                if (metadata.links.isNotEmpty()) {
                    SongInfoGroup(
                        section = SongInfoSection.LINKS,
                        title = Res.plurals.song_details_info_links,
                        count = metadata.links.size,
                        foldedSections = foldedSections,
                        toggledSections = toggledSections,
                        lookaheadScope = lookaheadScope,
                        onSectionToggled = onSectionToggled,
                        fontScale = fontScale,
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
    }
}

@Composable
private fun MetadataRow(
    label: String,
    value: String,
    fontScale: Float,
) = Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
) {
    Text(
        modifier = Modifier.weight(0.32f),
        text = label,
        style = MaterialTheme.typography.bodyMedium.scaled(fontScale),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        modifier = Modifier.weight(0.68f),
        text = value,
        style = MaterialTheme.typography.bodyMedium.scaled(fontScale),
    )
}

/**
 * One group of chips in the card, under a title naming what they are, one or several, that folds it where
 * [onSectionToggled] is given. A group of several also says how many, which is what is left to read of it once it is
 * folded; a single chip, which is what most songs have for a language, is named by the title alone. A song with none
 * has no group at all.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SongInfoGroup(
    section: SongInfoSection,
    title: PluralStringResource,
    count: Int,
    foldedSections: Set<SongInfoSection>,
    toggledSections: Set<SongInfoSection>,
    lookaheadScope: LookaheadScope?,
    onSectionToggled: ((SongInfoSection) -> Unit)?,
    fontScale: Float,
    chips: @Composable FlowRowScope.() -> Unit,
) = Column(
    // Clipped, since a group springing shut is measured at the height it is on the way to while its chips are still there.
    modifier = Modifier.springingBounds(lookaheadScope).clipToBounds().fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(4.dp),
) {
    val onToggled = onSectionToggled?.let { { it(section) } }
    val isFolded = onToggled != null && section in foldedSections
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onToggled == null) Modifier else Modifier.clickable(role = Role.Button, onClick = onToggled))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = pluralStringResource(title, count),
            style = MaterialTheme.typography.labelLarge.scaled(fontScale),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (count > 1) {
            Text(
                modifier = Modifier.padding(start = 8.dp),
                text = count.toString(),
                style = MaterialTheme.typography.labelLarge.scaled(fontScale),
                color = MaterialTheme.colorScheme.outline,
            )
        }
        if (onToggled != null) {
            Spacer(modifier = Modifier.weight(1f).widthIn(min = FOLD_CHEVRON_GAP))
            FoldChevron(
                modifier = Modifier.size(FOLD_CHEVRON_SIZE * fontScale),
                kind = null,
                isExpanded = !isFolded,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    // Composed afresh as it is unfolded, so that it fades in.
    if (!isFolded) {
        TagFlowRow(
            modifier = Modifier.fadingIn(isFadingIn = section in toggledSections).padding(horizontal = 12.dp),
            content = chips,
        )
    }
}

/** [animateBounds] in [lookaheadScope], or nothing where there is none. */
@OptIn(ExperimentalSharedTransitionApi::class)
private fun Modifier.springingBounds(lookaheadScope: LookaheadScope?) = if (lookaheadScope == null) this else animateBounds(lookaheadScope)

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
