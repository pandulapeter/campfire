/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.api.models

import com.pandulapeter.campfire.data.model.domain.SongLanguage
import com.pandulapeter.campfire.data.model.domain.UserPreferences

/**
 * What the song list is narrowed to right now, as opposed to how it is read: the tags and the languages picked in the
 * filter controls. It is deliberately not a preference and is never written anywhere — a filter is a question asked
 * of the library for the moment, and one that came back on the next launch would read as songs having gone missing.
 * How several selected values of a group combine ([UserPreferences.tagMatchMode], [UserPreferences.languageMatchMode])
 * is a standing choice instead, and stays a preference.
 */
data class SongFilter(
    /**
     * The tags the list is narrowed to, empty when every song is shown. A tag no song carries any more is kept rather
     * than pruned: the filter ignores it, and it starts working again the moment a song is tagged that way.
     */
    val selectedTags: Set<String> = emptySet(),
    /**
     * The languages the list is narrowed to, empty when every song is shown, including [SongLanguage.UNKNOWN] for the
     * songs that declare none. Kept rather than pruned like [selectedTags] is.
     */
    val selectedLanguages: Set<String> = emptySet(),
)
