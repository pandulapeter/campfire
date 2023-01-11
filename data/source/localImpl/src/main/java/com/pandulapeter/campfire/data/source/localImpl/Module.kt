package com.pandulapeter.campfire.data.source.localImpl

import androidx.room.Room
import com.pandulapeter.campfire.data.source.local.CollectionLocalSource
import com.pandulapeter.campfire.data.source.local.DatabaseLocalSource
import com.pandulapeter.campfire.data.source.local.PlaylistLocalSource
import com.pandulapeter.campfire.data.source.local.SongLocalSource
import com.pandulapeter.campfire.data.source.localImpl.implementation.CollectionLocalSourceImpl
import com.pandulapeter.campfire.data.source.localImpl.implementation.DatabaseLocalSourceImpl
import com.pandulapeter.campfire.data.source.localImpl.implementation.PlaylistLocalSourceImpl
import com.pandulapeter.campfire.data.source.localImpl.implementation.SongLocalSourceImpl
import com.pandulapeter.campfire.data.source.localImpl.implementation.storage.StorageManager
import org.koin.dsl.module

val dataLocalSourceModule = module {
    single { Room.databaseBuilder(get(), StorageManager::class.java, "campfireDatabase.db").build() }
    factory { get<StorageManager>().getCollectionDao() }
    factory { get<StorageManager>().getDatabaseDao() }
    factory { get<StorageManager>().getPlaylistDao() }
    factory { get<StorageManager>().getSongsDao() }
    factory<CollectionLocalSource> { CollectionLocalSourceImpl(get()) }
    factory<DatabaseLocalSource> { DatabaseLocalSourceImpl(get()) }
    factory<PlaylistLocalSource> { PlaylistLocalSourceImpl(get()) }
    factory<SongLocalSource> { SongLocalSourceImpl(get()) }
}