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
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.domain.SongLanguage
import com.pandulapeter.campfire.data.model.domain.Tag
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.currentLanguage
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.songs_languages
import com.pandulapeter.campfire.presentation.resources.songs_languages_clear
import com.pandulapeter.campfire.presentation.resources.songs_languages_match_mode
import com.pandulapeter.campfire.presentation.resources.songs_languages_match_mode_all
import com.pandulapeter.campfire.presentation.resources.songs_languages_match_mode_any
import com.pandulapeter.campfire.presentation.resources.songs_tags
import com.pandulapeter.campfire.presentation.resources.songs_tags_clear
import com.pandulapeter.campfire.presentation.resources.songs_tags_match_mode
import com.pandulapeter.campfire.presentation.resources.songs_tags_match_mode_all
import com.pandulapeter.campfire.presentation.resources.songs_tags_match_mode_any
import com.pandulapeter.campfire.presentation.resources.songs_filters_show_all
import com.pandulapeter.campfire.presentation.resources.songs_filters_reset
import com.pandulapeter.campfire.presentation.resources.songs_filters_show_less
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.platform.bounceVerticalScroll

/**
 * The filters of the song list, shown in a side panel on wide enough screens and in a bottom sheet otherwise, and
 * neither where there is nothing to filter by (`CampfireViewModel.hasSongFilters`). The order of the list is not among
 * them: it is a [SortMenu] in the app bar, as on the setlists screen, since it changes how the library is laid out
 * rather than which songs are in it.
 *
 * The groups share the height of the panel or the sheet between them ([FilterGroupsLayout]), so that as much of both
 * is in sight at once as fits, and each keeps the rest of its chips behind a "Show all" of its own.
 *
 * @param state What the filters show, see [SongFilterUiState].
 * @param actions What their controls change.
 * @param uncoveredTopInset The sheet's `BottomSheetContentScope.uncoveredTopInset`, for the height of the sheet at
 *   its tallest rather than at the offset it happens to be at.
 * @param fadeBackgroundColor The opaque color the filters stand on, the screen's or the sheet's, which their top edge
 *   fades into.
 */
@Composable
internal fun SongFilters(
    modifier: Modifier = Modifier,
    state: SongFilterUiState,
    actions: SongFilterActions,
    contentPadding: PaddingValues = PaddingValues(),
    uncoveredTopInset: () -> Dp = { 0.dp },
    fadeBackgroundColor: Color,
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
        )
        ResetFiltersButton(
            modifier = Modifier.layoutId(FilterSlot.TRANSIENT),
            isVisible = state.isActive,
            onClick = actions::clearSongFilter,
        )
    }
}

/**
 * Empties every group at once, under the last one. It is offered for exactly as long as the filter action carries its
 * badge, since what it resets is the selection that badge points at, and it is left out of the sharing like the
 * "any / every" choice, so its arrival takes no room from the chips that were just tapped.
 */
@Composable
private fun ResetFiltersButton(
    modifier: Modifier = Modifier,
    isVisible: Boolean,
    onClick: () -> Unit,
) = AnimatedVisibility(
    modifier = modifier,
    visible = isVisible,
    enter = expandVertically() + fadeIn(),
    exit = shrinkVertically() + fadeOut(),
) {
    OutlinedButton(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CONTROLS_PADDING)
            .padding(top = FILTER_GROUP_GAP * 2),
        onClick = onClick,
    ) {
        Text(text = stringResource(Res.string.songs_filters_reset))
    }
}

/**
 * The tags of the library as a filter. There is no fixed set of tags to lay out: they are whatever the songs happen
 * to carry, so the first ones in the chosen order - the most used, or the alphabet's first - are shown and the rest
 * are a tap away once there are more than the room [FilterGroupsLayout] gives them holds: a library with two hundred
 * tags must not push the languages under it off the bottom of the panel.
 *
 * A selected tag is always among the ones shown, whatever its position: the filter that is on has to be visible to
 * be turned off.
 *
 * It is not one layout but the children of [FilterGroupsLayout] that the group is made of, since that is what shares
 * the room out between the groups' chips.
 */
