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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.add_demo_songs
import com.pandulapeter.campfire.presentation.resources.error_no_data
import com.pandulapeter.campfire.presentation.resources.error_no_data_hint
import com.pandulapeter.campfire.presentation.resources.ic_archive
import com.pandulapeter.campfire.presentation.resources.ic_error
import com.pandulapeter.campfire.presentation.resources.ic_search
import com.pandulapeter.campfire.presentation.resources.ic_songs
import com.pandulapeter.campfire.presentation.resources.ic_tune
import com.pandulapeter.campfire.presentation.resources.ic_setlists
import com.pandulapeter.campfire.presentation.resources.retry
import com.pandulapeter.campfire.presentation.resources.setlists_all_hidden
import com.pandulapeter.campfire.presentation.resources.setlists_all_hidden_hint
import com.pandulapeter.campfire.presentation.resources.setlists_create_setlist
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
import com.pandulapeter.campfire.presentation.resources.songs_no_search_results
import com.pandulapeter.campfire.presentation.resources.songs_no_search_results_hint
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import org.jetbrains.compose.resources.painterResource

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
            CircularProgressIndicator()
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
