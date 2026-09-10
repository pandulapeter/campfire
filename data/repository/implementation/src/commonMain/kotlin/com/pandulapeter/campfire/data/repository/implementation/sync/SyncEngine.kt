package com.pandulapeter.campfire.data.repository.implementation.sync

import com.pandulapeter.campfire.data.model.domain.SyncSummary
import com.pandulapeter.campfire.data.source.local.api.LibraryFileLocalSource
import com.pandulapeter.campfire.data.source.remote.api.SyncAuthorizationException
import com.pandulapeter.campfire.data.source.remote.api.SyncNetworkException
import com.pandulapeter.campfire.data.source.remote.api.SyncProvider
import com.pandulapeter.campfire.data.source.remote.api.hashing.localContentHash
import com.pandulapeter.campfire.data.source.remote.api.model.RemoteWriteResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Carries out what [SyncPlanner] worked out.
 *
 * Everything here is written so that a run which is interrupted - the network drops, the app is killed - leaves the
 * library usable and the next run able to pick up: the index is only told about a file once that file has actually
 * moved, so anything half done simply looks unsynced next time rather than done.
 *
 * A failure on one file does not end the run. A song the storage cannot read must not keep the other four hundred
 * from travelling, so only the two failures that make every further call pointless - the credentials being refused
 * and the service being unreachable - stop it.
 */
