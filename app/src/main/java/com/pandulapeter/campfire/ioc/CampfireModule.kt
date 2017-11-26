package com.pandulapeter.campfire.ioc

import com.pandulapeter.campfire.feature.detail.DetailActivity
import com.pandulapeter.campfire.feature.detail.page.SongPageFragment
import com.pandulapeter.campfire.feature.home.HomeActivity
import com.pandulapeter.campfire.feature.home.cloud.CloudFragment
import com.pandulapeter.campfire.feature.home.downloads.DownloadsFragment
import com.pandulapeter.campfire.feature.home.favorites.FavoritesFragment
import com.pandulapeter.campfire.feature.home.settings.SettingsFragment
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
abstract class CampfireModule {

    @ContributesAndroidInjector
    abstract fun contributeHomeActivity(): HomeActivity

    @ContributesAndroidInjector
    abstract fun contributeCloudFragment(): CloudFragment

    @ContributesAndroidInjector
    abstract fun contributeDownloadsFragment(): DownloadsFragment

    @ContributesAndroidInjector
    abstract fun contributeFavoritesFragment(): FavoritesFragment

    @ContributesAndroidInjector
    abstract fun contributeSettingsFragment(): SettingsFragment

    @ContributesAndroidInjector
    abstract fun contributeDetailActivity(): DetailActivity

    @ContributesAndroidInjector
    abstract fun contributeSongPageFragment(): SongPageFragment
}