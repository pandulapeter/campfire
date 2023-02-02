package com.pandulapeter.campfire.data.source.local.implementationDesktop.storage

import com.pandulapeter.campfire.data.source.local.implementationDesktop.model.DatabaseEntity
import com.pandulapeter.campfire.data.source.local.implementationDesktop.model.RawSongDetailsEntity
import com.pandulapeter.campfire.data.source.local.implementationDesktop.model.SetlistEntity
import com.pandulapeter.campfire.data.source.local.implementationDesktop.model.SongEntity
import com.pandulapeter.campfire.data.source.local.implementationDesktop.model.UserPreferencesEntity
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.migration.AutomaticSchemaMigration

internal class StorageManager {

    val database = Realm.open(
        RealmConfiguration.Builder(
            schema = setOf(
                DatabaseEntity::class,
                SetlistEntity::class,
                SongEntity::class,
                RawSongDetailsEntity::class,
                UserPreferencesEntity::class
            )
        )
            .name("campfireDatabase.db")
            .migration(object : AutomaticSchemaMigration {
                override fun migrate(migrationContext: AutomaticSchemaMigration.MigrationContext) = Unit
            })
            .build()
    )
}