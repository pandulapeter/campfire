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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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

/**
 * @param index The song's place in the setlist it is listed in, drawn in front of the title by [ListItemHeadline].
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
 * @param actions The trailing content of the row, which is the overflow button of the song's actions
 *   ([SongActionsButton]).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun SongListItem(
    modifier: Modifier = Modifier,
    song: Song,
    index: Int? = null,
    key: String? = null,
    shouldShowChords: Boolean = true,
    labelsOnEverySong: CampfireViewModel.LabelsOnEverySong,
    isBeingDragged: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
) {
    // A progress value instead of an animated color, so that the row follows the color scheme immediately while it
    // is animating between the light and the dark theme (a color animation would chase it and trail behind).
    val dragProgress by animateFloatAsState(
        if (isBeingDragged) 1f else 0f,
        MaterialTheme.motionScheme.defaultEffectsSpec(),
    )
    val containerColor = lerp(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surfaceContainerHigh, dragProgress)
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
            description = stringResource(Res.string.songs_key, key),
        )

        else -> null
    }
    val languages = song.languages.filterNot { it in labelsOnEverySong.languages }
    val tags = song.tags.filterNot { it.lowercase() in labelsOnEverySong.tags }
    ListItem(
        modifier = modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = ListItemDefaults.colors(containerColor = containerColor),
        trailingContent = actions?.let { { ListItemActions(content = it) } },
        headlineContent = {
            ListItemHeadline(
                index = index,
                text = song.title,
            )
        },
        // Songs written in the app need no artist, and an empty second line would just make the row taller. Nothing
        // here shares the title's line: a title is the longest thing on the row and the one that must never be
        // pushed out of sight, while the artist is short enough to leave the key room next to it. The languages and
        // tags go under both, since a row of those is as long as somebody chose to make it.
        supportingContent = if (song.artist.isBlank() && note == null && languages.isEmpty() && tags.isEmpty()) {
            null
        } else {
            {
                Column(
                    modifier = Modifier.listItemIndexIndent(hasIndex = index != null),
                ) {
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
    actions: (@Composable () -> Unit)? = null,
) = ListItem(
    modifier = modifier.alpha(0.5f),
    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
    trailingContent = actions?.let { { ListItemActions(content = it) } },
    headlineContent = {
        ListItemHeadline(
            index = index,
            text = songFileName,
        )
    },
    supportingContent = {
        Text(
            modifier = Modifier.listItemIndexIndent(hasIndex = true),
            text = stringResource(Res.string.setlists_missing_song),
            fontStyle = FontStyle.Italic,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    },
)

/**
 * Whatever a row carries at its end - the overflow button, and on the setlists screen the drag handle after it.
 * `ListItem` insets its trailing slot further from the edge than `TopAppBar` insets its actions, so the last control
 * of a row and the last control of the bar above it sit on two keylines a few dp apart; this closes that gap, and
 * every row of every list is drawn through here so the one keyline holds down the whole screen. A list with a
 * [FastScroller] moves its rows' controls back off that keyline again, by [FAST_SCROLLER_CLEARANCE].
 *
 * An offset rather than a smaller padding: the inset belongs to Material's own layout, and moving what is drawn
 * leaves the width the row reserved for it exactly as it was.
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
 * The title of a row, with the row's place in its setlist in front of it where it has one. The number is part of
 * the headline rather than the row's leading slot, for two reasons. The headline starts on [LIST_ITEM_KEYLINE],
 * which is where the app bar's title and the text of the setlist's own header pill start, and a number in the
 * leading slot sat between the two on a keyline of its own. And `ListItem` puts its leading slot at the top of a
 * three line row but in the middle of a shorter one, while the title is the first line of either, so the number
 * and the title only ever shared a line by accident; here the two are aligned on their baselines, so the number
 * reads as part of the title's line whatever the row holds under it.
 *
 * @param index Null where the row is not numbered, which leaves the title alone on the keyline.
 */
