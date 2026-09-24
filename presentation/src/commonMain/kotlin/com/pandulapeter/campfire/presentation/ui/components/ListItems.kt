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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.add_demo_songs
import com.pandulapeter.campfire.presentation.resources.error_no_data
import com.pandulapeter.campfire.presentation.resources.error_no_data_hint
import com.pandulapeter.campfire.presentation.resources.ic_archive
import com.pandulapeter.campfire.presentation.resources.ic_dot
import com.pandulapeter.campfire.presentation.resources.ic_drag_handle
import com.pandulapeter.campfire.presentation.resources.ic_error
import com.pandulapeter.campfire.presentation.resources.ic_open_in_new
import com.pandulapeter.campfire.presentation.resources.ic_search
import com.pandulapeter.campfire.presentation.resources.ic_songs
import com.pandulapeter.campfire.presentation.resources.ic_tune
import com.pandulapeter.campfire.presentation.resources.ic_setlists
import com.pandulapeter.campfire.presentation.resources.retry
import com.pandulapeter.campfire.presentation.resources.setlists_all_hidden
import com.pandulapeter.campfire.presentation.resources.setlists_all_hidden_hint
import com.pandulapeter.campfire.presentation.resources.setlists_create_setlist
import com.pandulapeter.campfire.presentation.resources.setlists_missing_song
import com.pandulapeter.campfire.presentation.resources.setlists_reorder
import com.pandulapeter.campfire.presentation.resources.setlists_no_data
import com.pandulapeter.campfire.presentation.resources.setlists_no_data_hint
import com.pandulapeter.campfire.presentation.resources.setlists_no_search_results
import com.pandulapeter.campfire.presentation.resources.setlists_no_search_results_hint
import com.pandulapeter.campfire.presentation.resources.songs_all_hidden
import com.pandulapeter.campfire.presentation.resources.songs_all_hidden_hint
import com.pandulapeter.campfire.presentation.resources.songs_empty_hint
import com.pandulapeter.campfire.presentation.resources.import_files
import com.pandulapeter.campfire.presentation.resources.songs_create_song
import com.pandulapeter.campfire.presentation.resources.songs_empty_title
import com.pandulapeter.campfire.presentation.resources.songs_key
import com.pandulapeter.campfire.presentation.resources.songs_lyrics_only
import com.pandulapeter.campfire.presentation.resources.songs_no_search_results
import com.pandulapeter.campfire.presentation.resources.songs_no_search_results_hint
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

