/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.songFilters

import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.components.menu.SortMenu
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.components.bounceVerticalScroll

/**
 * The filters of the song list, shown in a bottom sheet on every screen size, and not offered where there is nothing to
 * filter by (`SongListState.hasSongFilters`). The order of the list is not among
 * them: it is a [SortMenu] in the app bar, as on the setlists screen, since it changes how the library is laid out
 * rather than which songs are in it.
 *
 * The groups share the height of the sheet between them ([FilterGroupsLayout]), so that as much of both
 * is in sight at once as fits, and each keeps the rest of its chips behind a "Show all" of its own.
 *
 * @param state What the filters show, see [SongFilterUiState].
 * @param actions What their controls change.
 * @param uncoveredTopInset The sheet's `BottomSheetContentScope.uncoveredTopInset`, for the height of the sheet at
 *   its tallest rather than at the offset it happens to be at.
 * @param fadeBackgroundColor The opaque color of the sheet the filters stand on, which their top edge fades into.
 * @param normalizeForSearch How a group's search and its chips' names are folded before they are compared:
 *   `SongRenderer.normalizeForSearch`, so that a tag is found the way a song is.
 */
@Composable
internal fun SongFilters(
    modifier: Modifier = Modifier,
    state: SongFilterUiState,
    actions: SongFilterActions,
    contentPadding: PaddingValues,
    uncoveredTopInset: () -> Dp,
    fadeBackgroundColor: Color,
    normalizeForSearch: (String) -> String,
) = BoxWithConstraints(
    modifier = modifier.fillMaxWidth(),
) {
    val scrollState = rememberScrollState()
    // The height of what the filters are shown in is only known outside the scroll, which measures its content
    // against an unbounded one.
    val availableHeight = maxHeight - uncoveredTopInset() - contentPadding.calculateTopPadding() - contentPadding.calculateBottomPadding()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fadingTopEdge(scrollState, fadeBackgroundColor)
            .bounceVerticalScroll(scrollState)
            .padding(contentPadding),
    ) {
        // Inside the scroll: animateBounds follows positions in the scope, and a scope around the scroll would read
        // every scrolled pixel as a move to animate.
        LookaheadScope {
            FilterGroups(
                lookaheadScope = this,
                availableHeight = availableHeight,
                state = state,
                actions = actions,
                normalizeForSearch = normalizeForSearch,
            )
        }
    }
}

/** The groups of [SongFilters], sharing the room between them, laid out in [lookaheadScope]. */
@Composable
private fun FilterGroups(
    lookaheadScope: LookaheadScope,
    availableHeight: Dp,
    state: SongFilterUiState,
    actions: SongFilterActions,
    normalizeForSearch: (String) -> String,
) {
    FilterGroupsLayout(
        modifier = Modifier.fillMaxWidth(),
        availableHeight = availableHeight,
    ) {
        // A library nobody has tagged has nothing to offer here, and a section title above an empty row would only
        // ask a question the songs cannot answer yet.
        TagFilters(
            lookaheadScope = lookaheadScope,
            isVisible = state.tags.isNotEmpty(),
            tags = state.tags,
            selectedTags = state.selectedTags,
            matchMode = state.tagMatchMode,
            sortingMode = state.tagSortingMode,
            onTagClicked = actions::toggleTagFilter,
            onClear = actions::clearTagFilter,
            onMatchModeSelected = actions::setTagMatchMode,
            onSortingModeSelected = actions::setTagSortingMode,
            normalizeForSearch = normalizeForSearch,
        )
        // A library that sings in one language has nothing to choose between, and the one group it would offer
        // ("Unknown", against the single language) is a question about a library nobody has filled in yet.
        LanguageFilters(
            lookaheadScope = lookaheadScope,
            isVisible = state.languages.size > 1,
            languages = state.languages,
            selectedLanguages = state.selectedLanguages,
            matchMode = state.languageMatchMode,
            sortingMode = state.languageSortingMode,
            onLanguageClicked = actions::toggleLanguageFilter,
            onClear = actions::clearLanguageFilter,
            onMatchModeSelected = actions::setLanguageMatchMode,
            onSortingModeSelected = actions::setLanguageSortingMode,
            normalizeForSearch = normalizeForSearch,
        )
    }
}

/** Between the tags and the languages under them. */
internal val FILTER_GROUP_GAP = 8.dp
