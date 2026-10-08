/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.repository.implementation.sync

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.domain.ParsedSetlist
import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.SongContent
import com.pandulapeter.campfire.data.model.domain.SyncProviderId
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.data.repository.api.SetlistRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.data.repository.api.UserPreferencesRepository
import com.pandulapeter.campfire.data.source.local.api.SetlistComparison
import com.pandulapeter.campfire.data.source.local.api.SyncIndexLocalSource
import com.pandulapeter.campfire.data.source.remote.api.PendingAuthorization
import com.pandulapeter.campfire.data.source.remote.api.PendingAuthorizationStore
import com.pandulapeter.campfire.data.source.remote.api.SyncAuthenticator
import com.pandulapeter.campfire.data.source.remote.api.model.AuthorizationCompletionPage
import com.pandulapeter.campfire.data.source.remote.api.model.RemoteAuthorizationRequest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalDate

/** Tells no two setlists apart by anything less than their bytes, so a test using it pins the engine without that rule. */
internal object NoSetlistComparison : SetlistComparison {

    override fun isSameApartFromDate(first: ByteArray, second: ByteArray) = false

    override fun withoutDate(bytes: ByteArray): ByteArray? = null
}

/**
 * Reads a `date:…;` token in a test's text as the day a setlist names, so that the engine's handling of it is what is
 * tested rather than the JSON the real comparison reads.
 */
internal object DayBlindSetlistComparison : SetlistComparison {

    private val day = Regex("date:[^;]*;")

    override fun isSameApartFromDate(first: ByteArray, second: ByteArray) =
        first.decodeToString().replace(day, "") == second.decodeToString().replace(day, "")

    override fun withoutDate(bytes: ByteArray) = bytes.decodeToString().replace(day, "").encodeToByteArray()
}

/**
 * The two documents sync keeps between runs, held in memory. [onSaveIndex] runs before a write of the index is
 * stored, and [onLoadIndex] before a read of it answers, which is where a test makes either fail by throwing from it. A cancelled caller's write is refused the
 * way the real storage's is, whose writes are a `withContext` and so answer a cancellation on the way in.
 */
internal class FakeSyncIndexLocalSource(
    var index: String? = null,
    var onSaveIndex: (String?) -> Unit = {},
    var onLoadIndex: () -> Unit = {},
) : SyncIndexLocalSource {

    override suspend fun loadSyncIndex(): String? {
        onLoadIndex()
        return index
    }

    override suspend fun saveSyncIndex(document: String?) {
        currentCoroutineContext().ensureActive()
        onSaveIndex(document)
        index = document
    }

    var isForgettingOwed = false

    /** Runs before a read of [isForgettingOwed] answers, which is where a test makes it fail. */
    var onIsForgettingOwed: () -> Unit = {}

    /** Runs before a write of [isForgettingOwed] is stored, which is where a test records or fails it. */
    var onSetForgettingOwed: (Boolean) -> Unit = {}

    override suspend fun isForgettingCredentialsOwed(): Boolean {
        onIsForgettingOwed()
        return isForgettingOwed
    }

    override suspend fun setForgettingCredentialsOwed(isOwed: Boolean) {
        onSetForgettingOwed(isOwed)
        isForgettingOwed = isOwed
    }
}

/**
 * A platform whose consent page answers with [outcome] every time, and that has no redirect waiting at start up.
 * Without an outcome, being asked for consent at all is a mistake of the test. [onAuthorize] runs before the page
 * answers, which is where a test keeps the user on it.
 */
internal class FakeSyncAuthenticator(
    private val outcome: SyncAuthenticator.AuthorizationOutcome? = null,
    private val onAuthorize: suspend () -> Unit = {},
) : SyncAuthenticator {

    override suspend fun prepareRedirectUri(): String? = null

    override suspend fun authorize(
        authorizationUrl: String,
        completionPage: AuthorizationCompletionPage,
    ): SyncAuthenticator.AuthorizationOutcome {
        onAuthorize()
        return outcome ?: throw UnsupportedOperationException()
    }

    override suspend fun consumePendingRedirect(): String? = null
}

/**
 * The authorization that was started and not finished, held in memory. [onWrite] runs before every save and clear,
 * which is where a test makes the storage refuse them.
 */
internal class FakePendingAuthorizationStore : PendingAuthorizationStore {

    var pending: PendingAuthorization? = null
    var onWrite: () -> Unit = {}

    override suspend fun savePendingAuthorization(providerId: SyncProviderId, request: RemoteAuthorizationRequest) {
        onWrite()
        pending = PendingAuthorization(
            providerId = providerId,
            state = request.state,
            verifier = request.verifier,
            redirectUri = request.redirectUri,
        )
    }

    override suspend fun loadPendingAuthorization() = pending

    override suspend fun clearPendingAuthorization() {
        onWrite()
        pending = null
    }
}

/**
 * Stands in for the song list that sync tells to read the library again, and records what it was told: how many whole
 * rescans, and which files it was asked to refresh. [onRescan] runs on every rescan, which is where a test sees what the
 * library held at that moment. The list has been read unless a test says otherwise through [songs], and
 * [onLoadIfNeeded] is where it finishes a read it left going.
 */
