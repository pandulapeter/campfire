package com.pandulapeter.campfire.data.source.local.implementationAndroid

import androidx.room.Room
import com.pandulapeter.campfire.data.source.local.api.DatabaseLocalSource
import com.pandulapeter.campfire.data.source.local.api.RawSongDetailsLocalSource
import com.pandulapeter.campfire.data.source.local.api.SetlistLocalSource
import com.pandulapeter.campfire.data.source.local.api.SongLocalSource
import com.pandulapeter.campfire.data.source.local.api.TranspositionLocalSource
import com.pandulapeter.campfire.data.source.local.api.UserPreferencesLocalSource
import com.pandulapeter.campfire.data.source.local.implementationAndroid.source.DatabaseLocalSourceImpl
import com.pandulapeter.campfire.data.source.local.implementationAndroid.source.RawSongDetailsLocalSourceImpl
import com.pandulapeter.campfire.data.source.local.implementationAndroid.source.SetlistLocalSourceImpl
import com.pandulapeter.campfire.data.source.local.implementationAndroid.source.SongLocalSourceImpl
import com.pandulapeter.campfire.data.source.local.implementationAndroid.source.TranspositionLocalSourceImpl
import com.pandulapeter.campfire.data.source.local.implementationAndroid.source.UserPreferencesLocalSourceImpl
import com.pandulapeter.campfire.data.source.local.implementationAndroid.storage.MIGRATION_1_2
import com.pandulapeter.campfire.data.source.local.implementationAndroid.storage.MIGRATION_2_3
import com.pandulapeter.campfire.data.source.local.implementationAndroid.storage.StorageManager
import org.koin.dsl.module

val dataLocalSourceAndroidModule = module {
    single {
        Room.databaseBuilder(get(), StorageManager::class.java, "campfireDatabase.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }
    factory { get<StorageManager>().getDatabaseDao() }
    factory { get<StorageManager>().getSetlistDao() }
    factory { get<StorageManager>().getSongsDao() }
    factory { get<StorageManager>().getRawSongDetailsDao() }
    factory { get<StorageManager>().getUserPreferencesDao() }
    factory { get<StorageManager>().getTranspositionDao() }
    factory<DatabaseLocalSource> { DatabaseLocalSourceImpl(get()) }
    factory<SetlistLocalSource> { SetlistLocalSourceImpl(get()) }
    factory<SongLocalSource> { SongLocalSourceImpl(get()) }
    factory<RawSongDetailsLocalSource> { RawSongDetailsLocalSourceImpl(get()) }
    factory<UserPreferencesLocalSource> { UserPreferencesLocalSourceImpl(get()) }
    factory<TranspositionLocalSource> { TranspositionLocalSourceImpl(get()) }
}