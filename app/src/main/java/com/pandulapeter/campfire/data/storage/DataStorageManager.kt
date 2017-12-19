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
 * Wrapper for storing complex data in a local database.
 *
 * TODO: This data shouldn't be stored in Shared Preferences, replace with a Room-based implementation.
 */
class DataStorageManager(context: Context, gson: Gson) {

    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    var songInfoCache by MapDelegate<SongInfo>(gson, sharedPreferences, KEY_SONG_INFO_IDS, KEY_SONG_INFO)

    var downloadedSongCache by MapDelegate<DownloadedSong>(gson, sharedPreferences, KEY_DOWNLOADED_SONG_IDS, KEY_DOWNLOADED_SONG)

    var playlists by MapDelegate<Playlist>(gson, sharedPreferences, KEY_PLAYLIST_IDS, KEY_PLAYLIST)

    private class MapDelegate<T>(
        private val gson: Gson,
        private val sharedPreferences: SharedPreferences,
        private val idKey: String,
        private val valueKeyPrefix: String) : ReadWriteProperty<Any, HashMap<String, T>> {

        override fun getValue(thisRef: Any, property: KProperty<*>): HashMap<String, T> {
            val map = HashMap<String, T>()
            getIds().forEach { id ->
                val value: T? = try {
                    gson.fromJson(sharedPreferences.getString(valueKeyPrefix + id, VALUE_EMPTY_OBJECT), object : TypeToken<T>() {}.type)
                } catch (_: JsonSyntaxException) {
                    null
                }
                value?.let { map[id] = it }
            }
            return map
        }

        override fun setValue(thisRef: Any, property: KProperty<*>, value: HashMap<String, T>) {
            sharedPreferences.edit().putString(idKey, gson.toJson(value.keys)).apply()
            value.keys.forEach { id ->
                sharedPreferences.edit().putString(valueKeyPrefix + id, gson.toJson(value[id])).apply()
            }
        }

        private fun getIds(): List<String> = try {
            gson.fromJson(sharedPreferences.getString(idKey, VALUE_EMPTY_JSON_ARRAY), object : TypeToken<List<String>>() {}.type)
        } catch (_: JsonSyntaxException) {
            listOf()
        }

        companion object {
            private const val VALUE_EMPTY_JSON_ARRAY = "[]"
            private const val VALUE_EMPTY_OBJECT = "{}"
        }
    }

    companion object {
        private const val KEY_SONG_INFO_IDS = "song_info_ids"
        private const val KEY_SONG_INFO = "song_info"
        private const val KEY_DOWNLOADED_SONG_IDS = "downloaded_song_ids"
        private const val KEY_DOWNLOADED_SONG = "downloaded_song_"
        private const val KEY_PLAYLIST_IDS = "playlist_ids"
        private const val KEY_PLAYLIST = "playlist_"
    }
}
}