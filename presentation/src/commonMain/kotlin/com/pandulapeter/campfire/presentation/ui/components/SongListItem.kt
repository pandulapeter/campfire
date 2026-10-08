/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.ChordProDuration
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.domain.api.models.SongFilter
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_dot
import com.pandulapeter.campfire.presentation.resources.song_details_tempo
import com.pandulapeter.campfire.presentation.resources.songs_key
import com.pandulapeter.campfire.presentation.resources.songs_lyrics_only
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.theme.LocalSecondAccentColor
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Duration

/**
 * @param index The song's place in the setlist it is listed in, prefixed to its title.
 *   Null on the screens where a song is not in an order of anyone's making and a number would only claim it was.
 * @param key The key the song sounds in where it is listed, the transposition and the capo of that listing applied,
 *   which is a different key in every setlist that plays it differently (`CampfireViewModel.renderKey`), drawn next to
 *   the artist. Null for a file that declares none.
 * @param tempo The tempo the song is played at where it is listed, the override of that listing applied. Null where
 *   neither the file nor an override names one, since the metronome's default says nothing about this song.
 * @param shouldShowChords False with the Chords switch off, where the row says nothing about chords at all: not the
 *   key, and not the "Lyrics only" marker either, which only tells this song from the others while the others are
 *   showing chords.
 * @param duration The song's duration, shown on the Setlists screen even with the chords switched off.
 * @param labelsOnEverySong The tags and languages the row leaves off, because every song in the library carries
 *   them and a label that is on every row tells the reader nothing about this one.
 * @param shouldShowLabels False inside a setlist, which lists the songs somebody wrote down rather than a view of the
 *   library that a tag or a language could narrow, so neither says anything there about why the song is in it.
 * @param songFilter What the song list is filtered by, whose pills are drawn selected.
 * @param onTagClicked Toggles a tag in the song list's filter, exactly as its chip in the filters does. Null, together
 *   with [onLanguageClicked], leaves the pills to be read only.
 * @param onLanguageClicked The same for a language.
 * @param onLongClick A shortcut to the row's overflow menu, on the touch platforms where holding a row is a natural
 *   way to ask what can be done to it.
 * @param cardPadding The card's space from the edges of its grid cell, adjusted for inner columns in wide grids.
 * @param containerColor The card color, raised while the row is dragged ([draggedListItemContainerColor]).
 * @param coverArtUrl The song's cover, drawn as a thumbnail at the start of the card, in front of the text rather than
 *   behind it, so that a row is told apart at a glance without anything being written over the image. Null where the
 *   song names none or covers are turned off.
 * @param actions The trailing content of the row, which is the overflow button of the song's actions
 *   ([SongActions]).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SongListItem(
    modifier: Modifier = Modifier,
    song: Song,
    index: Int? = null,
    key: String? = null,
    tempo: Int? = null,
    shouldShowChords: Boolean = true,
    duration: Duration? = null,
    labelsOnEverySong: CampfireViewModel.LabelsOnEverySong,
    shouldShowLabels: Boolean = true,
    songFilter: SongFilter = SongFilter(),
    onTagClicked: ((String) -> Unit)? = null,
    onLanguageClicked: ((String) -> Unit)? = null,
    cardPadding: PaddingValues = PaddingValues(horizontal = SONG_CARD_OUTER_PADDING, vertical = SONG_CARD_VERTICAL_PADDING),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    shadowElevation: Dp = 0.dp,
    coverArtUrl: String? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
) {
    val displayedDuration = duration?.let(ChordProDuration::format)
    // Worked out before the row is laid out rather than inside it, because it is also one of the things that decide
    // whether the row has a second line at all: a song written in the app carries no artist, no language and no tag,
    // and its key is then the only thing there is to put under the title.
    val note = when {
        !shouldShowChords -> null

        !song.hasChords -> SongListItemNote(
            text = stringResource(Res.string.songs_lyrics_only),
            isEmphasized = false,
        )

        !key.isNullOrBlank() -> SongListItemNote(
            text = key,
            isEmphasized = true,
            description = textResource(Res.string.songs_key, key),
        )

        else -> null
    }
    // A number of its own rather than a second emphasized value next to the key: the key is the one thing a player
    // has to find on a card at a glance, and a second accent colored item beside it halves that. It keeps the
    // duration's muted color, since the two are what a set is timed by.
    val tempoNote = tempo?.let {
        SongListItemNote(
            text = stringResource(Res.string.song_details_tempo, it.toString()),
            isEmphasized = false,
        )
    }
    // Remembered, so that a row composed again with the same song hands the same lists on and the labels under it skip.
    val languages = remember(song.languages, labelsOnEverySong, shouldShowLabels) {
        if (shouldShowLabels) song.languages.filterNot { it in labelsOnEverySong.languages } else emptyList()
    }
    val tags = remember(song.tags, labelsOnEverySong, shouldShowLabels) {
        if (shouldShowLabels) song.tags.filterNot { it.lowercase() in labelsOnEverySong.tags } else emptyList()
    }
    Surface(
        modifier = modifier.fillMaxWidth().padding(cardPadding),
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        shadowElevation = shadowElevation,
    ) {
        CenteredSongCardContent(
            modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
            coverArtUrl = coverArtUrl,
            actions = actions,
            headlineContent = {
                ListItemHeadline(text = songCardTitle(song.title, index))
            },
            // Songs written in the app need no artist, and an empty second line would just make the row taller. Nothing
            // here shares the title's line: a title is the longest thing on the row and the one that must never be
            // pushed out of sight, while the artist is short enough to leave the key room next to it. The languages and
            // tags go under both, since a row of those is as long as somebody chose to make it.
            supportingContent = if (song.artist.isBlank() && note == null && tempoNote == null && displayedDuration == null) {
                null
            } else {
                {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (song.artist.isNotBlank()) {
                            Text(
                                modifier = Modifier.weight(1f, fill = false),
                                text = song.artist,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        SongCardNote(note = note, hasPrecedingContent = song.artist.isNotBlank())
                        SongCardNote(note = tempoNote, hasPrecedingContent = song.artist.isNotBlank() || note != null)
                        if (displayedDuration != null) {
                            // Follows the notes, which fade and close up where they stand rather than leaving the line
                            // in one frame, so the duration slides once instead of jumping and then sliding.
                            AnimatedVisibility(
                                visible = song.artist.isNotBlank() || note != null || tempoNote != null,
                                enter = fadeIn() + expandHorizontally(),
                                exit = fadeOut() + shrinkHorizontally(),
                            ) {
                                SongCardNoteDot()
                            }
                            Text(
                                text = displayedDuration,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            labelsContent = if (languages.isEmpty() && tags.isEmpty()) {
                null
            } else {
                {
                    SongLabels(
                        tags = tags,
                        languages = languages,
                        selectedTags = songFilter.selectedTags,
                        selectedLanguages = songFilter.selectedLanguages,
                        onTagClicked = onTagClicked,
                        onLanguageClicked = onLanguageClicked,
                    )
                }
            },
        )
    }
}

/**
 * The container color of a row that can be dragged: the card, tinted for as long as it is off the list.
 *
 * A progress value instead of an animated color, so that the row follows the color scheme immediately while it
 * is animating between the light and the dark theme (a color animation would chase it and trail behind). It is
 * asked for by the list whose rows are dragged rather than worked out by every [SongListItem], because the
 * animation behind it is a coroutine that runs for as long as the row is composed, and the song list has
 * thousands of rows and no drag.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun draggedListItemContainerColor(isBeingDragged: Boolean): Color {
    val dragProgress by animateFloatAsState(
        if (isBeingDragged) 1f else 0f,
        MaterialTheme.motionScheme.defaultEffectsSpec(),
    )
    return lerp(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.colorScheme.surfaceContainerHigh, dragProgress)
}

/**
 * One of the things a song row says about how the song is played, after the artist: the key it sounds in where it is
 * listed, drawn in the accent color the song details header keeps for what is played, or that the file has no chords
 * at all - never both, since the viewer names no key for a song there is nothing to play and a row that named one
 * would be contradicting the screen it opens - and the tempo it is played at.
 *
 * @param isEmphasized Whether the note is drawn in the accent color, which is what a key is.
 * @param description What the note is read out as, since a key is two letters that say nothing on their own.
 */
