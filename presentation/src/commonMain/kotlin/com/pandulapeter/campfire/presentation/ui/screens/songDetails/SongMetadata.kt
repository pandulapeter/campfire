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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.ChordProDuration
import com.pandulapeter.campfire.chordpro.model.ChordProLink
import com.pandulapeter.campfire.chordpro.model.ChordProMetadata
import com.pandulapeter.campfire.metronome.api.model.TimeSignature
import com.pandulapeter.campfire.presentation.localization.pluralStringResource
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_add
import com.pandulapeter.campfire.presentation.resources.ic_album
import com.pandulapeter.campfire.presentation.resources.song_details_change_cover_art
import com.pandulapeter.campfire.presentation.resources.song_details_set_cover_art
import com.pandulapeter.campfire.presentation.resources.ic_edit
import com.pandulapeter.campfire.presentation.resources.ic_info
import com.pandulapeter.campfire.presentation.resources.ic_label
import com.pandulapeter.campfire.presentation.resources.ic_language
import com.pandulapeter.campfire.presentation.resources.ic_link
import com.pandulapeter.campfire.presentation.resources.song_details_album
import com.pandulapeter.campfire.presentation.resources.song_details_capo
import com.pandulapeter.campfire.presentation.resources.song_details_composer
import com.pandulapeter.campfire.presentation.resources.song_details_duration
import com.pandulapeter.campfire.presentation.resources.song_details_info_defaults
import com.pandulapeter.campfire.presentation.resources.song_details_info_languages
import com.pandulapeter.campfire.presentation.resources.song_details_info_links
import com.pandulapeter.campfire.presentation.resources.song_details_info_tags
import com.pandulapeter.campfire.presentation.resources.song_details_lyricist
import com.pandulapeter.campfire.presentation.resources.song_details_playing_edit
import com.pandulapeter.campfire.presentation.resources.song_details_links_edit
import com.pandulapeter.campfire.presentation.resources.song_details_languages_edit
import com.pandulapeter.campfire.presentation.resources.song_details_tags_manage
import com.pandulapeter.campfire.presentation.resources.song_details_playing_override_library
import com.pandulapeter.campfire.presentation.resources.song_details_playing_override_setlist
import com.pandulapeter.campfire.presentation.resources.song_details_metadata_edit
import com.pandulapeter.campfire.presentation.resources.song_details_song_info
import com.pandulapeter.campfire.presentation.resources.song_details_tempo
import com.pandulapeter.campfire.presentation.resources.song_details_time
import com.pandulapeter.campfire.presentation.resources.song_details_transposition
import com.pandulapeter.campfire.presentation.resources.song_details_year
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_capo
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_key
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_tempo
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_time
import com.pandulapeter.campfire.presentation.ui.components.TagFlowRow
import com.pandulapeter.campfire.presentation.ui.components.ACTION_BUTTON_OVERLAP
import com.pandulapeter.campfire.presentation.ui.components.CoverArtImage
import com.pandulapeter.campfire.presentation.ui.components.rememberSettledCoverArtUrl
import com.pandulapeter.campfire.presentation.ui.components.overlappingAction
import com.pandulapeter.campfire.presentation.ui.components.TagPill
import com.pandulapeter.campfire.presentation.ui.components.languageLabel
import com.pandulapeter.campfire.presentation.ui.components.sortedAlphabeticallyBy
import com.pandulapeter.campfire.presentation.ui.components.textResource
import com.pandulapeter.campfire.presentation.ui.metronome.EffectiveTempo
import com.pandulapeter.campfire.presentation.ui.metronome.TempoStepper
import com.pandulapeter.campfire.presentation.ui.theme.LocalSecondAccentColor
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.painterResource

/**
 * What the song says about itself is the first section of its own grid, so it shares the rows, columns, stepping and
 * motion of the lyrics rather than taking their full width away above them: the card of what the song is, where it is
 * shown there, and under it how it is played.
 *
 * @param shouldShowChords False for lyrics-only mode, which leaves out the key, capo, tempo and time along with the
 * chords: they are what is played, and say nothing to somebody who is only singing.
 * @param isSongInfoShown Whether the card of what the song is belongs to the section: it does in the editor's preview,
 * which shows everything being typed, and not on the song details screen, whose app bar opens it as a sheet instead.
 * @param isSongInfoEditable Whether that card has edit buttons, which puts it there for a song that says nothing about
 * itself yet too: it is where its first tag or link is added.
 * @param hasPlayingControls Whether the four playing values are drawn as the controls that set them, which is what
 * the song details screen hands down outside read only mode. The section is then there for every song, since a capo
 * and a tempo can be set on one that names neither.
 * @param readsCapoAndTime Whether the line of text read only mode draws in their place always names the capo and the
 * time signature, a capo of none and the common time the click counts where the file names neither: a player reading
 * from a music stand has no control left to look at to tell that nothing was set, and the absence of a value is a
 * value to the hands on the guitar. The section is then there for every song too.
 */
