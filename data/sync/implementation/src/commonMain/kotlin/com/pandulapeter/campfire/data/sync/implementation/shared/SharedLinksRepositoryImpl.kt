/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
@file:OptIn(ExperimentalTime::class)

package com.pandulapeter.campfire.data.sync.implementation.shared

import com.pandulapeter.campfire.data.model.recovering
import com.pandulapeter.campfire.data.model.share.CreateShareResult
import com.pandulapeter.campfire.data.model.share.SharedLinkKind
import com.pandulapeter.campfire.data.model.share.SharingFailure
import com.pandulapeter.campfire.data.model.sync.SyncState
import com.pandulapeter.campfire.data.repository.api.SharedLinksRepository
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncAuthorizationException
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncFolder
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncNetworkException
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncProviders
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncRemoteStorageFullException
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncScopeMissingException
import com.pandulapeter.campfire.data.source.remote.api.sync.model.RemoteWriteResult
import com.pandulapeter.campfire.data.source.remote.api.sync.model.SharedLinkResult
import com.pandulapeter.campfire.data.sync.implementation.SyncEnvironment
import com.pandulapeter.campfire.data.sync.implementation.SyncStateHolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.core.annotation.Single
import kotlin.time.ExperimentalTime

/**
 * Sharing actions against the connected account's folder, through [SharedLinksRegistry]. Never under the run lock: the
 * copy is named by a fresh id and the list is edited against its revision, so an action and a run, which edits the
 * same list, never clobber each other.
 */
