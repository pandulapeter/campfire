/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel

/**
 * Collects the sync state itself, so that a run reporting every file it moves recomposes this section and no other.
 * It is the first section of the library's tab, and the one on a card of its own, since it is the one with something
 * going on in it: the card is what sets it apart from the library's rows under it, in place of a title over each.
 */
@Composable
internal fun SyncSection(
    viewModel: CampfireViewModel,
) = SettingsCard {
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    SyncSettings(viewModel = viewModel, syncState = syncState)
}
