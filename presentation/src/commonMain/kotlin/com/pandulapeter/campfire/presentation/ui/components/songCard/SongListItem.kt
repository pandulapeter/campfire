/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.songCard

import com.pandulapeter.campfire.presentation.ui.components.labels.LabelsOnEverySong
import com.pandulapeter.campfire.presentation.ui.components.actions.SongActions
import com.pandulapeter.campfire.presentation.ui.components.labels.IconLabel
import com.pandulapeter.campfire.presentation.ui.components.labels.IconLabelMark
import com.pandulapeter.campfire.presentation.ui.components.labels.SongLabels
import com.pandulapeter.campfire.presentation.ui.components.list.onContextClick
import com.pandulapeter.campfire.presentation.ui.components.only
import com.pandulapeter.campfire.presentation.localization.textResource
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.ChordProDuration
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.domain.api.library.SongFilter
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_tempo
import com.pandulapeter.campfire.presentation.resources.songs_tempo_and_time
import com.pandulapeter.campfire.presentation.resources.songs_key
import com.pandulapeter.campfire.presentation.resources.songs_lyrics_only
import com.pandulapeter.campfire.metronome.api.model.TimeSignature
import com.pandulapeter.campfire.presentation.ui.metronome.timeSignatureOrDefault
import com.pandulapeter.campfire.presentation.ui.platform.isDesktopPlatform
import com.pandulapeter.campfire.presentation.ui.theme.LocalSecondAccentColor
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Duration

/**
 * @param index The song's place in the setlist it is listed in, prefixed to its title.
 *   Null on the screens where a song is not in an order of anyone's making and a number would only claim it was.
 * @param key The key the song sounds in where it is listed, the transposition and the capo of that listing applied,
 *   which is a different key in every setlist that plays it differently (`SongRenderer.renderKey`), drawn next to
 *   the artist. Null for a file that declares none.
 * @param tempo The tempo the song is played at where it is listed, the override of that listing applied. Null where
 *   neither the file nor an override names one, since the metronome's default says nothing about this song.
 * @param shouldShowChords False with the Chords switch off, where the row says nothing about chords at all: not the
 *   key, and not the "Lyrics only" marker either, which only tells this song from the others while the others are
 *   showing chords.
 * @param duration The song's duration, where the list's cards show it (`UserPreferences.CardDetails`), even with the
 *   chords switched off.
 * @param labelsOnEverySong The tags and languages the row leaves off, because every song in the library carries
 *   them and a label that is on every row tells the reader nothing about this one.
 * @param isArtistShown False on a card filed under a header that names its artist already, the songs list sorted by artist,
 *   where the artist on every card would only repeat it. The artist is still drawn where nothing else is left on the
 *   second line, since a card losing that line would change height, and it is still read out, since a screen reader
 *   moving from card to card never hears the header again.
 * @param shouldShowLabels False inside a setlist, which lists the songs somebody wrote down rather than a view of the
 *   library that a tag or a language could narrow, so neither says anything there about why the song is in it.
 * @param songFilter What the song list is filtered by, whose pills are drawn selected.
 * @param onTagClicked Toggles a tag in the song list's filter, exactly as its chip in the filters does. Null, together
 *   with [onLanguageClicked], leaves the pills to be read only.
 * @param onLanguageClicked The same for a language.
 * @param onContextClick A shortcut to the row's overflow menu: a mouse's right click wherever there is a mouse, and a
 *   long press on the touch platforms, where holding a row is a natural way to ask what can be done to it.
 * @param cardPadding The card's space from the edges of its grid cell, adjusted for inner columns in wide grids.
 * @param containerColor The card color, raised while the row is dragged ([draggedListItemContainerColor]).
 * @param coverArtUrl The song's cover, drawn as a thumbnail at the start of the card, in front of the text rather than
 *   behind it, so that a row is told apart at a glance without anything being written over the image. Null where the
 *   song names none or covers are turned off.
 * @param actions The trailing content of the row, which is the overflow button of the song's actions
 *   ([SongActions]).
 * @param toggleableState Whether the song is ticked, where the card is one of a selection; null elsewhere. A ticked card
 *   is a checkbox to a screen reader, and the state is set on the node the card's click merges its text into, since a
 *   checkbox around that node would be read without a label and the card without its state.
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
    labelsOnEverySong: LabelsOnEverySong,
    isArtistShown: Boolean = true,
    shouldShowLabels: Boolean = true,
    songFilter: SongFilter = SongFilter(),
    onTagClicked: ((String) -> Unit)? = null,
    onLanguageClicked: ((String) -> Unit)? = null,
    cardPadding: PaddingValues = PaddingValues(horizontal = SONG_CARD_OUTER_PADDING, vertical = SONG_CARD_VERTICAL_PADDING),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    shadowElevation: Dp = 0.dp,
    coverArtUrl: String? = null,
    onClick: () -> Unit,
    onContextClick: (() -> Unit)? = null,
    toggleableState: ToggleableState? = null,
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
    // The time signature comes with it, since the metronome the mark draws is set by both, and is the file's own, which
    // no listing overrides. Common time is left off: it is what most songs are in, and on every card it would only take
    // letters off the artist.
    val timeSignature = song.timeSignatureOrDefault.takeUnless { it == TimeSignature.COMMON_TIME }
    val tempoNote = tempo?.let {
        SongListItemNote(
            text = if (timeSignature == null) {
                stringResource(Res.string.song_details_tempo, it.toString())
            } else {
                stringResource(Res.string.songs_tempo_and_time, it.toString(), timeSignature.toString())
            },
            isEmphasized = false,
            mark = IconLabelMark.TEMPO,
        )
    }
    val hasArtist = song.artist.isNotBlank()
    val isArtistDrawn = hasArtist && (isArtistShown || (note == null && tempoNote == null && displayedDuration == null))
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
            modifier = Modifier
                .onContextClick(onContextClick)
                .combinedClickable(onClick = onClick, onLongClick = onContextClick.takeUnless { isDesktopPlatform })
                .then(remember(toggleableState) { selectionSemanticsOf(toggleableState) }),
            coverArtUrl = coverArtUrl,
            actions = actions,
            headlineContent = {
                ListItemHeadline(text = songCardTitle(song.title, index))
            },
            // Songs written in the app need no artist, and an empty second line would just make the row taller. Nothing
            // here shares the title's line: a title is the longest thing on the row and the one that must never be
            // pushed out of sight, while the artist is short enough to leave the key room next to it. The languages and
            // tags go under both, since a row of those is as long as somebody chose to make it.
            supportingContent = if (!hasArtist && note == null && tempoNote == null && displayedDuration == null) {
                null
            } else {
                {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Faded and closed up rather than dropped, since switching the sort or opening the search, which
                        // takes the headers away, is what hides and shows it.
                        AnimatedVisibility(
                            modifier = Modifier.weight(1f, fill = false),
                            visible = isArtistDrawn,
                            enter = fadeIn() + expandHorizontally(),
                            exit = fadeOut() + shrinkHorizontally(),
                        ) {
                            Text(
                                text = song.artist,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (hasArtist && !isArtistDrawn) {
                            Box(modifier = Modifier.semantics { text = AnnotatedString(song.artist) })
                        }
                        SongCardNote(note = note, separator = songCardSeparatorBefore(isArtistDrawn, hasValueBefore = false))
                        SongCardNote(note = tempoNote, separator = songCardSeparatorBefore(isArtistDrawn, hasValueBefore = note != null))
                        if (displayedDuration != null) {
                            val separator = songCardSeparatorBefore(isArtistDrawn, hasValueBefore = note != null || tempoNote != null)
                            // Follows the notes, which fade and close up where they stand rather than leaving the line
                            // in one frame, so the duration slides once instead of jumping and then sliding.
                            AnimatedVisibility(
                                visible = separator != null,
                                enter = fadeIn() + expandHorizontally(),
                                exit = fadeOut() + shrinkHorizontally(),
                            ) {
                                SongCardSeparator(kind = separator ?: SongCardSeparatorKind.DOT)
                            }
                            IconLabel(text = displayedDuration, mark = IconLabelMark.DURATION)
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
 * A card of the selection mode as a checkbox to a screen reader, or nothing for [toggleableState] null. Remembered by
 * the caller, since the semantics lambda is a new one on every pass, which would make the modifier unequal and have
 * every visible card composed again on every tick of the selection mode.
 */
