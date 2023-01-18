package com.pandulapeter.campfire.data.source.local.implementationDesktop.model

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

internal class UserPreferencesEntity : RealmObject {

    @PrimaryKey var id: String = TABLE_NAME
    var unselectedDatabaseUrls: String = ""
    var shouldShowExplicitSongs: Boolean = false

    companion object {
        const val TABLE_NAME = "userPreferences"
    }
}