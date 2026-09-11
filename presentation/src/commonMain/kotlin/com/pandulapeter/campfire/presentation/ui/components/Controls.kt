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
import androidx.compose.foundation.border
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.domain.SongLanguage
import com.pandulapeter.campfire.data.model.domain.Tag
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.filters
import com.pandulapeter.campfire.presentation.resources.ic_check
import com.pandulapeter.campfire.presentation.resources.setlists_show_archived
import com.pandulapeter.campfire.presentation.resources.setlists_sorting_mode
import com.pandulapeter.campfire.presentation.resources.setlists_sorting_mode_by_title
import com.pandulapeter.campfire.presentation.resources.setlists_sorting_mode_newest_first
import com.pandulapeter.campfire.presentation.resources.songs_languages
import com.pandulapeter.campfire.presentation.resources.songs_languages_clear
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
import org.jetbrains.compose.resources.painterResource

/**
 * Whether the [ControlsSidePanel] fits into a screen of the given width: the list comes first, so the panel only gets
 * its space when at least [SIDE_PANEL_MIN_COLUMN_COUNT] columns of songs remain next to it. On narrower screens the
 * same controls are shown in a bottom sheet instead.
 */
internal fun hasRoomForSidePanel(screenWidth: Dp) = columnCountForWidth(screenWidth - SIDE_PANEL_WIDTH) >= SIDE_PANEL_MIN_COLUMN_COUNT

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
 * A screen's controls ([SongsControls], [SetlistsControls]) in a panel that spans the full height of the screen next
 * to its app bar and content, shown on screens that are wide enough for it, see [hasRoomForSidePanel].
 *
 * @param content The controls themselves, handed the modifier that gives the panel its size and its background, and
 *   the insets the panel is responsible for.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    Row {
        VerticalDivider()
        content(
            Modifier.width(SIDE_PANEL_WIDTH + endPadding).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainerLow),
            PaddingValues(
                // The panel sits next to the app bar instead of below it, so it handles the top inset on its own.
                top = TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Top).asPaddingValues().calculateTopPadding(),
                end = endPadding,
                bottom = contentPadding.calculateBottomPadding(),
            ),
        )
    }
}

/**
 * The padding of the content shown next to a [ControlsSidePanel]: while the panel is visible, the end inset belongs
 * to the panel.
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
 * Sorting and filter controls of the setlists screen, shown in a side panel on wide enough screens and in a bottom
 * sheet otherwise, exactly as [SongsControls] is. They are a screen apart and deliberately not the same controls:
 * the song filters narrow a view of the library, while a setlist is a list somebody wrote down and shows what it
 * holds either way. What is left to ask here is the order the setlists come in, and whether the ones that have been
 * put away are among them.
 */
@Composable
internal fun SetlistsControls(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(contentPadding)
    ) {
        SettingsSectionTitle(text = stringResource(Res.string.setlists_sorting_mode))
        SegmentedChoice(
            options = listOf(
                UserPreferences.SetlistSortingMode.NEWEST_FIRST to stringResource(Res.string.setlists_sorting_mode_newest_first),
                UserPreferences.SetlistSortingMode.BY_TITLE to stringResource(Res.string.setlists_sorting_mode_by_title),
            ),
            selected = userPreferences?.setlistSortingMode,
            onSelected = viewModel::setSetlistSortingMode,
        )
        SettingsSectionTitle(text = stringResource(Res.string.filters))
        CheckboxListItem(
            title = stringResource(Res.string.setlists_show_archived),
            isChecked = userPreferences?.shouldShowArchivedSetlists == true,
            onCheckedChange = viewModel::setShouldShowArchivedSetlists,
        )
    }
}

/**
 * Sorting and filter controls of the song list, shown in a side panel on wide enough screens and in a bottom sheet
 * otherwise.
 */
