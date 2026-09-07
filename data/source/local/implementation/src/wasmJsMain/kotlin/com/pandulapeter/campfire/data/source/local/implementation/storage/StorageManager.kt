package com.pandulapeter.campfire.data.source.local.implementation.storage

import com.pandulapeter.campfire.data.source.local.implementation.model.DatabaseEntity
import com.pandulapeter.campfire.data.source.local.implementation.model.RawSongDetailsEntity
import com.pandulapeter.campfire.data.source.local.implementation.model.SetlistEntity
import com.pandulapeter.campfire.data.source.local.implementation.model.SongEntity
import com.pandulapeter.campfire.data.source.local.implementation.model.TranspositionEntity
import com.pandulapeter.campfire.data.source.local.implementation.model.UserPreferencesEntity
import kotlinx.browser.localStorage
import kotlinx.serialization.json.Json

/**
 * The web counterpart of the Room database the other platforms use: every table becomes a single JSON document in the
 * browser's `localStorage`, which - like the database file elsewhere - survives reloads and is scoped to the origin.
 *
 * The data set is small and is only ever read or written as a whole, so the query capabilities of Room aren't needed
 * here. Documents that fail to parse (written by a version with an incompatible schema) are discarded rather than
 * migrated, which mirrors the `fallbackToDestructiveMigration` behaviour of the Room implementation.
 */
internal class StorageManager {

    fun loadDatabases(): List<DatabaseEntity> = load(KEY_DATABASES) ?: emptyList()

    fun saveDatabases(databases: List<DatabaseEntity>) = save(KEY_DATABASES, databases)

    fun loadRawSongDetails(): List<RawSongDetailsEntity> = load(KEY_RAW_SONG_DETAILS) ?: emptyList()

    fun saveRawSongDetails(rawSongDetails: List<RawSongDetailsEntity>) = save(KEY_RAW_SONG_DETAILS, rawSongDetails)

    fun loadSetlists(): List<SetlistEntity> = load(KEY_SETLISTS) ?: emptyList()

    fun saveSetlists(setlists: List<SetlistEntity>) = save(KEY_SETLISTS, setlists)

    fun loadSongs(): List<SongEntity> = load(KEY_SONGS) ?: emptyList()

    fun saveSongs(songs: List<SongEntity>) = save(KEY_SONGS, songs)

    fun loadTranspositions(): List<TranspositionEntity> = load(KEY_TRANSPOSITIONS) ?: emptyList()

    fun saveTranspositions(transpositions: List<TranspositionEntity>) = save(KEY_TRANSPOSITIONS, transpositions)

    fun loadUserPreferences(): UserPreferencesEntity? = load(KEY_USER_PREFERENCES)

    fun saveUserPreferences(userPreferences: UserPreferencesEntity) = save(KEY_USER_PREFERENCES, userPreferences)

    private inline fun <reified T> load(key: String): T? = try {
        localStorage.getItem(KEY_PREFIX + key)?.let { json.decodeFromString<T>(it) }
    } catch (_: Exception) {
        null
    }

    private inline fun <reified T> save(key: String, value: T) = localStorage.setItem(KEY_PREFIX + key, json.encodeToString(value))

    private companion object {

        // Namespaced because localStorage is shared by everything served from the same origin.
        const val KEY_PREFIX = "campfire."
        const val KEY_DATABASES = "databases"
        const val KEY_RAW_SONG_DETAILS = "rawSongDetails"
        const val KEY_SETLISTS = "setlists"
        const val KEY_SONGS = "songs"
        const val KEY_TRANSPOSITIONS = "transpositions"
        const val KEY_USER_PREFERENCES = "userPreferences"

        val json = Json { ignoreUnknownKeys = true }
    }
}
