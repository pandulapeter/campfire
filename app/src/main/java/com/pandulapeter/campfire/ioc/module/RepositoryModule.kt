package com.pandulapeter.campfire.ioc.module

import com.pandulapeter.campfire.networking.NetworkManager
import com.pandulapeter.campfire.data.repository.DownloadedSongRepository
import com.pandulapeter.campfire.data.repository.FirstTimeUserExperienceRepository
import com.pandulapeter.campfire.data.repository.HistoryRepository
import com.pandulapeter.campfire.data.repository.LanguageRepository
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.data.repository.UserPreferenceRepository
import com.pandulapeter.campfire.data.storage.DataStorageManager
import com.pandulapeter.campfire.data.storage.FileStorageManager
import com.pandulapeter.campfire.data.storage.PreferenceStorageManager
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
object RepositoryModule {

    @Provides
    @Singleton
    @JvmStatic
    fun provideDownloadedSongRepository(
        dataStorageManager: DataStorageManager,
        fileStorageManager: FileStorageManager,
        networkManager: NetworkManager) = DownloadedSongRepository(dataStorageManager, fileStorageManager, networkManager)

    @Provides
    @Singleton
    @JvmStatic
    fun provideFirstTimeUserExperienceRepository(
        preferenceStorageManager: PreferenceStorageManager) = FirstTimeUserExperienceRepository(preferenceStorageManager)

    @Provides
    @Singleton
    @JvmStatic
    fun provideHistoryRepository(
        dataStorageManager: DataStorageManager) = HistoryRepository(dataStorageManager)

    @Provides
    @Singleton
    @JvmStatic
    fun provideLanguageRepository(
        preferenceStorageManager: PreferenceStorageManager) = LanguageRepository(preferenceStorageManager)

    @Provides
    @Singleton
    @JvmStatic
    fun providePlaylistRepository(
        dataStorageManager: DataStorageManager) = PlaylistRepository(dataStorageManager)

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
}