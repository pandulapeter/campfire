/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.api

import com.pandulapeter.campfire.data.model.domain.UserPreferences

interface UserPreferencesLocalSource {

    /**
     * Never null: a document that has not been written yet, or one that cannot be read, both mean the defaults. They
     * are defined in one place, next to the document itself, so that a new install and a document written by an
     * older version can never disagree about them.
     */
    suspend fun loadUserPreferences(): UserPreferences

    suspend fun saveUserPreferences(userPreferences: UserPreferences)
}
