package com.pandulapeter.campfire.data.source.local.implementation.storage

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
 *
 * Decoding blocks the browser's only thread, so two things keep it off the critical path:
 *
 * - Every document is parsed at most once per session and kept in [documents] afterwards, so repeated reads and
 *   writes of the same table do not parse and re-encode it over and over.
 * - The text of the saved songs is *not* one document: each song gets a key of its own, read only when that song
 *   is opened.
 *
 * Nothing else writes to these keys while the app runs (a second tab would, exactly as a second process would with
 * Room), so the cache can be kept in sync by the writes going through here.
 */
internal class StorageManager {

    private val documents = mutableMapOf<String, Any?>()

    init {
        migrateRawSongDetailsToOwnKeys()
    }

    private fun loadSavedSongUrls(): Set<String> = loadDocument<Set<String>>(KEY_RAW_SONG_DETAIL_URLS) ?: emptySet()

    /** Not cached: the repository holds on to what it reads, so a second copy here would only double the memory. */
    fun loadRawSongDetails(url: String): RawSongDetailsEntity? = decode(KEY_RAW_SONG_DETAIL_PREFIX + url)

    fun saveRawSongDetails(rawSongDetails: RawSongDetailsEntity) {
        encode(KEY_RAW_SONG_DETAIL_PREFIX + rawSongDetails.url, rawSongDetails)
        val urls = loadSavedSongUrls()
        if (rawSongDetails.url !in urls) {
            saveDocument(KEY_RAW_SONG_DETAIL_URLS, urls + rawSongDetails.url)
        }
    }

    fun loadSetlists(): List<SetlistEntity> = loadDocument(KEY_SETLISTS) ?: emptyList()

    fun saveSetlists(setlists: List<SetlistEntity>) = saveDocument(KEY_SETLISTS, setlists)

    fun loadSongs(): List<SongEntity> = loadDocument(KEY_SONGS) ?: emptyList()

    fun saveSongs(songs: List<SongEntity>) = saveDocument(KEY_SONGS, songs)

    fun loadTranspositions(): List<TranspositionEntity> = loadDocument(KEY_TRANSPOSITIONS) ?: emptyList()

    fun saveTranspositions(transpositions: List<TranspositionEntity>) = saveDocument(KEY_TRANSPOSITIONS, transpositions)

    fun loadUserPreferences(): UserPreferencesEntity? = loadDocument(KEY_USER_PREFERENCES)

    fun saveUserPreferences(userPreferences: UserPreferencesEntity) = saveDocument(KEY_USER_PREFERENCES, userPreferences)

    /**
     * Earlier versions kept the text of every saved song in one document, which had to be parsed in full before
     * the app could show anything. It is split into one key per song here, once, so that nothing is lost.
     */
    private fun migrateRawSongDetailsToOwnKeys() {
        val legacyDocument = localStorage.getItem(KEY_PREFIX + KEY_LEGACY_RAW_SONG_DETAILS) ?: return
        try {
            val rawSongDetails = json.decodeFromString<List<RawSongDetailsEntity>>(legacyDocument)
            rawSongDetails.forEach { encode(KEY_RAW_SONG_DETAIL_PREFIX + it.url, it) }
            saveDocument(KEY_RAW_SONG_DETAIL_URLS, loadSavedSongUrls() + rawSongDetails.map { it.url })
        } catch (_: Exception) {
            // Written by a version with an incompatible schema, discarded like any other document that fails to parse.
        }
        localStorage.removeItem(KEY_PREFIX + KEY_LEGACY_RAW_SONG_DETAILS)
    }

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T> loadDocument(key: String): T? = if (documents.containsKey(key)) {
        documents[key] as T?
    } else {
        decode<T>(key).also { documents[key] = it }
    }

    private inline fun <reified T> saveDocument(key: String, value: T) {
        documents[key] = value
        encode(key, value)
    }

    private inline fun <reified T> decode(key: String): T? = try {
        localStorage.getItem(KEY_PREFIX + key)?.let { json.decodeFromString<T>(it) }
    } catch (_: Exception) {
        null
    }

    private inline fun <reified T> encode(key: String, value: T) = localStorage.setItem(KEY_PREFIX + key, json.encodeToString(value))

    private companion object {

        // Namespaced because localStorage is shared by everything served from the same origin.
        const val KEY_PREFIX = "campfire."
        const val KEY_RAW_SONG_DETAIL_URLS = "rawSongDetailUrls"
        const val KEY_RAW_SONG_DETAIL_PREFIX = "rawSongDetail."
        const val KEY_LEGACY_RAW_SONG_DETAILS = "rawSongDetails"
        const val KEY_SETLISTS = "setlists"
        const val KEY_SONGS = "songs"
        const val KEY_TRANSPOSITIONS = "transpositions"
        const val KEY_USER_PREFERENCES = "userPreferences"

        val json = Json { ignoreUnknownKeys = true }
    }
}
