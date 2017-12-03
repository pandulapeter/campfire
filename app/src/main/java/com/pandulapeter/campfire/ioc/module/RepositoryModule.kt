package com.pandulapeter.campfire.ioc.module

import com.pandulapeter.campfire.data.network.NetworkManager
import com.pandulapeter.campfire.data.repository.LanguageRepository
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.data.repository.UserPreferenceRepository
import com.pandulapeter.campfire.data.storage.DataStorageManager
import com.pandulapeter.campfire.data.storage.PreferenceStorageManager
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
object RepositoryModule {

    @Provides
    @Singleton
    @JvmStatic
    fun provideSongInfoRepository(
        preferenceStorageManager: PreferenceStorageManager,
        dataStorageManager: DataStorageManager,
        networkManager: NetworkManager,
        languageRepository: LanguageRepository) = SongInfoRepository(preferenceStorageManager, dataStorageManager, networkManager, languageRepository)

    @Provides
    @Singleton
    @JvmStatic
    fun provideUserPreferenceRepository(
        preferenceStorageManager: PreferenceStorageManager) = UserPreferenceRepository(preferenceStorageManager)

    @Provides
    @Singleton
    @JvmStatic
    fun provideLanguageRepository(
        preferenceStorageManager: PreferenceStorageManager) = LanguageRepository(preferenceStorageManager)

    @Provides
    @Singleton
    @JvmStatic
    fun providePlaylistRepository(
        preferenceStorageManager: PreferenceStorageManager,
        songInfoRepository: SongInfoRepository) = PlaylistRepository(preferenceStorageManager, songInfoRepository)
}