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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.error_no_data
import com.pandulapeter.campfire.presentation.resources.error_no_data_hint
import com.pandulapeter.campfire.presentation.resources.ic_dot
import com.pandulapeter.campfire.presentation.resources.ic_error
import com.pandulapeter.campfire.presentation.resources.ic_open_in_new
import com.pandulapeter.campfire.presentation.resources.ic_search
import com.pandulapeter.campfire.presentation.resources.ic_songs
import com.pandulapeter.campfire.presentation.resources.ic_tune
import com.pandulapeter.campfire.presentation.resources.ic_setlists
import com.pandulapeter.campfire.presentation.resources.retry
import com.pandulapeter.campfire.presentation.resources.setlists_missing_song
import com.pandulapeter.campfire.presentation.resources.setlists_no_data
import com.pandulapeter.campfire.presentation.resources.setlists_no_data_hint
import com.pandulapeter.campfire.presentation.resources.songs_all_hidden
import com.pandulapeter.campfire.presentation.resources.songs_all_hidden_hint
import com.pandulapeter.campfire.presentation.resources.songs_empty_hint
import com.pandulapeter.campfire.presentation.resources.songs_empty_title
import com.pandulapeter.campfire.presentation.resources.songs_import
import com.pandulapeter.campfire.presentation.resources.songs_lyrics_only
import com.pandulapeter.campfire.presentation.resources.songs_new_song
import com.pandulapeter.campfire.presentation.resources.songs_no_search_results
import com.pandulapeter.campfire.presentation.resources.songs_no_search_results_hint
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import org.jetbrains.compose.resources.painterResource

/**
 * @param onLongClick Opens the song's actions where a dropdown menu would be out of place (touch platforms).
 * @param actions The trailing content of the row, which on desktop is the overflow button and its menu.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun SongListItem(
    modifier: Modifier = Modifier,
    song: Song,
    isBeingDragged: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null
) {
    // A progress value instead of an animated color, so that the row follows the color scheme immediately while it
    // is animating between the light and the dark theme (a color animation would chase it and trail behind).
    val dragProgress by animateFloatAsState(
        if (isBeingDragged) 1f else 0f,
        MaterialTheme.motionScheme.defaultEffectsSpec()
    )
    val containerColor = lerp(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surfaceContainerHigh, dragProgress)
    ListItem(
        modifier = modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = ListItemDefaults.colors(containerColor = containerColor),
        trailingContent = actions,
        headlineContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    modifier = Modifier.weight(1f, fill = false),
                    text = song.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!song.hasChords) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_dot),
                        contentDescription = null
                    )
                    Text(
                        modifier = Modifier.padding(start = 8.dp),
                        text = stringResource(Res.string.songs_lyrics_only),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        // Songs created in the app need no artist, and an empty second line would just make the row taller.
        supportingContent = song.artist.takeIf { it.isNotBlank() }?.let { artist ->
            {
                Text(
                    text = artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    )
}

/**
 * A setlist entry whose file is no longer in the library: it cannot be opened, but it can still be removed, so it is
 * shown greyed out rather than silently dropped - a setlist that quietly loses a song would look like the app lost it.
 */
