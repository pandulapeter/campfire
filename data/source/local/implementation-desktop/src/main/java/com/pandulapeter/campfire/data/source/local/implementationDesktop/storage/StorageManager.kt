package com.pandulapeter.campfire.data.source.local.implementationDesktop.storage

import com.pandulapeter.campfire.data.source.local.implementationDesktop.model.CollectionEntity
import com.pandulapeter.campfire.data.source.local.implementationDesktop.model.DatabaseEntity
import com.pandulapeter.campfire.data.source.local.implementationDesktop.model.PlaylistEntity
import com.pandulapeter.campfire.data.source.local.implementationDesktop.model.SongEntity
import com.pandulapeter.campfire.data.source.local.implementationDesktop.model.UserPreferencesEntity
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration

internal class StorageManager {

    val database = Realm.open(
        RealmConfiguration.create(
            schema = setOf(
                CollectionEntity::class,
                DatabaseEntity::class,
                PlaylistEntity::class,
                SongEntity::class,
                UserPreferencesEntity::class
            )
        )
    )
}