/**
 * @param index The song's place in the setlist it is listed in, prefixed to its title.
 *   Null on the screens where a song is not in an order of anyone's making and a number would only claim it was.
 * @param key The key the song sounds in where it is listed, which is a different key in every setlist that
 *   transposes it (`CampfireViewModel.renderKey`), drawn next to the artist. Null for a file that declares none.
 * @param shouldShowChords False under lyrics only mode, where the row says nothing about chords at all: not the
 *   key, and not the "Lyrics only" marker either, which only tells this song from the others while the others are
 *   showing chords.
 * @param labelsOnEverySong The tags and languages the row leaves off, because every song in the library carries
 *   them and a label that is on every row tells the reader nothing about this one.
 * @param onLongClick A shortcut to the row's overflow menu, on the touch platforms where holding a row is a natural
 *   way to ask what can be done to it.
 * @param cardPadding The card's space from the edges of its grid cell, adjusted for inner columns in wide grids.
 * @param containerColor The card color, raised while the row is dragged ([draggedListItemContainerColor]).
 * @param actions The trailing content of the row, which is the overflow button of the song's actions
 *   ([SongActionsButton]).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SongListItem(
    modifier: Modifier = Modifier,
    song: Song,
    index: Int? = null,
    key: String? = null,
    shouldShowChords: Boolean = true,
    labelsOnEverySong: CampfireViewModel.LabelsOnEverySong,
    cardPadding: PaddingValues = PaddingValues(horizontal = SONG_CARD_OUTER_PADDING, vertical = SONG_CARD_VERTICAL_PADDING),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    shadowElevation: Dp = 0.dp,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
) {
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
    val languages = song.languages.filterNot { it in labelsOnEverySong.languages }
    val tags = song.tags.filterNot { it.lowercase() in labelsOnEverySong.tags }
    Surface(
        modifier = modifier.fillMaxWidth().padding(cardPadding),
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        shadowElevation = shadowElevation,
    ) {
        CenteredSongCardContent(
            modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
            actions = actions,
            headlineContent = {
                ListItemHeadline(text = songCardTitle(song.title, index))
            },
            // Songs written in the app need no artist, and an empty second line would just make the row taller. Nothing
            // here shares the title's line: a title is the longest thing on the row and the one that must never be
            // pushed out of sight, while the artist is short enough to leave the key room next to it. The languages and
            // tags go under both, since a row of those is as long as somebody chose to make it.
            supportingContent = if (song.artist.isBlank() && note == null && languages.isEmpty() && tags.isEmpty()) {
                null
            } else {
                {
                    Column {
                        if (song.artist.isNotBlank() || note != null) {
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
                                // The note changes under the reader: a transposition renames the key, and lyrics only
                                // mode takes the place away altogether. So it is crossfaded where it stands and the line
                                // closes up around it, rather than the row being redrawn around the change. The color is
                                // resolved inside rather than carried by the state, since the scheme is interpolated on
                                // every frame of a theme change and each of those frames would start another crossfade.
                                AnimatedContent(
                                    targetState = note,
                                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                                ) { currentNote ->
                                    if (currentNote != null) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            // The dot separates the note from the artist, so a song that names no artist
                                            // starts the line with the note itself rather than with a separator before
                                            // nothing. Neither it nor the note carries padding of its own: the glyph is a
                                            // 4dp dot in the middle of a 24dp icon, so the box it sits in is the gap
                                            // already, and the same gap on both sides of it - anything added here is
                                            // added to one side only.
                                            if (song.artist.isNotBlank()) {
                                                Icon(
                                                    painter = painterResource(Res.drawable.ic_dot),
                                                    contentDescription = null,
                                                )
                                            }
                                            Text(
                                                modifier = Modifier.semantics { contentDescription = currentNote.description },
                                                text = currentNote.text,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = if (currentNote.isEmphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (languages.isNotEmpty() || tags.isNotEmpty()) {
                            SongLabels(
                                modifier = Modifier.padding(top = if (song.artist.isBlank() && note == null) 0.dp else 4.dp),
                                languages = languages,
                                tags = tags,
                            )
                        }
                    }
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
 * The one thing a song row says about chords, next to the artist: the key the song sounds in where it is listed,
 * drawn in the accent color the song details header keeps for what is played, or that the file has no chords at all.
 * Never both, since the viewer names no key for a song there is nothing to play and a row that named one would be
 * contradicting the screen it opens.
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
 * A setlist entry whose file is no longer in the library: it cannot be opened, but it can still be removed, so it is
 * shown greyed out rather than silently dropped - a setlist that quietly loses a song would look like the app lost it.
 */
