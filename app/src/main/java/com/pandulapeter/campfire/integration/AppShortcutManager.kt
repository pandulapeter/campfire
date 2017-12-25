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
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.data.storage.DataStorageManager
import com.pandulapeter.campfire.feature.home.HomeViewModel
import kotlin.math.min

/**
 * Handles dynamic app shortcuts on or above Android Oreo.
 *
 * TODO: Does not work as expected.
 */
class AppShortcutManager(context: Context, dataStorageManager: DataStorageManager, playlistRepository: PlaylistRepository) : Features {
    private val implementation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) Implementation(context, dataStorageManager, playlistRepository) else CompatImplementation()

    override fun onLibraryOpened() = implementation.onLibraryOpened()

    override fun onCollectionsOpened() = implementation.onCollectionsOpened()

    override fun onPlaylistOpened(playlistId: Int) = implementation.onPlaylistOpened(playlistId)

    override fun updateAppShortcuts() = implementation.updateAppShortcuts()

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private class Implementation(private val context: Context,
                                 private val dataStorageManager: DataStorageManager,
                                 private val playlistRepository: PlaylistRepository) : Features {
        private val shortcutManager: ShortcutManager = context.getSystemService(Context.SHORTCUT_SERVICE) as ShortcutManager

        override fun onLibraryOpened() = trackAppShortcutUsage(LIBRARY_ID)

        override fun onCollectionsOpened() = trackAppShortcutUsage(COLLECTIONS_ID)

        override fun onPlaylistOpened(playlistId: Int) {
            trackAppShortcutUsage(playlistId.toString())
            val list = dataStorageManager.playlistHistory.toMutableList().apply { add(0, playlistId.toString()) }.distinct()
            dataStorageManager.playlistHistory = list.subList(0, min(list.size, 3))
            updateAppShortcuts()
        }

        override fun updateAppShortcuts() {
            removeAppShortcuts()
            val shortcuts = mutableListOf<ShortcutInfo>()
            shortcuts.add(createAppShortcut(
                LIBRARY_ID,
                context.getString(R.string.home_library),
                context.getString(R.string.app_shortcut_open_library),
                R.drawable.ic_shortcut_library_48dp,
                HomeViewModel.NavigationItem.Library))
            shortcuts.add(createAppShortcut(
                COLLECTIONS_ID,
                context.getString(R.string.home_collections),
                context.getString(R.string.app_shortcut_open_collections),
                R.drawable.ic_shortcut_collections_48dp,
                HomeViewModel.NavigationItem.Collections))
            dataStorageManager.playlistHistory.forEach {
                playlistRepository.getPlaylist(it.toInt())?.let { playlist ->
                    val title = playlist.title ?: context.getString(R.string.home_favorites)
                    shortcuts.add(createAppShortcut(
                        PLAYLIST_ID + playlist.id,
                        title,
                        context.getString(R.string.app_shortcut_open_playlist, title),
                        R.drawable.ic_shortcut_playlist_48dp,
                        HomeViewModel.NavigationItem.Playlist(playlist.id)))
                }
            }
            shortcutManager.dynamicShortcuts = shortcuts
        }

        private fun createAppShortcut(id: String,
                                      shortLabel: String,
                                      longLabel: String,
                                      @DrawableRes icon: Int,
                                      navigationItem: HomeViewModel.NavigationItem) = ShortcutInfo.Builder(context, id)
            .setShortLabel(shortLabel)
            .setLongLabel(longLabel)
            .setIcon(Icon.createWithResource(context, icon)) //TODO: Tint icon.
            .setIntent(Intent().setAction(Intent.ACTION_VIEW)) //TODO: Handle navigationItem.
            .build()

        private fun removeAppShortcuts() = shortcutManager.removeAllDynamicShortcuts()

        private fun trackAppShortcutUsage(id: String) = shortcutManager.reportShortcutUsed(id)

        private companion object {
            const val LIBRARY_ID = "library"
            const val COLLECTIONS_ID = "collections"
            const val PLAYLIST_ID = "playlist_"
        }
    }

    private class CompatImplementation : Features {

        override fun onLibraryOpened() = Unit

        override fun onCollectionsOpened() = Unit

        override fun onPlaylistOpened(playlistId: Int) = Unit

        override fun updateAppShortcuts() = Unit
    }
}

private interface Features {

    fun onLibraryOpened()

    fun onCollectionsOpened()

    fun onPlaylistOpened(playlistId: Int)

    fun updateAppShortcuts()
}