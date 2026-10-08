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

/**
 * The counts the settings screen shows for the library, only once there is a library to count.
 *
 * @param size The bytes the song and setlist files that were counted take up on disk.
 */
data class LibrarySummary(
    val songCount: Int,
    val setlistCount: Int,
    val size: Long,
)
