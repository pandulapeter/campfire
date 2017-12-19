package com.pandulapeter.campfire.data.storage

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
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
    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
    var songInfoCache by MapDelegate<SongInfo>(gson, sharedPreferences, "song_info_ids", "song_info_")
    var downloadedSongCache by MapDelegate<DownloadedSong>(gson, sharedPreferences, "downloaded_song_ids", "downloaded_song_")
    var playlists by MapDelegate<Playlist>(gson, sharedPreferences, "playlist_ids", "playlist_")
    var history by ListDelegate<String>(gson, sharedPreferences, "history_ids")

    private class ListDelegate<T>(
        private val gson: Gson,
        private val sharedPreferences: SharedPreferences,
        private val key: String) : ReadWriteProperty<Any, List<T>> {

        override fun getValue(thisRef: Any, property: KProperty<*>): List<T> = try {
            gson.fromJson(sharedPreferences.getString(key, "[]"), object : TypeToken<List<T>>() {}.type)
        } catch (_: JsonSyntaxException) {
            listOf()
        }

        override fun setValue(thisRef: Any, property: KProperty<*>, value: List<T>) = sharedPreferences.edit().putString(key, gson.toJson(value)).apply()
    }

    private class MapDelegate<T>(
        private val gson: Gson,
        private val sharedPreferences: SharedPreferences,
        idKey: String,
        private val valueKeyPrefix: String) : ReadWriteProperty<Any, HashMap<String, T>> {
        private var ids by ListDelegate<String>(gson, sharedPreferences, idKey)

        override fun getValue(thisRef: Any, property: KProperty<*>): HashMap<String, T> {
            val map = HashMap<String, T>()
            ids.forEach { id ->
                val value: T? = try {
                    gson.fromJson(sharedPreferences.getString(valueKeyPrefix + id, "{}"), object : TypeToken<T>() {}.type)
                } catch (_: JsonSyntaxException) {
                    null
                }
                value?.let { map[id] = it }
            }
            return map
        }

        override fun setValue(thisRef: Any, property: KProperty<*>, value: HashMap<String, T>) {
            ids = value.keys.toList()
            value.keys.forEach { id ->
                sharedPreferences.edit().putString(valueKeyPrefix + id, gson.toJson(value[id])).apply()
            }
        }
    }
}