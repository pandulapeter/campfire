package com.pandulapeter.campfire.data.source.local.implementation

import com.pandulapeter.campfire.data.source.local.api.ArchiveLocalSource
import com.pandulapeter.campfire.data.source.local.api.LibraryFileLocalSource
import com.pandulapeter.campfire.data.source.local.api.SetlistLocalSource
import com.pandulapeter.campfire.data.source.local.api.SongLocalSource
import com.pandulapeter.campfire.data.source.local.api.SyncStateLocalSource
import com.pandulapeter.campfire.data.source.local.api.UserPreferencesLocalSource
import com.pandulapeter.campfire.data.source.local.implementation.source.ArchiveLocalSourceImpl
import com.pandulapeter.campfire.data.source.local.implementation.source.LibraryFileLocalSourceImpl
import com.pandulapeter.campfire.data.source.local.implementation.source.SetlistLocalSourceImpl
import com.pandulapeter.campfire.data.source.local.implementation.source.SongLocalSourceImpl
import com.pandulapeter.campfire.data.source.local.implementation.source.SyncStateLocalSourceImpl
import com.pandulapeter.campfire.data.source.local.implementation.source.UserPreferencesLocalSourceImpl
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.FileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.createFileStorage
import org.koin.dsl.module

val dataLocalSourceModule = module {
    single<FileStorage> { createFileStorage() }
    single<ArchiveLocalSource> { ArchiveLocalSourceImpl() }
    single<SongLocalSource> { SongLocalSourceImpl(get()) }
    single<SetlistLocalSource> { SetlistLocalSourceImpl(get()) }
    single<UserPreferencesLocalSource> { UserPreferencesLocalSourceImpl(get()) }
    single<LibraryFileLocalSource> { LibraryFileLocalSourceImpl(get()) }
    single<SyncStateLocalSource> { SyncStateLocalSourceImpl(get()) }
}