@Single
internal class SharedLinksRepositoryImpl(
    syncProviders: SyncProviders,
    private val stateHolder: SyncStateHolder,
    private val mirror: SharedLinksMirror,
    private val registry: SharedLinksRegistry,
    private val journal: SharedLinksJournal,
    private val journalApplier: SharedLinksJournalApplier,
    private val environment: SyncEnvironment,
) : SharedLinksRepository {

    private val providers = syncProviders.all

    override val shares = combine(mirror.mirrored, journal.entries) { mirrored, pending -> pendingApplied(mirrored.document, pending).toShares() }
        .distinctUntilChanged()

    override suspend fun refresh(): SharingFailure? {
        val folder = connectedFolder() ?: return SharingFailure.NOT_CONNECTED
        return failureOf("read the shared links") {
            when (registry.read(folder)) {
                is SharedLinksRegistry.Read.Readable -> null
                is SharedLinksRegistry.Read.FromNewerVersion -> SharingFailure.NEWER_VERSION
            }
        }
    }

    override suspend fun share(
        kind: SharedLinkKind,
        fileName: String,
        title: String,
        copy: ByteArray,
    ): CreateShareResult {
        val folder = connectedFolder() ?: return CreateShareResult.Failed(SharingFailure.NOT_CONNECTED)
        // A stop by name or of everything, and a rename that names no share, are resolved against the list when applied,
        // so one still waiting would catch this share too: the journal goes first, and a share waits for what stays.
        applyPendingJournal(folder)?.let { return CreateShareResult.Failed(it) }
        val shareId = ShareIds.generate()
        var isUploaded = false
        var isKept = false
        return try {
            when (folder.uploadSharedFile(shareId = shareId, bytes = copy, expectedRevision = null)) {
                is RemoteWriteResult.Written -> isUploaded = true
                // A fresh id that is taken is a copy nobody's list names, which is not this share's to overwrite.
                RemoteWriteResult.Conflict -> return CreateShareResult.Failed(SharingFailure.UNKNOWN)
            }
            val link = when (val result = folder.createSharedLink(shareId)) {
                is SharedLinkResult.Created -> result
                SharedLinkResult.NotPublic -> return CreateShareResult.Failed(SharingFailure.NOT_PUBLIC)
                SharedLinkResult.Refused -> return CreateShareResult.Failed(SharingFailure.REFUSED)
            }
            val entry = SharedLinksEntry(
                shareId = shareId,
                kind = kind.id,
                fileName = fileName,
                title = title,
                fileId = link.fileId,
                rlkey = link.rlkey,
                sharedAt = environment.clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString(),
            )
            if (entry.toShare() == null) {
                environment.logger.log("The service answered a link whose parts make no Campfire link.")
                return CreateShareResult.Failed(SharingFailure.REFUSED)
            }
            // The caller found no share of the item, but another device may have added one since. Every attempt of the
            // edit reads the list again, so that share is found at the latest on the attempt after the conflict its
            // write caused, and answered instead: one item never has two links. A share whose copy is gone is no share
            // to answer, since its link opens nothing: this one takes its place in the same write. The item is looked up
            // in the list as the journal will leave it, which an entry noted since its application may still change.
            val pending = journal.load()
            val change = { document: SharedLinksDocument ->
                val existing = pendingApplied(document, pending).entryFor(kind, fileName)
                if (existing == null || existing.copyMissing) {
                    SharedLinksRegistry.Change(document.copy(links = document.links.filterNot { it.shareId == existing?.shareId } + entry), entry)
                } else {
                    SharedLinksRegistry.Change(null, existing)
                }
            }
            // Not cancellable, and isKept decided inside: once the list may have been written, this share has to learn
            // whether it was, or the finally below would delete a copy the list names. A withContext whose caller was
            // cancelled meanwhile may throw on its way out instead of returning, so nothing may be decided after it.
            withContext(NonCancellable) {
                val edit = try {
                    registry.edit(folder, change)
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    // The write may have landed before its answer was lost.
                    return@withContext settleUnclearEdit(folder, shareId, exception.toSharingFailure("share $fileName")) { isKept = true }
                }
                when (edit) {
                    is SharedLinksRegistry.Edit.Done -> {
                        isKept = edit.result.shareId == shareId
                        edit.result.toShare()?.let(CreateShareResult::Shared) ?: CreateShareResult.Failed(SharingFailure.UNKNOWN)
                    }

                    // The edit writes nothing then.
                    is SharedLinksRegistry.Edit.FromNewerVersion -> CreateShareResult.Failed(SharingFailure.NEWER_VERSION)
                    // An earlier attempt's write, its answer lost, may be the very one the later attempts met.
                    SharedLinksRegistry.Edit.Contended -> settleUnclearEdit(folder, shareId, SharingFailure.UNKNOWN) { isKept = true }
                }
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            CreateShareResult.Failed(exception.toSharingFailure("share $fileName"))
        } finally {
            // A copy no list names is served by a link nobody was given, and only Stop sharing everything would find it.
            // Deleted only where the list surely does not name it: one kept because nothing could tell is such a
            // leftover at worst, which Stop sharing everything removes.
            if (isUploaded && !isKept) {
                withContext(NonCancellable) {
                    failureOf("delete the unused shared copy $shareId") {
                        folder.deleteSharedFile(shareId)
                        null
                    }
                }
            }
        }
    }

    /**
     * What a share answers after an edit that may have written [shareId]'s entry without saying so: the entry's share
     * where the list names it, and [failure] otherwise. [keep] is called where the copy has to stay: where the list
     * names it, and where the list cannot be read either, since only a copy no list names may be deleted.
     */
    private suspend fun settleUnclearEdit(folder: SyncFolder, shareId: String, failure: SharingFailure, keep: () -> Unit): CreateShareResult {
        val entry = try {
            registeredEntry(folder, shareId)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            environment.logger.log("Could not tell whether the shared links name $shareId: ${exception::class.simpleName}")
            keep()
            return CreateShareResult.Failed(failure)
        } ?: return CreateShareResult.Failed(failure)
        keep()
        return entry.toShare()?.let(CreateShareResult::Shared) ?: CreateShareResult.Failed(failure)
    }

    /**
     * The entry of [shareId] after an edit that may have written it; null where the list surely does not name it. Throws
     * where it cannot tell, a list of a later version included.
     */
    private suspend fun registeredEntry(folder: SyncFolder, shareId: String) = when (val read = registry.read(folder)) {
        is SharedLinksRegistry.Read.Readable -> read.document.links.firstOrNull { it.shareId == shareId }
        is SharedLinksRegistry.Read.FromNewerVersion -> throw IllegalStateException("The shared links are of version ${read.version}.")
    }

    override suspend fun stopSharing(shareId: String): SharingFailure? {
        val folder = connectedFolder() ?: return SharingFailure.NOT_CONNECTED
        // The copy goes first: an entry removed with its copy left behind would leave a working link that no screen shows
        // any more.
        failureOf("stop sharing $shareId") {
            folder.deleteSharedCopy(shareId)
            null
        }?.let { return it }
        return removeStopped(folder, setOf(shareId))
    }

    override suspend fun followRename(kind: SharedLinkKind, fileName: String, newFileName: String) = note("note the rename of $fileName") {
        // Naming the share where it is known keeps a new share of another item under the old name where it is.
        val shared = sharedNow().entryFor(kind, fileName)
        if (shared != null || isAccountStored()) {
            listOf(SharedLinksJournal.Entry.Rename(from = fileName, to = newFileName, shareId = shared?.shareId))
        } else {
            emptyList()
        }
    }

    override suspend fun stopSharingDeleted(kind: SharedLinkKind, fileName: String) = note("note the deletion of $fileName") {
        sharedNow().entryFor(kind, fileName)?.let { listOf(SharedLinksJournal.Entry.Stop(it.shareId)) }
            ?: if (isAccountStored()) listOf(SharedLinksJournal.Entry.StopItem(kind = kind.id, fileName = fileName)) else emptyList()
    }

    override suspend fun stopSharingEverything() = note("note the deletion of the library") {
        sharedNow().links.map { SharedLinksJournal.Entry.Stop(it.shareId) } +
            if (isAccountStored()) listOf(SharedLinksJournal.Entry.StopAll) else emptyList()
    }

    /**
     * Whether renames and deletions are noted whatever the mirror names: another device may have shared an item since
     * this one last read the list, so they are resolved against the list when applied. Only while this device holds an
     * account's credentials, which the journal is forgotten with: connected, or failing with credentials still stored.
     * A first connection that failed stores none, and what it noted would wait for the next account and stop its shares.
     * A failure to ask counts as none.
     */
    private suspend fun isAccountStored() = when (val state = stateHolder.value) {
        is SyncState.Connected -> true
        is SyncState.ConnectionFailed -> environment.logger.recovering(
            describe = { "Could not tell whether credentials are stored: ${it::class.simpleName}" },
            fallback = { false },
        ) { providers.firstOrNull { it.id == state.providerId }?.isConnected() == true }

        else -> false
    }

    /**
     * Appends what [entries] answers to the journal, then tells the account's list, without waiting for it, where an
     * account is connected. The journal comes first, so that whatever the request cannot write, offline or failing, is
     * still there for the next run.
     */
    private suspend fun note(action: String, entries: suspend () -> List<SharedLinksJournal.Entry>) {
        val isNoted = environment.logger.recovering(
            describe = { "Could not $action for the shared links: ${it::class.simpleName}" },
            fallback = { false },
        ) {
            val noted = entries()
            if (noted.isNotEmpty()) journal.append(noted)
            noted.isNotEmpty()
        }
        if (isNoted) connectedFolder()?.let(journalApplier::applySoon)
    }

    /**
     * The shares as the list will have them once the journal is applied: an item renamed while the list could not be
     * told is still shared under its new name, and its next rename must follow it too.
     */
    private suspend fun sharedNow() = pendingApplied(mirror.load().document, journal.load())

    /** [document] as applying [pending], this device's journal, will leave it. */
    private fun pendingApplied(document: SharedLinksDocument, pending: List<SharedLinksJournal.Entry>): SharedLinksDocument {
        val changes = SharedCopyChanges()
        pending.forEach { changes.take(it, document) }
        return changes.applyTo(document)
    }

    /**
     * Applies this device's journal where it holds anything, and answers why entries stay where some do: the cause
     * thrown, or [SharingFailure.UNKNOWN] where nothing was. Free while the journal is empty; otherwise it may wait for
     * a run's shared-copy step, which applies it too.
     */
    private suspend fun applyPendingJournal(folder: SyncFolder): SharingFailure? = failureOf("apply the shared links journal first") {
        if (journal.load().isEmpty()) return@failureOf null
        val failure = journalApplier.apply(folder)
        when {
            journal.load().isEmpty() -> null
            failure != null -> failure.toSharingFailure("apply the shared links journal first")
            else -> SharingFailure.UNKNOWN
        }
    }

    override suspend fun stopSharingEverythingNow(): SharingFailure? {
        val folder = connectedFolder() ?: return SharingFailure.NOT_CONNECTED
        val stopped = mutableSetOf<String>()
        val failure = failureOf("stop sharing everything") {
            // The listing finds the copies no list names; the list finds those a listing taken a moment earlier missed.
            val listed = folder.list().sharedFiles.map { it.shareId }
            val registered = when (val read = registry.read(folder)) {
                is SharedLinksRegistry.Read.Readable -> read.document.links.map { it.shareId }
                is SharedLinksRegistry.Read.FromNewerVersion -> return@failureOf SharingFailure.NEWER_VERSION
            }
            (listed + registered).distinct().forEach { shareId ->
                folder.deleteSharedCopy(shareId)
                stopped += shareId
            }
            null
        }
        if (stopped.isEmpty()) return failure
        // Also after a failure, so that the shares whose copies did go are not listed as working; a share another
        // device made meanwhile was not among them and stays.
        val removal = removeStopped(folder, stopped)
        return failure ?: removal
    }

    /**
     * Removes the entries of [stopped], shares whose copies are already deleted, from the list. Their links work no
     * more whatever happens here, so a list that cannot be edited leaves their stops to the journal, which the next run
     * (or the next share, first) finishes, and they show as stopped meanwhile: only where even the journal cannot be
     * written is the list's failure answered, and stopping again finds the copies gone and edits the list.
     */
    private suspend fun removeStopped(folder: SyncFolder, stopped: Set<String>): SharingFailure? {
        val failure = failureOf("remove the stopped shares from the list") {
            val edit = registry.edit(folder) { document ->
                SharedLinksRegistry.Change(
                    document = document.takeIf { it.links.any { entry -> entry.shareId in stopped } }
                        ?.let { it.copy(links = it.links.filterNot { entry -> entry.shareId in stopped }) },
                    result = Unit,
                )
            }
            when (edit) {
                is SharedLinksRegistry.Edit.Done -> null
                is SharedLinksRegistry.Edit.FromNewerVersion -> SharingFailure.NEWER_VERSION
                SharedLinksRegistry.Edit.Contended -> SharingFailure.UNKNOWN
            }
        } ?: return null
        return failure.takeUnless {
            environment.logger.recovering(
                describe = { "Could not note the stopped shares for the next run: ${it::class.simpleName}" },
                fallback = { false },
            ) {
                journal.append(stopped.map(SharedLinksJournal.Entry::Stop))
                true
            }
        }
    }

    /** The folder of the account on screen; an action never connects, or acts for an account that is failing. */
    private fun connectedFolder(): SyncFolder? {
        val account = (stateHolder.value as? SyncState.Connected)?.account ?: return null
        return providers.firstOrNull { it.id == account.providerId }
    }

    private suspend fun failureOf(action: String, block: suspend () -> SharingFailure?) = try {
        block()
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        exception.toSharingFailure(action)
    }

    private fun Exception.toSharingFailure(action: String): SharingFailure {
        environment.logger.log("Could not $action: ${this::class.simpleName}")
        return when (this) {
            is SyncScopeMissingException -> SharingFailure.SCOPE_MISSING
            is SyncNetworkException -> SharingFailure.NETWORK
            is SyncRemoteStorageFullException -> SharingFailure.REMOTE_STORAGE_FULL
            is SyncAuthorizationException -> SharingFailure.AUTHORIZATION
            else -> SharingFailure.UNKNOWN
        }
    }
}
