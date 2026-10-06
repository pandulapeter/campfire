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
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateBounds
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.domain.SongLanguage
import com.pandulapeter.campfire.data.model.domain.Tag
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.currentLanguage
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_sort_by_alpha
import com.pandulapeter.campfire.presentation.resources.ic_sort_by_usage
import com.pandulapeter.campfire.presentation.resources.songs_labels_sorted_alphabetically
import com.pandulapeter.campfire.presentation.resources.songs_labels_sorted_by_usage
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
import org.jetbrains.compose.resources.painterResource
import com.pandulapeter.campfire.presentation.ui.platform.bounceHorizontalScroll
import com.pandulapeter.campfire.presentation.ui.platform.bounceVerticalScroll

/**
 * Whether the [ControlsSidePanel] fits into a screen of the given width: the list comes first, so the panel only gets
 * its space when at least [SIDE_PANEL_MIN_COLUMN_COUNT] columns of songs remain next to it. On narrower screens the
 * same controls are shown in a bottom sheet instead. The [FastScroller]'s column is taken off as well, since
 * [songListColumnCount] takes it off too, and a panel granted by a width the list then lays out one column fewer in
 * would leave the list with less than the columns it was promised.
 */
internal fun hasRoomForSidePanel(screenWidth: Dp) =
    columnCountForWidth(screenWidth - SIDE_PANEL_WIDTH - FAST_SCROLLER_WIDTH) >= SIDE_PANEL_MIN_COLUMN_COUNT

/**
 * The panel and the sheet are the same controls shown two different ways, so a window resize that grows the panel
 * into view has to close whichever sheet was covering the screen instead of leaving both on screen at once.
 */
@Composable
internal fun DismissSheetWhenSidePanelAppears(
    isSidePanelVisible: Boolean,
    isSheetVisible: Boolean,
    onDismiss: () -> Unit,
) = LaunchedEffect(isSidePanelVisible) {
    if (isSidePanelVisible && isSheetVisible) onDismiss()
}

/**
 * What a list screen decides from the width it settles at: whether its filter side panel fits, how many columns the
 * Songs screen lays out with and without that panel next to it, and how many the Setlists screen's wider cards fit
 * (see [MIN_SETLIST_COLUMN_WIDTH]). The app's `CampfireScreens` works it out and hands it down in place of the width
 * itself, so that a window being resized, whose width changes on every frame, recomposes the screens only in the
 * frames one of these decisions changes in: the value is equal everywhere between two breakpoints.
 */
@Immutable
internal data class ListLayout(
    val hasRoomForSidePanel: Boolean,
    val columnCount: Int,
    val columnCountBesideSidePanel: Int,
    val setlistColumnCount: Int,
) {
    companion object {

        /**
         * @param settledWidth The width of the screen once the navigation bars have finished animating.
         * @param contentPadding The insets the screen hands to its list, whose start and end are not part of its width.
         */
        fun of(settledWidth: Dp, contentPadding: PaddingValues, layoutDirection: LayoutDirection) = ListLayout(
            hasRoomForSidePanel = hasRoomForSidePanel(settledWidth),
            columnCount = songListColumnCount(settledWidth, contentPadding, layoutDirection, isSidePanelVisible = false),
            columnCountBesideSidePanel = songListColumnCount(settledWidth, contentPadding, layoutDirection, isSidePanelVisible = true),
            setlistColumnCount = songListColumnCount(
                settledWidth = settledWidth,
                contentPadding = contentPadding,
                layoutDirection = layoutDirection,
                isSidePanelVisible = false,
                minColumnWidth = MIN_SETLIST_COLUMN_WIDTH,
            ),
        )
    }
}

/**
 * The number of columns the song lists lay their items out in, measured from the width the screen settles at rather
 * than from the width the grid currently has, see [ListColumns]. The [FastScroller] occupies the grid's end padding,
 * not song card width.
 */
