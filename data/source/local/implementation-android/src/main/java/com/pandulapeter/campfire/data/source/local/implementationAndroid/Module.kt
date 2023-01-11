package com.pandulapeter.campfire.data.source.local.implementationAndroid

import androidx.room.Room
import com.pandulapeter.campfire.data.source.local.api.CollectionLocalSource
import com.pandulapeter.campfire.data.source.local.api.DatabaseLocalSource
import com.pandulapeter.campfire.data.source.local.api.PlaylistLocalSource
import com.pandulapeter.campfire.data.source.local.api.SongLocalSource
import com.pandulapeter.campfire.data.source.local.api.UserPreferencesLocalSource
import com.pandulapeter.campfire.data.source.local.implementationAndroid.source.CollectionLocalSourceImpl
import com.pandulapeter.campfire.data.source.local.implementationAndroid.source.DatabaseLocalSourceImpl
import com.pandulapeter.campfire.data.source.local.implementationAndroid.source.PlaylistLocalSourceImpl
import com.pandulapeter.campfire.data.source.local.implementationAndroid.source.SongLocalSourceImpl
import com.pandulapeter.campfire.data.source.local.implementationAndroid.source.UserPreferencesLocalSourceImpl
import com.pandulapeter.campfire.data.source.local.implementationAndroid.storage.StorageManager
import org.koin.dsl.module

val dataLocalSourceAndroidModule = module {
    single { Room.databaseBuilder(get(), StorageManager::class.java, "campfireDatabase.db").build() }
    factory { get<StorageManager>().getCollectionDao() }
    factory { get<StorageManager>().getDatabaseDao() }
    factory { get<StorageManager>().getPlaylistDao() }
    factory { get<StorageManager>().getSongsDao() }
    factory { get<StorageManager>().getUserPreferencesDao() }
    factory<CollectionLocalSource> { CollectionLocalSourceImpl(get()) }
    factory<DatabaseLocalSource> { DatabaseLocalSourceImpl(get()) }
    factory<PlaylistLocalSource> { PlaylistLocalSourceImpl(get()) }
    factory<SongLocalSource> { SongLocalSourceImpl(get()) }
    factory<UserPreferencesLocalSource> { UserPreferencesLocalSourceImpl(get()) }
}