@Composable
private fun TagFilters(
    lookaheadScope: LookaheadScope,
    isVisible: Boolean,
    tags: List<Tag>,
    selectedTags: Set<String>,
    matchMode: UserPreferences.MatchMode,
    sortingMode: UserPreferences.LabelSortingMode,
    onTagClicked: (String) -> Unit,
    onClear: () -> Unit,
    onMatchModeSelected: (UserPreferences.MatchMode) -> Unit,
    onSortingModeSelected: (UserPreferences.LabelSortingMode) -> Unit,
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    val selected = remember(selectedTags) { selectedTags.mapTo(mutableSetOf()) { it.lowercase() } }
    val orderedTags = remember(tags, sortingMode) { tags.orderedBy(sortingMode) }
    val isPinned = remember(orderedTags, selected) { orderedTags.map { it.name.lowercase() in selected } }
    // A tag the library no longer has stays selected, and clearing deliberately leaves it alone
    // (CampfireViewModel.clearTagFilter), so what the clear action and the match mode are offered for is a selection
    // among the chips rather than whatever the filter still holds - a button that cleared nothing visible, or an
    // "any / every" asked about one chip and a tag nobody can see, would be answering a question the screen never
    // asked.
    val selectedChipCount = isPinned.count { it }
    FilterGroupPart(isVisible = isVisible) {
        FilterSectionTitle(
            title = stringResource(Res.string.songs_tags),
            sortingMode = sortingMode,
            onSortingModeSelected = onSortingModeSelected,
            isClearVisible = selectedChipCount > 0,
            clearText = stringResource(Res.string.songs_tags_clear),
            onClearClicked = onClear,
        )
    }
    FilterGroupChips(
        lookaheadScope = lookaheadScope,
        isVisible = isVisible,
        items = orderedTags,
        key = { it.name.lowercase() },
        isExpanded = isExpanded,
        isPinned = isPinned,
        onExpandedChanged = { isExpanded = it },
    ) { tag ->
        CountedFilterChip(
            label = tag.name,
            songCount = tag.songCount,
            isSelected = tag.name.lowercase() in selected,
            onClick = { onTagClicked(tag.name) },
        )
    }
    MatchModeChoice(
        modifier = Modifier.layoutId(FilterSlot.TRANSIENT),
        isVisible = isVisible && selectedChipCount > 1,
        title = stringResource(Res.string.songs_tags_match_mode),
        anyText = stringResource(Res.string.songs_tags_match_mode_any),
        allText = stringResource(Res.string.songs_tags_match_mode_all),
        matchMode = matchMode,
        onMatchModeSelected = onMatchModeSelected,
    )
}

/**
 * The languages of the library as a filter, under the tags and laid out the way they are: a library usually sings in
 * a handful of languages, which then take a line or two and are shown whole, but one gathered from all over the world
 * is cut down to the room it is given like the tags. A song can carry several languages the way it carries several
 * tags, so "any / every" is asked here too.
 */
