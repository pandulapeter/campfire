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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.domain.Tag
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.filters
import com.pandulapeter.campfire.presentation.resources.ic_check
import com.pandulapeter.campfire.presentation.resources.songs_show_without_chords
import com.pandulapeter.campfire.presentation.resources.songs_sorting_mode
import com.pandulapeter.campfire.presentation.resources.songs_sorting_mode_by_artist
import com.pandulapeter.campfire.presentation.resources.songs_sorting_mode_by_title
import com.pandulapeter.campfire.presentation.resources.songs_tags
import com.pandulapeter.campfire.presentation.resources.songs_tags_clear
import com.pandulapeter.campfire.presentation.resources.songs_tags_match_mode
import com.pandulapeter.campfire.presentation.resources.songs_tags_match_mode_all
import com.pandulapeter.campfire.presentation.resources.songs_tags_match_mode_any
import com.pandulapeter.campfire.presentation.resources.songs_tags_show_all
import com.pandulapeter.campfire.presentation.resources.songs_tags_show_less
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.localization.stringResource
import org.jetbrains.compose.resources.painterResource

/**
 * Whether the [SongsControlsSidePanel] fits into a screen of the given width: the song list comes first, so the panel
 * only gets its space when at least [SIDE_PANEL_MIN_COLUMN_COUNT] columns of songs remain next to it. On narrower
 * screens the same controls are shown in a bottom sheet instead.
 */
internal fun hasRoomForSidePanel(screenWidth: Dp) = columnCountForWidth(screenWidth - SIDE_PANEL_WIDTH) >= SIDE_PANEL_MIN_COLUMN_COUNT

/**
 * The number of columns the song lists lay their items out in, measured from the width the screen settles at rather
 * than from the width the grid currently has, see [ListColumns].
 *
 * @param settledWidth The width of the screen once the navigation bars have finished animating.
 * @param contentPadding The insets the screen hands to its list, whose start and end are not part of its width.
 */
@Composable
internal fun songListColumnCount(
    settledWidth: Dp,
    contentPadding: PaddingValues,
    isSidePanelVisible: Boolean,
): Int {
    val layoutDirection = LocalLayoutDirection.current
    // The panel covers the end inset while it is visible (see besideSidePanel), so either way the same width goes.
    val sidePanelWidth = if (isSidePanelVisible) SIDE_PANEL_WIDTH else 0.dp
    return columnCountForWidth(
        settledWidth - contentPadding.calculateStartPadding(layoutDirection) - contentPadding.calculateEndPadding(layoutDirection) - sidePanelWidth
    )
}

/**
 * [SongsControls] in a panel that spans the full height of the screen next to its app bar and content, shown on
 * screens that are wide enough for it, see [hasRoomForSidePanel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SongsControlsSidePanel(
    isVisible: Boolean,
    viewModel: CampfireViewModel,
    shouldIncludeSorting: Boolean,
    contentPadding: PaddingValues,
) = AnimatedVisibility(
    visible = isVisible,
    enter = expandHorizontally() + fadeIn(),
    exit = shrinkHorizontally() + fadeOut(),
) {
    val endPadding = contentPadding.calculateEndPadding(LocalLayoutDirection.current)
    Row {
        VerticalDivider()
        SongsControls(
            modifier = Modifier.width(SIDE_PANEL_WIDTH + endPadding).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainerLow),
            viewModel = viewModel,
            shouldIncludeSorting = shouldIncludeSorting,
            contentPadding = PaddingValues(
                // The panel sits next to the app bar instead of below it, so it handles the top inset on its own.
                top = TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Top).asPaddingValues().calculateTopPadding(),
                end = endPadding,
                bottom = contentPadding.calculateBottomPadding(),
            ),
        )
    }
}

/**
 * The padding of the content shown next to a [SongsControlsSidePanel]: while the panel is visible, the end inset
 * belongs to the panel.
 */
@Composable
internal fun PaddingValues.besideSidePanel(isSidePanelVisible: Boolean): PaddingValues {
    if (!isSidePanelVisible) return this
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(layoutDirection),
        top = calculateTopPadding(),
        bottom = calculateBottomPadding(),
    )
}

/**
 * Sorting and filter controls of the song list, shown in a side panel on wide enough screens and in a bottom sheet
 * otherwise.
 */
