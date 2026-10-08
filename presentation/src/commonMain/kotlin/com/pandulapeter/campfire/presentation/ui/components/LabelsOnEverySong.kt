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

import com.pandulapeter.campfire.presentation.ui.CampfireViewModel

/**
 * What every song in the library is filed under, see [CampfireViewModel.labelsOnEverySong]. The tags are lowercase,
 * so a song's own has to be folded before it is looked up here.
 */
data class LabelsOnEverySong(
    val tags: Set<String> = emptySet(),
    val languages: Set<String> = emptySet(),
)
