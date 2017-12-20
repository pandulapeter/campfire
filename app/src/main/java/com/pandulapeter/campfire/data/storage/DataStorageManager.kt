package com.pandulapeter.campfire.data.storage

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.pandulapeter.campfire.data.model.DownloadedSong
import com.pandulapeter.campfire.data.model.Playlist
import com.pandulapeter.campfire.data.model.SongInfo
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Wrapper for locally storing complex data in a database.
 *
 * TODO: This data shouldn't be stored in Shared Preferences, replace with a Room-based implementation.
 */
class DataStorageManager(context: Context, gson: Gson) {
    private val songInfoPreferences = context.applicationContext.getSharedPreferences("song_info_storage", Context.MODE_PRIVATE)
    private val downloadedSongPreferences = context.applicationContext.getSharedPreferences("downloaded_song_storage", Context.MODE_PRIVATE)
    private val playlistPreferences = context.applicationContext.getSharedPreferences("playlist_storage", Context.MODE_PRIVATE)
    private val historyPreferences = context.applicationContext.getSharedPreferences("history_storage", Context.MODE_PRIVATE)
    var songInfoCache by MapDelegate(SongInfo::class.java, gson, songInfoPreferences, "ids", "song_")
    var downloadedSongCache by MapDelegate(DownloadedSong::class.java, gson, downloadedSongPreferences, "ids", "song_")
    var playlists by MapDelegate(Playlist::class.java, gson, playlistPreferences, "ids", "playlist_")
    var history by StringListDelegate(gson, historyPreferences, "ids")

    private class StringListDelegate(
        private val gson: Gson,
        private val sharedPreferences: SharedPreferences,
        private val key: String) : ReadWriteProperty<Any, List<String>> {

        override fun getValue(thisRef: Any, property: KProperty<*>): List<String> = try {
            gson.fromJson(sharedPreferences.getString(key, "[]"), object : TypeToken<List<String>>() {}.type)
        } catch (_: JsonSyntaxException) {
            listOf()
        }

        override fun setValue(thisRef: Any, property: KProperty<*>, value: List<String>) = sharedPreferences.edit().putString(key, gson.toJson(value)).apply()
    }

    private class MapDelegate<T>(
        private val type: Class<T>,
        private val gson: Gson,
        private val sharedPreferences: SharedPreferences,
        idKey: String,
        private val valueKeyPrefix: String) : ReadWriteProperty<Any, Map<String, T>> {
        private var ids by StringListDelegate(gson, sharedPreferences, idKey)

        override fun getValue(thisRef: Any, property: KProperty<*>): Map<String, T> {
            val map = HashMap<String, T>()
            ids.forEach { id ->
                val value: T? = try {
                    gson.fromJson(sharedPreferences.getString(valueKeyPrefix + id, "{}"), type)
                } catch (_: JsonSyntaxException) {
                    null
                }
                value?.let { map[id] = it }
            }
            return map
        }

        override fun setValue(thisRef: Any, property: KProperty<*>, value: Map<String, T>) {
            ids = value.keys.toList()
            value.keys.forEach { id ->
                sharedPreferences.edit().putString(valueKeyPrefix + id, gson.toJson(value[id])).apply()
            }
        }
    }
}