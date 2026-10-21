/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation.shared

import com.pandulapeter.campfire.data.model.share.CreateShareResult
import com.pandulapeter.campfire.data.model.share.ShareLinks
import com.pandulapeter.campfire.data.model.share.SharedLink
import com.pandulapeter.campfire.data.model.share.SharedLinkKind
import com.pandulapeter.campfire.data.model.share.SharingFailure
import com.pandulapeter.campfire.data.model.sync.SyncState
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncNetworkException
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncProviders
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncScopeMissingException
import com.pandulapeter.campfire.data.source.remote.api.sync.model.SharedLinkResult
import com.pandulapeter.campfire.data.sync.implementation.FakeSyncProvider
import com.pandulapeter.campfire.data.sync.implementation.SyncStateHolder
import com.pandulapeter.campfire.data.sync.implementation.TEST_ACCOUNT
import com.pandulapeter.campfire.data.sync.implementation.syncRepository
import com.pandulapeter.campfire.data.sync.implementation.testEnvironment
import com.pandulapeter.campfire.data.model.Logger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Sharing against an in-memory cloud folder, where another device's writes land between this one's read and write of
 * the account's list: the races the list's revision checks exist for.
 */
class SharedLinksRepositoryImplTest {

    private val copy = "zip".encodeToByteArray()

    @Test
    fun `sharing uploads the copy, links it, and adds it to the list and the mirror`() = runTest {
        val provider = FakeSyncProvider()
        val repository = repository(provider)

        val share = assertIs<CreateShareResult.Shared>(repository.share(SharedLinkKind.SETLIST, "gig.setlist.json", "Gig", copy)).share

        assertContentEquals(copy, provider.sharedFiles.getValue(share.shareId).first)
        assertEquals("id-${share.shareId}", share.link.fileId)
        assertEquals(share.link, ShareLinks.parse(share.link.landingUrl))
        assertEquals(listOf(share.shareId), registry(provider).links.map { it.shareId })
        assertEquals(listOf(share), repository.shares.first())
    }

    @Test
    fun `an item another device shares meanwhile answers that share and deletes the copy uploaded here`() = runTest {
        val provider = FakeSyncProvider()
        val repository = repository(provider)
        val other = entry(shareId = "aaaaaaaaaaaaaaaa", fileName = "gig.setlist.json")
        provider.onUploadDocument = { name ->
            if (provider.documents[name] == null) provider.writeDocumentElsewhere(name, encode(other))
        }

        val share = assertIs<CreateShareResult.Shared>(repository.share(SharedLinkKind.SETLIST, "gig.setlist.json", "Gig", copy)).share

        assertEquals(other.shareId, share.shareId)
        assertEquals(listOf(other.shareId), registry(provider).links.map { it.shareId })
        assertTrue(provider.sharedFiles.isEmpty())
    }

    @Test
    fun `another device's share of a different item meanwhile is kept beside this one`() = runTest {
        val provider = FakeSyncProvider()
        val repository = repository(provider)
        val other = entry(shareId = "aaaaaaaaaaaaaaaa", fileName = "other.setlist.json")
        provider.onUploadDocument = { name ->
            if (provider.documents[name] == null) provider.writeDocumentElsewhere(name, encode(other))
        }

        val share = assertIs<CreateShareResult.Shared>(repository.share(SharedLinkKind.SETLIST, "gig.setlist.json", "Gig", copy)).share

        assertEquals(listOf(other.shareId, share.shareId), registry(provider).links.map { it.shareId })
        assertEquals(setOf(share.shareId), provider.sharedFiles.keys)
    }

    @Test
    fun `a link only the account's own people can open is not shared, and its copy is deleted`() = runTest {
        val provider = FakeSyncProvider().apply { onCreateSharedLink = { SharedLinkResult.NotPublic } }

        val result = repository(provider).share(SharedLinkKind.SONG, "song.cho", "Song", copy)

        assertEquals(CreateShareResult.Failed(SharingFailure.NOT_PUBLIC), result)
        assertTrue(provider.sharedFiles.isEmpty())
        assertNull(provider.documents[SharedLinksFormat.NAME])
    }

    @Test
    fun `a connection without permission to make links says so, and leaves no copy behind`() = runTest {
        val provider = FakeSyncProvider().apply {
            onCreateSharedLink = { throw SyncScopeMissingException("missing_scope", requiredScope = "sharing.write") }
        }

        val result = repository(provider).share(SharedLinkKind.SONG, "song.cho", "Song", copy)

        assertEquals(CreateShareResult.Failed(SharingFailure.SCOPE_MISSING), result)
        assertTrue(provider.sharedFiles.isEmpty())
    }

