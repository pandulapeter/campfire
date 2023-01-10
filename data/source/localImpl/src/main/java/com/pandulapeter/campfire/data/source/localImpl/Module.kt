package com.pandulapeter.campfire.data.source.localImpl

import androidx.room.Room
import com.pandulapeter.campfire.data.source.local.CollectionLocalSource
import com.pandulapeter.campfire.data.source.local.SheetsLocalSource
import com.pandulapeter.campfire.data.source.local.SongLocalSource
import com.pandulapeter.campfire.data.source.localImpl.implementation.CollectionLocalSourceImpl
import com.pandulapeter.campfire.data.source.localImpl.implementation.SheetsLocalSourceImpl
import com.pandulapeter.campfire.data.source.localImpl.implementation.SongLocalSourceImpl
import com.pandulapeter.campfire.data.source.localImpl.implementation.database.DatabaseManager
import org.koin.dsl.module

val dataLocalSourceModule = module {
    single { Room.databaseBuilder(get(), DatabaseManager::class.java, "campfireDatabase.db").build() }
    factory { get<DatabaseManager>().getCollectionDao() }
    factory { get<DatabaseManager>().getSheetDao() }
    factory { get<DatabaseManager>().getSongsDao() }
    factory<CollectionLocalSource> { CollectionLocalSourceImpl(get()) }
    factory<SheetsLocalSource> { SheetsLocalSourceImpl(get()) }
    factory<SongLocalSource> { SongLocalSourceImpl(get()) }
}