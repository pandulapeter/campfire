package com.pandulapeter.campfire.data.storage

import android.content.Context
import android.preference.PreferenceManager
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.pandulapeter.campfire.data.model.DownloadedSong
import com.pandulapeter.campfire.data.model.Playlist
import com.pandulapeter.campfire.data.model.SongInfo


/**
 * Wrapper for storing complex data in a local database.
 *
 * TODO: This data shouldn't be stored in Shared Preferences, replace with a Room-based implementation.
 */
class DataStorageManager(context: Context, private val gson: Gson) {

    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    /**
     * The cached list of items from the cloud.
     */
    var cloudCache: List<SongInfo>
        get() = try {
            gson.fromJson(sharedPreferences.getString(KEY_CACHE, VALUE_EMPTY_JSON_ARRAY), object : TypeToken<List<SongInfo>>() {}.type)
        } catch (_: JsonSyntaxException) {
            listOf()
        }
        set(value) {
            sharedPreferences.edit().putString(KEY_CACHE, gson.toJson(value)).apply()
        }

    /**
     * The list of downloads songs.
     */
    var downloads: List<DownloadedSong>
        get() = try {
            gson.fromJson(sharedPreferences.getString(KEY_DOWNLOADED_SONGS, VALUE_EMPTY_JSON_ARRAY), object : TypeToken<List<DownloadedSong>>() {}.type)
        } catch (_: JsonSyntaxException) {
            listOf()
        }
        set(value) {
            sharedPreferences.edit().putString(KEY_DOWNLOADED_SONGS, gson.toJson(value)).apply()
        }

    fun getAllPlaylists() = mutableListOf<Playlist>().apply {
        playlistIds.forEach { add(getPlaylist(it)) }
    }.toList()

    fun getPlaylist(playlistId: Int) = if (playlistId == Playlist.FAVORITES_ID) {
        val ids: MutableList<String> = try {
            gson.fromJson(sharedPreferences.getString(KEY_PLAYLIST + Playlist.FAVORITES_ID, VALUE_EMPTY_JSON_ARRAY), object : TypeToken<List<String>>() {}.type)
        } catch (_: JsonSyntaxException) {
            mutableListOf()
        }
        Playlist.Favorites(ids)
    } else {
        val playlist: Playlist.Custom = try {
            gson.fromJson(sharedPreferences.getString(KEY_PLAYLIST + playlistId, ""), object : TypeToken<Playlist.Custom>() {}.type)
        } catch (_: JsonSyntaxException) {
            Playlist.Custom(playlistId, "", mutableListOf())
        }
        playlist
    }

    fun savePlaylist(playlist: Playlist) {
        if (!playlistIds.contains(playlist.id)) {
            playlistIds = playlistIds.toMutableList().apply { add(playlist.id) }
        }
        sharedPreferences.edit().putString(KEY_PLAYLIST + playlist.id, gson.toJson((playlist as? Playlist.Favorites)?.songIds ?: playlist)).apply()
    }

    fun deletePlaylist(playlist: Playlist) {
        if (playlist.id != Playlist.FAVORITES_ID) {
            playlistIds = playlistIds.toMutableList().filter { it == playlist.id }
            sharedPreferences.edit().remove(KEY_PLAYLIST + playlist.id).apply()
        }
    }

    fun newPlaylist(title: String) {
        //TODO: Rewrite this.
        var id = 1
        while (playlistIds.contains(id)) {
            id++
        }
        savePlaylist(Playlist.Custom(id, title, mutableListOf()))
    }

    private var playlistIds: List<Int>
        get() {
            val list: List<Int> = try {
                gson.fromJson(sharedPreferences.getString(KEY_PLAYLIST_IDS, VALUE_EMPTY_JSON_ARRAY), object : TypeToken<List<Int>>() {}.type)
            } catch (_: JsonSyntaxException) {
                listOf()
            }
            return if (list.isEmpty()) listOf(Playlist.FAVORITES_ID) else list
        }
        set(value) {
            sharedPreferences.edit().putString(KEY_PLAYLIST_IDS, gson.toJson(value)).apply()
        }


    companion object {
        private const val VALUE_EMPTY_JSON_ARRAY = "[]"
        private const val KEY_CACHE = "cache"
        private const val KEY_DOWNLOADED_SONGS = "downloaded_songs"
        private const val KEY_PLAYLIST_IDS = "playlist_ids"
        private const val KEY_PLAYLIST = "playlist_"
    }
}