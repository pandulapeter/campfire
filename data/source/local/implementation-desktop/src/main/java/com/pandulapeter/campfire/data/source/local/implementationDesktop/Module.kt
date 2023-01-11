package com.pandulapeter.campfire.data.source.local.implementationDesktop

import com.pandulapeter.campfire.data.source.local.api.CollectionLocalSource
import com.pandulapeter.campfire.data.source.local.api.DatabaseLocalSource
import com.pandulapeter.campfire.data.source.local.api.PlaylistLocalSource
import com.pandulapeter.campfire.data.source.local.api.SongLocalSource
import com.pandulapeter.campfire.data.source.local.api.UserPreferencesLocalSource
import com.pandulapeter.campfire.data.source.local.implementationDesktop.source.CollectionLocalSourceImpl
import com.pandulapeter.campfire.data.source.local.implementationDesktop.source.DatabaseLocalSourceImpl
import com.pandulapeter.campfire.data.source.local.implementationDesktop.source.PlaylistLocalSourceImpl
import com.pandulapeter.campfire.data.source.local.implementationDesktop.source.SongLocalSourceImpl
import com.pandulapeter.campfire.data.source.local.implementationDesktop.source.UserPreferencesLocalSourceImpl
import org.koin.dsl.module

val dataLocalSourceDesktopModule = module {
    factory<CollectionLocalSource> { CollectionLocalSourceImpl() }
    factory<DatabaseLocalSource> { DatabaseLocalSourceImpl() }
    factory<PlaylistLocalSource> { PlaylistLocalSourceImpl() }
    factory<SongLocalSource> { SongLocalSourceImpl() }
    factory<UserPreferencesLocalSource> { UserPreferencesLocalSourceImpl() }
}