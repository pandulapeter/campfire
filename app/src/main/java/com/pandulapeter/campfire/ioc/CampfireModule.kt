package com.pandulapeter.campfire.ioc

import com.pandulapeter.campfire.feature.detail.DetailActivity
import com.pandulapeter.campfire.feature.detail.page.SongPageFragment
import com.pandulapeter.campfire.feature.home.HomeActivity
import com.pandulapeter.campfire.feature.home.collections.CollectionsFragment
import com.pandulapeter.campfire.feature.home.history.HistoryFragment
import com.pandulapeter.campfire.feature.home.library.LibraryFragment
import com.pandulapeter.campfire.feature.home.library.SongOptionsBottomSheetFragment
import com.pandulapeter.campfire.feature.home.playlist.PlaylistFragment
import com.pandulapeter.campfire.feature.home.settings.SettingsFragment
import com.pandulapeter.campfire.feature.shared.NewPlaylistDialogFragment
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
abstract class CampfireModule {

    @ContributesAndroidInjector
    abstract fun contributeHomeActivity(): HomeActivity

    @ContributesAndroidInjector
    abstract fun contributeLibraryFragment(): LibraryFragment

    @ContributesAndroidInjector
    abstract fun contributeCollectionsFragment(): CollectionsFragment

    @ContributesAndroidInjector
    abstract fun contributeHistoryFragment(): HistoryFragment

    @ContributesAndroidInjector
    abstract fun contributePlaylistFragment(): PlaylistFragment

    @ContributesAndroidInjector
    abstract fun contributeSettingsFragment(): SettingsFragment

    @ContributesAndroidInjector
    abstract fun contributeDetailActivity(): DetailActivity

    @ContributesAndroidInjector
    abstract fun contributeSongPageFragment(): SongPageFragment

    @ContributesAndroidInjector
    abstract fun contributeSongOptionsBottomSheetFragment(): SongOptionsBottomSheetFragment

    @ContributesAndroidInjector
    abstract fun contributeNewPlaylistDialogFragment(): NewPlaylistDialogFragment
}