    @Test
    fun `a network failure between the upload and the list deletes the copy`() = runTest {
        val provider = FakeSyncProvider().apply { onUploadDocument = { throw SyncNetworkException("offline") } }

        val result = repository(provider).share(SharedLinkKind.SONG, "song.cho", "Song", copy)

        assertEquals(CreateShareResult.Failed(SharingFailure.NETWORK), result)
        assertTrue(provider.sharedFiles.isEmpty())
    }

    @Test
    fun `a list from a newer version is left alone`() = runTest {
        val provider = FakeSyncProvider()
        val newer = """{"version": 2, "links": "elsewhere"}""".encodeToByteArray()
        provider.writeDocumentElsewhere(SharedLinksFormat.NAME, newer)
        val repository = repository(provider)

        assertEquals(SharingFailure.NEWER_VERSION, repository.refresh())
        assertEquals(CreateShareResult.Failed(SharingFailure.NEWER_VERSION), repository.share(SharedLinkKind.SONG, "song.cho", "Song", copy))
        assertContentEquals(newer, provider.documents.getValue(SharedLinksFormat.NAME).first)
        assertTrue(provider.sharedFiles.isEmpty())
    }

    @Test
    fun `a list that cannot be read counts as empty and is written over by a share`() = runTest {
        val provider = FakeSyncProvider()
        provider.writeDocumentElsewhere(SharedLinksFormat.NAME, "{ not json".encodeToByteArray())

        val share = assertIs<CreateShareResult.Shared>(repository(provider).share(SharedLinkKind.SONG, "song.cho", "Song", copy)).share

        assertEquals(listOf(share.shareId), registry(provider).links.map { it.shareId })
    }

    @Test
    fun `nothing is shared while no account is connected`() = runTest {
        val provider = FakeSyncProvider()

        val result = repository(provider, stateHolder = SyncStateHolder(Logger.Standard)).share(SharedLinkKind.SONG, "song.cho", "Song", copy)

        assertEquals(CreateShareResult.Failed(SharingFailure.NOT_CONNECTED), result)
        assertTrue(provider.sharedFiles.isEmpty())
    }

    @Test
    fun `stopping a share deletes its copy and its entry, and keeps the others`() = runTest {
        val provider = FakeSyncProvider()
        val repository = repository(provider)
        val kept = assertIs<CreateShareResult.Shared>(repository.share(SharedLinkKind.SONG, "kept.cho", "Kept", copy)).share
        val stopped = assertIs<CreateShareResult.Shared>(repository.share(SharedLinkKind.SONG, "stopped.cho", "Stopped", copy)).share

        assertNull(repository.stopSharing(stopped.shareId))

        assertEquals(setOf(kept.shareId), provider.sharedFiles.keys)
        assertEquals(listOf(kept.shareId), registry(provider).links.map { it.shareId })
        assertEquals(listOf(kept.shareId), repository.shares.first().map { it.shareId })
    }

    @Test
    fun `stopping a share that another device already stopped succeeds`() = runTest {
        val provider = FakeSyncProvider()

        assertNull(repository(provider).stopSharing("aaaaaaaaaaaaaaaa"))
    }

    @Test
    fun `stopping everything deletes every copy, unlisted ones included, and empties the list`() = runTest {
        val provider = FakeSyncProvider()
        val repository = repository(provider)
        assertIs<CreateShareResult.Shared>(repository.share(SharedLinkKind.SONG, "one.cho", "One", copy))
        assertIs<CreateShareResult.Shared>(repository.share(SharedLinkKind.SONG, "two.cho", "Two", copy))
        provider.sharedFiles["bbbbbbbbbbbbbbbb"] = copy to "left-behind"

        assertNull(repository.stopSharingEverythingNow())

        assertTrue(provider.sharedFiles.isEmpty())
        assertEquals(emptyList(), registry(provider).links)
        assertEquals(emptyList(), repository.shares.first())
    }

    @Test
    fun `stopping everything keeps listing the shares whose copies could not be deleted`() = runTest {
        val provider = FakeSyncProvider()
        val repository = repository(provider)
        val first = assertIs<CreateShareResult.Shared>(repository.share(SharedLinkKind.SONG, "one.cho", "One", copy)).share
        val second = assertIs<CreateShareResult.Shared>(repository.share(SharedLinkKind.SONG, "two.cho", "Two", copy)).share
        provider.onDeleteSharedFile = { shareId -> if (shareId == second.shareId) throw SyncNetworkException("offline") }

        assertEquals(SharingFailure.NETWORK, repository.stopSharingEverythingNow())

        assertEquals(setOf(second.shareId), provider.sharedFiles.keys)
        assertEquals(listOf(second.shareId), registry(provider).links.map { it.shareId })
        assertTrue(first.shareId !in provider.sharedFiles)
    }

