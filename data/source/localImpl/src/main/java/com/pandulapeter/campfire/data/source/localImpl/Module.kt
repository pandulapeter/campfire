package com.pandulapeter.campfire.data.source.localImpl

import androidx.room.Room
import com.pandulapeter.campfire.data.source.local.CollectionLocalSource
import com.pandulapeter.campfire.data.source.local.LanguageLocalSource
import com.pandulapeter.campfire.data.source.local.SongLocalSource
import com.pandulapeter.campfire.data.source.localImpl.implementation.CollectionLocalSourceImpl
import com.pandulapeter.campfire.data.source.localImpl.implementation.LanguageLocalSourceImpl
import com.pandulapeter.campfire.data.source.localImpl.implementation.SongLocalSourceImpl
import com.pandulapeter.campfire.data.source.localImpl.implementation.database.DatabaseManager
import org.koin.dsl.module

val dataLocalSourceModule = module {
    single { Room.databaseBuilder(get(), DatabaseManager::class.java, "campfireDatabase.db").build() }
    factory { get<DatabaseManager>().getCollectionDao() }
    factory { get<DatabaseManager>().getLanguageDao() }
    factory { get<DatabaseManager>().getSongsDao() }
    factory<CollectionLocalSource> { CollectionLocalSourceImpl(get()) }
    factory<LanguageLocalSource> { LanguageLocalSourceImpl(get()) }
    factory<SongLocalSource> { SongLocalSourceImpl(get()) }
}