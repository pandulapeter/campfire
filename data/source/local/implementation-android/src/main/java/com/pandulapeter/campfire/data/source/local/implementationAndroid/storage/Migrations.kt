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

internal val MIGRATION_2_3 = object : Migration(2, 3) {

    override fun migrate(connection: SQLiteConnection) {
        val temporaryTableName = "${TranspositionEntity.TABLE_NAME}Temporary"
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `$temporaryTableName` (`songId` TEXT NOT NULL, `setlistId` TEXT NOT NULL, `transposition` INTEGER NOT NULL, PRIMARY KEY(`songId`, `setlistId`))"
        )
        connection.execSQL(
            "INSERT INTO `$temporaryTableName` (`songId`, `setlistId`, `transposition`) SELECT `songId`, '${TranspositionEntity.NO_SETLIST_ID}', `transposition` FROM `${TranspositionEntity.TABLE_NAME}`"
        )
        connection.execSQL("DROP TABLE `${TranspositionEntity.TABLE_NAME}`")
        connection.execSQL("ALTER TABLE `$temporaryTableName` RENAME TO `${TranspositionEntity.TABLE_NAME}`")
    }
}
