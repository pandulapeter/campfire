package com.pandulapeter.campfire

import androidx.room.Room
import com.google.gson.GsonBuilder
import com.pandulapeter.campfire.data.networking.NetworkManager
import com.pandulapeter.campfire.data.persistence.Database
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.data.repository.*
import com.pandulapeter.campfire.feature.detail.DetailEventBus
import com.pandulapeter.campfire.feature.detail.DetailPageEventBus
import com.pandulapeter.campfire.feature.main.home.HomeContainerViewModel
import com.pandulapeter.campfire.feature.main.home.home.HomeViewModel
import com.pandulapeter.campfire.feature.main.home.onboarding.OnboardingViewModel
import com.pandulapeter.campfire.feature.main.home.onboarding.contentLanguage.ContentLanguageViewModel
import com.pandulapeter.campfire.feature.main.home.onboarding.songAppearance.SongAppearanceViewModel
import com.pandulapeter.campfire.feature.main.home.onboarding.userData.UserDataViewModel
import com.pandulapeter.campfire.feature.main.home.onboarding.welcome.WelcomeViewModel
import com.pandulapeter.campfire.feature.main.options.OptionsViewModel
import com.pandulapeter.campfire.feature.main.options.about.AboutViewModel
import com.pandulapeter.campfire.feature.main.options.changelog.ChangelogViewModel
import com.pandulapeter.campfire.feature.main.options.preferences.PreferencesViewModel
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.integration.AppShortcutManager
import com.pandulapeter.campfire.integration.DeepLinkManager
import com.pandulapeter.campfire.integration.FirstTimeUserExperienceManager
import org.koin.androidx.viewmodel.experimental.builder.viewModel
import org.koin.dsl.module.module
import org.koin.experimental.builder.factory
import org.koin.experimental.builder.single

val integrationModule = module {
    factory<AppShortcutManager>()
    factory<DeepLinkManager>()
    factory<FirstTimeUserExperienceManager>()
}

val networkingModule = module {
    single { GsonBuilder().create() }
    factory<AnalyticsManager>()
    factory<NetworkManager>()
}

val repositoryModule = module {
    single<SongRepository>()
    single<SongDetailRepository>()
    single<ChangelogRepository>()
    single<HistoryRepository>()
    single<PlaylistRepository>()
    single<CollectionRepository>()
}

val persistenceModule = module {
    factory<PreferenceDatabase>()
    single { Room.databaseBuilder(get(), Database::class.java, "songDatabase.db").build() }
}

val detailModule = module {
    single<DetailEventBus>()
    single<DetailPageEventBus>()
}

val featureModule = module {

    viewModel<HomeContainerViewModel>()

    viewModel<OnboardingViewModel>()
    viewModel<WelcomeViewModel>()
    viewModel<UserDataViewModel>()
    viewModel<SongAppearanceViewModel>()
    viewModel<ContentLanguageViewModel>()

    viewModel<HomeViewModel>()

    viewModel<OptionsViewModel>()
    viewModel<PreferencesViewModel>()
    viewModel<ChangelogViewModel>()
    viewModel<AboutViewModel>()
}