@Composable
internal fun SongsControls(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val languages by viewModel.languages.collectAsStateWithLifecycle()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(bottom = 16.dp)
    ) {
        SettingsSectionTitle(text = stringResource(Res.string.songs_sorting_mode))
        SegmentedChoice(
            options = listOf(
                UserPreferences.SortingMode.BY_ARTIST to stringResource(Res.string.songs_sorting_mode_by_artist),
                UserPreferences.SortingMode.BY_TITLE to stringResource(Res.string.songs_sorting_mode_by_title),
            ),
            selected = userPreferences?.sortingMode,
            onSelected = viewModel::setSortingMode,
        )
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
        // A library that sings in one language has nothing to choose between, and the one group it would offer
        // ("Unknown", against the single language) is a question about a library nobody has filled in yet.
        AnimatedVisibility(
            visible = languages.size > 1,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            LanguageFilters(
                languages = languages,
                selectedLanguages = userPreferences?.selectedLanguages.orEmpty(),
                onLanguageClicked = viewModel::toggleLanguageFilter,
                onClear = viewModel::clearLanguageFilter,
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
    // A tag the library no longer has stays selected, and clearing deliberately leaves it alone
    // (CampfireViewModel.clearTagFilter), so what the action is offered for is a selection among the chips rather than
    // whatever the preferences still hold - a button that cleared nothing visible would be answering a question the
    // screen never asked.
    val hasClearableSelection = remember(tags, selected) { tags.any { it.name.lowercase() in selected } }
    FilterSectionTitle(
        title = stringResource(Res.string.songs_tags),
        isClearVisible = hasClearableSelection,
        clearText = stringResource(Res.string.songs_tags_clear),
        onClearClicked = onClear,
    )
    TagFlowRow(
        modifier = Modifier.padding(horizontal = CONTROLS_PADDING)
    ) {
        visibleTags.forEach { tag ->
            CountedFilterChip(
                label = tag.name,
                songCount = tag.songCount,
                isSelected = tag.name.lowercase() in selected,
                onClick = { onTagClicked(tag.name) },
            )
        }
    }
    // This one stays under the chips, since what it asks about is the list it is at the end of - and unlike the
    // clearing of the filter it comes and goes with the size of the library rather than with what is selected.
    if (tags.size > MAX_COLLAPSED_TAG_COUNT) {
        TextButton(
            modifier = Modifier.padding(horizontal = CONTROLS_PADDING - BUTTON_INSET),
            onClick = { isExpanded = !isExpanded },
        ) {
            Text(
                text = if (isExpanded) {
                    stringResource(Res.string.songs_tags_show_less)
                } else {
                    stringResource(Res.string.songs_tags_show_all, tags.size)
                }
            )
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

/**
 * The languages of the library as a filter, under the tags and deliberately simpler than they are: a library sings
 * in a handful of languages rather than in a hundred, so there is nothing to hide behind a "show all", and a song is
 * never sung in every selected language at once, so there is no "any / every" to ask about either.
 */
@Composable
private fun LanguageFilters(
    modifier: Modifier = Modifier,
    languages: List<SongLanguage>,
    selectedLanguages: Set<String>,
    onLanguageClicked: (String) -> Unit,
    onClear: () -> Unit,
) = Column(modifier = modifier) {
    // The chips rather than the preferences, for the same reason [TagFilters] counts them that way.
    val hasClearableSelection = remember(languages, selectedLanguages) { languages.any { it.code in selectedLanguages } }
    FilterSectionTitle(
        title = stringResource(Res.string.songs_languages),
        isClearVisible = hasClearableSelection,
        clearText = stringResource(Res.string.songs_languages_clear),
        onClearClicked = onClear,
    )
    TagFlowRow(
        modifier = Modifier.padding(horizontal = CONTROLS_PADDING)
    ) {
        languages.forEach { language ->
            CountedFilterChip(
                label = languageLabel(language.code),
                songCount = language.songCount,
                isSelected = language.code in selectedLanguages,
                onClick = { onLanguageClicked(language.code) },
            )
        }
    }
}

/**
 * The title of a filter group, with the action that empties it at the other end of the same row.
 *
 * It is up here rather than under the chips because it comes and goes with the selection, and a button of its own
 * would grow and shrink everything below it - in a bottom sheet, the sheet itself - every time a filter was turned on
 * or off. The row is laid out so that it cannot: it keeps the height a plain [SettingsSectionTitle] has, the title's
 * own padding split around a content box tall enough to hold the action ([SECTION_ACTION_HEIGHT]), so the title reads
 * exactly where it would have and the action never decides anything.
 */
@Composable
private fun FilterSectionTitle(
    modifier: Modifier = Modifier,
    title: String,
    isClearVisible: Boolean,
    clearText: String,
    onClearClicked: () -> Unit,
) = Row(
    modifier = modifier
        .fillMaxWidth()
        .padding(
            start = CONTROLS_PADDING,
            end = CONTROLS_PADDING - BUTTON_INSET,
            top = SECTION_TITLE_TOP_PADDING,
            bottom = SECTION_TITLE_BOTTOM_PADDING,
        )
        .height(SECTION_ACTION_HEIGHT),
    verticalAlignment = Alignment.CenterVertically,
) {
    SettingsSectionTitle(
        modifier = Modifier.weight(1f),
        text = title,
        contentPadding = PaddingValues(),
    )
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
                contentPadding = PaddingValues(horizontal = BUTTON_INSET),
            ) {
                Text(text = clearText)
            }
        }
    }
}

/**
 * One value of a filter group: what it is called, how many songs it still leaves, and whether it is on. The count is
 * part of the chip rather than a line under the group, since the number is what tells a tag worth picking from one
 * that would leave a single song on screen.
 *
 * It is a Material filter chip drawn by hand rather than [androidx.compose.material3.FilterChip] itself, for the one
 * thing the chip gets wrong: its press and hover state layer is drawn around the label instead of around the chip, so
 * only a band hugging the text lights up while the rest of what can be clicked stays dark. Everything the chip would
 * decide is still asked of `FilterChipDefaults`, so the colors, the border, the shape and the height are the ones
 * Material would have used; what is ours is the order of the modifiers. The indication is a node of its own
 * ([androidx.compose.foundation.indication]) sitting outside the padding and driven by the same interaction source as
 * the click, which is what puts the state layer on the chip's own bounds, and the clip above it is what rounds it -
 * the state layer is drawn as a plain rectangle and is bound by nothing else, which is what used to let it spill past
 * the rounded corners on the web.
 */
@Composable
private fun CountedFilterChip(
    modifier: Modifier = Modifier,
    label: String,
    songCount: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val colors = FilterChipDefaults.filterChipColors()
    val contentColor = if (isSelected) colors.selectedLabelColor else colors.labelColor
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(FilterChipDefaults.shape)
            .background(if (isSelected) colors.selectedContainerColor else colors.containerColor)
            .border(FilterChipDefaults.filterChipBorder(enabled = true, selected = isSelected), FilterChipDefaults.shape)
            .indication(interactionSource, ripple(color = contentColor))
            .selectable(
                selected = isSelected,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Checkbox,
                onClick = onClick,
            )
            .height(FilterChipDefaults.Height)
            .padding(horizontal = CHIP_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The check grows into the chip the way the Material one does, so that turning a filter on is a movement
        // rather than a chip that changes width between two frames.
        AnimatedVisibility(
            visible = isSelected,
            enter = expandHorizontally() + fadeIn(),
            exit = shrinkHorizontally() + fadeOut(),
        ) {
            Icon(
                modifier = Modifier.padding(end = CHIP_ICON_GAP).size(FilterChipDefaults.IconSize),
                painter = painterResource(Res.drawable.ic_check),
                contentDescription = null,
                tint = if (isSelected) colors.selectedLeadingIconColor else colors.leadingIconColor,
            )
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
            modifier = Modifier.padding(start = TAG_GAP),
            text = songCount.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val SIDE_PANEL_WIDTH = 320.dp
private const val SIDE_PANEL_MIN_COLUMN_COUNT = 3
private const val MAX_COLLAPSED_TAG_COUNT = 12
private val MAX_TAG_WIDTH = 160.dp
private val CONTROLS_PADDING = 16.dp

/** What a Material filter chip keeps between its border and its label, and between the check and the label. */
private val CHIP_PADDING = 16.dp
private val CHIP_ICON_GAP = 8.dp

/**
 * The three that make a [FilterSectionTitle] exactly as tall as the [SettingsSectionTitle] it stands in for: its 24dp
 * and 8dp of padding, less the 6dp the content box grows past the line of text it would otherwise be.
 */
private val SECTION_ACTION_HEIGHT = 32.dp
private val SECTION_TITLE_TOP_PADDING = 18.dp
private val SECTION_TITLE_BOTTOM_PADDING = 2.dp

/** The padding a text button keeps inside its own bounds, taken off so that its label lines up with the titles. */
private val BUTTON_INSET = 12.dp