    @Test
    fun `an entry whose id no copy can have is stopped without a copy to delete`() = runTest {
        val provider = FakeSyncProvider()
        val odd = sharedEntry(shareId = "abc.def", kind = SharedLinkKind.SONG, fileName = "odd.cho")
        provider.writeDocumentElsewhere(SharedLinksFormat.NAME, encode(odd))
        val repository = repository(provider)

        assertNull(repository.stopSharing(odd.shareId))

        assertEquals(emptyList(), registry(provider).links)
    }

    @Test
    fun `stopping everything gets past an entry whose id no copy can have`() = runTest {
        val provider = FakeSyncProvider()
        provider.writeDocumentElsewhere(SharedLinksFormat.NAME, encode(sharedEntry(shareId = "abc.def", kind = SharedLinkKind.SONG, fileName = "odd.cho")))
        val repository = repository(provider)
        assertIs<CreateShareResult.Shared>(repository.share(SharedLinkKind.SONG, "one.cho", "One", copy))

        assertNull(repository.stopSharingEverythingNow())

        assertTrue(provider.sharedFiles.isEmpty())
        assertEquals(emptyList(), registry(provider).links)
    }

    @Test
    fun `a deleted shared item whose id no copy can have leaves the list and the journal`() = runTest {
        val provider = FakeSyncProvider()
        provider.writeDocumentElsewhere(SharedLinksFormat.NAME, encode(sharedEntry(shareId = "abc.def", kind = SharedLinkKind.SONG, fileName = "odd.cho")))
        val localSource = FakeSharedLinksLocalSource()
        val repository = repository(provider, localSource = localSource)
        assertNull(repository.refresh())

        repository.stopSharingDeleted(SharedLinkKind.SONG, "odd.cho")
        runCurrent()

        assertEquals(emptyList(), registry(provider).links)
        assertNull(localSource.journal)
    }

    @Test
    fun `refreshing brings another device's shares into the mirror`() = runTest {
        val provider = FakeSyncProvider()
        val other = entry(shareId = "aaaaaaaaaaaaaaaa", fileName = "gig.setlist.json")
        provider.writeDocumentElsewhere(SharedLinksFormat.NAME, encode(other))
        val localSource = FakeSharedLinksLocalSource()
        val repository = repository(provider, localSource = localSource)

        assertEquals(emptyList(), repository.shares.first())
        assertNull(repository.refresh())

        assertEquals(listOf(other.shareId), repository.shares.first().map { it.shareId })
        val reloaded = SharedLinksMirror(localSource, testEnvironment()).load()
        assertEquals(provider.documents.getValue(SharedLinksFormat.NAME).second, reloaded.revision)
        assertEquals(listOf(other.shareId), reloaded.document.links.map { it.shareId })
    }

    @Test
    fun `disconnecting forgets the mirror and the journal`() = runTest {
        val localSource = FakeSharedLinksLocalSource(mirror = "{}", journal = "{}")
        val repository = syncRepository(provider = FakeSyncProvider(account = TEST_ACCOUNT), sharedLinksLocalSource = localSource)
        repository.restore {}

        repository.disconnect()

        assertNull(localSource.mirror)
        assertNull(localSource.journal)
    }

    @Test
    fun `a rename of a shared item is noted and reaches the list at once, and its link stays`() = runTest {
        val provider = FakeSyncProvider()
        val localSource = FakeSharedLinksLocalSource()
        val repository = repository(provider, localSource = localSource)
        val share = assertIs<CreateShareResult.Shared>(repository.share(SharedLinkKind.SETLIST, "gig.setlist.json", "Gig", copy)).share

        repository.followRename(SharedLinkKind.SETLIST, "gig.setlist.json", "summer_gig.setlist.json")
        runCurrent()

        assertEquals(listOf("summer_gig.setlist.json"), registry(provider).links.map { it.fileName })
        assertEquals(listOf(share.link), repository.shares.first().map { it.link })
        assertNull(localSource.journal)
    }

    @Test
    fun `renames made while the list cannot be told are kept, and a second one follows the first`() = runTest {
        val provider = FakeSyncProvider()
        val localSource = FakeSharedLinksLocalSource()
        val repository = repository(provider, localSource = localSource)
        repository.share(SharedLinkKind.SONG, "a.cho", "A", copy)
        provider.onUploadDocument = { throw SyncNetworkException("offline") }

        repository.followRename(SharedLinkKind.SONG, "a.cho", "b.cho")
        repository.followRename(SharedLinkKind.SONG, "b.cho", "c.cho")
        runCurrent()

        assertEquals(listOf("a.cho"), registry(provider).links.map { it.fileName })
        assertEquals(
            listOf(SharedLinksJournal.Entry.Rename("a.cho", "b.cho"), SharedLinksJournal.Entry.Rename("b.cho", "c.cho")),
            SharedLinksJournal(localSource, testEnvironment()).load(),
        )
    }

