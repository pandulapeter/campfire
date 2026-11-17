/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state

/**
 * The counts the settings screen shows for the library, only once there is a library to count, and the bytes each part
 * of it takes up on disk: the song and setlist files that were counted, and the pictures and attachments of
 * `library/files`.
 */
data class LibrarySummary(
    val songCount: Int,
    val setlistCount: Int,
    val songsSize: Long,
    val setlistsSize: Long,
    val picturesSize: Long,
    val attachmentsSize: Long,
) {
    val size get() = songsSize + setlistsSize + picturesSize + attachmentsSize
}
