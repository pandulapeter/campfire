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

import com.pandulapeter.campfire.presentation.ui.components.labels.orderedBy
import com.pandulapeter.campfire.presentation.ui.components.labels.tagColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.layoutId
import com.pandulapeter.campfire.data.model.song.Tag
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.data.model.song.tagKey
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_label
import com.pandulapeter.campfire.presentation.resources.songs_tags
import com.pandulapeter.campfire.presentation.resources.songs_tags_clear
import com.pandulapeter.campfire.presentation.resources.songs_tags_match_mode
import com.pandulapeter.campfire.presentation.resources.songs_tags_match_mode_all
import com.pandulapeter.campfire.presentation.resources.songs_tags_match_mode_any
import org.jetbrains.compose.resources.painterResource

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
internal fun TagFilters(
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
    val selected = remember(selectedTags) { selectedTags.mapTo(mutableSetOf(), ::tagKey) }
    val orderedTags = remember(tags, sortingMode) { tags.orderedBy(sortingMode) }
    val isPinned = remember(orderedTags, selected) { orderedTags.map { tagKey(it.name) in selected } }
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
        key = { tagKey(it.name) },
        isExpanded = isExpanded,
        isPinned = isPinned,
        onExpandedChanged = { isExpanded = it },
    ) { tag ->
        CountedFilterChip(
            label = tag.name,
            songCount = tag.songCount,
            isSelected = tagKey(tag.name) in selected,
            onClick = { onTagClicked(tag.name) },
            leadingIcon = painterResource(Res.drawable.ic_label),
            leadingIconFill = tagColor(tag.name)?.color() ?: Color.Unspecified,
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
