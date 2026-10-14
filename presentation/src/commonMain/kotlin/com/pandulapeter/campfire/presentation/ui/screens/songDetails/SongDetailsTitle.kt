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

import com.pandulapeter.campfire.presentation.ui.songView.firstSection.places
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import com.pandulapeter.campfire.chordpro.ChordProDuration
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_expand
import com.pandulapeter.campfire.presentation.resources.song_details_scroll_to_top
import com.pandulapeter.campfire.presentation.resources.song_details_song_info
import com.pandulapeter.campfire.presentation.resources.songs_key
import com.pandulapeter.campfire.presentation.ui.components.coverArt.APP_BAR_COVER_GAP
import com.pandulapeter.campfire.presentation.ui.components.coverArt.APP_BAR_COVER_SIZE
import com.pandulapeter.campfire.presentation.ui.components.coverArt.CoverArtImage
import com.pandulapeter.campfire.presentation.ui.components.list.PRESSED_HEADING_ALPHA
import com.pandulapeter.campfire.presentation.localization.textResource
import com.pandulapeter.campfire.presentation.ui.playing.PlayingOverridesSnapshot
import com.pandulapeter.campfire.presentation.ui.playing.songPlaybackOf
import com.pandulapeter.campfire.presentation.ui.rendering.SongRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

/**
 * The title block of the song details screen's app bar: the cover, the title and the artist of [currentSong], and the
 * key it sounds in. The title and the cover are the heading of the sheet of what the song is, so a tap on them opens it
 * once there is nothing left to scroll back to. The scroll is read here rather than in the screen's own body, so that
 * passing the top recomposes the bar's title alone.
 */
@Composable
internal fun SongDetailsTitle(
    currentSong: Song?,
    setlistFileName: String?,
    shouldShowChords: Boolean,
    chordSpelling: UserPreferences.ChordSpelling,
    playingOverrides: PlayingOverridesSnapshot,
    songRenderer: SongRenderer,
    showsCoverInBar: Boolean,
    currentPageScrollState: () -> ScrollState?,
    openCurrentSongInfo: (() -> Unit)?,
    coroutineScope: CoroutineScope,
) {
    val isScrolledToTop by remember { derivedStateOf { (currentPageScrollState()?.value ?: 0) == 0 } }
    val openSongInfoAtTop = openCurrentSongInfo?.takeIf { isScrolledToTop }
    val titleInteractionSource = remember { MutableInteractionSource() }
    val isTitlePressed by titleInteractionSource.collectIsPressedAsState()
    val titleAlpha by animateFloatAsState(if (isTitlePressed) PRESSED_HEADING_ALPHA else 1f)
    AnimatedContent(
        modifier = Modifier
            .titleTouchTarget(
                isEnabled = currentSong != null && (openSongInfoAtTop != null || !isScrolledToTop),
                interactionSource = titleInteractionSource,
                onClickLabel = stringResource(if (openSongInfoAtTop == null) Res.string.song_details_scroll_to_top else Res.string.song_details_song_info),
                onClick = {
                    if (openSongInfoAtTop == null) {
                        currentPageScrollState()?.let { coroutineScope.launch { it.animateScrollTo(0) } }
                    } else {
                        openSongInfoAtTop()
                    }
                },
            )
            .graphicsLayer { alpha = titleAlpha },
        targetState = currentSong,
        // Keyed by the file, so that only paging to another song cross-fades the block: the same song
        // arriving again (a tag added, a tempo set, a sync run) is recomposed in place, and what changed in
        // it animates on its own below.
        contentKey = { it?.fileName },
        transitionSpec = { fadeIn() togetherWith fadeOut() },
    ) { song ->
        val headerKey = headerKeyOf(
            song = song,
            setlistFileName = setlistFileName,
            shouldShowChords = shouldShowChords,
            overrides = playingOverrides,
            songRenderer = songRenderer,
            chordSpelling = chordSpelling,
        )
        // The duration only inside a setlist, as the song's card there says it, since a set is what is
        // timed by its songs; and, as there, with the chords switched off too, since the singer is timed by it alike.
        val headerDuration = song?.takeIf { setlistFileName != null }?.duration?.let(ChordProDuration::format)
        SongTitleBlock(
            song = song,
            headerKey = headerKey,
            headerDuration = headerDuration,
            showsCoverInBar = showsCoverInBar,
            isChevronKept = openCurrentSongInfo != null,
            isChevronShown = openSongInfoAtTop != null,
        )
    }
}