@Composable
private fun LanguageFilters(
    lookaheadScope: LookaheadScope,
    isVisible: Boolean,
    languages: List<SongLanguage>,
    selectedLanguages: Set<String>,
    matchMode: UserPreferences.MatchMode,
    sortingMode: UserPreferences.LabelSortingMode,
    onLanguageClicked: (String) -> Unit,
    onClear: () -> Unit,
    onMatchModeSelected: (UserPreferences.MatchMode) -> Unit,
    onSortingModeSelected: (UserPreferences.LabelSortingMode) -> Unit,
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    val appLanguageCode = currentLanguage.value.code
    val orderedLanguages = remember(languages, sortingMode, appLanguageCode) {
        languages.orderedBy(sortingMode) { code -> languageName(code = code, appLanguageCode = appLanguageCode) ?: code.uppercase() }
    }
    // The chips rather than the filter, for the same reason [TagFilters] counts them that way.
    val isPinned = remember(orderedLanguages, selectedLanguages) { orderedLanguages.map { it.code in selectedLanguages } }
    val selectedChipCount = isPinned.count { it }
    FilterGroupPart(isVisible = isVisible) {
        FilterSectionTitle(
            modifier = Modifier.padding(top = FILTER_GROUP_GAP),
            title = stringResource(Res.string.songs_languages),
            sortingMode = sortingMode,
            onSortingModeSelected = onSortingModeSelected,
            isClearVisible = selectedChipCount > 0,
            clearText = stringResource(Res.string.songs_languages_clear),
            onClearClicked = onClear,
        )
    }
    FilterGroupChips(
        lookaheadScope = lookaheadScope,
        isVisible = isVisible,
        items = orderedLanguages,
        key = { it.code },
        isExpanded = isExpanded,
        isPinned = isPinned,
        onExpandedChanged = { isExpanded = it },
    ) { language ->
        CountedFilterChip(
            label = languageLabel(language.code),
            songCount = language.songCount,
            isSelected = language.code in selectedLanguages,
            onClick = { onLanguageClicked(language.code) },
        )
    }
    MatchModeChoice(
        modifier = Modifier.layoutId(FilterSlot.TRANSIENT),
        isVisible = isVisible && selectedChipCount > 1,
        title = stringResource(Res.string.songs_languages_match_mode),
        anyText = stringResource(Res.string.songs_languages_match_mode_any),
        allText = stringResource(Res.string.songs_languages_match_mode_all),
        matchMode = matchMode,
        onMatchModeSelected = onMatchModeSelected,
    )
}

/** A part of a filter group that comes and goes with the whole group, as one child of [FilterGroupsLayout]. */
@Composable
private fun FilterGroupPart(
    modifier: Modifier = Modifier,
    isVisible: Boolean,
    content: @Composable () -> Unit,
) = AnimatedVisibility(
    modifier = modifier,
    visible = isVisible,
    enter = expandVertically() + fadeIn(),
    exit = shrinkVertically() + fadeOut(),
) {
    content()
}

/**
 * The chips of a filter group, cut down to the room [FilterGroupsLayout] gives them, with the "Show all" that opens
 * the rest under them. That one stays under the chips, since what it asks about is the list it is at the end of - and
 * unlike the clearing of the filter it comes and goes with the room the chips have rather than with what is selected.
 */
@Composable
private fun <T : Any> FilterGroupChips(
    lookaheadScope: LookaheadScope,
    isVisible: Boolean,
    items: List<T>,
    key: (T) -> Any,
    isExpanded: Boolean,
    isPinned: List<Boolean>,
    onExpandedChanged: (Boolean) -> Unit,
    chip: @Composable (T) -> Unit,
) = FilterGroupPart(
    modifier = Modifier.layoutId(if (isExpanded) FilterSlot.EXPANDED_CHIPS else FilterSlot.CHIPS),
    isVisible = isVisible,
) {
    CollapsibleChipFlow(
        items = items,
        key = key,
        isExpanded = isExpanded,
        isPinned = isPinned,
        lookaheadScope = lookaheadScope,
        horizontalPadding = CONTROLS_PADDING,
        gap = CHIP_GAP,
        toggle = {
            TextButton(
                modifier = Modifier.padding(horizontal = CONTROLS_PADDING - BUTTON_INSET),
                onClick = { onExpandedChanged(!isExpanded) },
            ) {
                // The button rides the group's edge while it opens or closes, so its label turns into the other one
                // where it is rather than the button going and coming back.
                AnimatedContent(
                    targetState = isExpanded,
                    transitionSpec = { fadeIn() togetherWith fadeOut() using SizeTransform(clip = false) },
                ) { expanded ->
                    Text(
                        text = if (expanded) {
                            stringResource(Res.string.songs_filters_show_less)
                        } else {
                            stringResource(Res.string.songs_filters_show_all, items.size)
                        }
                    )
                }
            }
        },
        chip = chip,
    )
}