@Composable
internal fun MissingSongListItem(
    modifier: Modifier = Modifier,
    index: Int,
    songFileName: String,
    cardPadding: PaddingValues = PaddingValues(horizontal = SONG_CARD_OUTER_PADDING, vertical = SONG_CARD_VERTICAL_PADDING),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    shadowElevation: Dp = 0.dp,
    actions: (@Composable () -> Unit)? = null,
) = Surface(
    modifier = modifier.fillMaxWidth().padding(cardPadding).alpha(0.5f),
    shape = MaterialTheme.shapes.medium,
    color = containerColor,
    shadowElevation = shadowElevation,
) {
    CenteredSongCardContent(
        actions = actions,
        headlineContent = {
            ListItemHeadline(text = songCardTitle(songFileName, index))
        },
        supportingContent = {
            Text(
                text = stringResource(Res.string.setlists_missing_song),
                fontStyle = FontStyle.Italic,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

/** Material lays out three-line list items with a top-aligned trailing slot. Song cards center their actions. */
@Composable
private fun CenteredSongCardContent(
    modifier: Modifier = Modifier,
    actions: (@Composable () -> Unit)?,
    headlineContent: @Composable () -> Unit,
    supportingContent: (@Composable () -> Unit)?,
) = Layout(
    modifier = modifier.fillMaxWidth(),
    contents = listOf(
        { actions?.let { ListItemActions(content = it) } },
        {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = headlineContent,
                supportingContent = supportingContent,
            )
        },
    ),
) { (actionMeasurables, bodyMeasurables), constraints ->
    val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
    val actionPlaceable = actionMeasurables.firstOrNull()?.measure(looseConstraints)
    val actionWidth = actionPlaceable?.width ?: 0
    val actionReservation = if (actionPlaceable == null) 0 else actionWidth + LIST_ITEM_TRAILING_GAP.roundToPx()
    val bodyWidth = (constraints.maxWidth - actionReservation).coerceAtLeast(0)
    val bodyPlaceable = bodyMeasurables.single().measure(looseConstraints.copy(minWidth = bodyWidth, maxWidth = bodyWidth))
    val height = maxOf(bodyPlaceable.height, actionPlaceable?.height ?: 0)
        .coerceIn(constraints.minHeight, constraints.maxHeight)
    layout(constraints.maxWidth, height) {
        bodyPlaceable.placeRelative(0, (height - bodyPlaceable.height) / 2)
        actionPlaceable?.placeRelative(constraints.maxWidth - LIST_ITEM_KEYLINE.roundToPx() - actionWidth, (height - actionPlaceable.height) / 2)
    }
}

/**
 * Whatever a card carries at its end: the overflow button, and on the setlists screen the drag handle beside it.
 * Move the controls slightly toward the card's edge without changing the width reserved for them in the body.
 */
@Composable
private fun ListItemActions(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) = Box(
    modifier = modifier.offset(x = LIST_ITEM_TRAILING_KEYLINE_ADJUSTMENT),
) {
    content()
}

/**
 * The title remains one line even when its setlist number has grown to three digits.
 */
@Composable
private fun ListItemHeadline(
    text: String,
) = Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)

/** A setlist's zero-based position is shown to players as a one-based prefix. */
private fun songCardTitle(title: String, index: Int?): String = if (index == null) title else "${index + 1} - $title"

/**
 * The grip that says a row can be dragged somewhere else, placed in front of the row's overflow button so that the
 * button stays on the keyline every other song row keeps it on. It is an icon inside a plain box rather than an
 * [IconButton], because it is never pressed on its own: the caller is the one that puts the reorderable drag
 * modifier on it, and a button's ripple would promise a tap that does nothing. The box is what makes it big enough to
 * catch a finger, so the drag modifier has to go on [modifier] rather than on the icon. The icon sits at the end of
 * the box, against the button, since the button's own padding is already more of a gap than the two need.
 */
@Composable
internal fun DragHandle(
    modifier: Modifier = Modifier,
) = Box(
    modifier = modifier.size(width = DRAG_HANDLE_WIDTH, height = DRAG_HANDLE_HEIGHT),
    contentAlignment = Alignment.CenterEnd,
) {
    Icon(
        painter = painterResource(Res.drawable.ic_drag_handle),
        contentDescription = stringResource(Res.string.setlists_reorder),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * The fraction of the outgoing header still visible in the grid, and how far into the pinned position at the top of
 * the grid the header has come - which is where the app bar's buttons are over it.
 */
internal data class SectionHeaderState(
    val visibleFraction: Float,
    val pinnedFraction: Float,
)

/** The outgoing pinned header's position in the grid, including the part already above its viewport. */
internal data class PushedSectionHeader(
    val key: Any,
    val index: Int,
    val offset: IntOffset,
    val width: Int,
    val pushedDistance: Int,
    val visibleFraction: Float,
)

@Composable
internal fun pushedSectionHeader(listState: LazyGridState, contentType: String): PushedSectionHeader? {
    val pushedHeader by remember(listState, contentType) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val top = layoutInfo.viewportStartOffset
            layoutInfo.visibleItemsInfo.firstOrNull { item ->
                item.contentType == contentType && item.size.height > 0 && item.offset.y < top && item.offset.y + item.size.height > top
            }?.let { item ->
                PushedSectionHeader(
                    key = item.key,
                    index = item.index,
                    offset = item.offset,
                    width = item.size.width,
                    pushedDistance = top - item.offset.y,
                    visibleFraction = (item.offset.y + item.size.height - top).toFloat() / item.size.height,
                )
            }
        }
    }
    return pushedHeader
}

/** The scroll fractions that fade the outgoing header as the next one pushes it away, and narrow a pinned one. */
@Composable
internal fun sectionHeaderState(listState: LazyGridState, headerIndex: Int): SectionHeaderState {
    val state by remember(listState, headerIndex) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val header = layoutInfo.visibleItemsInfo.firstOrNull { it.index == headerIndex }
            if (header == null || header.size.height <= 0) {
                SectionHeaderState(visibleFraction = 1f, pinnedFraction = 0f)
            } else {
                val top = layoutInfo.viewportStartOffset
                SectionHeaderState(
                    visibleFraction = ((header.offset.y + header.size.height - top).toFloat() / header.size.height).coerceIn(0f, 1f),
                    pinnedFraction = 1f - ((header.offset.y - top).toFloat() / header.size.height).coerceIn(0f, 1f),
                )
            }
        }
    }
    return state
}