private data class SongListItemNote(
    val text: String,
    val isEmphasized: Boolean,
    val description: String = text,
)

/**
 * A [SongListItemNote] where the song card's second line has reached it, with the dot that separates it from whatever
 * stands in front of it.
 *
 * The note changes under the reader: a transposition or a capo renames the key, a stepper in a setlist moves the
 * tempo, and the chords switched off take the key away altogether. So it is crossfaded where it stands and the line closes
 * up around it, rather than the row being redrawn around the change. The color is resolved inside rather than carried
 * by the state, since the scheme is interpolated on every frame of a theme change and each of those frames would
 * start another crossfade.
 *
 * @param hasPrecedingContent Whether anything is drawn before it, which is what the dot would separate it from: a
 *   song that names no artist starts the line with its key itself rather than with a separator before nothing.
 */
@Composable
private fun SongCardNote(
    note: SongListItemNote?,
    hasPrecedingContent: Boolean,
) = AnimatedContent(
    targetState = note,
    transitionSpec = { fadeIn() togetherWith fadeOut() },
) { currentNote ->
    if (currentNote != null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (hasPrecedingContent) {
                SongCardNoteDot()
            }
            Text(
                modifier = Modifier.semantics { contentDescription = currentNote.description },
                text = currentNote.text,
                style = MaterialTheme.typography.labelMedium,
                color = if (currentNote.isEmphasized) LocalSecondAccentColor.current else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * What separates two values on a song card's second line. It carries no padding of its own: the glyph is a 4dp dot in
 * the middle of a 24dp icon, so the box it sits in is the gap already, and the same gap on both sides of it - anything
 * added here is added to one side only. A narrow window shrinks the box, and with it both gaps, since there every dp
 * of the line is one more letter of the artist.
 */
@Composable
private fun SongCardNoteDot() = Icon(
    modifier = Modifier.size(if (isNarrowSongCardWindow) NARROW_DOT_SIZE else DOT_SIZE),
    painter = painterResource(Res.drawable.ic_dot),
    contentDescription = null,
)

private val DOT_SIZE = 24.dp
private val NARROW_DOT_SIZE = 16.dp
