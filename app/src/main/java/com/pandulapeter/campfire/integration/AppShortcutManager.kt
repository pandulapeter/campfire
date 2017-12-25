package com.pandulapeter.campfire.integration

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import android.support.annotation.DrawableRes
import android.support.annotation.RequiresApi
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.Playlist
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.data.storage.DataStorageManager
import com.pandulapeter.campfire.feature.home.HomeActivity
import com.pandulapeter.campfire.feature.home.HomeViewModel
import kotlin.math.min

/**
 * Handles dynamic app shortcuts on or above Android Oreo.
 */
class AppShortcutManager(context: Context,
                         playlistRepository:
                         PlaylistRepository,
                         private val dataStorageManager: DataStorageManager) {
    private val implementation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) RealImplementation(context, playlistRepository, dataStorageManager) else FakeImplementation()

    fun onLibraryOpened() = implementation.trackAppShortcutUsage(LIBRARY_ID)

    fun onCollectionsOpened() = implementation.trackAppShortcutUsage(COLLECTIONS_ID)

    fun onPlaylistOpened(playlistId: Int) {
        implementation.trackAppShortcutUsage(PLAYLIST_ID + playlistId.toString())
        val list = dataStorageManager.playlistHistory.toMutableList().apply { add(0, playlistId.toString()) }.distinct()
        dataStorageManager.playlistHistory = list.subList(0, min(list.size, 3))
        implementation.updateAppShortcuts()
    }

    fun updateAppShortcuts() = implementation.updateAppShortcuts()

    private interface Implementation {

        fun updateAppShortcuts()

        fun removeAppShortcuts()

        fun trackAppShortcutUsage(id: String)
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private class RealImplementation(private val context: Context,
                                     private val playlistRepository: PlaylistRepository,
                                     private val dataStorageManager: DataStorageManager) : Implementation {
        private val shortcutManager: ShortcutManager = context.getSystemService(Context.SHORTCUT_SERVICE) as ShortcutManager

        override fun updateAppShortcuts() {
            removeAppShortcuts()
            val shortcuts = mutableListOf<ShortcutInfo>()
            shortcuts.add(createAppShortcut(
                LIBRARY_ID,
                context.getString(R.string.home_library),
                R.drawable.ic_shortcut_library_48dp,
                HomeViewModel.NavigationItem.Library))
            shortcuts.add(createAppShortcut(
                COLLECTIONS_ID,
                context.getString(R.string.home_collections),
                R.drawable.ic_shortcut_collections_48dp,
                HomeViewModel.NavigationItem.Collections))
            if (dataStorageManager.playlistHistory.isEmpty()) {
                dataStorageManager.playlistHistory = dataStorageManager.playlistHistory.toMutableList().apply { add(Playlist.FAVORITES_ID.toString()) }
            }
            dataStorageManager.playlistHistory.forEach {
                playlistRepository.getPlaylist(it.toInt())?.let { playlist ->
                    val title = playlist.title ?: context.getString(R.string.home_favorites)
                    shortcuts.add(createAppShortcut(
                        PLAYLIST_ID + playlist.id,
                        title,
                        R.drawable.ic_shortcut_playlist_48dp,
                        HomeViewModel.NavigationItem.Playlist(playlist.id)))
                }
            }
            shortcutManager.dynamicShortcuts = shortcuts
        }

        override fun removeAppShortcuts() = shortcutManager.removeAllDynamicShortcuts()

        override fun trackAppShortcutUsage(id: String) = shortcutManager.reportShortcutUsed(id)

        private fun createAppShortcut(id: String,
                                      label: String,
                                      @DrawableRes icon: Int,
                                      navigationItem: HomeViewModel.NavigationItem) = ShortcutInfo.Builder(context, id)
            .setShortLabel(label)
            .setIcon(Icon.createWithResource(context, icon))
            .setIntent(HomeActivity.getStartIntent(context, navigationItem).setAction(Intent.ACTION_VIEW))
            .build()
    }

    private class FakeImplementation : Implementation {

        override fun updateAppShortcuts() = Unit

        override fun removeAppShortcuts() = Unit

        override fun trackAppShortcutUsage(id: String) = Unit
    }

    private companion object {
        const val LIBRARY_ID = "library"
        const val COLLECTIONS_ID = "collections"
        const val PLAYLIST_ID = "playlist_"
    }
}