private fun selectionSemanticsOf(toggleableState: ToggleableState?) = if (toggleableState == null) {
    Modifier
} else {
    Modifier.semantics {
        role = Role.Checkbox
        this.toggleableState = toggleableState
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
 * @param mark What the note is drawn with in front of it ([IconLabel]), in the note's color; null for the text alone.
 */
private data class SongListItemNote(
    val text: String,
    val isEmphasized: Boolean,
    val description: String = text,
    val mark: IconLabelMark? = null,
)

/**
 * A [SongListItemNote] where the song card's second line has reached it, with what separates it from whatever stands in
 * front of it.
 *
 * The note changes under the reader: a transposition or a capo renames the key, a stepper in a setlist moves the
 * tempo, and the chords switched off take the key away altogether. So it is crossfaded where it stands and the line closes
 * up around it, rather than the row being redrawn around the change. The color is resolved inside rather than carried
 * by the state, since the scheme is interpolated on every frame of a theme change and each of those frames would
 * start another crossfade.
 *
 * @param separator What stands between it and what is drawn before it ([songCardSeparatorBefore]): a song that names no
 *   artist starts the line with its key itself rather than with a separator before nothing.
 */
@Composable
private fun SongCardNote(
    note: SongListItemNote?,
    separator: SongCardSeparatorKind?,
) = AnimatedContent(
    targetState = note,
    transitionSpec = { fadeIn() togetherWith fadeOut() },
) { currentNote ->
    if (currentNote != null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The first note's separator comes and goes with the artist in front of it, so it closes up the way the
            // duration's does.
            AnimatedVisibility(
                visible = separator != null,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally(),
            ) {
                SongCardSeparator(kind = separator ?: SongCardSeparatorKind.DOT)
            }
            val noteModifier = Modifier.semantics { contentDescription = currentNote.description }
            val noteColor = if (currentNote.isEmphasized) LocalSecondAccentColor.current else MaterialTheme.colorScheme.onSurfaceVariant
            if (currentNote.mark == null) {
                Text(
                    modifier = noteModifier,
                    text = currentNote.text,
                    style = MaterialTheme.typography.labelMedium,
                    color = noteColor,
                )
            } else {
                IconLabel(
                    modifier = noteModifier,
                    text = currentNote.text,
                    mark = currentNote.mark,
                    color = noteColor,
                )
            }
        }
    }
}