@Composable
internal fun SongsControls(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    shouldIncludeSorting: Boolean,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(contentPadding)
    ) {
        if (shouldIncludeSorting) {
            SettingsSectionTitle(text = stringResource(Res.string.songs_sorting_mode))
            SegmentedChoice(
                options = listOf(
                    UserPreferences.SortingMode.BY_ARTIST to stringResource(Res.string.songs_sorting_mode_by_artist),
                    UserPreferences.SortingMode.BY_TITLE to stringResource(Res.string.songs_sorting_mode_by_title),
                ),
                selected = userPreferences?.sortingMode,
                onSelected = viewModel::setSortingMode,
            )
        }
        SettingsSectionTitle(text = stringResource(Res.string.filters))
        CheckboxListItem(
            title = stringResource(Res.string.songs_show_without_chords),
            isChecked = userPreferences?.shouldShowSongsWithoutChords == true,
            onCheckedChange = viewModel::setShouldShowSongsWithoutChords,
        )
        // A library nobody has tagged has nothing to offer here, and a section title above an empty row would only
        // ask a question the songs cannot answer yet.
        AnimatedVisibility(
            visible = tags.isNotEmpty(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            TagFilters(
                tags = tags,
                selectedTags = userPreferences?.selectedTags.orEmpty(),
                matchMode = userPreferences?.tagMatchMode ?: UserPreferences.TagMatchMode.ANY,
                onTagClicked = viewModel::toggleTagFilter,
                onClear = viewModel::clearTagFilter,
                onMatchModeSelected = viewModel::setTagMatchMode,
            )
        }
    }
}

/**
 * The tags of the library as a filter. There is no fixed set of tags to lay out: they are whatever the songs happen
 * to carry, so the most used ones come first and the rest are a tap away ([MAX_COLLAPSED_TAG_COUNT]) - a library
 * with two hundred tags must not push the sorting mode off the top of the panel.
 *
 * A selected tag is always among the ones shown, whatever its position: the filter that is on has to be visible to
 * be turned off.
 */
@Composable
private fun TagFilters(
    modifier: Modifier = Modifier,
    tags: List<Tag>,
    selectedTags: Set<String>,
    matchMode: UserPreferences.TagMatchMode,
    onTagClicked: (String) -> Unit,
    onClear: () -> Unit,
    onMatchModeSelected: (UserPreferences.TagMatchMode) -> Unit,
) = Column(modifier = modifier) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    val selected = remember(selectedTags) { selectedTags.mapTo(mutableSetOf()) { it.lowercase() } }
    val visibleTags = remember(tags, selected, isExpanded) {
        if (isExpanded) tags else tags.filterIndexed { index, tag -> index < MAX_COLLAPSED_TAG_COUNT || tag.name.lowercase() in selected }
    }
    SettingsSectionTitle(text = stringResource(Res.string.songs_tags))
    TagFlowRow(
        modifier = Modifier.padding(horizontal = CONTROLS_PADDING)
    ) {
        visibleTags.forEach { tag ->
            val isSelected = tag.name.lowercase() in selected
            FilterChip(
                selected = isSelected,
                onClick = { onTagClicked(tag.name) },
                leadingIcon = if (isSelected) {
                    {
                        Icon(
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                            painter = painterResource(Res.drawable.ic_check),
                            contentDescription = null,
                        )
                    }
                } else {
                    null
                },
                label = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            modifier = Modifier.widthIn(max = MAX_TAG_WIDTH),
                            text = tag.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            modifier = Modifier.padding(start = TAG_GAP),
                            text = tag.songCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = CONTROLS_PADDING - BUTTON_INSET),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (tags.size > MAX_COLLAPSED_TAG_COUNT) {
            TextButton(onClick = { isExpanded = !isExpanded }) {
                Text(
                    text = if (isExpanded) {
                        stringResource(Res.string.songs_tags_show_less)
                    } else {
                        stringResource(Res.string.songs_tags_show_all, tags.size)
                    }
                )
            }
        }
        AnimatedVisibility(
            visible = selected.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            TextButton(onClick = onClear) {
                Text(stringResource(Res.string.songs_tags_clear))
            }
        }
    }
    // Only worth asking about once two tags are on: one tag means the same thing either way.
    AnimatedVisibility(
        visible = selected.size > 1,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Column {
            SettingsSectionTitle(text = stringResource(Res.string.songs_tags_match_mode))
            SegmentedChoice(
                options = listOf(
                    UserPreferences.TagMatchMode.ANY to stringResource(Res.string.songs_tags_match_mode_any),
                    UserPreferences.TagMatchMode.ALL to stringResource(Res.string.songs_tags_match_mode_all),
                ),
                selected = matchMode,
                onSelected = onMatchModeSelected,
            )
        }
    }
}

private val SIDE_PANEL_WIDTH = 320.dp
private const val SIDE_PANEL_MIN_COLUMN_COUNT = 3
private const val MAX_COLLAPSED_TAG_COUNT = 12
private val MAX_TAG_WIDTH = 160.dp
private val CONTROLS_PADDING = 16.dp

/** The padding a text button keeps inside its own bounds, taken off so that its label lines up with the titles. */
private val BUTTON_INSET = 12.dp