/**
 * Whether the values selected in a filter group mean "any of them" or "all of them", under the group's chips. Only
 * worth asking about once two of them are on, since one value means the same thing either way.
 */
@Composable
private fun MatchModeChoice(
    modifier: Modifier = Modifier,
    isVisible: Boolean,
    title: String,
    anyText: String,
    allText: String,
    matchMode: UserPreferences.MatchMode,
    onMatchModeSelected: (UserPreferences.MatchMode) -> Unit,
) = AnimatedVisibility(
    modifier = modifier,
    visible = isVisible,
    enter = expandVertically() + fadeIn(),
    exit = shrinkVertically() + fadeOut(),
) {
    Column {
        SettingsSectionTitle(
            text = title,
            contentPadding = PaddingValues(horizontal = CONTROLS_PADDING, vertical = CHIP_GAP),
        )
        SegmentedChoice(
            options = listOf(
                UserPreferences.MatchMode.ANY to anyText,
                UserPreferences.MatchMode.ALL to allText,
            ),
            selected = matchMode,
            onSelected = onMatchModeSelected,
        )
    }
}

/**
 * The title of a filter group, with the [LabelSortingToggle] of its chips right after it and the action that empties
 * it at the other end of the same row.
 *
 * It is up here rather than under the chips because it comes and goes with the selection, and a button of its own
 * would grow and shrink everything below it - in a bottom sheet, the sheet itself - every time a filter was turned on
 * or off. The row is laid out so that it cannot: it is always as tall as the action ([SECTION_ACTION_HEIGHT]), whether
 * the action is showing or not, so the title reads in the same place either way and the action never decides anything.
 */
@Composable
private fun FilterSectionTitle(
    modifier: Modifier = Modifier,
    title: String,
    sortingMode: UserPreferences.LabelSortingMode,
    onSortingModeSelected: (UserPreferences.LabelSortingMode) -> Unit,
    isClearVisible: Boolean,
    clearText: String,
    onClearClicked: () -> Unit,
) = Row(
    modifier = modifier
        .fillMaxWidth()
        .padding(
            start = CONTROLS_PADDING,
            end = CONTROLS_PADDING - BUTTON_INSET,
            bottom = SECTION_TITLE_BOTTOM_PADDING,
        )
        .height(SECTION_ACTION_HEIGHT),
    verticalAlignment = Alignment.CenterVertically,
) {
    // Only as wide as its text, since the toggle is about the title and sits right after it. The title fills whatever
    // width it is given, so a weight would hand it half of the row's free space.
    SettingsSectionTitle(
        modifier = Modifier.width(IntrinsicSize.Max),
        text = title,
        contentPadding = PaddingValues(),
    )
    // The same reason as the clear action's below: the row is as tall as the action, and the touch target would
    // make it taller.
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        LabelSortingToggle(
            modifier = Modifier.padding(start = SORTING_TOGGLE_GAP).size(SORTING_TOGGLE_SIZE),
            sortingMode = sortingMode,
            onSortingModeSelected = onSortingModeSelected,
        )
    }
    Spacer(modifier = Modifier.weight(1f))
    AnimatedVisibility(
        visible = isClearVisible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        // A button that reserved the 48dp touch target would be the tallest thing in the row and would decide its
        // height, which is the one thing this row must not let it do.
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            TextButton(
                modifier = Modifier.height(SECTION_ACTION_HEIGHT),
                onClick = onClearClicked,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                contentPadding = PaddingValues(horizontal = BUTTON_INSET),
            ) {
                Text(text = clearText)
            }
        }
    }
}

/** Between the tags and the languages under them. */
private val FILTER_GROUP_GAP = 8.dp

/** The height of a [FilterSectionTitle], which is its action's with no touch target around it, and the gap under it. */
private val SECTION_ACTION_HEIGHT = 32.dp
private val SECTION_TITLE_BOTTOM_PADDING = 4.dp

/** The padding a text button keeps inside its own bounds, taken off so that its label lines up with the titles. */
private val BUTTON_INSET = 12.dp