/**
 * What the song sounds like where it is being read, the way a song card says it: the key with the transposition and the
 * capo applied, so this is the key the band hears rather than the one the chords on the page spell. The tempo is left
 * to the song's own first section, a line below, which already says it. Worked out for the song the title's content was
 * composed for rather than for the current one, since a crossfade between two songs draws both at once. Lyrics only
 * mode says nothing about the key, as it says nothing in a row.
 */
internal fun headerKeyOf(
    song: Song?,
    setlistFileName: String?,
    shouldShowChords: Boolean,
    overrides: PlayingOverridesSnapshot,
    songRenderer: SongRenderer,
    chordSpelling: UserPreferences.ChordSpelling,
) = song?.takeIf { shouldShowChords && it.hasChords }?.let {
    val playback = songPlaybackOf(song = it, setlistFileName = setlistFileName, overrides = overrides)
    songRenderer.renderKey(
        song = it,
        transposition = playback.transposition,
        capo = playback.capo.fret,
        spelling = chordSpelling,
    )
}

/**
 * The cover, the title and the artist of [song], and its [headerKey] and [headerDuration] after them.
 *
 * @param isChevronKept Whether the room of the chevron saying that the title opens the sheet is kept, which it is wherever
 *   the sheet is offered at all.
 */
@Composable
private fun SongTitleBlock(
    song: Song?,
    headerKey: String?,
    headerDuration: String?,
    showsCoverInBar: Boolean,
    isChevronKept: Boolean,
    isChevronShown: Boolean,
) = Row(
    verticalAlignment = Alignment.CenterVertically,
) {
    // Only a window resized across the width that makes room for it, or a cover set or removed, animates:
    // one that opens the screen with the cover out or in shows it that way from its first frame. The
    // last address is kept while a removed cover leaves, and is the page's own, since this is
    // composed afresh for every song.
    val coverUrl = song?.coverArtUrl
    var lastCoverUrl by remember { mutableStateOf(coverUrl) }
    if (coverUrl != null) lastCoverUrl = coverUrl
    AnimatedVisibility(
        visible = showsCoverInBar && coverUrl != null,
        enter = fadeIn() + expandHorizontally(),
        exit = fadeOut() + shrinkHorizontally(),
    ) {
        lastCoverUrl?.let { url ->
            Crossfade(targetState = url) {
                CoverArtImage(
                    modifier = Modifier.padding(end = APP_BAR_COVER_GAP).size(APP_BAR_COVER_SIZE),
                    url = it,
                )
            }
        }
    }
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Edited in place (Edit song details) rather than paged to, so it fades in place.
            AnimatedContent(
                modifier = Modifier.weight(1f, fill = false),
                targetState = song?.title.orEmpty(),
                transitionSpec = { fadeIn() togetherWith fadeOut() },
            ) { title ->
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // What says that the title opens the sheet, shown only while a tap does: scrolled down,
            // a tap scrolls back to the top instead. Its room is kept while it is hidden wherever the
            // sheet is offered at all, so that a long title is not cut off afresh every time the
            // song passes its top.
            if (isChevronKept) {
                // A fade on the effects spring and a grow from half its size on the spatial one, which
                // overshoots a little as it arrives: the chevron pops in as an offer and recedes as the
                // song scrolls away, rather than collapsing to a point that reads as a glitch.
                                val chevronAlpha by animateFloatAsState(
                    targetValue = if (isChevronShown) 1f else 0f,
                    animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                )
                val chevronScale by animateFloatAsState(
                    targetValue = if (isChevronShown) 1f else HIDDEN_CHEVRON_SCALE,
                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                )
                Icon(
                    modifier = Modifier
                        .padding(start = APP_BAR_TITLE_CHEVRON_GAP)
                        .size(APP_BAR_TITLE_CHEVRON_SIZE)
                        .graphicsLayer {
                            alpha = chevronAlpha
                            scaleX = chevronScale
                            scaleY = chevronScale
                        },
                    painter = painterResource(Res.drawable.ic_expand),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Drawn for every song, blank or not, so that the bar is two lines tall whatever the
            // song says about itself: the whole title block is what the cover beside it is as tall
            // as, and a bar that changed height as the pager moved from a song with an artist to
            // one without would take the lyrics with it.
            AnimatedContent(
                modifier = Modifier.weight(1f, fill = false),
                targetState = song?.artist.orEmpty(),
                transitionSpec = { fadeIn() togetherWith fadeOut() },
            ) { artist ->
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            SongHeaderNote(
                text = headerKey,
                description = headerKey?.let { textResource(Res.string.songs_key, it) },
                isEmphasized = true,
                hasPrecedingContent = song?.artist?.isNotBlank() == true,
            )
            SongHeaderNote(
                text = headerDuration,
                isEmphasized = false,
                hasPrecedingContent = song?.artist?.isNotBlank() == true || headerKey != null,
            )
        }
    }
}

/**
 * The app bar's title as the touch target of what the caller does with a tap on it — scrolling the song back to its
 * top, the way tapping the bar does on a phone, and opening the sheet of what the song is once it is already there.
 * It is the whole of the bar that nothing else takes: as wide as the room the bar leaves the title and as tall
 * as the bar, so that a tap anywhere on its empty part counts. The bar pads that room by [TITLE_TOUCH_HORIZONTAL_OUTSET]
 * at either end and centers the title in its height, and the target reaches out over both while reporting only the room
 * itself, so the bar lays out the back button, the title and the actions exactly as it would without it. The outset
 * stops where the touch targets of the back button and the actions begin: the bar places the title after the back
 * button, so a target reaching any further would take its presses from it.
 *
 * It draws no indication: a press lighting up most of the bar reads as the bar itself reacting rather than as a button,
 * and a highlight around the title alone would need room the bar does not leave it, starting 4dp after the back
 * button's target. The caller dims the title through [interactionSource] instead, the way a text button on iOS answers
 * a press, which needs no room around it at all; a pointer is shown the hand over it while a click does anything.
 */
private fun Modifier.titleTouchTarget(
    isEnabled: Boolean,
    interactionSource: MutableInteractionSource,
    onClickLabel: String,
    onClick: () -> Unit,
) = layout { measurable, constraints ->
    val horizontalOutset = TITLE_TOUCH_HORIZONTAL_OUTSET.roundToPx()
    val verticalOutset = TITLE_TOUCH_VERTICAL_OUTSET.roundToPx()
    val placeable = measurable.measure(constraints.offset(horizontal = horizontalOutset * 2, vertical = verticalOutset * 2))
    layout(placeable.width - horizontalOutset * 2, placeable.height - verticalOutset * 2) {
        placeable.placeRelative(-horizontalOutset, -verticalOutset)
    }
}
    .then(if (isEnabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
    .clickable(
        interactionSource = interactionSource,
        indication = null,
        enabled = isEnabled,
        onClickLabel = onClickLabel,
        onClick = onClick,
    )
    .padding(horizontal = TITLE_TOUCH_HORIZONTAL_OUTSET, vertical = TITLE_TOUCH_VERTICAL_OUTSET)
    .fillMaxWidth()

private val TITLE_TOUCH_HORIZONTAL_OUTSET = 4.dp // The padding the bar puts around its title.
private val APP_BAR_TITLE_CHEVRON_SIZE = 20.dp // A little under the titleMedium line it follows.
private val APP_BAR_TITLE_CHEVRON_GAP = 2.dp // The chevron's own artwork leaves the rest of the gap after the title.
private const val HIDDEN_CHEVRON_SCALE = 0.5f
private val TITLE_TOUCH_VERTICAL_OUTSET = 12.dp // From the two lines of title, 40dp, to the bar's 64dp.