internal fun withMetadataSection(
    sections: List<RenderSection>,
    metadata: ChordProMetadata,
    shouldShowChords: Boolean,
    isSongInfoShown: Boolean,
    isSongInfoEditable: Boolean = false,
    hasPlayingControls: Boolean = false,
    readsCapoAndTime: Boolean = false,
): List<RenderSection> {
    val hasControls = shouldShowChords && hasPlayingControls
    val readsBoth = shouldShowChords && !hasControls && readsCapoAndTime
    val shownMetadata = when {
        !shouldShowChords -> metadata.copy(key = null, capo = null, tempo = null, time = null)
        readsBoth -> metadata.copy(
            capo = metadata.capo ?: 0,
            time = metadata.time?.takeIf { it.isNotBlank() } ?: TimeSignature.COMMON_TIME.toString(),
        )
        else -> metadata
    }
    return if ((isSongInfoShown && (isSongInfoEditable || shownMetadata.hasSongInfo)) || hasControls || readsBoth || shownMetadata.hasPlayingValues) {
        listOf(RenderSection.Metadata(metadata = shownMetadata, hasPlayingControls = hasControls, readsCapoAndTime = readsBoth)) + sections
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
 * @param playingControls What sets those four, where they are set here rather than only read: the song details screen
 * hands them down outside read only mode, see [SongPlayingControls].
 * @param readsCapoAndTime Whether the line of text names a capo of none too, see [withMetadataSection].
 * @param animatesControls Whether the controls travel to the places a new layout gives them, which the song's own
 * sections decide (see `SectionMotion`): never while a pinch or a window edge being dragged changes the layout every frame.
 * @param titleStyle The style of the section titles of the lyrics, which the card's own title follows as the text is
 * scaled; what the card holds scales with [fontScale] the way the lyrics do.
 */
@Composable
internal fun SongMetadataSection(
    modifier: Modifier = Modifier,
    metadata: ChordProMetadata,
    isSongInfoShown: Boolean,
    songInfoEditing: SongInfoEditing?,
    playingControls: SongPlayingControls?,
    readsCapoAndTime: Boolean,
    animatesControls: Boolean,
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
    if (playingControls == null) {
        SongPlayingMetadata(
            modifier = Modifier.padding(top = if (hasSongInfoCard) 12.dp else 0.dp),
            metadata = metadata,
            readsCapoAndTime = readsCapoAndTime,
            fontScale = fontScale,
        )
    } else {
        SongPlayingControlsRow(
            modifier = Modifier.padding(top = if (hasSongInfoCard) 12.dp else 0.dp),
            controls = playingControls,
            isAnimated = animatesControls,
            titleStyle = titleStyle,
            fontScale = fontScale,
        )
    }
}

@Composable
private fun SongPlayingMetadata(
    modifier: Modifier = Modifier,
    metadata: ChordProMetadata,
    readsCapoAndTime: Boolean,
    fontScale: Float,
) {
    val values = listOfNotNull(
        metadata.key?.takeIf { it.isNotBlank() },
        metadata.capo?.takeIf { readsCapoAndTime || it != 0 }?.let { stringResource(Res.string.song_details_capo, it) },
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
 * The four things that decide how the song is played, each next to the control that sets it: the transposition, which
 * is named as such and reads the key it takes the chords on the page to, the capo and the tempo with their own
 * steppers, the tempo's pill ending in the segment that taps one in, and the time signature after them, read rather
 * than set. They are laid out in balanced rows ([BalancedRows]) — all four side by side, two and two, or one under the
 * other — rather than flowed, since three over one reads as an accident. Starting a click is the app bar's button, which is
 * in reach wherever the song has been scrolled to; this row only says what it would play.
 *
 * The steppers change how the song is played where it is read and never touch the file. What the file itself
 * declares, the time signature among it, is edited in the "Song defaults" sheet of the editing menu, which is also
 * where that difference is explained, so the page carries nothing but the song and its controls.
 *
 * Labels and controls grow and shrink with the lyrics, since they are part of the song's own first section — but the
 * way the sections' own header pills do rather than as a bar's buttons scaled up: what is written in them is scaled and
 * the padding around it is not, so a control is exactly as tall as the pill heading the section under it
 * ([songControlHeight], from the [titleStyle] those pills are named in). What keeps them big enough to hit at the other
 * end is `UserPreferences.MIN_FONT_SCALE`, the size below which the song details screen is not read at all.
 */
@Composable
private fun SongPlayingControlsRow(
    modifier: Modifier = Modifier,
    controls: SongPlayingControls,
    isAnimated: Boolean,
    titleStyle: TextStyle,
    fontScale: Float,
) {
    val height = songControlHeight(titleStyle)
    BalancedRows(
        modifier = modifier.padding(horizontal = 12.dp),
        gap = PLAYING_CONTROL_GAP,
        isAnimated = isAnimated,
    ) {
        controls.key?.let { key ->
            PlayingControl(
                modifier = Modifier.layoutId(PlayingControlId.KEY),
                label = stringResource(Res.string.song_details_transposition),
                fontScale = fontScale,
            ) {
                TranspositionControls(
                    transposition = key.transposition,
                    key = key.key,
                    fontScale = fontScale,
                    height = height,
                    onStep = key.onStep,
                    onReset = key.onReset,
                )
            }
        }
        PlayingControl(
            modifier = Modifier.layoutId(PlayingControlId.CAPO),
            label = stringResource(Res.string.song_editor_insert_capo),
            fontScale = fontScale,
        ) {
            CapoControls(
                capo = controls.capo.capo,
                fontScale = fontScale,
                height = height,
                onStep = controls.capo.onStep,
                onReset = controls.capo.onReset,
            )
        }
        PlayingControl(
            modifier = Modifier.layoutId(PlayingControlId.TEMPO),
            label = stringResource(Res.string.song_editor_insert_tempo),
            fontScale = fontScale,
        ) {
            TempoStepper(
                tempo = controls.tempo.tempo,
                fontScale = fontScale,
                height = height,
                onStep = controls.tempo.onStep,
                onTapped = controls.tempo.onTapped,
                onReset = controls.tempo.onReset,
            )
        }
        // An item of its own rather than part of the tempo's, since it is the meter rather than how fast it goes, and
        // read rather than set: it is the one of the four that only the file says, which a control of its own on the
        // page made look like the others.
        PlayingControl(
            modifier = Modifier.layoutId(PlayingControlId.TIME),
            label = stringResource(Res.string.song_editor_insert_time),
            fontScale = fontScale,
        ) {
            Text(
                text = controls.timeSignature,
                style = MaterialTheme.typography.labelLarge.scaled(fontScale),
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** What [BalancedRows] follows each of [SongPlayingControlsRow]'s items by, since the transposition is not always among them. */
private enum class PlayingControlId { KEY, CAPO, TEMPO, TIME }

/** One of [SongPlayingControlsRow]'s items: what it is, in the accent color the line of text uses, and what sets it. */
@Composable
private fun PlayingControl(
    modifier: Modifier = Modifier,
    label: String,
    fontScale: Float,
    control: @Composable RowScope.() -> Unit,
) = Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(
        modifier = Modifier.padding(end = PLAYING_CONTROL_LABEL_GAP),
        text = label,
        style = MaterialTheme.typography.labelLarge.scaled(fontScale),
        color = LocalSecondAccentColor.current,
    )
    control()
}

/**
 * The card of what the song is in the editor's preview, as wide as its column whatever it holds, so that its edit buttons
 * stay where they are as the groups fill up and empty. Its links are not followed, since the preview is there to show
 * what is being typed. Its cover follows the text the way the editor's title does, once the typing has paused, and only
 * where the cover art setting gives the card its cover button, which is what tells the card that setting.
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
            if (editing != null) {
                editing.onEditCoverArt?.let { onEditCoverArt ->
                    IconButton(onClick = onEditCoverArt) {
                        Icon(
                            modifier = Modifier.size(EDIT_ICON_SIZE * fontScale),
                            painter = painterResource(Res.drawable.ic_album),
                            contentDescription = stringResource(
                                if (metadata.coverArt.isNullOrBlank()) Res.string.song_details_set_cover_art else Res.string.song_details_change_cover_art,
                            ),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                IconButton(
                    modifier = if (editing.onEditCoverArt != null) Modifier.overlappingAction(start = ACTION_BUTTON_OVERLAP, end = 0.dp) else Modifier,
                    onClick = editing.onEditMetadata,
                ) {
                    Icon(
                        modifier = Modifier.size(EDIT_ICON_SIZE * fontScale),
                        painter = painterResource(Res.drawable.ic_edit),
                        contentDescription = stringResource(Res.string.song_details_metadata_edit),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        SongInfoBody(
            modifier = Modifier.padding(bottom = 12.dp),
            metadata = metadata,
            coverArtUrl = rememberSettledCoverArtUrl(metadata.coverArt.takeIf { editing?.onEditCoverArt != null }),
            fontScale = fontScale,
            horizontalPadding = 12.dp,
            editing = editing,
            onOpenLink = null,
        )
    }
}

/**
 * What sets the four values a song is played by, from the song's own first section, see [SongPlayingControlsRow]. The
 * song details screen builds it for the song on screen and only outside read only mode: performance mode and a song
 * read from an archived setlist get the plain line of text instead, since neither changes anything about a song.
 *
 * Three of the four are set where the song is read — a transposition, a capo and a tempo belong to the setlist the
 * band plays it in, or to this device for a song opened from the library — while what the file itself declares, the
 * time signature among it, is edited in the editing menu's "Song defaults" sheet (`SongPlayingDialog`).
 */
@Immutable
internal class SongPlayingControls(
    /** Null for a song with nothing to transpose, whose key is then only read. */
    val key: SongKeyControl?,
    val capo: SongCapoControl,
    val tempo: SongTempoControl,
    /** The time signature as the file writes it, or the one the click counts the bar by where it names none. */
    val timeSignature: String,
)

/** The transposition, which the key reads: the amount, the key it takes the song to, and the stepper's two ends. */
@Immutable
internal class SongKeyControl(
    val transposition: Int,
    val key: String?,
    val onStep: (semitones: Int) -> Unit,
    val onReset: () -> Unit,
)

@Immutable
internal class SongCapoControl(
    val capo: EffectiveCapo,
    val onStep: (frets: Int) -> Unit,
    val onReset: () -> Unit,
)

/** The tempo the click would play at: the same stepper and Tap button the overflow menu's row had. */
@Immutable
internal class SongTempoControl(
    val tempo: EffectiveTempo,
    val onStep: (delta: Int) -> Unit,
    val onTapped: (bpm: Int) -> Unit,
    val onReset: () -> Unit,
)

/** Editing callbacks for the card or sheet header and the groups of [SongInfoBody], see [rememberSongInfoEditing]. */
@Immutable
internal class SongInfoEditing(
    val onEditCoverArt: (() -> Unit)?,
    val onEditMetadata: () -> Unit,
    val onEditTags: () -> Unit,
    val onEditLanguages: () -> Unit,
    val onEditLinks: () -> Unit,
)

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

/** What the song's file declares for the four values it is played by, for the "About the song" sheet, see [SongInfoBody]. */
@Immutable
internal class SongDefaults(
    /** In the reader's notation, the way the "Song defaults" sheet's field shows it. */
    val key: String?,
    val capo: Int?,
    val tempo: Int?,
    val time: String?,
    /** How the song is played differently from these where it is read, see `songPlayingOverrides`. */
    val overrides: List<String>,
    /** Whether [overrides] belong to a setlist rather than to the library on this device. */
    val isReadFromSetlist: Boolean,
    /** Opens the "Song defaults" sheet; null in read only mode, which changes nothing about a song. */
    val onEdit: (() -> Unit)?,
) {
    val hasValues get() = !key.isNullOrBlank() || capo != null || tempo != null || !time.isNullOrBlank()
}

/**
 * The song's defaults as label-over-value tiles, the way its details are shown, with a pencil next to the title that
 * opens the "Song defaults" sheet. Where the song is being played differently from them a line under the tiles says so, in the
 * color the steppers on the page draw an overridden value in: the sheet would otherwise name values the page does not
 * show, and that difference is the one the "Song defaults" sheet exists to explain.
 */
@Composable
private fun SongDefaultsGroup(
    defaults: SongDefaults,
    fontScale: Float,
    horizontalPadding: Dp,
) = SongInfoGroup(
    title = stringResource(Res.string.song_details_info_defaults),
    count = 0,
    fontScale = fontScale,
    horizontalPadding = horizontalPadding,
    action = defaults.onEdit?.let { onEdit ->
        {
            SongInfoAction(
                hasValues = defaults.hasValues,
                contentDescription = stringResource(Res.string.song_details_playing_edit),
                fontScale = fontScale,
                onClick = onEdit,
            )
        }
    },
    isContentShown = defaults.hasValues || defaults.overrides.isNotEmpty(),
) {
    SongDefaultsValues(defaults = defaults, fontScale = fontScale)
}

/** The tiles of [SongDefaultsGroup], and under them the line naming the overrides while there are any. */
@Composable
private fun SongDefaultsValues(
    defaults: SongDefaults,
    fontScale: Float,
) {
    val rows = listOfNotNull(
        defaults.key?.takeIf { it.isNotBlank() }?.let { stringResource(Res.string.song_editor_insert_key) to it },
        defaults.capo?.let { stringResource(Res.string.song_editor_insert_capo) to it.toString() },
        defaults.tempo?.let { stringResource(Res.string.song_editor_insert_tempo) to textResource(Res.string.song_details_tempo, it.toString()) },
        defaults.time?.takeIf { it.isNotBlank() }?.let { stringResource(Res.string.song_editor_insert_time) to it },
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp * fontScale)) {
        if (rows.isNotEmpty()) {
            MetadataTiles(
                rows = rows,
                fontScale = fontScale,
            )
        }
        AnimatedVisibility(
            visible = defaults.overrides.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            // The last overrides named stay while a reset folds the line away.
            var shownOverrides by remember { mutableStateOf(defaults.overrides) }
            if (defaults.overrides.isNotEmpty()) shownOverrides = defaults.overrides
            Column {
                Text(
                    text = stringResource(
                        if (defaults.isReadFromSetlist) Res.string.song_details_playing_override_setlist else Res.string.song_details_playing_override_library,
                    ),
                    style = MaterialTheme.typography.labelMedium.scaled(fontScale),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = shownOverrides.joinToString("  •  "),
                    style = MaterialTheme.typography.bodyMedium.scaled(fontScale),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

/**
 * The button next to a group's title that edits it: a pencil, or a plus where the group is empty. As tall as the title
 * row, so that a group reads the same with it and without it; the touch target still reaches beyond it.
 */
@Composable
private fun SongInfoAction(
    hasValues: Boolean,
    contentDescription: String,
    fontScale: Float,
    onClick: () -> Unit,
) = IconButton(
    modifier = Modifier.size(SONG_INFO_TITLE_HEIGHT * fontScale),
    onClick = onClick,
) {
    Icon(
        modifier = Modifier.size(EDIT_ICON_SIZE * fontScale),
        painter = painterResource(if (hasValues) Res.drawable.ic_edit else Res.drawable.ic_add),
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.primary,
    )
}

/**
 * One group of [SongInfoBody] under its title, if it has one, with a count next to the title when it is more than one
 * and the [action] that edits the group after that. Where [isContentShown] is false the group is its title row alone,
 * the content folding away rather than vanishing, since emptying a group is something the user did from this sheet.
 */
@Composable
private fun SongInfoGroup(
    title: String?,
    count: Int,
    fontScale: Float,
    horizontalPadding: Dp,
    action: (@Composable () -> Unit)? = null,
    isContentShown: Boolean = true,
    content: @Composable () -> Unit,
) = Column(modifier = Modifier.fillMaxWidth()) {
    if (title != null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(SONG_INFO_TITLE_HEIGHT * fontScale)
                .padding(horizontal = horizontalPadding),
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
            if (action != null) {
                Box(modifier = Modifier.padding(start = 4.dp * fontScale)) {
                    action()
                }
            }
        }
    }
    AnimatedVisibility(
        visible = isContentShown,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Box(modifier = Modifier.padding(start = horizontalPadding, top = if (title == null) 0.dp else 4.dp * fontScale, end = horizontalPadding)) {
            content()
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

/**
 * Between two of the playing controls, and between the rows they wrap into: the gap the card's chips keep. It does not
 * grow with the song's text, any more than the gaps between its sections do.
 */
private val PLAYING_CONTROL_GAP = 8.dp
private val PLAYING_CONTROL_LABEL_GAP = 8.dp


private val EDIT_ICON_SIZE = 18.dp
private val SONG_INFO_TITLE_HEIGHT = 32.dp
private val SONG_INFO_GROUP_GAP = 16.dp

/** As tall as two rows of detail tiles, which is what a song with a few details fills beside it. */
private val SONG_INFO_COVER_SIZE = 96.dp
private val COVER_EDIT_BADGE_SIZE = 24.dp
private val COVER_EDIT_BADGE_INSET = 4.dp
