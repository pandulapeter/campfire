package com.pandulapeter.campfire.data.source.local.implementation

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.pandulapeter.campfire.data.source.local.api.DatabaseLocalSource
import com.pandulapeter.campfire.data.source.local.api.RawSongDetailsLocalSource
import com.pandulapeter.campfire.data.source.local.api.SetlistLocalSource
import com.pandulapeter.campfire.data.source.local.api.SongLocalSource
import com.pandulapeter.campfire.data.source.local.api.TranspositionLocalSource
import com.pandulapeter.campfire.data.source.local.api.UserPreferencesLocalSource
import com.pandulapeter.campfire.data.source.local.implementation.source.DatabaseLocalSourceImpl
import com.pandulapeter.campfire.data.source.local.implementation.source.RawSongDetailsLocalSourceImpl
import com.pandulapeter.campfire.data.source.local.implementation.source.SetlistLocalSourceImpl
import com.pandulapeter.campfire.data.source.local.implementation.source.SongLocalSourceImpl
import com.pandulapeter.campfire.data.source.local.implementation.source.TranspositionLocalSourceImpl
import com.pandulapeter.campfire.data.source.local.implementation.source.UserPreferencesLocalSourceImpl
import com.pandulapeter.campfire.data.source.local.implementation.storage.StorageManager
import com.pandulapeter.campfire.data.source.local.implementation.storage.createStorageManagerBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import org.koin.dsl.module

val dataLocalSourceModule = module {
    single {
        createStorageManagerBuilder()
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