internal fun songListColumnCount(
    settledWidth: Dp,
    contentPadding: PaddingValues,
    layoutDirection: LayoutDirection,
    isSidePanelVisible: Boolean,
    minColumnWidth: Dp = MIN_SONG_COLUMN_WIDTH,
): Int {
    // The panel covers the end inset while it is visible (see besideSidePanel), so either way the same width goes.
    val sidePanelWidth = if (isSidePanelVisible) SIDE_PANEL_WIDTH else 0.dp
    return columnCountForWidth(
        width = settledWidth - contentPadding.calculateStartPadding(layoutDirection) - contentPadding.calculateEndPadding(layoutDirection) - sidePanelWidth - FAST_SCROLLER_WIDTH,
        minColumnWidth = minColumnWidth,
    )
}

/**
 * The [SongFilters] in a panel next to the list, shown on screens that are wide enough for it, see [hasRoomForSidePanel]. The list screens' app bar spans the list alone (see
 * [SearchableTopAppBar]), so the panel reaches the top of the screen beside it.
 *
 * @param content The controls themselves, handed the modifier that gives the panel its size and the insets the panel
 *   is responsible for.
 */
@Composable
internal fun ControlsSidePanel(
    isVisible: Boolean,
    contentPadding: PaddingValues,
    content: @Composable (modifier: Modifier, contentPadding: PaddingValues) -> Unit,
) = AnimatedVisibility(
    visible = isVisible,
    enter = expandHorizontally() + fadeIn(),
    exit = shrinkHorizontally() + fadeOut(),
) {
    val endPadding = contentPadding.calculateEndPadding(LocalLayoutDirection.current)
    // No background and no divider of its own: the panel is part of the screen the bar spans, and a tinted column
    // with an edge reads as a pane of its own under a bar that has not lifted yet, while the list is at its top.
    content(
        Modifier.width(SIDE_PANEL_WIDTH + endPadding).fillMaxHeight(),
        PaddingValues(
            end = endPadding,
            top = SIDE_PANEL_TOP_PADDING,
            bottom = contentPadding.calculateBottomPadding() + SIDE_PANEL_BOTTOM_PADDING,
        ),
    )
}

/**
 * The padding of the content shown next to a [ControlsSidePanel]: while the panel is visible, the end inset belongs
 * to the panel.
 */
internal fun PaddingValues.besideSidePanel(isSidePanelVisible: Boolean) =
    if (isSidePanelVisible) only(start = true, top = true, bottom = true) else this

/**
 * The filters of the song list, shown in a side panel on wide enough screens and in a bottom sheet otherwise, and
 * neither where there is nothing to filter by (`CampfireViewModel.hasSongFilters`). The order of the list is not among
 * them: it is a [SortMenu] in the app bar, as on the setlists screen, since it changes how the library is laid out
 * rather than which songs are in it.
 *
 * The groups share the height of the panel or the sheet between them ([FilterGroupsLayout]), so that as much of both
 * is in sight at once as fits, and each keeps the rest of its chips behind a "Show all" of its own.
 *
 * @param uncoveredTopInset The sheet's `BottomSheetContentScope.uncoveredTopInset`, for the height of the sheet at
 *   its tallest rather than at the offset it happens to be at.
 */
@Composable
internal fun SongFilters(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    contentPadding: PaddingValues = PaddingValues(),
    uncoveredTopInset: () -> Dp = { 0.dp },
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
            .fadingTopEdge(scrollState)
            .bounceVerticalScroll(scrollState)
            .padding(contentPadding),
    ) {
        // Inside the scroll: animateBounds follows positions in the scope, and a scope around the scroll would read
        // every scrolled pixel as a move to animate.
        LookaheadScope {
            FilterGroups(
                lookaheadScope = this,
                availableHeight = availableHeight,
                viewModel = viewModel,
            )
        }
    }
}

