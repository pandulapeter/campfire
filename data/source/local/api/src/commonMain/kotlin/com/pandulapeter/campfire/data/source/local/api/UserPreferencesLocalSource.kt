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
