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

import com.pandulapeter.campfire.presentation.ui.components.languageLabel
import com.pandulapeter.campfire.presentation.ui.components.languageName
import com.pandulapeter.campfire.presentation.ui.components.orderedBy
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.layoutId
import com.pandulapeter.campfire.data.model.domain.SongLanguage
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.currentLanguage
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_language
import com.pandulapeter.campfire.presentation.resources.songs_languages
import com.pandulapeter.campfire.presentation.resources.songs_languages_clear
import com.pandulapeter.campfire.presentation.resources.songs_languages_match_mode
import com.pandulapeter.campfire.presentation.resources.songs_languages_match_mode_all
import com.pandulapeter.campfire.presentation.resources.songs_languages_match_mode_any
import org.jetbrains.compose.resources.painterResource

/**
 * The languages of the library as a filter, under the tags and laid out the way they are: a library usually sings in
 * a handful of languages, which then take a line or two and are shown whole, but one gathered from all over the world
 * is cut down to the room it is given like the tags. A song can carry several languages the way it carries several
 * tags, so "any / every" is asked here too.
 */
@Composable
internal fun LanguageFilters(
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
            leadingIcon = painterResource(Res.drawable.ic_language),
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