internal class RecordingSongRepository(
    private val onRescan: () -> Unit = {},
    override val songs: MutableStateFlow<DataState<List<Song>>> = MutableStateFlow(DataState.Idle(emptyList())),
    private val onLoadIfNeeded: suspend () -> Unit = {},
) : SongRepository {

    var rescanCount = 0
    val refreshed = mutableListOf<String>()

    override suspend fun loadSongsIfNeeded(): List<Song>? {
        onLoadIfNeeded()
        return songs.value.data
    }

    override suspend fun loadSongFileSizes(): Map<String, Long> = throw UnsupportedOperationException()

    override suspend fun rescan() {
        rescanCount++
        onRescan()
    }

    override suspend fun refresh(fileNames: Set<String>) {
        refreshed += fileNames
    }

    override suspend fun adoptImported(songs: Collection<Song>) = throw UnsupportedOperationException()

    override suspend fun saveSong(content: SongContent, expectedText: String?): Boolean = throw UnsupportedOperationException()

    override suspend fun createSong(title: String, artist: String, text: String): Song = throw UnsupportedOperationException()

    override fun importFileName(fallbackTitle: String, text: String): String = throw UnsupportedOperationException()

    override suspend fun importSong(fileName: String, text: String, shouldReplace: Boolean): Song =
        throw UnsupportedOperationException()

    override suspend fun renameSong(song: Song): Song? = throw UnsupportedOperationException()

    override suspend fun deleteSong(fileName: String): Unit = throw UnsupportedOperationException()
    override suspend fun deleteAllSongs(): Unit = throw UnsupportedOperationException()
}

/** Stands in for the setlist list that sync tells to read the library again, and counts how often it was told. */
internal class RecordingSetlistRepository : SetlistRepository {

    var rescanCount = 0

    override val setlists: Flow<DataState<List<Setlist>>> = MutableStateFlow(DataState.Idle(emptyList()))

    override suspend fun loadSetlistsIfNeeded(): List<Setlist>? = throw UnsupportedOperationException()

    override suspend fun loadSetlistFileNamesNaming(songFileName: String): List<String> = throw UnsupportedOperationException()

    override suspend fun rescan() {
        rescanCount++
    }

    override suspend fun refresh(fileNames: Set<String>) = Unit

    override suspend fun adoptImported(setlists: Collection<Setlist>) = throw UnsupportedOperationException()

    override suspend fun createSetlist(title: String, description: String, date: LocalDate, isCountdownShown: Boolean): Setlist =
        throw UnsupportedOperationException()

    override suspend fun saveSetlist(setlist: Setlist): Unit = throw UnsupportedOperationException()

    override suspend fun updateSetlist(fileName: String, transform: (Setlist) -> Setlist): Setlist? =
        throw UnsupportedOperationException()

    override suspend fun renameSetlist(fileName: String, title: String, description: String, date: LocalDate, isCountdownShown: Boolean): Setlist? =
        throw UnsupportedOperationException()

    override suspend fun parseSetlist(document: String): ParsedSetlist? = throw UnsupportedOperationException()

    override suspend fun importSetlist(setlist: Setlist, shouldReplace: Boolean): Setlist = throw UnsupportedOperationException()

    override suspend fun loadSetlistFileSizes(): Map<String, Long> = throw UnsupportedOperationException()

    override suspend fun loadSetlistDocument(fileName: String, songFileNames: Set<String>?): String? = throw UnsupportedOperationException()

    override suspend fun deleteSetlist(fileName: String): Unit = throw UnsupportedOperationException()
    override suspend fun deleteAllSetlists(): Unit = throw UnsupportedOperationException()
}

/** The preferences held in memory, already read, as the app has them by the time a run starts. */
internal class FakeUserPreferencesRepository(
    preferences: UserPreferences = defaultUserPreferences(),
) : UserPreferencesRepository {

    private val state = MutableStateFlow<DataState<UserPreferences>>(DataState.Idle(preferences))

    override val userPreferences: Flow<DataState<UserPreferences>> = state

    val current get() = state.value.data!!

    override suspend fun loadUserPreferencesIfNeeded() = state.value.data

    override suspend fun saveUserPreferences(userPreferences: UserPreferences) {
        state.value = DataState.Idle(userPreferences)
    }

    override suspend fun updateUserPreferences(transform: (UserPreferences) -> UserPreferences) {
        state.value = DataState.Idle(transform(current))
    }

    override suspend fun hasStoredUserPreferences() = true
}

internal fun defaultUserPreferences(
    transpositions: Map<String, Int> = emptyMap(),
    tempos: Map<String, Int> = emptyMap(),
    capos: Map<String, Int> = emptyMap(),
) = UserPreferences(
    isPerformanceModeEnabled = false,
    shouldShowArchivedSetlists = false,
    areChordsEnabled = true,
    areSetlistsEnabled = true,
    isMetronomeEnabled = true,
    fontScale = UserPreferences.DEFAULT_FONT_SCALE,
    sortingMode = UserPreferences.SortingMode.BY_TITLE,
    setlistSortingMode = UserPreferences.SetlistSortingMode.BY_DATE,
    uiMode = UserPreferences.UiMode.SYSTEM_DEFAULT,
    themeColor = UserPreferences.ThemeColor.CAMPFIRE,
    isAppIconThemed = true,
    isCoverArtEnabled = true,
    shouldNumberSections = true,
    language = UserPreferences.Language.SYSTEM_DEFAULT,
    chordSpelling = UserPreferences.ChordSpelling.Default,
    transpositions = transpositions,
    tempos = tempos,
    capos = capos,
    foldedSections = emptyMap(),
    tagMatchMode = UserPreferences.MatchMode.ANY,
    languageMatchMode = UserPreferences.MatchMode.ANY,
    tagSortingMode = UserPreferences.LabelSortingMode.BY_USAGE,
    languageSortingMode = UserPreferences.LabelSortingMode.BY_USAGE,
)
