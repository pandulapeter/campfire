package com.pandulapeter.campfire.ioc.module

import com.pandulapeter.campfire.data.network.NetworkManager
import com.pandulapeter.campfire.data.repository.LanguageRepository
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.data.storage.StorageManager
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
object RepositoryModule {

    @Provides
    @Singleton
    @JvmStatic
    fun provideSongInfoRepository(storageManager: StorageManager, networkManager: NetworkManager, languageRepository: LanguageRepository) = SongInfoRepository(storageManager, networkManager, languageRepository)

    @Provides
    @Singleton
    @JvmStatic
    fun provideLanguageRepository(storageManager: StorageManager) = LanguageRepository(storageManager)
}