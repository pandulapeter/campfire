package com.pandulapeter.campfire.data.source.local.implementationDesktop

import com.pandulapeter.campfire.data.source.local.api.DatabaseLocalSource
import com.pandulapeter.campfire.data.source.local.api.RawSongDetailsLocalSource
import com.pandulapeter.campfire.data.source.local.api.SetlistLocalSource
import com.pandulapeter.campfire.data.source.local.api.SongLocalSource
import com.pandulapeter.campfire.data.source.local.api.UserPreferencesLocalSource
import com.pandulapeter.campfire.data.source.local.implementationDesktop.source.DatabaseLocalSourceImpl
import com.pandulapeter.campfire.data.source.local.implementationDesktop.source.RawSongDetailsLocalSourceImpl
import com.pandulapeter.campfire.data.source.local.implementationDesktop.source.SetlistLocalSourceImpl
import com.pandulapeter.campfire.data.source.local.implementationDesktop.source.SongLocalSourceImpl
import com.pandulapeter.campfire.data.source.local.implementationDesktop.source.UserPreferencesLocalSourceImpl
import com.pandulapeter.campfire.data.source.local.implementationDesktop.storage.StorageManager
import org.koin.dsl.module

val dataLocalSourceDesktopModule = module {
    single { StorageManager() }
    factory<DatabaseLocalSource> { DatabaseLocalSourceImpl(get()) }
    factory<SetlistLocalSource> { SetlistLocalSourceImpl(get()) }
    factory<SongLocalSource> { SongLocalSourceImpl(get()) }
    factory<RawSongDetailsLocalSource> { RawSongDetailsLocalSourceImpl(get()) }
    factory<UserPreferencesLocalSource> { UserPreferencesLocalSourceImpl(get()) }
}