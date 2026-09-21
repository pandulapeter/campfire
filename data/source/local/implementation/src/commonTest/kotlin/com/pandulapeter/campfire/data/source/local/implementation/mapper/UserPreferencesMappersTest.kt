/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.mapper

import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.data.source.local.implementation.model.UserPreferencesDocument
import kotlin.test.Test
import kotlin.test.assertEquals

/** A value a newer or an older version wrote costs that one setting, never the document it is in. */
internal class UserPreferencesMappersTest {

    @Test
    fun anUnknownEnumIdFallsBackForThatFieldOnly() {
        val preferences = UserPreferencesDocument(
            sortingMode = "by_mood",
            uiMode = UserPreferences.UiMode.DARK.id,
            tagMatchMode = "sometimes",
        ).toModel()

        assertEquals(UserPreferences.SortingMode.BY_ARTIST, preferences.sortingMode)
        assertEquals(UserPreferences.UiMode.DARK, preferences.uiMode)
        assertEquals(UserPreferences.TagMatchMode.ANY, preferences.tagMatchMode)
    }
}
