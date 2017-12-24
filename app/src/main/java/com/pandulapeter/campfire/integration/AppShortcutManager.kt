package com.pandulapeter.campfire.integration

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.Uri
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
class AppShortcutManager(private val context: Context,
                         private val dataStorageManager: DataStorageManager,
                         private val playlistRepository: PlaylistRepository) {

    fun onLibraryLoaded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1 && (context.getSystemService(Context.SHORTCUT_SERVICE) as ShortcutManager).dynamicShortcuts.isEmpty()) {
            updateAppShortcuts()
        }
    }

    fun onPlaylistOpened(playlistId: Int) {
        val list = dataStorageManager.playlistHistory.toMutableList().apply { add(0, playlistId.toString()) }.distinct()
        dataStorageManager.playlistHistory = list.subList(0, min(list.size, 3))
        updateAppShortcuts()
    }

    private fun updateAppShortcuts() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            val shortcuts = mutableListOf<ShortcutInfo>()
            shortcuts.add(createAppShortcut(
                "library",
                context.getString(R.string.home_library),
                context.getString(R.string.app_shortcut_open_library),
                R.drawable.ic_library_24dp,
                HomeViewModel.NavigationItem.Library))
            shortcuts.add(createAppShortcut(
                "collections",
                context.getString(R.string.home_collections),
                context.getString(R.string.app_shortcut_open_collections),
                R.drawable.ic_collections_24dp,
                HomeViewModel.NavigationItem.Collections))
            dataStorageManager.playlistHistory.forEach {
                playlistRepository.getPlaylist(it.toInt())?.let { playlist ->
                    val title = playlist.title ?: context.getString(R.string.home_favorites)
                    shortcuts.add(createAppShortcut(
                        playlist.id.toString(),
                        title,
                        context.getString(R.string.app_shortcut_open_playlist, title),
                        R.drawable.ic_playlist_24dp,
                        HomeViewModel.NavigationItem.Playlist(playlist.id)))
                }
            }
            (context.getSystemService(Context.SHORTCUT_SERVICE) as ShortcutManager).dynamicShortcuts = shortcuts
        }
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private fun createAppShortcut(id: String,
                                  shortLabel: String,
                                  longLabel: String,
                                  @DrawableRes icon: Int,
                                  navigationItem: HomeViewModel.NavigationItem) = ShortcutInfo.Builder(context, id)
        .setShortLabel(shortLabel)
        .setLongLabel(longLabel)
        .setIcon(Icon.createWithResource(context, icon)) //TODO: Tint icon.
        .setIntent(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/"))) //TODO: Do something with navigationItem.
        .build()
}