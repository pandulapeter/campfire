package com.pandulapeter.campfire.data.source.local.implementation

import com.pandulapeter.campfire.data.source.local.api.SetlistLocalSource
import com.pandulapeter.campfire.data.source.local.api.SongLocalSource
import com.pandulapeter.campfire.data.source.local.api.UserPreferencesLocalSource
import com.pandulapeter.campfire.data.source.local.implementation.source.SetlistLocalSourceImpl
import com.pandulapeter.campfire.data.source.local.implementation.source.SongLocalSourceImpl
import com.pandulapeter.campfire.data.source.local.implementation.source.UserPreferencesLocalSourceImpl
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.FileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.createFileStorage
import org.koin.dsl.module

val dataLocalSourceModule = module {
    single<FileStorage> { createFileStorage() }
    single<SongLocalSource> { SongLocalSourceImpl(get()) }
    single<SetlistLocalSource> { SetlistLocalSourceImpl(get()) }
    single<UserPreferencesLocalSource> { UserPreferencesLocalSourceImpl(get()) }
}