/**
 * A sticky section row: the section's name, and a setlist's menu, with nothing behind them and no divider under them.
 * The cards are what makes room for a header rather than a background of its own: they are faded out entirely under
 * the row of the one pinned at the top of the list and fade back in below it ([ListTopFade]), so the name is always
 * read against the screen's background, and it and the app bar's pill of buttons next to it are the only things at
 * the top of the list. Only the header's content takes a touch, laid out as a pill that the press is drawn in; the
 * rest of the row leaves it to whatever is under it.
 *
 * The list screens have no title in their app bar, and the header pinned at the top of the list is what stands in its
 * place, under the bar's buttons: so the row is as tall as that bar ([LIST_APP_BAR_HEIGHT]) and the pill as tall as the
 * bar's pill, its text on one line in the style of the settings screen's tab labels. The pill is as wide as the cards,
 * and narrows only to make room for the bar's buttons ([AppBarOverlap.reach]) as the header comes into the pinned place
 * ([SectionHeaderState.pinnedFraction], eased), so a pinned header never runs under them - its text cut off sooner,
 * and a setlist's menu, which ends the pill, carried up to the buttons. It widens again along the same eased curve as
 * an opening search moves the list down out from under the buttons ([AppBarOverlap.coverage]), and narrows along it
 * as a closing one brings the list back up.
 *
 * The row extends through the grid's end padding, beneath the fast scroller, so that the reach and the pill's end are
 * measured from the same edge. A decorative copy can be drawn outside the grid while the row is pushed up
 * ([pushedDistancePx]); its content lags behind the row there and fades with [contentOpacity].
 *
 * @param action The button at the end of the pill, handed the modifier that keeps it from taking the focus
 *   ([unfocusable]).
 * @param appBarOverlap How far in from the row's end edge the app bar's buttons reach while this row is pinned under
 *   them, and how much of the pinned place they still cover.
 */