internal class SyncEngine(
    private val libraryFileLocalSource: LibraryFileLocalSource
) {

    suspend fun synchronize(provider: SyncProvider, document: SyncIndexDocument, accountId: String): Result {
        // An index written for a different account describes a different remote folder, and acting on it would read
        // that folder's absent files as deletions of this one's songs.
        var index = document.takeIf { it.accountId == accountId }?.toIndex().orEmpty()
        var summary = SyncSummary()

        // Two passes at most. A file that a second device changed between this run's listing and its upload comes
        // back as a conflict; the second pass sees the revision it actually has now and resolves it properly. If it
        // happens again the run stops rather than chasing a device that is writing continuously, and the next sync
        // settles it.
        var pass = 0
        while (pass < MAXIMUM_PASSES) {
            val files = provider.list().files
            val plan = SyncPlanner.plan(
                local = readLocalStates(),
                remote = files.map {
                    RemoteFileState(
                        key = SyncKey(kind = it.kind, name = it.name),
                        revision = it.revision,
                        contentHash = it.contentHash
                    )
                },
                index = index
            )
            // Which is what most runs find, so nothing below this costs anything on an ordinary launch.
            if (plan.isEmpty()) break
            val outcome = apply(
                provider = provider,
                plan = plan,
                index = index,
                contentHashes = files.associate { SyncKey(kind = it.kind, name = it.name) to it.contentHash }
            )
            index = outcome.index
            summary = summary.plus(outcome.summary)
            if (!outcome.hasUnresolvedConflicts) break
            pass++
        }

        return Result(
            summary = summary,
            index = SyncIndexDocument.of(
                providerId = provider.id.id,
                accountId = accountId,
                lastSyncedAt = document.lastSyncedAt,
                index = index
            )
        )
    }

    /** Reading and hashing every file is the slow part of a run, so the files are read in parallel. */
    private suspend fun readLocalStates(): List<LocalFileState> = coroutineScope {
        libraryFileLocalSource.loadLibraryFiles()
            .map { file ->
                async {
                    val key = SyncKey(kind = file.kind, name = file.name)
                    libraryFileLocalSource.readLibraryFile(file.kind, file.name)
                        ?.let { LocalFileState(key = key, hash = localContentHash(it)) }
                }
            }
            .awaitAll()
            .filterNotNull()
    }

    private suspend fun apply(
        provider: SyncProvider,
        plan: List<SyncOperation>,
        index: Map<SyncKey, SyncIndexEntry>,
        contentHashes: Map<SyncKey, String?>
    ): PassOutcome {
        val updated = index.toMutableMap()
        var summary = SyncSummary()
        var hasUnresolvedConflicts = false

        // Incoming files first, then outgoing ones, then the deletions. A conflict copy has to be on disk before
        // the file it was made from is overwritten, and a deletion that runs before a download would undo it.
        plan.sortedBy { it.order }.forEach { operation ->
            try {
                when (operation) {
                    is SyncOperation.Download -> summary += download(provider, operation, updated, contentHashes)
                    is SyncOperation.Upload -> upload(provider, operation, updated)
                        .also { if (it == null) hasUnresolvedConflicts = true }
                        ?.let { summary += it }

                    is SyncOperation.Resolve -> resolve(provider, operation, updated, contentHashes)
                        .also { if (it == null) hasUnresolvedConflicts = true }
                        ?.let { summary += it }

                    is SyncOperation.DeleteLocal -> {
                        libraryFileLocalSource.deleteLibraryFile(operation.key.kind, operation.key.name)
                        updated -= operation.key
                        summary += SyncSummary(deletedLocally = 1)
                    }

                    is SyncOperation.DeleteRemote -> {
                        provider.delete(operation.key.kind, operation.key.name, operation.revision)
                        updated -= operation.key
                        summary += SyncSummary(deletedRemotely = 1)
                    }

                    is SyncOperation.Forget -> updated -= operation.key
                }
            } catch (exception: SyncAuthorizationException) {
                throw exception
            } catch (exception: SyncNetworkException) {
                throw exception
            } catch (exception: Exception) {
                // The index is left alone, so the next run sees this file as it was and tries again.
                println("Could not sync \"${operation.key.path}\": ${exception.message}")
            }
        }
        return PassOutcome(summary = summary, index = updated, hasUnresolvedConflicts = hasUnresolvedConflicts)
    }

    private suspend fun download(
        provider: SyncProvider,
        operation: SyncOperation.Download,
        index: MutableMap<SyncKey, SyncIndexEntry>,
        contentHashes: Map<SyncKey, String?>
    ): SyncSummary {
        val key = operation.key
        // The two sides may already hold the same bytes - two devices given the same file, or a library that was
        // copied across by hand before sync was set up. Nothing has to travel for that, only the index.
        val local = libraryFileLocalSource.readLibraryFile(key.kind, key.name)
        if (local != null && isSameContent(provider, local, contentHashes[key])) {
            index[key] = SyncIndexEntry(localHash = localContentHash(local), remoteRevision = operation.revision)
            return SyncSummary()
        }
        val bytes = provider.download(key.kind, key.name)
        libraryFileLocalSource.writeLibraryFile(key.kind, key.name, bytes)
        index[key] = SyncIndexEntry(localHash = localContentHash(bytes), remoteRevision = operation.revision)
        return SyncSummary(downloaded = 1)
    }

    /** Null when the remote file moved under the write, which is what asks for another pass. */
    private suspend fun upload(
        provider: SyncProvider,
        operation: SyncOperation.Upload,
        index: MutableMap<SyncKey, SyncIndexEntry>
    ): SyncSummary? {
        val key = operation.key
        // Deleted between the listing and now, which the next run will see as a deletion and handle properly.
        val bytes = libraryFileLocalSource.readLibraryFile(key.kind, key.name) ?: return SyncSummary()
        return when (val result = provider.upload(key.kind, key.name, bytes, operation.expectedRevision)) {
            is RemoteWriteResult.Written -> {
                index[key] = SyncIndexEntry(localHash = localContentHash(bytes), remoteRevision = result.revision)
                SyncSummary(uploaded = 1)
            }

            RemoteWriteResult.Conflict -> null
        }
    }

    /**
     * A file that changed on both sides. The local version keeps the name and goes up; the remote one comes down
     * next to it under a free name and goes back up under that name, so that both devices end with both versions
     * and the same two names. Nothing is merged, and nothing is thrown away.
     */
    private suspend fun resolve(
        provider: SyncProvider,
        operation: SyncOperation.Resolve,
        index: MutableMap<SyncKey, SyncIndexEntry>,
        contentHashes: Map<SyncKey, String?>
    ): SyncSummary? {
        val key = operation.key
        val local = libraryFileLocalSource.readLibraryFile(key.kind, key.name) ?: return SyncSummary()
        // The two sides changed to the same thing, which is not a conflict at all - the same edit made twice.
        if (isSameContent(provider, local, contentHashes[key])) {
            index[key] = SyncIndexEntry(localHash = localContentHash(local), remoteRevision = operation.revision)
            return SyncSummary()
        }
        val remote = provider.download(key.kind, key.name)
        if (remote.contentEquals(local)) {
            index[key] = SyncIndexEntry(localHash = localContentHash(local), remoteRevision = operation.revision)
            return SyncSummary()
        }
        val copyName = libraryFileLocalSource.writeLibraryFileToFreeName(key.kind, key.name, remote)
        val copyKey = SyncKey(kind = key.kind, name = copyName)

        val uploaded = provider.upload(key.kind, key.name, local, operation.revision)
        if (uploaded !is RemoteWriteResult.Written) return null
        index[key] = SyncIndexEntry(localHash = localContentHash(local), remoteRevision = uploaded.revision)

        val copyUploaded = provider.upload(copyKey.kind, copyKey.name, remote, expectedRevision = null)
        if (copyUploaded is RemoteWriteResult.Written) {
            index[copyKey] = SyncIndexEntry(localHash = localContentHash(remote), remoteRevision = copyUploaded.revision)
        }
        // Counted as one upload even when the copy also went up: what the user needs to know is that one file was
        // in two states, and that both of them survived under the name in the summary.
        return SyncSummary(uploaded = 1, downloaded = 1, conflicts = listOf(copyName))
    }

    private fun isSameContent(provider: SyncProvider, local: ByteArray, remoteContentHash: String?) =
        remoteContentHash != null && provider.contentHashOf(local) == remoteContentHash

    private val SyncOperation.order
        get() = when (this) {
            is SyncOperation.Resolve -> 0
            is SyncOperation.Download -> 1
            is SyncOperation.Upload -> 2
            is SyncOperation.DeleteLocal -> 3
            is SyncOperation.DeleteRemote -> 4
            is SyncOperation.Forget -> 5
        }

    private operator fun SyncSummary.plus(other: SyncSummary) = SyncSummary(
        downloaded = downloaded + other.downloaded,
        uploaded = uploaded + other.uploaded,
        deletedLocally = deletedLocally + other.deletedLocally,
        deletedRemotely = deletedRemotely + other.deletedRemotely,
        conflicts = conflicts + other.conflicts
    )

    data class Result(
        val summary: SyncSummary,
        val index: SyncIndexDocument
    )

    private data class PassOutcome(
        val summary: SyncSummary,
        val index: Map<SyncKey, SyncIndexEntry>,
        val hasUnresolvedConflicts: Boolean
    )

    private companion object {
        const val MAXIMUM_PASSES = 2
    }
}
