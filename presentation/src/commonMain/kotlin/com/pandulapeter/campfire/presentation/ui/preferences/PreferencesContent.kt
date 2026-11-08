/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.preferences

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.metronome.MetronomeController
import com.pandulapeter.campfire.presentation.ui.metronome.MetronomePlaybackSection
import com.pandulapeter.campfire.presentation.ui.settingsPage.SECTION_GAP
import com.pandulapeter.campfire.presentation.ui.state.PreferencesController

/**
 * The Preferences, drawn the same as Settings' Preferences tab and as the sheet the screens they affect open: read only
 * mode ([PerformanceSection]) above everything, since it affects everything, then the [pages] under a secondary tab row
 * ([PreferencesPageRow]), lighter than Settings' own so that the one reads as nested in the other. A single page has no
 * row. Emits its two parts as siblings, so the caller's column spaces them as it spaces its sections.
 *
 * @param selectedPage Shown only while it is one of [pages]; a page whose feature has just been switched off gives way
 *   to the first one.
 */
@Composable
internal fun PreferencesContent(
    preferencesController: PreferencesController,
    metronomeController: MetronomeController,
    userPreferences: UserPreferences?,
    isPerformanceModeEnabled: Boolean,
    pages: List<PreferencesPage>,
    selectedPage: PreferencesPage,
    onPageSelected: (PreferencesPage) -> Unit,
    showDialog: (DialogType) -> Unit,
) {
    // Only there while the songs are, since everything it locks is about them.
    AnimatedVisibility(
        visible = userPreferences?.areSongsEnabled != false,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        PerformanceSection(
            isPerformanceModeEnabled = isPerformanceModeEnabled,
            areSetlistsEnabled = userPreferences?.areSetlistsEnabled != false,
            onPerformanceModeChanged = preferencesController::setPerformanceModeEnabled,
        )
    }
    val shownPage = selectedPage.takeIf { it in pages } ?: pages.first()
    Column(modifier = Modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = pages.size > 1,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            PreferencesPageRow(pages = pages, selectedPage = shownPage, onPageSelected = onPageSelected)
        }
        val slideSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
        val fadeSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        // The pages slide in the direction of their tabs, the outgoing one leaving as the next arrives, and the height
        // follows from one page to the next, so that the sheet they are in grows and shrinks with them rather than
        // jumping to the new page's height.
        AnimatedContent(
            modifier = Modifier.fillMaxWidth(),
            targetState = shownPage,
            transitionSpec = {
                val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (slideInHorizontally(slideSpec) { it * direction } + fadeIn(fadeSpec))
                    .togetherWith(slideOutHorizontally(slideSpec) { -it * direction } + fadeOut(fadeSpec))
                    .using(SizeTransform())
            },
        ) { page ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(SECTION_GAP),
            ) {
                when (page) {
                    PreferencesPage.SONG -> SongPreferencesPage(userPreferences = userPreferences, preferencesController = preferencesController)
                    PreferencesPage.CHORDS -> ChordsPreferencesPage(
                        userPreferences = userPreferences,
                        preferencesController = preferencesController,
                        showDialog = showDialog,
                    )
                    PreferencesPage.METRONOME -> MetronomePlaybackSection(metronomeController = metronomeController)
                }
            }
        }
    }
}