    @Test
    fun `an item that is not shared notes nothing`() = runTest {
        val provider = FakeSyncProvider()
        val localSource = FakeSharedLinksLocalSource()
        val repository = repository(provider, localSource = localSource)
        repository.share(SharedLinkKind.SONG, "a.cho", "A", copy)

        repository.followRename(SharedLinkKind.SONG, "other.cho", "renamed.cho")
        repository.followRename(SharedLinkKind.SETLIST, "a.cho", "renamed.cho")
        repository.stopSharingDeleted(SharedLinkKind.SONG, "other.cho")
        runCurrent()

        assertNull(localSource.journal)
        assertEquals(listOf("a.cho"), registry(provider).links.map { it.fileName })
    }

    @Test
    fun `a deleted shared item stops being shared, its copy before its entry`() = runTest {
        val provider = FakeSyncProvider()
        val localSource = FakeSharedLinksLocalSource()
        val repository = repository(provider, localSource = localSource)
        val kept = assertIs<CreateShareResult.Shared>(repository.share(SharedLinkKind.SONG, "kept.cho", "Kept", copy)).share
        repository.share(SharedLinkKind.SONG, "deleted.cho", "Deleted", copy)

        repository.stopSharingDeleted(SharedLinkKind.SONG, "deleted.cho")
        runCurrent()

        assertEquals(setOf(kept.shareId), provider.sharedFiles.keys)
        assertEquals(listOf(kept.shareId), registry(provider).links.map { it.shareId })
        assertNull(localSource.journal)
    }

    @Test
    fun `a deleted library stops every share, and while disconnected leaves the stops to the next run`() = runTest {
        val provider = FakeSyncProvider()
        val localSource = FakeSharedLinksLocalSource()
        val shared = listOf("a.cho", "b.cho").map { fileName ->
            assertIs<CreateShareResult.Shared>(repository(provider, localSource = localSource).share(SharedLinkKind.SONG, fileName, fileName, copy)).share
        }

        repository(provider, stateHolder = SyncStateHolder(Logger.Standard), localSource = localSource).stopSharingEverything()
        runCurrent()

        assertEquals(2, provider.sharedFiles.size)
        assertEquals(
            shared.map { SharedLinksJournal.Entry.Stop(it.shareId) }.toSet(),
            SharedLinksJournal(localSource, testEnvironment()).load().toSet(),
        )
    }

    private fun TestScope.repository(
        provider: FakeSyncProvider,
        stateHolder: SyncStateHolder = connectedStateHolder(),
        localSource: FakeSharedLinksLocalSource = FakeSharedLinksLocalSource(),
    ): SharedLinksRepositoryImpl {
        val environment = testEnvironment()
        val mirror = SharedLinksMirror(localSource, environment)
        val registry = SharedLinksRegistry(mirror, environment)
        val journal = SharedLinksJournal(localSource, environment)
        return SharedLinksRepositoryImpl(
            syncProviders = SyncProviders(listOf(provider)),
            stateHolder = stateHolder,
            mirror = mirror,
            registry = registry,
            journal = journal,
            journalApplier = SharedLinksJournalApplier(journal, registry, environment),
            environment = environment,
        )
    }

    private fun connectedStateHolder() = SyncStateHolder(Logger.Standard).apply {
        update { SyncState.Connected(account = TEST_ACCOUNT, progress = null, lastSyncedAt = null, lastOutcome = null) }
    }

    private fun registry(provider: FakeSyncProvider) = assertIs<SharedLinksFormat.Decoded.Readable>(
        SharedLinksFormat.decode(provider.documents.getValue(SharedLinksFormat.NAME).first.decodeToString()),
    ).document

    private fun entry(shareId: String, fileName: String) = SharedLinksEntry(
        shareId = shareId,
        kind = SharedLinkKind.SETLIST.id,
        fileName = fileName,
        title = "Elsewhere",
        fileId = "file",
        rlkey = "key",
        sharedAt = "2026-10-16",
    ).also { assertIs<SharedLink.Readable>(it.toShare()?.link) }

    private fun encode(vararg entries: SharedLinksEntry) =
        SharedLinksFormat.encode(SharedLinksDocument(links = entries.toList())).encodeToByteArray()
}
