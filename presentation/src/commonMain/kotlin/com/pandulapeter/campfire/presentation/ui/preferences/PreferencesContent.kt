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
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.ui.components.bounceVerticalScroll
import com.pandulapeter.campfire.presentation.ui.components.fadingVerticalEdges
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.metronome.MetronomeController
import com.pandulapeter.campfire.presentation.ui.metronome.MetronomePlaybackSection
import com.pandulapeter.campfire.presentation.ui.settingsPage.SECTION_GAP
import com.pandulapeter.campfire.presentation.ui.state.PreferencesController

/**
 * The page of the Preferences sheet that is picked, the same component Settings shows as the tab of that name,
 * crossfading into another as it is picked, scrolled above those buttons ([PreferencesFrame]).
 *
 * @param scrollStates One for each page, so that each keeps its own position and the outgoing one fades out where it
 *   was; its edges fade into [fadeColor].
 * @param selectedPage Shown only while it is one of [pages]; a page whose feature has just been switched off gives way
 *   to the first one (see [shownPreferencesPage]).
 */
@Composable
internal fun PreferencesContent(
    modifier: Modifier = Modifier,
    scrollStates: Map<PreferencesPage, ScrollState>,
    fadeColor: Color,
    preferencesController: PreferencesController,
    metronomeController: MetronomeController,
    userPreferences: UserPreferences?,
    isPerformanceModeEnabled: Boolean,
    pages: List<PreferencesPage>,
    selectedPage: PreferencesPage,
    showDialog: (DialogType) -> Unit,
) {
    val shownPage = shownPreferencesPage(pages, selectedPage)
    val fadeSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    // The size is the new page's at once: the sheet animates its own height to it, and a second animation of the same
    // height here would have the two disagree, the buttons under the page bobbing between them.
    AnimatedContent(
        modifier = modifier.fillMaxWidth(),
        targetState = shownPage,
        transitionSpec = { fadeIn(fadeSpec).togetherWith(fadeOut(fadeSpec)).using(SizeTransform { _, _ -> snap() }) },
    ) { page ->
        val scrollState = scrollStates.getValue(page)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fadingVerticalEdges(scrollState, fadeColor)
                .bounceVerticalScroll(scrollState)
                .padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(SECTION_GAP),
        ) {
            when (page) {
                PreferencesPage.GENERAL -> GeneralPreferencesPage(
                    userPreferences = userPreferences,
                    preferencesController = preferencesController,
                    isPerformanceModeEnabled = isPerformanceModeEnabled,
                )
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

/** [selectedPage] while it is one of [pages], else the first of them. */
internal fun shownPreferencesPage(pages: List<PreferencesPage>, selectedPage: PreferencesPage) = selectedPage.takeIf { it in pages } ?: pages.first()
