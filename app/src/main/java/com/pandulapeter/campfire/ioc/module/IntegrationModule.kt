package com.pandulapeter.campfire.ioc.module

import android.content.Context
import com.pandulapeter.campfire.data.integration.AppShortcutManager
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.data.storage.DataStorageManager
import com.pandulapeter.campfire.ioc.app.AppContext
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
object IntegrationModule {

    @Provides
    @Singleton
    @JvmStatic
    fun provideAppShortcutManager(
        @AppContext context: Context,
        dataStorageManager: DataStorageManager,
        playlistRepository: PlaylistRepository) = AppShortcutManager(context, dataStorageManager, playlistRepository)
}