@Composable
private fun ListItemHeadline(
    modifier: Modifier = Modifier,
    index: Int?,
    text: String,
) = Row(
    modifier = modifier,
) {
    index?.let {
        ListItemIndex(
            modifier = Modifier.alignByBaseline(),
            index = it,
        )
    }
    Text(
        modifier = Modifier.alignByBaseline(),
        text = text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * The place of a song inside a setlist, counted from one because it is read by whoever is playing the set rather
 * than by the code that orders it. Laid out over a fixed width ([LIST_ITEM_INDEX_KEYLINE]) instead of around the
 * number, so that the titles of a setlist stay on a single keyline however far its numbering has run and a drag
 * that renumbers the rows it passes does not shift them sideways underneath the finger.
 */
@Composable
private fun ListItemIndex(
    modifier: Modifier = Modifier,
    index: Int,
) = Text(
    modifier = modifier.width(LIST_ITEM_INDEX_KEYLINE),
    text = (index + 1).toString(),
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)

/**
 * What goes under a [ListItemHeadline] starts where its title starts rather than under the number in front of it,
 * since the number is the row's and not the title's.
 */
private fun Modifier.listItemIndexIndent(hasIndex: Boolean) = padding(start = if (hasIndex) LIST_ITEM_INDEX_KEYLINE else 0.dp)

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
 * Header of a list section: a raised pill that scrolls with the items of its section, like any other item of the
 * list. Clicking it scrolls the list back to the start of its own section, which is the header itself (see
 * [animateScrollToKey]).
 *
 * The pill hangs into the keyline of the items below it, so that its text and their text start at the same x
 * position (see [LIST_ITEM_KEYLINE]).
 *
 * @param icon Drawn before the text, for a section that is something as well as being named - an archived setlist,
 *   which is on the screen at all only because the user asked for it and has to be recognizable among the rest.
 * @param iconContentDescription What the icon says, since there is nothing else on the pill that says it.
 */
@Composable
internal fun SectionHeader(
    modifier: Modifier = Modifier,
    text: String,
    icon: Painter? = null,
    iconContentDescription: String? = null,
    onClick: () -> Unit,
    action: (@Composable () -> Unit)? = null,
) = Box(
    modifier = modifier.fillMaxWidth().padding(horizontal = LIST_ITEM_KEYLINE - SECTION_HEADER_PADDING, vertical = SECTION_HEADER_GAP)
) {
    // The pill is a label first and a control second, and the touch target enforcement would grow it (and with it
    // the gaps around it) to 48dp, so it is laid out at its own size, like the other compact controls of the app.
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Surface(
            onClick = onClick,
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 2.dp,
        ) {
            Row(
                modifier = Modifier.padding(start = SECTION_HEADER_PADDING),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // The painter is kept after it has been taken away, so that the mark has something to draw while it
                // fades: a setlist is archived from the menu at the other end of this very pill, and the answer to
                // that has to be seen happening rather than found already done.
                var lastIcon by remember { mutableStateOf(icon) }
                icon?.let { lastIcon = it }
                AnimatedVisibility(
                    visible = icon != null,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally(),
                ) {
                    lastIcon?.let { painter ->
                        Icon(
                            modifier = Modifier.padding(end = 6.dp).size(16.dp),
                            painter = painter,
                            contentDescription = iconContentDescription,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    modifier = Modifier.padding(
                        end = if (action == null) SECTION_HEADER_PADDING else 4.dp,
                        top = 6.dp,
                        bottom = 6.dp,
                    ),
                    text = text,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                action?.invoke()
            }
        }
    }
}

/**
 * Scrolls the item with the given [key] to the top of the list. The section headers are ordinary items, so they do
 * not know their own index; they do know their key, and the list knows where the item with that key is as long as it
 * is on the screen, which it is whenever it can be clicked.
 */
internal suspend fun LazyListState.animateScrollToKey(key: Any) {
    layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }?.let { animateScrollToItem(it.index) }
}

/** The [LazyGridState] counterpart of the [LazyListState.animateScrollToKey] above. */
internal suspend fun LazyGridState.animateScrollToKey(key: Any) {
    layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }?.let { animateScrollToItem(it.index) }
}

/**
 * A compact icon button that fits inside a [SectionHeader] pill.
 */
@Composable
internal fun SectionHeaderAction(
    icon: Painter,
    contentDescription: String,
    onClick: () -> Unit,
) = IconButton(
    modifier = Modifier.size(32.dp),
    onClick = onClick,
) {
    Icon(
        modifier = Modifier.size(18.dp),
        painter = icon,
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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

@Composable
internal fun LinkListItem(
    modifier: Modifier = Modifier,
    title: String,
    icon: Painter,
    onClick: () -> Unit,
) = ListItem(
    modifier = modifier.clickable(onClick = onClick),
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    headlineContent = { Text(title) },
    leadingContent = { Icon(painter = icon, contentDescription = null) },
    trailingContent = {
        Icon(
            painter = painterResource(Res.drawable.ic_open_in_new),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
 *   list is empty, because the setlists screen shows the empty library's state as well, and a "Create a song" there
 *   that opened the setlist dialog would be a lie.
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
 * The x position the text of a [ListItem] starts at, which the pills of the sticky headers line up with, as does the
 * number a [ListItemHeadline] puts in front of a setlist's rows. It is also where a top level screen's app bar
 * starts its title, so the one line runs down from the bar through the pills into the numbers.
 */
private val LIST_ITEM_KEYLINE = 16.dp

/**
 * How far a row's trailing controls are moved towards the end edge to reach the keyline the app bar's actions sit
 * on: `ListItem` insets its trailing slot by 16dp (Material's, and not something that can be passed in) and
 * `TopAppBar` its actions by 4dp, and with both controls 48dp wide the difference between the two keylines is the
 * whole of it. See [ListItemActions].
 */
private val LIST_ITEM_TRAILING_KEYLINE_ADJUSTMENT = 4.dp

/**
 * How far after the start of a [ListItemIndex] the title of its row starts: two digits and the gap they keep from
 * the title, since a setlist long enough to need three has other problems.
 */
private val LIST_ITEM_INDEX_KEYLINE = 24.dp

/**
 * The width of a [DragHandle]'s touch target. It is narrower than the `IconButton` after it, and can be, since
 * the handle is not a button: the width it gives up is empty space around an icon rather than anything that can be
 * pressed, and the drag it offers is on the row's own long press as well.
 */
private val DRAG_HANDLE_WIDTH = 32.dp

/** The height of a [DragHandle], which is a full touch target since it is the one thing on the row that is dragged. */
private val DRAG_HANDLE_HEIGHT = 48.dp

/**
 * The padding between the edge of a [SectionHeader] pill and its text.
 */
private val SECTION_HEADER_PADDING = 12.dp

/**
 * The gap a [SectionHeader] pill keeps from whatever is above and below it. Lists add the same gap above their first
 * item, so that a pill at the top of a list clears the app bar by twice this; a pinned pill keeps one of the two,
 * since the item it is pinned inside of stops at the top of the list.
 */
internal val SECTION_HEADER_GAP = 4.dp
