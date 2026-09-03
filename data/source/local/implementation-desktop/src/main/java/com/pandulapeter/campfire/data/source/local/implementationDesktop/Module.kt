package com.pandulapeter.campfire.data.source.local.implementationDesktop

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.pandulapeter.campfire.data.source.local.api.DatabaseLocalSource
import com.pandulapeter.campfire.data.source.local.api.RawSongDetailsLocalSource
import com.pandulapeter.campfire.data.source.local.api.SetlistLocalSource
import com.pandulapeter.campfire.data.source.local.api.SongLocalSource
import com.pandulapeter.campfire.data.source.local.api.TranspositionLocalSource
import com.pandulapeter.campfire.data.source.local.api.UserPreferencesLocalSource
import com.pandulapeter.campfire.data.source.local.implementationDesktop.source.DatabaseLocalSourceImpl
import com.pandulapeter.campfire.data.source.local.implementationDesktop.source.RawSongDetailsLocalSourceImpl
import com.pandulapeter.campfire.data.source.local.implementationDesktop.source.SetlistLocalSourceImpl
import com.pandulapeter.campfire.data.source.local.implementationDesktop.source.SongLocalSourceImpl
import com.pandulapeter.campfire.data.source.local.implementationDesktop.source.TranspositionLocalSourceImpl
import com.pandulapeter.campfire.data.source.local.implementationDesktop.source.UserPreferencesLocalSourceImpl
import com.pandulapeter.campfire.data.source.local.implementationDesktop.storage.MIGRATION_1_2
import com.pandulapeter.campfire.data.source.local.implementationDesktop.storage.MIGRATION_2_3
import com.pandulapeter.campfire.data.source.local.implementationDesktop.storage.StorageManager
import kotlinx.coroutines.Dispatchers
import org.koin.dsl.module

val dataLocalSourceDesktopModule = module {
    single {
        Room.databaseBuilder<StorageManager>(name = "campfireDatabase.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
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
