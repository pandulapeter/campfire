package com.pandulapeter.campfire.data.source.local.implementation

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.pandulapeter.campfire.data.source.local.api.RawSongDetailsLocalSource
import com.pandulapeter.campfire.data.source.local.api.SetlistLocalSource
import com.pandulapeter.campfire.data.source.local.api.SongLocalSource
import com.pandulapeter.campfire.data.source.local.api.TranspositionLocalSource
import com.pandulapeter.campfire.data.source.local.api.UserPreferencesLocalSource
import com.pandulapeter.campfire.data.source.local.implementation.source.RawSongDetailsLocalSourceImpl
import com.pandulapeter.campfire.data.source.local.implementation.source.SetlistLocalSourceImpl
import com.pandulapeter.campfire.data.source.local.implementation.source.SongLocalSourceImpl
import com.pandulapeter.campfire.data.source.local.implementation.source.TranspositionLocalSourceImpl
import com.pandulapeter.campfire.data.source.local.implementation.source.UserPreferencesLocalSourceImpl
import com.pandulapeter.campfire.data.source.local.implementation.storage.StorageManager
import com.pandulapeter.campfire.data.source.local.implementation.storage.createStorageManagerBuilder
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.FileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.createFileStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import org.koin.core.module.Module
import org.koin.dsl.module

actual val dataLocalSourceModule: Module = module {
    single {
        createStorageManagerBuilder()
            .addMigrations(*StorageManager.migrations)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }
    single<FileStorage> { createFileStorage() }
    factory { get<StorageManager>().getSetlistDao() }
    factory { get<StorageManager>().getSongsDao() }
    factory { get<StorageManager>().getRawSongDetailsDao() }
    factory { get<StorageManager>().getUserPreferencesDao() }
    factory { get<StorageManager>().getTranspositionDao() }
    factory<SetlistLocalSource> { SetlistLocalSourceImpl(get()) }
    factory<SongLocalSource> { SongLocalSourceImpl(get()) }
    factory<RawSongDetailsLocalSource> { RawSongDetailsLocalSourceImpl(get()) }
    factory<UserPreferencesLocalSource> { UserPreferencesLocalSourceImpl(get()) }
    factory<TranspositionLocalSource> { TranspositionLocalSourceImpl(get()) }
}