/** The groups of [SongFilters], sharing the room between them, laid out in [lookaheadScope]. */
@Composable
private fun FilterGroups(
    lookaheadScope: LookaheadScope,
    availableHeight: Dp,
    viewModel: CampfireViewModel,
) {
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val songFilter by viewModel.songFilter.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val languages by viewModel.languages.collectAsStateWithLifecycle()
    val isSongFilterActive by viewModel.isSongFilterActive.collectAsStateWithLifecycle()
    FilterGroupsLayout(
        modifier = Modifier.fillMaxWidth(),
        availableHeight = availableHeight,
    ) {
        // A library nobody has tagged has nothing to offer here, and a section title above an empty row would only
        // ask a question the songs cannot answer yet.
        TagFilters(
            lookaheadScope = lookaheadScope,
            isVisible = tags.isNotEmpty(),
            tags = tags,
            selectedTags = songFilter.selectedTags,
            matchMode = userPreferences?.tagMatchMode ?: UserPreferences.MatchMode.ANY,
            sortingMode = userPreferences?.tagSortingMode ?: UserPreferences.LabelSortingMode.BY_USAGE,
            onTagClicked = viewModel::toggleTagFilter,
            onClear = viewModel::clearTagFilter,
            onMatchModeSelected = viewModel::setTagMatchMode,
            onSortingModeSelected = viewModel::setTagSortingMode,
        )
        // A library that sings in one language has nothing to choose between, and the one group it would offer
        // ("Unknown", against the single language) is a question about a library nobody has filled in yet.
        LanguageFilters(
            lookaheadScope = lookaheadScope,
            isVisible = languages.size > 1,
            languages = languages,
            selectedLanguages = songFilter.selectedLanguages,
            matchMode = userPreferences?.languageMatchMode ?: UserPreferences.MatchMode.ANY,
            sortingMode = userPreferences?.languageSortingMode ?: UserPreferences.LabelSortingMode.BY_USAGE,
            onLanguageClicked = viewModel::toggleLanguageFilter,
            onClear = viewModel::clearLanguageFilter,
            onMatchModeSelected = viewModel::setLanguageMatchMode,
            onSortingModeSelected = viewModel::setLanguageSortingMode,
        )
        ResetFiltersButton(
            modifier = Modifier.layoutId(FilterSlot.TRANSIENT),
            isVisible = isSongFilterActive,
            onClick = viewModel::clearSongFilter,
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

/**
 * Switches the values of a group between the two [UserPreferences.LabelSortingMode]s: most used first, or
 * alphabetical. The icon shows the order the list is in rather than the one a tap would put it in, the way a column
 * header's sort indicator does, and so does what a screen reader announces.
 */
@Composable
internal fun LabelSortingToggle(
    modifier: Modifier = Modifier,
    sortingMode: UserPreferences.LabelSortingMode,
    onSortingModeSelected: (UserPreferences.LabelSortingMode) -> Unit,
) = IconButton(
    modifier = modifier,
    onClick = {
        onSortingModeSelected(
            when (sortingMode) {
                UserPreferences.LabelSortingMode.BY_USAGE -> UserPreferences.LabelSortingMode.ALPHABETICAL
                UserPreferences.LabelSortingMode.ALPHABETICAL -> UserPreferences.LabelSortingMode.BY_USAGE
            }
        )
    },
) {
    Crossfade(targetState = sortingMode) { mode ->
        Icon(
            modifier = Modifier.size(SORTING_TOGGLE_ICON_SIZE),
            painter = painterResource(
                when (mode) {
                    UserPreferences.LabelSortingMode.BY_USAGE -> Res.drawable.ic_sort_by_usage
                    UserPreferences.LabelSortingMode.ALPHABETICAL -> Res.drawable.ic_sort_by_alpha
                }
            ),
            contentDescription = stringResource(
                when (mode) {
                    UserPreferences.LabelSortingMode.BY_USAGE -> Res.string.songs_labels_sorted_by_usage
                    UserPreferences.LabelSortingMode.ALPHABETICAL -> Res.string.songs_labels_sorted_alphabetically
                }
            ),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A group of filter chips on one row that scrolls sideways, with its [LabelSortingToggle] pinned at the start of the
 * row in place of a section title: the chips scroll behind it and fade out before they reach it
 * ([fadingUnderStartOverlay]), so the toggle stays where it is, read against the background, however far the row is
 * scrolled. Kept as small as it is next to a section title, with no touch target around it, so that the row is as tall
 * as its chips.
 *
 * A new order sends the row back to its start, which is where the order is read from, and every chip travels to its
 * new place (`animateBounds`) rather than staying put and being handed another label. That is why the row is not lazy:
 * a lazy row sent back to its start lets the placement animation of its items run for a single frame and then drops
 * them where they land, and the row only holds the library's tags and languages, which the songs screen's filters
 * compose all of too. The [LookaheadScope] is inside the scroll, so that the scroll moves the chips as a whole and only
 * the change of order is animated.
 */
@Composable
internal fun <T : Any> SortableChipRow(
    modifier: Modifier = Modifier,
    items: List<T>,
    key: (T) -> String,
    order: ChecklistOrder,
    refreshKey: Any?,
    sortingMode: UserPreferences.LabelSortingMode,
    onSortingModeSelected: (UserPreferences.LabelSortingMode) -> Unit,
    chip: @Composable (T) -> Unit,
) = Box(
    modifier = modifier.fillMaxWidth(),
    contentAlignment = Alignment.CenterStart,
) {
    val scrollState = rememberScrollState()
    val orderedItems = remember(items, order) { order.ordered(items, key) }
    val leadingCount = order.leadingCount(orderedItems, key)
    val selection = remember { ChecklistSelection(order.checkedKeys) }
    val scrollRefreshKey = sortingMode to refreshKey
    // Opening/restoring the row leaves its scroll position alone; selecting or refreshing animates to the start.
    var scrolledToStartFor by remember { mutableStateOf(scrollRefreshKey) }
    LaunchedEffect(scrollRefreshKey, order.checkedKeys) {
        val hasNewSelection = selection.newlyCheckedIndex(order.checkedKeys, orderedItems.map(key)) != null
        if (scrollRefreshKey != scrolledToStartFor || hasNewSelection) {
            scrolledToStartFor = scrollRefreshKey
            scrollState.animateScrollTo(0)
        }
    }
    // The icon itself starts at the keyline the search field above the row starts at.
    val toggleStart = CONTROLS_PADDING - (SORTING_TOGGLE_SIZE - SORTING_TOGGLE_ICON_SIZE) / 2
    val toggleEnd = toggleStart + SORTING_TOGGLE_SIZE
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fadingUnderStartOverlay(scrolledFromStart = { scrollState.value }, overlayWidth = toggleEnd)
            .bounceHorizontalScroll(scrollState),
    ) {
        LookaheadScope {
            Row(
                modifier = Modifier.padding(start = toggleEnd + SORTING_TOGGLE_GAP, end = CONTROLS_PADDING),
                horizontalArrangement = Arrangement.spacedBy(CHIP_GAP),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                orderedItems.forEachIndexed { index, item ->
                    if (index == leadingCount && leadingCount > 0) {
                        VerticalDivider(modifier = Modifier.height(FilterChipDefaults.Height))
                    }
                    key(key(item)) {
                        Box(modifier = Modifier.animateBounds(this@LookaheadScope)) { chip(item) }
                    }
                }
            }
        }
    }
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        LabelSortingToggle(
            modifier = Modifier.padding(start = toggleStart).size(SORTING_TOGGLE_SIZE),
            sortingMode = sortingMode,
            onSortingModeSelected = onSortingModeSelected,
        )
    }
}

/**
 * One value of a filter group: what it is called, how many songs it still leaves, and whether it is on. The count is
 * part of the chip rather than a line under the group, since the number is what tells a tag worth picking from one
 * that would leave a single song on screen.
 *
 * A value the other group has counted down to nothing is disabled rather than left out (see `ScreenData.tags`): the
 * chips hold still while the other group changes, and picking it would only empty the list. One that is already on
 * stays enabled whatever its count, since a filter that is on has to be possible to turn off.
 *
 * It is a [SelectableChip], for the reason that one exists.
 *
 * @param leadingIcon What the chip is, for a group that is not told apart by a section title of its own: the song
 *   picker lists the languages and the tags in one row. The check takes its place while the chip is selected, the
 *   way Material swaps a filter chip's leading icon, so the chip keeps its width either way.
 */
@Composable
internal fun CountedFilterChip(
    modifier: Modifier = Modifier,
    label: String,
    songCount: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    leadingIcon: Painter? = null,
) {
    val isEnabled = songCount > 0 || isSelected
    val colors = FilterChipDefaults.filterChipColors()
    SelectableChip(
        modifier = modifier,
        isSelected = isSelected,
        isEnabled = isEnabled,
        role = Role.Checkbox,
        onClick = onClick,
    ) { contentColor ->
        if (leadingIcon != null) {
            Crossfade(targetState = isSelected) { isChecked ->
                Icon(
                    modifier = Modifier.padding(end = CHIP_ICON_GAP).size(FilterChipDefaults.IconSize),
                    painter = leadingIcon,
                    contentDescription = null,
                    tint = animateColorAsState(
                        when {
                            !isEnabled -> colors.disabledLeadingIconColor
                            isChecked -> colors.selectedLeadingIconColor
                            else -> colors.leadingIconColor
                        }
                    ).value,
                )
            }
        }
        Text(
            modifier = Modifier.widthIn(max = MAX_TAG_WIDTH),
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            modifier = Modifier.padding(
                start = TAG_GAP,
                top = 2.dp,
            ),
            text = songCount.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = if (isEnabled) MaterialTheme.colorScheme.onSurfaceVariant else colors.disabledLabelColor,
        )
    }
}

/**
 * A Material filter chip drawn by hand rather than [androidx.compose.material3.FilterChip] itself, for the one thing the
 * chip gets wrong: its press and hover state layer is drawn around the label instead of around the chip, so only a band
 * hugging the text lights up inside the chip's own, which reads as a ripple inside a ripple. Everything the chip would
 * decide is still asked of `FilterChipDefaults`, so the colors, the border, the shape and the height are the ones
 * Material would have used; what is ours is the order of the modifiers. The indication is a node of its own
 * ([androidx.compose.foundation.indication]) sitting outside the padding and driven by the same interaction source as
 * the click, which is what puts the state layer on the chip's own bounds, and the clip above it is what rounds it -
 * the state layer is drawn as a plain rectangle and is bound by nothing else, which is what let it spill past the
 * rounded corners on the web.
 *
 * @param role [Role.Checkbox] for a chip that is one filter of several, [Role.RadioButton] for one choice of a group.
 * @param content The chip's label, handed the color it is drawn in.
 */
@Composable
internal fun SelectableChip(
    modifier: Modifier = Modifier,
    isSelected: Boolean,
    isEnabled: Boolean = true,
    role: Role,
    onClick: () -> Unit,
    content: @Composable RowScope.(contentColor: Color) -> Unit,
) {
    val colors = FilterChipDefaults.filterChipColors()
    val contentColor = animateColorAsState(
        when {
            !isEnabled -> colors.disabledLabelColor
            isSelected -> colors.selectedLabelColor
            else -> colors.labelColor
        }
    ).value
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .clip(FilterChipDefaults.shape)
            .background(if (isSelected) colors.selectedContainerColor else colors.containerColor)
            .border(FilterChipDefaults.filterChipBorder(enabled = isEnabled, selected = isSelected), FilterChipDefaults.shape)
            .indication(interactionSource, ripple(color = contentColor))
            .selectable(
                selected = isSelected,
                enabled = isEnabled,
                interactionSource = interactionSource,
                indication = null,
                role = role,
                onClick = onClick,
            )
            .height(FilterChipDefaults.Height)
            .padding(horizontal = CHIP_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        content = { content(contentColor) },
    )
}

private val SIDE_PANEL_WIDTH = 320.dp
private val SIDE_PANEL_TOP_PADDING = 16.dp
private val SIDE_PANEL_BOTTOM_PADDING = 16.dp
private const val SIDE_PANEL_MIN_COLUMN_COUNT = 2
private val MAX_TAG_WIDTH = 160.dp
private val CONTROLS_PADDING = 16.dp

/** Between the tags and the languages under them. */
private val FILTER_GROUP_GAP = 8.dp

/** What a Material filter chip keeps between its border and its label, and between the check and the label. */
private val CHIP_PADDING = 16.dp
private val CHIP_ICON_GAP = 8.dp

/** Between two [CountedFilterChip]s, the same across a row and between rows, wherever a group of them is laid out. */
internal val CHIP_GAP = 8.dp

/** The height of a [FilterSectionTitle], which is its action's with no touch target around it, and the gap under it. */
private val SECTION_ACTION_HEIGHT = 32.dp
private val SECTION_TITLE_BOTTOM_PADDING = 4.dp

/** The [LabelSortingToggle] is a small one, a hint about the list next to its title rather than a control of its own. */
private val SORTING_TOGGLE_SIZE = 32.dp
private val SORTING_TOGGLE_ICON_SIZE = 18.dp
private val SORTING_TOGGLE_GAP = 4.dp

/** The padding a text button keeps inside its own bounds, taken off so that its label lines up with the titles. */
private val BUTTON_INSET = 12.dp
