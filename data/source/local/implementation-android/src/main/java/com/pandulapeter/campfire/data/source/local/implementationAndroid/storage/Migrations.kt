package com.pandulapeter.campfire.data.source.local.implementationAndroid.storage

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.pandulapeter.campfire.data.source.local.implementationAndroid.model.TranspositionEntity

internal val MIGRATION_1_2 = object : Migration(1, 2) {

    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `${TranspositionEntity.TABLE_NAME}` (`songId` TEXT NOT NULL, `transposition` INTEGER NOT NULL, PRIMARY KEY(`songId`))")
    }
}
