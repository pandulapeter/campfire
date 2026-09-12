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

    /**
     * Whether a preferences document has ever been written. It is the one question [loadUserPreferences] cannot
     * answer, since for every other caller a missing document simply means the defaults - and it is asked about the
     * document rather than about what is in it: the preferences are the first thing Campfire writes about itself, so
     * their absence is what an installation that has never been used looks like from the inside.
     */
    suspend fun hasStoredUserPreferences(): Boolean
}
