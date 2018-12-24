package com.pandulapeter.campfire

import androidx.room.Room
import com.google.gson.GsonBuilder
import com.pandulapeter.campfire.data.model.remote.Collection
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.data.networking.NetworkManager
import com.pandulapeter.campfire.data.persistence.Database
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.data.repository.ChangelogRepository
import com.pandulapeter.campfire.data.repository.CollectionRepository
import com.pandulapeter.campfire.data.repository.HistoryRepository
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.data.repository.SongDetailRepository
import com.pandulapeter.campfire.data.repository.SongRepository
import com.pandulapeter.campfire.feature.detail.DetailEventBus
import com.pandulapeter.campfire.feature.detail.DetailPageEventBus
import com.pandulapeter.campfire.feature.detail.DetailViewModel
import com.pandulapeter.campfire.feature.detail.page.DetailPageViewModel
import com.pandulapeter.campfire.feature.detail.page.parsing.SongParser
import com.pandulapeter.campfire.feature.main.collections.CollectionsViewModel
import com.pandulapeter.campfire.feature.main.collections.detail.CollectionDetailViewModel
import com.pandulapeter.campfire.feature.main.history.HistoryViewModel
import com.pandulapeter.campfire.feature.main.home.HomeContainerViewModel
import com.pandulapeter.campfire.feature.main.home.home.HomeViewModel
import com.pandulapeter.campfire.feature.main.home.onboarding.OnboardingViewModel
import com.pandulapeter.campfire.feature.main.home.onboarding.contentLanguage.ContentLanguageViewModel
import com.pandulapeter.campfire.feature.main.home.onboarding.songAppearance.SongAppearanceViewModel
import com.pandulapeter.campfire.feature.main.home.onboarding.userData.UserDataViewModel
import com.pandulapeter.campfire.feature.main.home.onboarding.welcome.WelcomeViewModel
import com.pandulapeter.campfire.feature.main.manageDownloads.ManageDownloadsViewModel
import com.pandulapeter.campfire.feature.main.managePlaylists.ManagePlaylistsViewModel
import com.pandulapeter.campfire.feature.main.options.OptionsViewModel
import com.pandulapeter.campfire.feature.main.options.about.AboutViewModel
import com.pandulapeter.campfire.feature.main.options.changelog.ChangelogViewModel
import com.pandulapeter.campfire.feature.main.options.preferences.PreferencesViewModel
import com.pandulapeter.campfire.feature.shared.InteractionBlocker
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.integration.AppShortcutManager
import com.pandulapeter.campfire.integration.DeepLinkManager
import com.pandulapeter.campfire.integration.FirstTimeUserExperienceManager
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.experimental.builder.viewModel
import org.koin.androidx.viewmodel.ext.koin.viewModel
import org.koin.dsl.module.module

val integrationModule = module {
    factory { AppShortcutManager(androidContext(), get(), get()) }
    factory { DeepLinkManager() }
    factory { FirstTimeUserExperienceManager(get()) }
}

val networkingModule = module {
    single { GsonBuilder().create() }
    factory { AnalyticsManager(androidContext(), get(), get()) }
    factory { NetworkManager(get()) }
}

val persistenceModule = module {
    factory { PreferenceDatabase(androidContext()) }
    single { Room.databaseBuilder(androidContext(), Database::class.java, "songDatabase.db").build() }
}

val repositoryModule = module {
    single { SongRepository(get(), get(), get()) }
    single { SongDetailRepository(get(), get()) }
    single { ChangelogRepository() }
    single { HistoryRepository(get()) }
    single { PlaylistRepository(get()) }
    single { CollectionRepository(get(), get(), get()) }
}

val detailModule = module {
    single { DetailEventBus() }
    single { DetailPageEventBus() }
}

val featureModule = module {

    single { InteractionBlocker() }

    viewModel<HomeContainerViewModel>()

    viewModel<OnboardingViewModel>()
    viewModel<WelcomeViewModel>()
    viewModel<UserDataViewModel>()
    viewModel<SongAppearanceViewModel>()
    viewModel<ContentLanguageViewModel>()

    viewModel<HomeViewModel>()
    viewModel<CollectionsViewModel>()
    viewModel { (collection: Collection) -> CollectionDetailViewModel(collection, androidContext(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel<HistoryViewModel>()

    viewModel<OptionsViewModel>()
    viewModel<PreferencesViewModel>()
    viewModel<ChangelogViewModel>()
    viewModel<AboutViewModel>()

    viewModel<ManagePlaylistsViewModel>()
    viewModel<ManageDownloadsViewModel>()

    viewModel<DetailViewModel>()
    viewModel { (song: Song, songParser: SongParser) -> DetailPageViewModel(song, androidContext(), get(), get(), get(), get(), get(), songParser) }
}