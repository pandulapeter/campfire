/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

/** The groups of [SongInfoBody], in the order they are drawn. */
internal enum class SongInfoGroupKind {
    DETAILS,
    DEFAULTS,
    TAGS,
    LANGUAGES,
    LINKS,
}

/**
 * Which groups [SongInfoBody] draws, in order: what the sheet's grouped list counts its tiles by, since only the first
 * and the last of the tiles that are there get the list's outer corners.
 */
internal fun songInfoGroupsOf(
    hasDetails: Boolean,
    hasDefaults: Boolean,
    hasTags: Boolean,
    hasLanguages: Boolean,
    hasLinks: Boolean,
): List<SongInfoGroupKind> = listOfNotNull(
    SongInfoGroupKind.DETAILS.takeIf { hasDetails },
    SongInfoGroupKind.DEFAULTS.takeIf { hasDefaults },
    SongInfoGroupKind.TAGS.takeIf { hasTags },
    SongInfoGroupKind.LANGUAGES.takeIf { hasLanguages },
    SongInfoGroupKind.LINKS.takeIf { hasLinks },
)
