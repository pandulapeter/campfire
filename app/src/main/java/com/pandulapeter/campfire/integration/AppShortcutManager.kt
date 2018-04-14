package com.pandulapeter.campfire.integration

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.support.annotation.DrawableRes
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.local.Playlist
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.feature.CampfireActivity

class AppShortcutManager(
    private val context: Context,
    private val preferenceDatabase: PreferenceDatabase,
    private val playlistRepository: PlaylistRepository
) {

    private val shortcutManager: ShortcutManager = context.getSystemService(Context.SHORTCUT_SERVICE) as ShortcutManager

    fun onLibraryOpened() = trackAppShortcutUsage(LIBRARY_ID)

    fun onPlaylistOpened(playlistId: String) {
        trackAppShortcutUsage(PLAYLIST_ID + playlistId)
        val list = preferenceDatabase.playlistHistory.toMutableList().apply { add(0, playlistId) }.distinct()
        preferenceDatabase.playlistHistory = list.subList(0, Math.min(list.size, 3)).toSet()
        updateAppShortcuts()
    }

    fun onPlaylistDeleted(playlistId: String) {
        preferenceDatabase.playlistHistory = preferenceDatabase.playlistHistory.toMutableSet().apply { remove(playlistId) }
        updateAppShortcuts()
    }

    fun updateAppShortcuts() {
        removeAppShortcuts()
        val shortcuts = mutableListOf<ShortcutInfo>()
        shortcuts.add(createAppShortcut(LIBRARY_ID, context.getString(R.string.home_library), R.drawable.ic_shortcut_library_48dp, CampfireActivity.getLibraryIntent(context)))
        if (preferenceDatabase.playlistHistory.isEmpty()) {
            preferenceDatabase.playlistHistory = preferenceDatabase.playlistHistory.toMutableSet().apply { add(Playlist.FAVORITES_ID) }
        }
        preferenceDatabase.playlistHistory.forEach { playlistId ->
            playlistRepository.cache.find { it.id == playlistId }?.let { playlist ->
                val title = playlist.title ?: context.getString(R.string.home_favorites)
                shortcuts.add(
                    createAppShortcut(
                        PLAYLIST_ID + playlist.id,
                        title,
                        R.drawable.ic_shortcut_playlist_48dp,
                        CampfireActivity.getPlaylistIntent(context, playlist.id)
                    )
                )
            }
        }
        shortcutManager.dynamicShortcuts = shortcuts
    }

    private fun removeAppShortcuts() = shortcutManager.removeAllDynamicShortcuts()

    private fun trackAppShortcutUsage(id: String) = shortcutManager.reportShortcutUsed(id)

    private fun createAppShortcut(id: String, label: String, @DrawableRes icon: Int, startIntent: Intent) = ShortcutInfo.Builder(context, id)
        .setShortLabel(label)
        .setIcon(Icon.createWithResource(context, icon))
        .setIntent(startIntent.setAction(Intent.ACTION_VIEW))
        .build()

    private companion object {
        const val LIBRARY_ID = "library"
        const val PLAYLIST_ID = "playlist_"
    }
}