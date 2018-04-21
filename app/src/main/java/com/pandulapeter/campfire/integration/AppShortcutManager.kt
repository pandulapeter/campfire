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
import com.pandulapeter.campfire.data.model.local.Playlist
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.feature.CampfireActivity

class AppShortcutManager(context: Context, preferenceDatabase: PreferenceDatabase, playlistRepository: PlaylistRepository) {

    private val implementation: Functionality =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) Implementation(context, preferenceDatabase, playlistRepository) else object : Functionality {}

    fun onLibraryOpened() = implementation.onLibraryOpened()

    fun onCollectionsOpened() = implementation.onCollectionsOpened()

    fun onPlaylistOpened(playlistId: String) = implementation.onPlaylistOpened(playlistId)

    fun onPlaylistDeleted(playlistId: String) = implementation.onPlaylistDeleted(playlistId)

    fun updateAppShortcuts() = implementation.updateAppShortcuts()

    interface Functionality {
        fun onLibraryOpened() = Unit
        fun onCollectionsOpened() = Unit
        fun onPlaylistOpened(playlistId: String) = Unit
        fun onPlaylistDeleted(playlistId: String) = Unit
        fun updateAppShortcuts() = Unit
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    class Implementation(
        private val context: Context,
        private val preferenceDatabase: PreferenceDatabase,
        private val playlistRepository: PlaylistRepository
    ) : Functionality {

        private companion object {
            const val LIBRARY_ID = "library"
            const val COLLECTIONS_ID = "collections"
            const val PLAYLIST_ID = "playlist_"
        }

        private val shortcutManager: ShortcutManager = context.getSystemService(Context.SHORTCUT_SERVICE) as ShortcutManager

        override fun onLibraryOpened() = trackAppShortcutUsage(LIBRARY_ID)

        override fun onCollectionsOpened() = trackAppShortcutUsage(COLLECTIONS_ID)

        override fun onPlaylistOpened(playlistId: String) {
            trackAppShortcutUsage(PLAYLIST_ID + playlistId)
            val list = preferenceDatabase.playlistHistory.toMutableList().apply { add(0, playlistId) }.distinct()
            preferenceDatabase.playlistHistory = list.subList(0, Math.min(list.size, 3)).toSet()
            updateAppShortcuts()
        }

        override fun onPlaylistDeleted(playlistId: String) {
            preferenceDatabase.playlistHistory = preferenceDatabase.playlistHistory.toMutableSet().apply { remove(playlistId) }
            updateAppShortcuts()
        }

        override fun updateAppShortcuts() {
            removeAppShortcuts()
            val shortcuts = mutableListOf<ShortcutInfo>()
            shortcuts.add(createAppShortcut(LIBRARY_ID, context.getString(R.string.home_library), R.drawable.ic_shortcut_library_48dp, CampfireActivity.getLibraryIntent(context)))
            shortcuts.add(
                createAppShortcut(
                    COLLECTIONS_ID,
                    context.getString(R.string.home_collections),
                    R.drawable.ic_shortcut_collections_48dp,
                    CampfireActivity.getCollectionsIntent(context)
                )
            )
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
    }
}