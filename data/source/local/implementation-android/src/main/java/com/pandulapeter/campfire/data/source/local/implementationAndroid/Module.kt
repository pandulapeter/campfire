package com.pandulapeter.campfire.data.source.local.implementationAndroid

import androidx.room.Room
import com.pandulapeter.campfire.data.source.local.api.DatabaseLocalSource
import com.pandulapeter.campfire.data.source.local.api.SetlistLocalSource
import com.pandulapeter.campfire.data.source.local.api.SongLocalSource
import com.pandulapeter.campfire.data.source.local.api.UserPreferencesLocalSource
import com.pandulapeter.campfire.data.source.local.implementationAndroid.source.DatabaseLocalSourceImpl
import com.pandulapeter.campfire.data.source.local.implementationAndroid.source.SetlistLocalSourceImpl
import com.pandulapeter.campfire.data.source.local.implementationAndroid.source.SongLocalSourceImpl
import com.pandulapeter.campfire.data.source.local.implementationAndroid.source.UserPreferencesLocalSourceImpl
import com.pandulapeter.campfire.data.source.local.implementationAndroid.storage.StorageManager
import org.koin.dsl.module

val dataLocalSourceAndroidModule = module {
    single {
        Room.databaseBuilder(get(), StorageManager::class.java, "campfireDatabase.db")
            .fallbackToDestructiveMigration()
            .build()
    }
    factory { get<StorageManager>().getDatabaseDao() }
    factory { get<StorageManager>().getSetlistDao() }
    factory { get<StorageManager>().getSongsDao() }
    factory { get<StorageManager>().getUserPreferencesDao() }
    factory<DatabaseLocalSource> { DatabaseLocalSourceImpl(get()) }
    factory<SetlistLocalSource> { SetlistLocalSourceImpl(get()) }
    factory<SongLocalSource> { SongLocalSourceImpl(get()) }
    factory<UserPreferencesLocalSource> { UserPreferencesLocalSourceImpl(get()) }
}