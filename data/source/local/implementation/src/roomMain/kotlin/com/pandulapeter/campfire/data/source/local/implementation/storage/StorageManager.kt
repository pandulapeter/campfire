package com.pandulapeter.campfire.data.source.local.implementation.storage

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.pandulapeter.campfire.data.source.local.implementation.model.DatabaseEntity
import com.pandulapeter.campfire.data.source.local.implementation.model.RawSongDetailsEntity
import com.pandulapeter.campfire.data.source.local.implementation.model.SetlistEntity
import com.pandulapeter.campfire.data.source.local.implementation.model.SongEntity
import com.pandulapeter.campfire.data.source.local.implementation.model.TranspositionEntity
import com.pandulapeter.campfire.data.source.local.implementation.model.UserPreferencesEntity
import com.pandulapeter.campfire.data.source.local.implementation.storage.dao.DatabaseDao
import com.pandulapeter.campfire.data.source.local.implementation.storage.dao.RawSongDetailsDao
import com.pandulapeter.campfire.data.source.local.implementation.storage.dao.SetlistDao
import com.pandulapeter.campfire.data.source.local.implementation.storage.dao.SongDao
import com.pandulapeter.campfire.data.source.local.implementation.storage.dao.TranspositionDao
import com.pandulapeter.campfire.data.source.local.implementation.storage.dao.UserPreferencesDao

@Database(
    entities = [
        DatabaseEntity::class,
        SetlistEntity::class,
        SongEntity::class,
        RawSongDetailsEntity::class,
        UserPreferencesEntity::class,
        TranspositionEntity::class
    ],
    version = 6,
    exportSchema = false
)
@ConstructedBy(StorageManagerConstructor::class)
internal abstract class StorageManager : RoomDatabase() {

    abstract fun getDatabaseDao(): DatabaseDao

    abstract fun getSetlistDao(): SetlistDao

    abstract fun getSongsDao(): SongDao

    abstract fun getRawSongDetailsDao(): RawSongDetailsDao

    abstract fun getUserPreferencesDao(): UserPreferencesDao

    abstract fun getTranspositionDao(): TranspositionDao

    companion object {

        val migrations: Array<Migration> = arrayOf(
            // Version 2: "Lyrics only" mode setting.
            object : Migration(1, 2) {
                override fun migrate(connection: SQLiteConnection) {
                    connection.execSQL("ALTER TABLE ${UserPreferencesEntity.TABLE_NAME} ADD COLUMN isLyricsOnlyModeEnabled INTEGER NOT NULL DEFAULT 0")
                }
            },
            // Version 3: text size of the song details screen.
            object : Migration(2, 3) {
                override fun migrate(connection: SQLiteConnection) {
                    connection.execSQL("ALTER TABLE ${UserPreferencesEntity.TABLE_NAME} ADD COLUMN fontScale REAL NOT NULL DEFAULT 1.0")
                }
            },
            // Version 4: the "explicit" flag of songs and the filter based on it were removed.
            object : Migration(3, 4) {
                override fun migrate(connection: SQLiteConnection) {
                    connection.execSQL("ALTER TABLE ${SongEntity.TABLE_NAME} DROP COLUMN isExplicit")
                    connection.execSQL("ALTER TABLE ${UserPreferencesEntity.TABLE_NAME} DROP COLUMN shouldShowExplicitSongs")
                }
            },
            // Version 5: "Horizontal section flow" setting of the song details screen.
            object : Migration(4, 5) {
                override fun migrate(connection: SQLiteConnection) {
                    connection.execSQL("ALTER TABLE ${UserPreferencesEntity.TABLE_NAME} ADD COLUMN isHorizontalSectionFlowEnabled INTEGER NOT NULL DEFAULT 0")
                }
            },
            // Version 6: the moment the text of a song was last downloaded. Songs saved before this are dated to the
            // epoch, so the first time each of them is opened it gets refreshed.
            object : Migration(5, 6) {
                override fun migrate(connection: SQLiteConnection) {
                    connection.execSQL("ALTER TABLE ${RawSongDetailsEntity.TABLE_NAME} ADD COLUMN refreshTimestamp INTEGER NOT NULL DEFAULT 0")
                }
            }
        )
    }
}

// The Room compiler generates the actual implementation for every target.
@Suppress("KotlinNoActualForExpect", "NO_ACTUAL_FOR_EXPECT")
internal expect object StorageManagerConstructor : RoomDatabaseConstructor<StorageManager> {
    override fun initialize(): StorageManager
}