@Composable
internal fun MissingSongListItem(
    modifier: Modifier = Modifier,
    songFileName: String
) = ListItem(
    modifier = modifier.alpha(0.5f),
    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
    headlineContent = {
        Text(
            text = songFileName,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    },
    supportingContent = {
        Text(
            text = stringResource(Res.string.setlists_missing_song),
            fontStyle = FontStyle.Italic,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
)

/**
 * Header of a list section: a raised pill that scrolls with the items of its section, like any other item of the
 * list. Clicking it scrolls the list back to the start of its own section, which is the header itself (see
 * [animateScrollToKey]).
 *
 * The pill hangs into the keyline of the items below it, so that its text and their text start at the same x
 * position (see [LIST_ITEM_KEYLINE]).
 */
@Composable
internal fun SectionHeader(
    modifier: Modifier = Modifier,
    text: String,
    onClick: () -> Unit,
    action: (@Composable () -> Unit)? = null
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
            shadowElevation = 2.dp
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    modifier = Modifier.padding(
                        start = SECTION_HEADER_PADDING,
                        end = if (action == null) SECTION_HEADER_PADDING else 4.dp,
                        top = 6.dp,
                        bottom = 6.dp
                    ),
                    text = text,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
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
    onClick: () -> Unit
) = IconButton(
    modifier = Modifier.size(32.dp),
    onClick = onClick
) {
    Icon(
        modifier = Modifier.size(18.dp),
        painter = icon,
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.onSurfaceVariant
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
    contentPadding: PaddingValues = PaddingValues(start = LIST_ITEM_KEYLINE, end = LIST_ITEM_KEYLINE, top = 24.dp, bottom = 8.dp)
) = Text(
    modifier = modifier.fillMaxWidth().padding(contentPadding),
    text = text,
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.primary
)

@Composable
internal fun SwitchListItem(
    modifier: Modifier = Modifier,
    title: String,
    description: String? = null,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) = ListItem(
    modifier = modifier.toggleable(value = isChecked, role = Role.Switch, onValueChange = onCheckedChange),
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    headlineContent = { Text(title) },
    supportingContent = description?.let { { Text(it) } },
    trailingContent = { Switch(checked = isChecked, onCheckedChange = null) }
)

@Composable
internal fun CheckboxListItem(
    modifier: Modifier = Modifier,
    title: String,
    isChecked: Boolean,
    isEnabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) = ListItem(
    modifier = modifier
        .toggleable(value = isChecked, enabled = isEnabled, role = Role.Checkbox, onValueChange = onCheckedChange)
        .alpha(if (isEnabled) 1f else 0.5f),
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    headlineContent = { Text(title) },
    leadingContent = { Checkbox(checked = isChecked, enabled = isEnabled, onCheckedChange = null) }
)

@Composable
internal fun LinkListItem(
    modifier: Modifier = Modifier,
    title: String,
    icon: Painter,
    onClick: () -> Unit
) = ListItem(
    modifier = modifier.clickable(onClick = onClick),
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    headlineContent = { Text(title) },
    leadingContent = { Icon(painter = icon, contentDescription = null) },
    trailingContent = {
        Icon(
            painter = painterResource(Res.drawable.ic_open_in_new),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
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
    onClick: () -> Unit
) = ListItem(
    modifier = modifier.clickable(enabled = isEnabled, onClick = onClick).alpha(if (isEnabled) 1f else 0.5f),
    colors = if (isEmphasized) {
        ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = MaterialTheme.colorScheme.primary,
            leadingIconColor = MaterialTheme.colorScheme.primary
        )
    } else {
        ListItemDefaults.colors(containerColor = Color.Transparent)
    },
    headlineContent = { Text(title) },
    leadingContent = { Icon(painter = icon, contentDescription = null) }
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
 * @param onNewSong Null where filling the library is not this list's business, which hides both of its offers to.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ListPlaceholder(
    modifier: Modifier = Modifier,
    placeholder: CampfireViewModel.Placeholder,
    onRetry: () -> Unit,
    onNewSong: (() -> Unit)? = null,
    onImport: (() -> Unit)? = null
) = AnimatedContent(
    modifier = modifier,
    targetState = placeholder,
    transitionSpec = { fadeIn() togetherWith fadeOut() }
) { currentPlaceholder ->
    when (currentPlaceholder) {
        CampfireViewModel.Placeholder.LOADING -> Box(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            ContainedLoadingIndicator()
        }

        CampfireViewModel.Placeholder.ERROR -> EmptyState(
            icon = painterResource(Res.drawable.ic_error),
            title = stringResource(Res.string.error_no_data),
            hint = stringResource(Res.string.error_no_data_hint),
            actionText = stringResource(Res.string.retry),
            onAction = onRetry
        )

        CampfireViewModel.Placeholder.NO_SONGS -> EmptyState(
            icon = painterResource(Res.drawable.ic_songs),
            title = stringResource(Res.string.songs_empty_title),
            hint = stringResource(Res.string.songs_empty_hint),
            actionText = onNewSong?.let { stringResource(Res.string.songs_new_song) },
            onAction = onNewSong,
            secondaryActionText = onNewSong?.let { stringResource(Res.string.songs_import) },
            onSecondaryAction = onImport
        )

        CampfireViewModel.Placeholder.NO_SETLISTS -> EmptyState(
            icon = painterResource(Res.drawable.ic_setlists),
            title = stringResource(Res.string.setlists_no_data),
            hint = stringResource(Res.string.setlists_no_data_hint)
        )

        CampfireViewModel.Placeholder.ALL_SONGS_HIDDEN -> EmptyState(
            icon = painterResource(Res.drawable.ic_tune),
            title = stringResource(Res.string.songs_all_hidden),
            hint = stringResource(Res.string.songs_all_hidden_hint)
        )

        CampfireViewModel.Placeholder.NO_SEARCH_RESULTS -> EmptyState(
            icon = painterResource(Res.drawable.ic_search),
            title = stringResource(Res.string.songs_no_search_results),
            hint = stringResource(Res.string.songs_no_search_results_hint)
        )
    }
}

/**
 * @param actionText The label of the button under the hint. Without it the state is text only, which is what an empty
 *   list that is empty for a good reason gets: there is nothing to retry.
 * @param onAction Null next to a non-null [actionText] leaves the button visible but disabled, for an action that
 *   the app will be able to offer but cannot yet.
 * @param secondaryActionText The label of a second, outlined button next to the first one.
 */
@Composable
internal fun EmptyState(
    modifier: Modifier = Modifier,
    icon: Painter,
    title: String,
    /** Null when the title already says everything, e.g. for a song file that is simply still empty. */
    hint: String? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryActionText: String? = null,
    onSecondaryAction: (() -> Unit)? = null
) = Column(
    // Always the full width, so that the text is centered on the list rather than on itself.
    modifier = modifier.fillMaxWidth().padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally
) {
    Icon(
        modifier = Modifier.padding(bottom = 16.dp).alpha(0.6f),
        painter = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary
    )
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center
    )
    if (hint != null) {
        Text(
            modifier = Modifier.padding(top = 4.dp),
            text = hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
    if (actionText != null || secondaryActionText != null) {
        Row(
            modifier = Modifier.padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (actionText != null) {
                Button(
                    enabled = onAction != null,
                    onClick = { onAction?.invoke() }
                ) {
                    Text(actionText)
                }
            }
            if (actionText != null && secondaryActionText != null) {
                Spacer(modifier = Modifier.size(8.dp))
            }
            if (secondaryActionText != null) {
                OutlinedButton(
                    enabled = onSecondaryAction != null,
                    onClick = { onSecondaryAction?.invoke() }
                ) {
                    Text(secondaryActionText)
                }
            }
        }
    }
}

/**
 * The x position the text of a [ListItem] starts at, which the pills of the sticky headers line up with.
 */
private val LIST_ITEM_KEYLINE = 16.dp

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
