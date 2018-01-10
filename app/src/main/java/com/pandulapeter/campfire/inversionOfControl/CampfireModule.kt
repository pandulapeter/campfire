package com.pandulapeter.campfire.inversionOfControl

import com.pandulapeter.campfire.feature.MainActivity
import com.pandulapeter.campfire.feature.detail.DetailFragment
import com.pandulapeter.campfire.feature.detail.songPage.SongPageFragment
import com.pandulapeter.campfire.feature.home.HomeFragment
import com.pandulapeter.campfire.feature.home.collections.CollectionsFragment
import com.pandulapeter.campfire.feature.home.history.HistoryFragmentInfo
import com.pandulapeter.campfire.feature.home.library.LibraryFragmentInfo
import com.pandulapeter.campfire.feature.home.library.PlaylistChooserBottomSheetFragment
import com.pandulapeter.campfire.feature.home.manageDownloads.ManageDownloadsFragmentInfo
import com.pandulapeter.campfire.feature.home.managePlaylists.ManagePlaylistsFragment
import com.pandulapeter.campfire.feature.home.playlist.PlaylistFragmentInfo
import com.pandulapeter.campfire.feature.home.settings.SettingsFragment
import com.pandulapeter.campfire.feature.shared.dialog.NewPlaylistDialogFragment
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
abstract class CampfireModule {

    @ContributesAndroidInjector
    abstract fun contributeMainActivity(): MainActivity

    @ContributesAndroidInjector
    abstract fun contributeHomeFragment(): HomeFragment

    @ContributesAndroidInjector
    abstract fun contributeLibraryFragment(): LibraryFragmentInfo

    @ContributesAndroidInjector
    abstract fun contributeCollectionsFragment(): CollectionsFragment

    @ContributesAndroidInjector
    abstract fun contributeHistoryFragment(): HistoryFragmentInfo

    @ContributesAndroidInjector
    abstract fun contributeSettingsFragment(): SettingsFragment

    @ContributesAndroidInjector
    abstract fun contributePlaylistFragment(): PlaylistFragmentInfo

    @ContributesAndroidInjector
    abstract fun contributeManagePlaylistsFragment(): ManagePlaylistsFragment

    @ContributesAndroidInjector
    abstract fun contributeManageDownloadsFragment(): ManageDownloadsFragmentInfo

    @ContributesAndroidInjector
    abstract fun contributeNewPlaylistDialogFragment(): NewPlaylistDialogFragment

    @ContributesAndroidInjector
    abstract fun contributePlaylistChooserBottomSheetFragment(): PlaylistChooserBottomSheetFragment

    @ContributesAndroidInjector
    abstract fun contributeDetailFragment(): DetailFragment

    @ContributesAndroidInjector
    abstract fun contributeSongPageFragment(): SongPageFragment
}