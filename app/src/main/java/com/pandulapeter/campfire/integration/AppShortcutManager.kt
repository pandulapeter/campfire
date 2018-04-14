package com.pandulapeter.campfire.integration

import android.content.Context
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager

class AppShortcutManager(context: Context) {

    private val shortcutManager: ShortcutManager = context.getSystemService(Context.SHORTCUT_SERVICE) as ShortcutManager

    fun onLibraryOpened() = trackAppShortcutUsage(LIBRARY_ID)

    fun onPlaylistOpened(playlistId: String) {
        trackAppShortcutUsage(PLAYLIST_ID + playlistId)
        //val list = dataStorageManager.playlistHistory.toMutableList().apply { add(0, playlistId) }.distinct()
        //dataStorageManager.playlistHistory = list.subList(0, Math.min(list.size, 3))
        updateAppShortcuts()
    }

    fun onPlaylistDeleted(playlistId: String) {
        //dataStorageManager.playlistHistory = dataStorageManager.playlistHistory.toMutableList().apply { remove(playlistId) }
        updateAppShortcuts()
    }

    fun updateAppShortcuts() {
        removeAppShortcuts()
        val shortcuts = mutableListOf<ShortcutInfo>()
//            shortcuts.add(createAppShortcut(LIBRARY_ID, context.getString(R.string.home_library), R.drawable.ic_shortcut_library_48dp, HomeViewModel.HomeNavigationItem.Library))
//            if (dataStorageManager.playlistHistory.isEmpty()) {
//                dataStorageManager.playlistHistory = dataStorageManager.playlistHistory.toMutableList().apply { add(Playlist.FAVORITES_ID.toString()) }
//            }
//            dataStorageManager.playlistHistory.forEach {
//                playlistRepository.getPlaylist(it.toInt())?.let { playlist ->
//                    val title = playlist.title ?: context.getString(R.string.home_favorites)
//                    shortcuts.add(
//                        createAppShortcut(
//                            PLAYLIST_ID + playlist.id,
//                            title,
//                            R.drawable.ic_shortcut_playlist_48dp,
//                            HomeViewModel.HomeNavigationItem.Playlist(playlist.id)
//                        )
//                    )
//                }
//            }
        shortcutManager.dynamicShortcuts = shortcuts
    }

    private fun removeAppShortcuts() = shortcutManager.removeAllDynamicShortcuts()

    private fun trackAppShortcutUsage(id: String) = shortcutManager.reportShortcutUsed(id)

//        private fun createAppShortcut(id: String, label: String, @DrawableRes icon: Int, homeNavigationItem: HomeViewModel.HomeNavigationItem) = ShortcutInfo.Builder(context, id)
//            .setShortLabel(label)
//            .setIcon(Icon.createWithResource(context, icon))
//            .setIntent(MainActivity.getStartIntent(context, MainViewModel.MainNavigationItem.Home(homeNavigationItem)).setAction(Intent.ACTION_VIEW))
//            .build()

    private companion object {
        const val LIBRARY_ID = "library"
        const val PLAYLIST_ID = "playlist_"
    }
}