@Composable
internal fun SectionHeader(
    modifier: Modifier = Modifier,
    text: String,
    state: SectionHeaderState,
    endPadding: Dp,
    icon: Painter? = null,
    iconContentDescription: String? = null,
    onClick: (() -> Unit)?,
    action: (@Composable (modifier: Modifier) -> Unit)? = null,
    actionIcon: Painter? = null,
    opacity: Float = 1f,
    contentOpacity: Float = 1f,
    pushedDistancePx: Int = 0,
    appBarOverlap: AppBarOverlap,
) = Box(
    modifier = modifier
        .extendIntoEndPadding(endPadding)
        .graphicsLayer {
            alpha = opacity
            clip = false
        }
        .fillMaxWidth()
        .defaultMinSize(minHeight = LIST_APP_BAR_HEIGHT),
    contentAlignment = Alignment.CenterStart,
) {
    val hasAction = action != null || actionIcon != null
    val pillModifier = Modifier
        .padding(start = SONG_CARD_OUTER_PADDING)
        .layout { measurable, constraints ->
            // The cards end at the scroller's column, and a pinned header ends where the bar's buttons begin.
            val cardsEndInset = (endPadding + SONG_CARD_OUTER_PADDING).toPx()
            val pinnedEndInset = maxOf(cardsEndInset, (appBarOverlap.reach + SECTION_HEADER_APP_BAR_GAP).toPx())
            // Eased rather than linear, since the fraction is the scroll position itself: the header gives way
            // gently as it starts coming into the bar's place and settles into the room it has left the same way.
            // The list moving down under an opening search takes a pinned header out of that place, so the same
            // curve is run on how much of it the buttons still cover, and a pinned one widens and narrows along
            // the path it narrowed along as it was scrolled up.
            val coveredFraction = FastOutSlowInEasing.transform(state.pinnedFraction) *
                FastOutSlowInEasing.transform(appBarOverlap.coverage)
            val endInset = cardsEndInset + (pinnedEndInset - cardsEndInset) * coveredFraction
            val width = (constraints.maxWidth - endInset.roundToInt()).coerceAtLeast(0)
            val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
            layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
        }
        .graphicsLayer {
            alpha = contentOpacity
            // The outgoing pill lags the row slightly, then disappears before reaching the bar's controls.
            translationY = pushedDistancePx * SECTION_HEADER_CONTENT_PARALLAX_FRACTION
        }
    val pillContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .heightIn(min = SECTION_HEADER_PILL_HEIGHT)
                .padding(start = SECTION_HEADER_PILL_START_PADDING, end = if (hasAction) SECTION_HEADER_PILL_ACTION_END_PADDING else SECTION_HEADER_PILL_START_PADDING),
            // The action keeps to the end of the row, however short the name before it.
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Keep the painter through its exit animation when a setlist is archived from this row's own menu.
                var lastIcon by remember { mutableStateOf(icon) }
                icon?.let { lastIcon = it }
                AnimatedVisibility(
                    visible = icon != null,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally(),
                ) {
                    lastIcon?.let { painter ->
                        Icon(
                            modifier = Modifier.padding(end = 8.dp).size(20.dp),
                            painter = painter,
                            contentDescription = iconContentDescription,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    modifier = Modifier.weight(1f, fill = false).padding(end = if (hasAction) SECTION_HEADER_TEXT_GAP else 0.dp),
                    text = text,
                    // The settings screen's tab labels, the other thing that stands at the top of a top level screen.
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (actionIcon != null) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(painter = actionIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                action?.invoke(Modifier.unfocusable())
            }
        }
    }
    if (onClick == null) {
        // The decorative copy takes no touches, so a plain box rather than a surface, which would. It hands its
        // content the pill's width as a minimum the way a surface does, or the row would wrap the name and pull a
        // setlist's menu in next to it.
        Box(modifier = pillModifier, propagateMinConstraints = true) { pillContent() }
    } else {
        // Nothing behind it: the cards under a pinned header are faded out entirely (see ListTopFade), and the pill's
        // shape is only what the press is drawn in.
        Surface(
            modifier = pillModifier.unfocusable(),
            onClick = onClick,
            shape = CircleShape,
            color = Color.Transparent,
        ) { pillContent() }
    }
}

/**
 * Keeps a [SectionHeader]'s pill and its action from taking the focus, which a click hands them on the desktop and the
 * web: a lazy grid keeps its focused item composed and placed after it has scrolled away, and a header held that way
 * upsets how the grid pins the ones after it, which are then drawn nowhere once they reach the top. The pill only
 * scrolls to its section and the action opens a menu, neither of which the keyboard needs a stop in the list for.
 */
private fun Modifier.unfocusable() = focusProperties { canFocus = false }

/** Paint the header into the grid's end padding without changing the width the grid assigns its item. */
private fun Modifier.extendIntoEndPadding(endPadding: Dp) = layout { measurable, constraints ->
    val extraWidth = endPadding.roundToPx()
    val width = constraints.maxWidth
    val placeable = measurable.measure(constraints.copy(minWidth = width + extraWidth, maxWidth = width + extraWidth))
    layout(width, placeable.height) { placeable.placeRelative(0, 0) }
}

/**
 * Title of a group of controls: the label above a [SegmentedChoice] or a set of switches, in the same color as a
 * [SectionHeader] pill but without the pill, since it names a part of a section rather than a section.
 *
 * [contentPadding] defaults to the gaps a sheet or a side panel wants, where the groups are the whole content; a
 * list that has a rhythm of its own passes its own gaps instead.
 */
@Composable
internal fun SettingsSectionTitle(
    modifier: Modifier = Modifier,
    text: String,
    contentPadding: PaddingValues = PaddingValues(start = LIST_ITEM_KEYLINE, end = LIST_ITEM_KEYLINE, top = 24.dp, bottom = 8.dp),
) = Text(
    modifier = modifier.fillMaxWidth().padding(contentPadding),
    text = text,
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.primary,
)

@Composable
internal fun SwitchListItem(
    modifier: Modifier = Modifier,
    title: String,
    description: String? = null,
    isChecked: Boolean,
    isEnabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) = ListItem(
    modifier = modifier
        .toggleable(value = isChecked, enabled = isEnabled, role = Role.Switch, onValueChange = onCheckedChange)
        .alpha(if (isEnabled) 1f else 0.5f),
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    headlineContent = { Text(title) },
    supportingContent = description?.let { { Text(it) } },
    trailingContent = { Switch(checked = isChecked, enabled = isEnabled, onCheckedChange = null) },
)

@Composable
internal fun CheckboxListItem(
    modifier: Modifier = Modifier,
    title: String,
    description: String? = null,
    isChecked: Boolean,
    isEnabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) = ListItem(
    modifier = modifier
        .toggleable(value = isChecked, enabled = isEnabled, role = Role.Checkbox, onValueChange = onCheckedChange)
        .alpha(if (isEnabled) 1f else 0.5f),
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    headlineContent = { Text(title) },
    supportingContent = description?.let { { Text(it) } },
    leadingContent = { Checkbox(checked = isChecked, enabled = isEnabled, onCheckedChange = null) },
)

@Composable
internal fun RadioListItem(
    modifier: Modifier = Modifier,
    title: String,
    description: String? = null,
    isSelected: Boolean,
    onSelected: () -> Unit,
) = ListItem(
    modifier = modifier.selectable(selected = isSelected, role = Role.RadioButton, onClick = onSelected),
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    headlineContent = { Text(title) },
    supportingContent = description?.let { { Text(it) } },
    leadingContent = { RadioButton(selected = isSelected, onClick = null) },
)

/**
 * @param isEnabled False for a link that leads nowhere yet, which stays in its list, dimmed, with [description]
 *   saying why: a store the app has not reached.
 * @param onClick Null for a row of a list of links that is not one itself - the web build's own entry, whose address
 *   is the page it is already on - which is drawn at full strength, but with nothing to tap and no mark saying
 *   that it opens something.
 */
@Composable
internal fun LinkListItem(
    modifier: Modifier = Modifier,
    title: String,
    description: String? = null,
    icon: Painter,
    isEnabled: Boolean = true,
    onClick: (() -> Unit)?,
) = ListItem(
    modifier = (if (onClick == null) modifier else modifier.clickable(enabled = isEnabled, onClick = onClick))
        .alpha(if (isEnabled) 1f else 0.5f),
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    headlineContent = { Text(title) },
    supportingContent = description?.let { { Text(it) } },
    leadingContent = { Icon(painter = icon, contentDescription = null) },
    trailingContent = onClick?.let {
        {
            Icon(
                painter = painterResource(Res.drawable.ic_open_in_new),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    },
)

/**
 * @param isEmphasized Whether the row is an invitation to do something ("New setlist") rather than one entry of a
 *   list of things that can be done, which is what the actions of a song or of the library are.
 */
@Composable
internal fun ActionListItem(
    modifier: Modifier = Modifier,
    title: String,
    icon: Painter,
    isEnabled: Boolean = true,
    isEmphasized: Boolean = true,
    onClick: () -> Unit,
) = ListItem(
    modifier = modifier.clickable(enabled = isEnabled, onClick = onClick).alpha(if (isEnabled) 1f else 0.5f),
    colors = if (isEmphasized) {
        ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = MaterialTheme.colorScheme.primary,
            leadingIconColor = MaterialTheme.colorScheme.primary,
        )
    } else {
        ListItemDefaults.colors(containerColor = Color.Transparent)
    },
    headlineContent = { Text(title) },
    leadingContent = { Icon(painter = icon, contentDescription = null) },
)

/**
 * What a song list shows in place of its songs: the indicator of a load that is still running, the error of one
 * that failed with nothing cached to fall back on, or an empty state. A list that has no data is always in one of
 * these, so a load that never arrives ends in something the user can act on rather than in an endless indicator.
 */
/**
 * All of these share one slot in their list, so they cross fade into each other rather than being swapped in a
 * single frame: the load that ends in an empty library is one continuous thing to look at, not two.
 *
 * @param onNewSong Null where filling the library is not this list's business, which hides all of its offers to.
 * @param onNewSetlist The same for the setlists. It is a parameter of its own rather than one "create" for whichever
 *   list is empty, because each list creates a different thing.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ListPlaceholder(
    modifier: Modifier = Modifier,
    placeholder: CampfireViewModel.Placeholder,
    onRetry: () -> Unit,
    onNewSong: (() -> Unit)? = null,
    onNewSetlist: (() -> Unit)? = null,
    onDemoLibrary: (() -> Unit)? = null,
    onImport: (() -> Unit)? = null,
) = AnimatedContent(
    modifier = modifier,
    targetState = placeholder,
    transitionSpec = { fadeIn() togetherWith fadeOut() },
) { currentPlaceholder ->
    when (currentPlaceholder) {
        CampfireViewModel.Placeholder.LOADING -> Box(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            ContainedLoadingIndicator()
        }

        CampfireViewModel.Placeholder.ERROR -> EmptyState(
            icon = painterResource(Res.drawable.ic_error),
            title = stringResource(Res.string.error_no_data),
            hint = stringResource(Res.string.error_no_data_hint),
            actions = listOf(EmptyStateAction(text = stringResource(Res.string.retry), onClick = onRetry)),
        )

        // All three offers stand or fall with onNewSong: they are the three ways of filling a library, and a
        // screen with no business offering any of them - performance mode is on - passes none of them. The demo
        // songs come last of the three: they are the way out for somebody who wants neither of the other two.
        CampfireViewModel.Placeholder.NO_SONGS -> EmptyState(
            icon = painterResource(Res.drawable.ic_songs),
            title = stringResource(Res.string.songs_empty_title),
            hint = stringResource(Res.string.songs_empty_hint),
            actions = onNewSong?.let { newSong ->
                listOf(
                    EmptyStateAction(text = stringResource(Res.string.songs_create_song), onClick = newSong),
                    EmptyStateAction(text = stringResource(Res.string.import_files), onClick = onImport),
                    EmptyStateAction(text = stringResource(Res.string.add_demo_songs), onClick = onDemoLibrary),
                )
            }.orEmpty(),
        )

        // The same two first offers as an empty library, and the same two as the screen's own "New setlist" menu,
        // which leaves the bar while this is up. There are no demo setlists to add on their own: the one the app is
        // shipped with names demo songs, and it arrives with them from the songs screen.
        CampfireViewModel.Placeholder.NO_SETLISTS -> EmptyState(
            icon = painterResource(Res.drawable.ic_setlists),
            title = stringResource(Res.string.setlists_no_data),
            hint = stringResource(Res.string.setlists_no_data_hint),
            actions = onNewSetlist?.let { newSetlist ->
                listOf(
                    EmptyStateAction(text = stringResource(Res.string.setlists_create_setlist), onClick = newSetlist),
                    EmptyStateAction(text = stringResource(Res.string.import_files), onClick = onImport),
                )
            }.orEmpty(),
        )

        CampfireViewModel.Placeholder.ALL_SETLISTS_HIDDEN -> EmptyState(
            icon = painterResource(Res.drawable.ic_archive),
            title = stringResource(Res.string.setlists_all_hidden),
            hint = stringResource(Res.string.setlists_all_hidden_hint),
        )

        CampfireViewModel.Placeholder.ALL_SONGS_HIDDEN -> EmptyState(
            icon = painterResource(Res.drawable.ic_tune),
            title = stringResource(Res.string.songs_all_hidden),
            hint = stringResource(Res.string.songs_all_hidden_hint),
        )

        CampfireViewModel.Placeholder.NO_MATCHING_SONGS -> EmptyState(
            icon = painterResource(Res.drawable.ic_search),
            title = stringResource(Res.string.songs_no_search_results),
            hint = stringResource(Res.string.songs_no_search_results_hint),
        )

        CampfireViewModel.Placeholder.NO_MATCHING_SETLISTS -> EmptyState(
            icon = painterResource(Res.drawable.ic_search),
            title = stringResource(Res.string.setlists_no_search_results),
            hint = stringResource(Res.string.setlists_no_search_results_hint),
        )
    }
}

/**
 * Whether a list screen's "New" menu belongs in its app bar while this placeholder is shown (or none is). The menu
 * waits for the list to have been read rather than appearing over the loading indicator and going away again a moment
 * later, and it stays away from the empty state and the error, both of which offer their own buttons for the same
 * thing.
 */
internal val CampfireViewModel.Placeholder?.allowsNewItemMenu
    get() = when (this) {
        null,
        CampfireViewModel.Placeholder.ALL_SONGS_HIDDEN,
        CampfireViewModel.Placeholder.NO_MATCHING_SONGS,
        CampfireViewModel.Placeholder.ALL_SETLISTS_HIDDEN,
        CampfireViewModel.Placeholder.NO_MATCHING_SETLISTS -> true

        CampfireViewModel.Placeholder.LOADING,
        CampfireViewModel.Placeholder.ERROR,
        CampfireViewModel.Placeholder.NO_SONGS,
        CampfireViewModel.Placeholder.NO_SETLISTS -> false
    }

/**
 * One of the buttons under an [EmptyState]'s text. The first of them is filled and the rest are outlined, so that a
 * list of them reads as the thing to do here followed by the other things that could be done instead.
 *
 * @param onClick Null leaves the button visible but disabled, for an action the app will be able to offer but
 *   cannot yet.
 */
internal data class EmptyStateAction(
    val text: String,
    val onClick: (() -> Unit)?,
)

/**
 * @param actions The buttons under the hint. Empty leaves the state text only, which is what a list that is empty
 *   for a good reason gets: there is nothing to retry.
 */
@Composable
internal fun EmptyState(
    modifier: Modifier = Modifier,
    icon: Painter,
    title: String,
    /** Null when the title already says everything, e.g. for a song file that is simply still empty. */
    hint: String? = null,
    actions: List<EmptyStateAction> = emptyList(),
) = Column(
    // Always the full width, so that the text is centered on the list rather than on itself.
    modifier = modifier.fillMaxWidth().padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
) {
    Icon(
        modifier = Modifier.padding(bottom = 16.dp).alpha(0.6f),
        painter = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
    )
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
    )
    if (hint != null) {
        Text(
            modifier = Modifier.padding(top = 4.dp),
            text = hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
    if (actions.isNotEmpty()) {
        // The window rather than the space this is being laid out in: an empty state sits in a lazy list, whose
        // items are measured with no height at all to compare a width against.
        val windowSize = LocalWindowInfo.current.containerSize
        if (windowSize.height > windowSize.width) {
            // One under the other and all the same width, which is what a portrait window has room for: side by
            // side these wrap into a ragged two and one, and three labels this long read as a list rather than as
            // a row anyway.
            Column(
                modifier = Modifier.padding(top = 16.dp).widthIn(max = EMPTY_STATE_ACTION_WIDTH),
                verticalArrangement = Arrangement.spacedBy(EMPTY_STATE_ACTION_GAP),
            ) {
                actions.forEachIndexed { index, action ->
                    EmptyStateActionButton(
                        modifier = Modifier.fillMaxWidth(),
                        action = action,
                        isEmphasized = index == 0,
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(EMPTY_STATE_ACTION_GAP),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                actions.forEachIndexed { index, action ->
                    EmptyStateActionButton(action = action, isEmphasized = index == 0)
                }
            }
        }
    }
}

/** @param isEmphasized Whether this is the first of an [EmptyState]'s actions, which is the filled one. */
@Composable
private fun EmptyStateActionButton(
    modifier: Modifier = Modifier,
    action: EmptyStateAction,
    isEmphasized: Boolean,
) = if (isEmphasized) {
    Button(
        modifier = modifier,
        enabled = action.onClick != null,
        onClick = { action.onClick?.invoke() },
    ) {
        Text(action.text)
    }
} else {
    OutlinedButton(
        modifier = modifier,
        enabled = action.onClick != null,
        onClick = { action.onClick?.invoke() },
    ) {
        Text(action.text)
    }
}

/** The gap between two of an [EmptyState]'s buttons. */
private val EMPTY_STATE_ACTION_GAP = 8.dp

/** How wide a stacked column of [EmptyState] buttons grows, so that a portrait tablet does not stretch them. */
private val EMPTY_STATE_ACTION_WIDTH = 280.dp

/**
 * The x position the text of a [ListItem] starts at, before the card's own outer inset.
 */
private val LIST_ITEM_KEYLINE = 16.dp

/**
 * How far a card's trailing controls move toward its edge from the inset `ListItem` gives them.
 */
private val LIST_ITEM_TRAILING_KEYLINE_ADJUSTMENT = 8.dp

/**
 * The width of a [DragHandle]'s touch target. It is narrower than the `IconButton` after it, and can be, since
 * the handle is not a button: the width it gives up is empty space around an icon rather than anything that can be
 * pressed, and the drag it offers is on the row's own long press as well.
 */
private val DRAG_HANDLE_WIDTH = 32.dp

/** The height of a [DragHandle], which is a full touch target since it is the one thing on the row that is dragged. */
private val DRAG_HANDLE_HEIGHT = 48.dp
private val LIST_ITEM_TRAILING_GAP = 16.dp

/** Facing card edges each contribute 4dp, matching the 4dp above and below each card. */
internal fun songCardPadding(itemIndex: Int, columnCount: Int): PaddingValues {
    val column = itemIndex % columnCount
    return PaddingValues(
        start = if (column == 0) SONG_CARD_OUTER_PADDING else SONG_CARD_INNER_PADDING,
        end = if (column == columnCount - 1) SONG_CARD_OUTER_PADDING else SONG_CARD_INNER_PADDING,
        top = SONG_CARD_VERTICAL_PADDING,
        bottom = SONG_CARD_VERTICAL_PADDING,
    )
}

private val SONG_CARD_OUTER_PADDING = 8.dp
private val SONG_CARD_INNER_PADDING = 4.dp
private val SONG_CARD_VERTICAL_PADDING = 4.dp

/**
 * The most lines the text of a pinned [SectionHeader] runs to before it is cut short.
 */
/** The room a section header's text leaves before its action. */
private val SECTION_HEADER_TEXT_GAP = 4.dp

/** The room a pinned section header's pill leaves before the app bar's buttons. */
private val SECTION_HEADER_APP_BAR_GAP = 8.dp

/** As tall as the pill behind the app bar's buttons, which it stands next to once pinned. */
private val SECTION_HEADER_PILL_HEIGHT = 48.dp

/** Puts a header's text on the cards' keyline, the pill starting at their edge. */
private val SECTION_HEADER_PILL_START_PADDING = 16.dp

/** What the pill leaves after an action at its end, the same as the app bar's pill leaves after its buttons. */
private val SECTION_HEADER_PILL_ACTION_END_PADDING = 4.dp

private const val SECTION_HEADER_CONTENT_PARALLAX_FRACTION = 0.5f
