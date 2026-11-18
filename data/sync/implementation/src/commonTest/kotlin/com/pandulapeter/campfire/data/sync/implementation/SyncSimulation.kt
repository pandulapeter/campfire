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

package com.pandulapeter.campfire.data.sync.implementation

import com.pandulapeter.campfire.chordpro.edit.libraryFilesNamedBy
import com.pandulapeter.campfire.data.model.library.LibraryFileKind
import com.pandulapeter.campfire.data.source.remote.api.hashing.localContentHash
import com.pandulapeter.campfire.data.model.library.LibraryFiles
import com.pandulapeter.campfire.data.model.sync.SyncDeletionDirection
import com.pandulapeter.campfire.data.model.sync.SyncDeletionPolicy
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncNetworkException
import com.pandulapeter.campfire.data.sync.implementation.run.DeletionGuard
import com.pandulapeter.campfire.data.sync.implementation.run.SyncEngine
import com.pandulapeter.campfire.data.sync.implementation.shared.registry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.yield
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Two or three devices sharing one cloud folder, driven by [seed] through [steps] random things users and the network
 * do: songs and setlists created, edited, renamed, and deleted, under names that differ only by case or Unicode form
 * too; runs on any device, some of them stopped, failed, or raced part way by a save or by another device's run. Every
 * step checks what sync promises at all times, and the end lets every device sync until nothing moves and checks that
 * they all hold one library. Odd seeds stand a service with exact names in for Dropbox. Each device's file system
 * compares names as one of the platforms' does ([PLATFORMS]), so one library meets case-sensitive and case-insensitive,
 * composing and decomposing devices at once. Items are shared and stopped too, setlists naming songs of the pool, and
 * the shared links checked ([SimulatedSharing]);
 * a device may disconnect and connect again, meeting the folder with no index. A run's first listing may be partial or
 * stale, as a service's with a paging bug or an eventually consistent one may be, and a batch deletion may be seen in
 * pieces. Songs show pictures and attach files, a device's editor may hold unsaved text naming some, a writer outside
 * Campfire may put other bytes under a picture's name in the folder, and every device's clock is off in its own way
 * ([SkewedClock]), which nothing in sync may read.
 * What it checks:
 * - no version a user saved, and nobody replaced or deleted since, is gone from every device and the folder: an edit
 *   beats a deletion, and a conflict keeps both versions;
 * - no device and no folder holds one name in two spellings;
 * - no run, faulted, raced, or over several passes, deleted more on a side than the deletion guard lets through,
 *   counting what it deleted itself against the index it started from, unless an answer waived that side;
 * - every device, settled, holds what the folder does, names compared the way the service compares them, and another
 *   round of runs moves nothing;
 * - no picture a saved song names is gone from every device, the folder, and its history, while each device deletes the
 *   pictures its own songs do not name whenever it likes, as the app's pruning does; settled, every one is back and no
 *   picture is left that no song names;
 * - no device holds a picture or an attachment whose bytes are not what its name says, and none loses one its editor's
 *   unsaved text names while that text names it.
 *
 * What a user saved is a version no other save has, so that whether it survived is a question of its bytes alone.
 */
internal class SyncSimulation(private val seed: Int, private val steps: Int) {

    private val random = Random(seed)
    private val cloud = FakeSyncProvider(ignoresCase = seed % 2 == 0, resolvesListedNames = seed % 2 != 0)

    /** The true time, a minute later at every step; no device's clock tells it. */
    private var worldTime = TEST_NOW
    private val clocks = CLOCK_OFFSETS.shuffled(random).map { SkewedClock(world = { worldTime }, offset = it) }
    private val devices = List(2 + random.nextInt(2)) { Device(naming = PLATFORMS.random(random), clock = clocks[it]) }

    /** The files each device's editor named when its unsaved text last changed and the device held them. */
    private val drafts = mutableMapOf<Device, Set<SyncKey>>()
    private val log = mutableListOf<String>()
    private val sharing = SimulatedSharing(cloud, random)

    /** Every version a user saved that no user has replaced or deleted since: what sync must never lose. */
    private val saved = mutableSetOf<String>()
    private var versions = 0
    private var fault: Fault? = null
    private var faultCountdown = 0

    /** How many writes the folder had taken when each device's last run ended, which no listing of its own goes behind. */
    private val seenWrites = mutableMapOf<Device, Int>()

    /** Whether the running step's next batch deletion stops part way for another device's run, as Dropbox's may. */
    private var deletesInPieces = false
    private var running: Job? = null
    private var runningDevice: Device? = null
    private val lostLibraries = mutableSetOf<Device>()

    /** The devices whose runs are under way, one inside another's where a fault started it. */
    private val syncing = mutableSetOf<Device>()

    /** The devices a stop is under way on. */
    private val stopping = mutableSetOf<Device>()

    /**
     * The files no promise of sync's covers any more, until a save stores one again: those that went with a lost library
     * folder and were nowhere else, and those a writer outside Campfire overwrote in the folder, whose devices' copies
     * may go meanwhile (a falsified listing takes a song away, its picture is pruned), with the folder's history of
     * overwritten versions, which the app never asks back, not modelled.
     */
    private val lostOutsideSync = mutableSetOf<String>()

    /** The device whose run makes the requests now: the one a step runs, or another whose run a fault started meanwhile. */
    private var actor: Device? = null

    /** The songs and setlists each device's run under way deleted, here and in the folder, by their folded names. */
    private val deletedByRun = mutableMapOf<Device, RunDeletions>()

    init {
        cloud.onList = { onRequest(key = null) }
        cloud.onDownload = { key ->
            onRequest(key)
            // Lets the run's other transfers go first now and then, so that they finish in another order.
            if (random.nextInt(3) == 0) yield()
        }
        cloud.onUpload = { key -> dueFault(canRunElsewhere = false)?.let { strike(it, key) } }
        cloud.onDelete = {
            dueFault(canRunElsewhere = false)?.let { strike(it, key = null) }
            // A batch seen in pieces, as Dropbox's is, never inside a run a fault started meanwhile.
            if (deletesInPieces && actor === runningDevice) {
                deletesInPieces = false
                cloud.deletionPause = FakeSyncProvider.DeletionPause(after = { 1 + random.nextInt(it - 1) }) {
                    log += "  the folder's deletion pauses part way"
                    runElsewhere()
                }
            }
        }
        cloud.onDeleted = { key -> if (key.kind.isDocument) actor?.let { deletedByRun[it]?.remote?.add(key.folded()) } }
        devices.forEach { device ->
            device.library.onDeleted = { key -> if (key.kind.isDocument) deletedByRun[device]?.local?.add(key.folded()) }
        }
        // The run's last step, bringing the shared copies and the list up to date, fails and is stopped like the rest.
        cloud.onUploadDocument = { dueFault(canRunElsewhere = false)?.let { strike(it, key = null) } }
        cloud.onUploadSharedFile = {
            dueFault(canRunElsewhere = false)?.let { strike(it, key = null) }
            // A stop on another device while this run's step uploads a copy it read the list for before the stop.
            if (actor?.isSettlingSharedCopies == true && random.nextInt(4) == 0) stopElsewhere()
        }
        cloud.onSharedFileWritten = { shareId -> sharing.onCopyWritten(shareId, actor) }
        cloud.onDeleteSharedFile = { dueFault(canRunElsewhere = false)?.let { strike(it, key = null) } }
    }

    suspend fun run(scope: TestScope) {
        try {
            repeat(steps) {
                worldTime += 1.minutes
                val device = devices.random(random)
                val roll = random.nextInt(100)
                when {
                    roll == 0 -> loseLibrary(device)
                    roll == 1 -> reconnect(device, "disconnects and connects again")
                    roll == 2 -> damageFolderFile()
                    roll < 50 -> userStep(device)
                    else -> syncStep(scope, device)
                }
                checkAlways()
            }
            log += "-- every device syncs until nothing moves"
            settle(scope)
            checkSettled()
            if (random.nextBoolean()) stopEverythingSettled()
        } catch (throwable: Throwable) {
            throw AssertionError("Seed $seed: ${throwable.message}\n${describeWorld()}", throwable)
        }
    }

    private val Device.label get() = "ABC"[devices.indexOf(this)].toString()

    /** Changes [device]'s library the way the app does: never two files whose names differ only by case or form. */
    private suspend fun userStep(device: Device) {
        if (device in lostLibraries) return
        val files = device.library.files
        // Pictures are added and deleted only with the songs naming them: a save stores them, [prune] deletes them.
        val existing = files.keys.filter { it.kind.isDocument }.sortedBy { it.path }
        // Never a sharing action from inside the device's own shared-copy step, which a save during the run may come
        // from: the app's actions wait for that step, which here would wait for them.
        val canShare = !device.isSettlingSharedCopies && device !in stopping
        when {
            canShare && existing.isNotEmpty() && random.nextInt(8) == 0 -> existing.random(random).let { key ->
                log += "${device.label} shares ${key.name}: ${sharing.share(device, key)}"
            }

            canShare && random.nextInt(14) == 0 -> stop(device)

            random.nextInt(12) == 0 -> editDraft(device)
            random.nextInt(6) == 0 -> prune(device)
            existing.isEmpty() || random.nextInt(10) < 5 -> {
                save(device, existing.takeIf { random.nextBoolean() }?.randomOrNull(random) ?: newName(device))
            }
            random.nextInt(10) == 0 -> existing.filter { random.nextInt(3) > 0 }.forEach { delete(device, it) }
            random.nextInt(3) == 0 -> delete(device, existing.random(random))
            else -> rename(device, existing.random(random))
        }
    }

    /**
     * The library folder gone from under the app, which a run cannot tell from every file deleted on purpose: the guard
     * asks before any of it reaches the folder, and the answer that keeps the folder's files is given. Nobody saves on
     * the device until a run has brought them back, since a new file would make it a library deleted all but one file.
     */
    private fun loseLibrary(device: Device) {
        // Gone with the folder may be any version the device held: one it had not sent yet, or one it had sent and
        // then moved to a name it had not sent, whose old name another device has deleted since.
        device.library.files.values.forEach { saved -= it.decodeToString() }
        // And any file the device alone held, which a song held elsewhere may name: one whose upload failed, say.
        val elsewhere = (devices.filter { it !== device }.map { it.library.files } + cloud.files.mapValues { it.value.first } + cloud.deletedFiles)
        lostOutsideSync += device.library.files.filter { (key, bytes) ->
            key.kind == LibraryFileKind.FILE && elsewhere.none { files -> files[key]?.let { isIntact(key, it) } == true }
        }.keys.map { it.name }
        device.library.files.clear()
        // The editor's text still names its files, but they went with the folder, which no promise of sync's covers.
        drafts -= device
        lostLibraries += device
        log += "${device.label} loses its library folder"
    }

    /** Stop sharing for one share, or now and then Stop sharing everything, and a disconnect while a stop is still noted. */
    private suspend fun stop(device: Device) {
        stopping += device
        log += "${device.label} " + try {
            if (random.nextInt(6) == 0) sharing.stopEverything(device) else sharing.stop(device)
        } finally {
            stopping -= device
        }
        if (sharing.hasPendingStops(device) && random.nextBoolean()) {
            reconnect(device, "disconnects with a stop still noted, and connects again")
        }
    }

    /**
     * Disconnecting [device] and connecting the same account again, which forgets the index, the mirror, and the journal,
     * a stop still noted there included, whose entry then stays in the list (an accepted edge, which the Disconnect
     * confirmation names).
     */
    private suspend fun reconnect(device: Device, described: String) {
        device.reconnect()
        log += "${device.label} $described"
    }

    /** A stop on a device other than the one whose step is uploading a copy, which waits for it, and not stopping already. */
    private suspend fun stopElsewhere() {
        val other = devices.filter { it !== actor && it !in stopping && !it.isSettlingSharedCopies }.randomOrNull(random) ?: return
        log += "  meanwhile:"
        stop(other)
    }

    /**
     * A name from the pool, or the file already holding it under another spelling, which a save then edits. A new file
     * may take another spelling than the folder's, also while the device's own run is going.
     */
    private fun newName(device: Device): SyncKey {
        val key = if (random.nextInt(4) == 0) {
            SyncKey(kind = LibraryFileKind.SETLIST, name = SETLIST_NAMES.random(random))
        } else {
            SyncKey(kind = LibraryFileKind.SONG, name = SONG_NAMES.random(random))
        }
        return device.library.files.keys.firstOrNull { it.isSameFileAs(key) } ?: key
    }

    private fun save(device: Device, key: SyncKey) {
        // A song shows a picture or attaches a file now and then, which the device then holds, as the app stores one
        // before the song naming it.
        val shown = if (key.kind == LibraryFileKind.SONG && random.nextBoolean()) PICTURES.shuffled(random).take(1 + random.nextInt(2)) else emptyList()
        val attached = if (key.kind == LibraryFileKind.SONG && random.nextInt(3) == 0) listOf(ATTACHMENTS.random(random)) else emptyList()
        (shown + attached).forEach { file ->
            if (file.key !in device.library.files) device.library.put(file.key, file.bytes)
            lostOutsideSync -= file.key.name
        }
        // A setlist names songs of the pool, in any spelling, which its shared copy then holds where the library does.
        val songs = if (key.kind == LibraryFileKind.SETLIST) SONG_NAMES.shuffled(random).take(random.nextInt(4)) else emptyList()
        val version = "v${++versions} by ${device.label}" + shown.joinToString("") { "\n{image: src=${it.key.name}}" } +
            attached.joinToString("") { "\n{meta: attachment ${it.key.name} Rehearsal take.pdf}" } + songs.joinToString("") { "\n$it" }
        device.library.put(key, version.encodeToByteArray())?.let { saved -= it.decodeToString() }
        // A save during the device's own run may come before the run lists the library, which then lists something.
        deletedByRun[device]?.listsAnything = true
        saved += version
        log += "${device.label} saves ${key.name} = $version"
    }

    /**
     * What the app's pruning does after a finished read: every picture and attachment none of this device's songs names,
     * nor its editor's unsaved text, is deleted.
     */
    private fun prune(device: Device) {
        val named = device.namedPictures() + device.heldPictures
        val unnamed = device.library.files.keys.filter { it.kind == LibraryFileKind.FILE && it.name !in named }
        unnamed.forEach { device.library.files.remove(it) }
        if (unnamed.isNotEmpty()) log += "${device.label} prunes ${unnamed.joinToString { it.name }}"
    }

    /**
     * The editor's unsaved text on [device] changes: it names some of the files the device holds, whatever its songs name
     * (a line kept while the song lost it, or a file added and not saved yet), or the text is saved or discarded.
     */
    private fun editDraft(device: Device) {
        val files = device.library.files.keys.filter { it.kind == LibraryFileKind.FILE }
        if (files.isEmpty() || random.nextInt(3) == 0) {
            discardDraft(device)
        } else {
            val named = files.filter { random.nextBoolean() }.ifEmpty { listOf(files.random(random)) }.toSet()
            device.heldPictures = named.mapTo(mutableSetOf()) { it.name }
            drafts[device] = named
            log += "${device.label}'s editor names ${named.joinToString { it.name }}"
        }
    }

    private fun discardDraft(device: Device) {
        if (device.heldPictures.isEmpty()) return
        device.heldPictures = emptySet()
        drafts -= device
        log += "${device.label}'s editor names nothing"
    }

    /**
     * Something outside Campfire writes other bytes under the name of a file in the folder, sometimes as many as the file
     * had, so that its size gives nothing away.
     */
    private fun damageFolderFile() {
        // Never the one intact copy that is sure to stay: what only the folder held, a writer outside Campfire destroys
        // for good, and a device keeps its copy only while one of its songs, or its editor, names it.
        val kept = devices.map { device -> device.library.files.filterKeys { it.name in device.namedPictures() + device.heldPictures } } + cloud.deletedFiles
        val key = cloud.files.keys.filter { key ->
            key.kind == LibraryFileKind.FILE && kept.any { files -> files[key]?.let { isIntact(key, it) } == true }
        }.randomOrNull(random) ?: return
        val bytes = cloud.files.getValue(key).first
        val damaged = if (random.nextBoolean()) bytes.reversedArray() + 0 else bytes.reversedArray()
        if (damaged.contentEquals(bytes)) return
        cloud.writeElsewhere(key, damaged)
        lostOutsideSync += key.name
        log += "the folder's ${key.name} is overwritten outside Campfire"
    }

    private fun Device.namedPictures() = library.files.filterKeys { it.kind == LibraryFileKind.SONG }.values.flatMapTo(mutableSetOf()) { namedIn(it.decodeToString()) }

    private suspend fun delete(device: Device, key: SyncKey) {
        device.library.remove(key)?.let { saved -= it.decodeToString() }
        // A deletion during the device's own run is of a file the run may have brought down, after a pass that listed
        // nothing: the next pass lists the rest, and may delete this one from the folder as the user asked.
        deletedByRun[device]?.listsAnything = true
        log += "${device.label} deletes ${key.name}"
        sharing.noteDeletion(device, key)
    }

    private suspend fun rename(device: Device, key: SyncKey) {
        val names = if (key.kind == LibraryFileKind.SONG) SONG_NAMES else SETLIST_NAMES
        val target = SyncKey(kind = key.kind, name = names.random(random))
        val files = device.library.files
        if (target == key || files.keys.any { it != key && it.isSameFileAs(target) }) return
        device.library.put(target, files.remove(key)!!)
        log += "${device.label} renames ${key.name} to ${target.name}"
        sharing.noteRename(device, from = key, to = target)
    }

    private suspend fun syncStep(scope: TestScope, device: Device) {
        if (random.nextInt(2) == 0) {
            fault = Fault.entries.random(random)
            faultCountdown = 1 + random.nextInt(5)
        }
        deletesInPieces = random.nextInt(3) == 0
        // Never with the app killed in the same run: a listing that leaves out a file the folder holds deletes it here,
        // and an index written before that deletion (the throttled writer's) makes the next run delete it from the
        // folder as this device's own deletion, the version with it. A known limit, `run/CLAUDE.md`.
        val listingFault = if (fault == Fault.KILLED) null else when (random.nextInt(LISTING_FAULT_ODDS)) {
            0 -> FakeSyncProvider.ListingFault.Partial { random.nextInt(3) == 0 } to ", the first listing partial"
            1 -> (1 + random.nextInt(4)).let {
                FakeSyncProvider.ListingFault.Stale(writesAgo = it, notBefore = seenWrites[device] ?: 0) to ", the first listing $it writes behind"
            }
            else -> null
        }
        cloud.listingFault = listingFault?.first
        val described = fault?.let { ", $it at request $faultCountdown" }.orEmpty() +
            (if (deletesInPieces) ", deleting in pieces" else "") + listingFault?.second.orEmpty()
        val result = runOn(scope, device, SyncDeletionPolicy.ASK, described)
        deletesInPieces = false
        cloud.listingFault = null
        val question = (result as? Attempt.Ended)?.result as? SyncEngine.Result.DeletionsNeedConfirmation ?: return
        if (random.nextBoolean()) {
            val answer = answers(device, question.direction).random(random)
            runOn(scope, device, answer)
        }
    }

    /** A run on [device] in a job of its own, which is what a fault stops. */
    private suspend fun runOn(scope: TestScope, device: Device, policy: SyncDeletionPolicy, described: String = ""): Attempt {
        log += "${device.label} syncs, $policy$described"
        var attempt: Attempt = Attempt.Stopped
        var violation: AssertionError? = null
        startCounting(device, policy)
        val job = scope.launch {
            attempt = try {
                Attempt.Ended(device.attempt(cloud, policy))
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Attempt.Failed(exception.message.orEmpty())
            } catch (_: ProcessKilled) {
                Attempt.Killed
            } catch (error: AssertionError) {
                // A check that failed inside the run, which the run's job would otherwise take past this simulation.
                violation = error
                Attempt.Stopped
            }
        }
        running = job
        runningDevice = device
        job.join()
        syncing -= device
        violation?.let { throw it }
        running = null
        runningDevice = null
        actor = null
        if (fault == Fault.KILLED && (attempt as? Attempt.Ended)?.result is SyncEngine.Result.Completed && random.nextBoolean()) {
            log += "  ${device.label}'s run completed, killed before writing its index"
            attempt = Attempt.Killed
        }
        if (attempt == Attempt.Killed) device.killed(random)
        sharing.onRunEnded(device, isClean = attempt.isClean(device))
        fault = null
        device.library.onRead = {}
        device.library.onWrite = {}
        device.library.onDelete = {}
        log += "  ${device.label}'s run ${attempt.described()}"
        clocks[devices.indexOf(device)].afterRun()
        seenWrites[device] = cloud.writes
        checkGuard(device)
        if ((attempt as? Attempt.Ended)?.result is SyncEngine.Result.Completed) lostLibraries -= device
        return attempt
    }

    /** A request that can suspend, about the file [key] where it is about one. */
    private suspend fun onRequest(key: SyncKey?) {
        val due = dueFault(canRunElsewhere = true) ?: return
        if (due != Fault.ANOTHER_DEVICE_SYNCS) return strike(due, key)
        runElsewhere()
    }

    /** A user step and a run on another device while a request of the running device's waits. */
    private suspend fun runElsewhere() {
        // Never a device whose run is under way, this one's or one that started this one: a device runs one sync at a
        // time. Nor one in the middle of a stop, whose listing this may be: its run's step would wait for the stop, which
        // here waits for the run.
        val other = devices.filter { it !in syncing && it !in stopping }.randomOrNull(random) ?: return
        userStep(other)
        // The names a share takes on the way, since two of these may come within one step: a rename, then a deletion.
        sharing.observe()
        log += "  meanwhile ${other.label} syncs"
        val outer = actor
        startCounting(other, SyncDeletionPolicy.ASK)
        val attempt = try {
            other.attempt(cloud, SyncDeletionPolicy.ASK).let(Attempt::Ended)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Attempt.Failed(exception.message.orEmpty())
        } finally {
            actor = outer
            syncing -= other
        }
        log += "  meanwhile ${other.label}'s run ${attempt.described()}"
        sharing.onRunEnded(other, isClean = attempt.isClean(other))
        seenWrites[other] = cloud.writes
        checkGuard(other)
        if ((attempt as? Attempt.Ended)?.result is SyncEngine.Result.Completed) lostLibraries -= other
    }

    /**
     * Counts what [device]'s run, about to start under [policy], deletes from here on. Songs and setlists only, as the
     * guard counts them: pictures follow the songs that name them.
     */
    private fun startCounting(device: Device, policy: SyncDeletionPolicy) {
        deletedByRun[device] = RunDeletions(
            listsAnything = device.library.files.keys.any { it.kind.isDocument },
            policy = policy,
        )
        actor = device
        syncing += device
        sharing.onRunStarted(device, mayListStale = cloud.listingFault != null)
    }

    /** The pending [fault] if this request is the one it waits for; a request that cannot suspend never runs another device. */
    private fun dueFault(canRunElsewhere: Boolean): Fault? {
        val pending = fault ?: return null
        if (pending == Fault.ANOTHER_DEVICE_SYNCS && !canRunElsewhere) return null
        if (--faultCountdown > 0) return null
        fault = null
        return pending
    }

    /** What [fault] does to the run, whose request is about the file [key] where it is about one. */
    private suspend fun strike(fault: Fault, key: SyncKey?) {
        if (fault != Fault.SAVE_DURING_RUN && fault != Fault.ANOTHER_DEVICE_SYNCS) sharing.onFault()
        when (fault) {
            Fault.OFFLINE -> throw SyncNetworkException("Offline")
            Fault.REFUSED -> throw IllegalStateException("Refused")
            Fault.STOPPED -> running?.cancel()
            Fault.KILLED -> if (random.nextBoolean()) {
                throw ProcessKilled()
            } else {
                runningDevice!!.library.let { library ->
                    library.onWrite = { throw ProcessKilled() }
                    library.onDelete = { throw ProcessKilled() }
                }
            }

            Fault.SAVE_DURING_RUN -> {
                // The file the request is about, as often as not, since that is where a save meets the run's own change.
                val device = runningDevice!!.takeIf { it !in lostLibraries } ?: return
                // Never a picture: nothing in the app writes over one.
                val local = key?.takeIf { it.kind.isDocument }?.let { device.library.files.keys.firstOrNull { file -> file.isSameFileAs(it) } }
                if (local != null && random.nextBoolean()) save(device, local) else userStep(device)
            }
            Fault.READ_FAILS -> runningDevice!!.library.let { library ->
                library.onRead = {
                    library.onRead = {}
                    throw IllegalStateException("Unreadable")
                }
            }

            Fault.WRITE_FAILS -> runningDevice!!.library.let { library ->
                library.onWrite = {
                    library.onWrite = {}
                    throw IllegalStateException("Disk full")
                }
            }

            Fault.ANOTHER_DEVICE_SYNCS -> Unit
        }
    }

    /** The answers a user may give, the first being the one that carries out what they did. */
    private fun answers(device: Device, direction: SyncDeletionDirection) = when {
        direction == SyncDeletionDirection.LOCAL -> listOf(SyncDeletionPolicy.DELETE_LOCALLY, SyncDeletionPolicy.KEEP_AND_UPLOAD)
        device in lostLibraries -> listOf(SyncDeletionPolicy.KEEP_AND_DOWNLOAD)
        else -> listOf(SyncDeletionPolicy.DELETE_REMOTELY, SyncDeletionPolicy.KEEP_AND_DOWNLOAD)
    }

    /**
     * [device]'s run, however it ended, deleted no more on a side its answer did not waive than the guard lets through,
     * all its passes together: more than half of the songs and setlists its index knew at any point (at least
     * [DeletionGuard.MIN_DELETIONS_TO_ASK]) or all of them asks, and so does any remote deletion from a device whose
     * library listed nothing. A file a first pass brought down counts as one the run knew, so a second pass deleting it
     * again counts as well. The index a plan is made from is never larger than the one the device reported, so a count
     * that is too many against that one was too many for the plans as well.
     */
    private fun checkGuard(device: Device) {
        val run = deletedByRun.remove(device) ?: return
        val index = device.documentIndexKeysOfLastAttempt.mapTo(mutableSetOf()) { it.folded() }
        val indexSize = index.size
        val deletedLocally = run.local.count { it in index }
        val deletedRemotely = run.remote.count { it in index }
        fun Int.isTooMany() = this > 0 && (this == indexSize || (this >= DeletionGuard.MIN_DELETIONS_TO_ASK && this * 2 > indexSize))
        if (!run.policy.waivesLocalGuard) {
            check(!deletedLocally.isTooMany()) { "${device.label}: $deletedLocally of $indexSize files deleted locally without asking" }
        }
        if (!run.policy.waivesRemoteGuard) {
            check(!deletedRemotely.isTooMany()) { "${device.label}: $deletedRemotely of $indexSize files deleted remotely without asking" }
            check(run.listsAnything || indexSize == 0 || deletedRemotely == 0) {
                "${device.label}: an empty library deleted from the folder without asking"
            }
        }
    }

    /** What holds between any two steps: no saved version is gone from everywhere, and no name is held twice. */
    private fun checkAlways() {
        val everywhere = devices.flatMap { it.library.files.values } + cloud.files.values.map { it.first }
        val held = everywhere.mapTo(mutableSetOf()) { it.decodeToString() }
        check(held.containsAll(saved)) { "Saved versions lost from every device and the folder: ${saved - held}" }
        // The folder's history counts: a picture whose only song is a conflict copy a run left on one device, unsent,
        // may be deleted everywhere else meanwhile, and that device's next completed run asks it back from there.
        // Only an intact copy counts: a damaged one in the folder is no picture of anybody's.
        val heldPictures = (devices.flatMap { it.library.files.entries } + cloud.files.mapValues { it.value.first }.entries + cloud.deletedFiles.entries)
            .filter { (key, bytes) -> key.kind == LibraryFileKind.FILE && isIntact(key, bytes) }.mapTo(mutableSetOf()) { it.key.name }
        val namedBySaved = saved.flatMapTo(mutableSetOf()) { namedIn(it) } - lostOutsideSync
        check(heldPictures.containsAll(namedBySaved)) { "Pictures saved songs name lost from every device, the folder, and its history: ${namedBySaved - heldPictures}" }
        devices.forEach { device ->
            val damaged = device.library.files.filter { (key, bytes) -> key.kind == LibraryFileKind.FILE && !isIntact(key, bytes) }.keys
            check(damaged.isEmpty()) { "${device.label} holds files whose bytes are not what their names say: ${damaged.map { it.name }}" }
            val lost = drafts[device].orEmpty().filter { it !in device.library.files }
            check(lost.isEmpty()) { "${device.label} lost files its editor's unsaved text names: ${lost.map { it.name }}" }
        }
        (devices.map { it.label to it.library.files.keys } + ("cloud" to cloud.files.keys)).forEach { (label, keys) ->
            val twice = keys.groupBy { it.folded() }.filterValues { it.size > 1 }
            check(twice.isEmpty()) { "$label holds one name in several spellings: ${twice.values}" }
        }
        sharing.observe()
    }

    /**
     * Rounds of a run on every device until one moves nothing. A question about deletions is answered by deleting: every
     * deletion here is one a user made. A library that settles does so in a few rounds; one that does not is a bug.
     */
    private suspend fun settle(scope: TestScope) {
        repeat(MAXIMUM_SETTLING_ROUNDS) {
            val before = worldState()
            devices.forEach { device ->
                discardDraft(device)
                prune(device)
                val attempt = runOn(scope, device, SyncDeletionPolicy.ASK)
                val question = (attempt as? Attempt.Ended)?.result as? SyncEngine.Result.DeletionsNeedConfirmation
                if (question != null) {
                    runOn(scope, device, answers(device, question.direction).first())
                }
                checkAlways()
            }
            if (worldState() == before) return
        }
        error("Still changing after $MAXIMUM_SETTLING_ROUNDS rounds of runs on every device")
    }

    /** Every device and the folder hold one library, names compared the way the service compares them, with every saved version in it. */
    private fun checkSettled() {
        // A damaged copy that no device holds intact stays in the folder, a failure of every run, which no device can
        // replace: nothing then tells what it should hold (a known limit, `run/CLAUDE.md`). Any device's intact copy of
        // it would differ from the folder below.
        val intact = cloud.files.filter { (key, file) -> key.kind != LibraryFileKind.FILE || isIntact(key, file.first) }
        val folder = intact.entries.associate { (key, file) -> key.folded() to file.first.decodeToString() }
        devices.forEach { device ->
            val library = device.library.files.entries.associate { (key, bytes) -> key.folded() to bytes.decodeToString() }
            check(library == folder) { "${device.label} settled on another library than the folder's" }
        }
        check(folder.values.containsAll(saved)) { "Saved versions lost: ${saved - folder.values.toSet()}" }
        val named = intact.filterKeys { it.kind == LibraryFileKind.SONG }.values.flatMapTo(mutableSetOf()) { namedIn(it.first.decodeToString()) }
        val pictures = intact.keys.filter { it.kind == LibraryFileKind.FILE }.mapTo(mutableSetOf()) { it.name }
        check(pictures.all { it in named }) { "Settled with pictures no song names: ${pictures - named}" }
        // A song may name a picture that is nowhere, where the only device holding it lost its library folder after the
        // song went up: what sync promises is every picture of a version nobody replaced, deleted, or lost.
        val namedBySaved = saved.flatMapTo(mutableSetOf()) { namedIn(it) } - lostOutsideSync
        check(pictures.containsAll(namedBySaved)) { "Settled without pictures saved songs name: ${namedBySaved - pictures}" }
        sharing.checkSettled()
    }

    /** Stop sharing everything on a settled library, with nothing failing: no copy and no share is left, leftovers included. */
    private suspend fun stopEverythingSettled() {
        val device = devices.random(random)
        log += "${device.label} " + sharing.stopEverything(device)
        check(cloud.sharedFiles.isEmpty()) { "Stop sharing everything left the copies ${cloud.sharedFiles.keys}" }
        check(cloud.registry().links.isEmpty()) { "Stop sharing everything left the shares ${cloud.registry().links.map { it.shareId }}" }
    }

    /**
     * Every library, the folder, the shares, and every index, since a picture a device prunes and its run brings down
     * again leaves the files as they were but its index knowing it, which the next round then deletes.
     */
    private fun worldState() = devices.map { it.library.files.entries.associate { (key, bytes) -> key.path to bytes.decodeToString() } } +
        devices.map { it.indexState } +
        listOf(cloud.files.entries.associate { (key, file) -> key.path to file.first.decodeToString() + "@" + file.second }) +
        listOf(sharing.state().associateWith { "" })

    private fun describeWorld() = buildString {
        appendLine("Steps:")
        log.forEach { appendLine("  $it") }
        devices.forEach { device ->
            appendLine("${device.label} (${device.library.naming}): ${device.library.files.entries.joinToString { (key, bytes) -> "${key.name}=${bytes.decodeToString()}" }}")
        }
        appendLine("Cloud: ${cloud.files.entries.joinToString { (key, file) -> "${key.name}=${file.first.decodeToString()}" }}")
        appendLine("Saved: ${saved.sorted()}")
        append(sharing.describe())
    }

    private fun SyncKey.isSameFileAs(other: SyncKey) = kind == other.kind && LibraryFiles.isSameLibraryName(name, other.name)

    private fun SyncKey.folded() = "${kind.name}/${LibraryFiles.identityKey(name)}"

    private sealed interface Attempt {
        data class Ended(val result: SyncEngine.Result) : Attempt
        data class Failed(val message: String) : Attempt
        data object Stopped : Attempt
        data object Killed : Attempt
    }

    /** Whether a run got through its shared-copy step's final edit and everything after it. */
    private fun Attempt.isClean(device: Device) = (this as? Attempt.Ended)?.result is SyncEngine.Result.Completed && device.areSharedCopiesSettled

    private fun Attempt.described() = when (this) {
        is Attempt.Ended -> when (val result = result) {
            is SyncEngine.Result.Completed -> result.summary.run {
                "completed (down $downloaded, up $uploaded, deleted here $deletedLocally, there $deletedRemotely, " +
                    "conflicts $conflicts, failed $failed)"
            }

            is SyncEngine.Result.DeletionsNeedConfirmation -> "asks before deleting ${result.count} of ${result.total} ${result.direction}"
        }

        is Attempt.Failed -> "failed: $message"
        Attempt.Stopped -> "stopped"
        Attempt.Killed -> "killed"
    }

    /**
     * What may happen to a run part way, at the request a step picks: the failures of the storage strike its next call.
     * [KILLED] strikes at the request or at the next local write or deletion, or, for a run that gets to its end first,
     * after it, before the index it ended with is written.
     */
    private enum class Fault { OFFLINE, REFUSED, STOPPED, KILLED, READ_FAILS, WRITE_FAILS, SAVE_DURING_RUN, ANOTHER_DEVICE_SYNCS }

    /**
     * The process gone, which nothing in the app gets to answer: an [Error], which no file's failure is caught as, so
     * that it ends the run where it struck, and a write it strikes before never lands.
     */
    private class ProcessKilled : Error("Killed")

    private class NamedFile(val key: SyncKey, val bytes: ByteArray)

    /**
     * A device's clock, [offset] away from the true time [world]; one whose offset is negative and grows after every run
     * steps backwards between runs, as a clock set by hand or by a network that disagrees does.
     */
    private class SkewedClock(private val world: () -> Instant, private var offset: Duration) : Clock {

        private val stepsBack = offset < Duration.ZERO && offset > -(1.days)

        override fun now() = world() + offset

        fun afterRun() {
            if (stepsBack) offset -= 1.hours
        }
    }

    /** What one run deleted so far, by folded name, and whether its library listed anything, and its answer. */
    private class RunDeletions(var listsAnything: Boolean, val policy: SyncDeletionPolicy) {
        val local = mutableSetOf<String>()
        val remote = mutableSetOf<String>()
    }

    private companion object {
        const val MAXIMUM_SETTLING_ROUNDS = 6

        /** One step in this many lists partially, and one more stale: rare enough that most runs stay ordinary. */
        const val LISTING_FAULT_ODDS = 16

        // Escaped, so that no editor or formatter folds the composed and the decomposed spelling into one.
        val SONG_NAMES = listOf(
            "song_1.cho", "song_2.cho", "song_3.cho", "song_4.cho", "song_5.cho", "song_6.cho", "song_1 (2).cho",
            "hallelujah.cho", "Hallelujah.cho", "HALLELUJAH.cho", "café.cho", "café.cho", "Café.cho", "CAFE\u0301.cho",
        )
        /**
         * The file systems a library lives on, a Mac's twice: as APFS keeps the name it was given, and as an HFS+ volume
         * keeps every name decomposed. A write through another spelling may leave the file under either spelling.
         */
        val PLATFORMS = listOf(
            FileSystemNaming.EXACT,
            FileSystemNaming.WINDOWS,
            FileSystemNaming.WINDOWS.copy(takesWrittenSpelling = true),
            FileSystemNaming.IPHONE,
            FileSystemNaming.MAC,
            FileSystemNaming.MAC.copy(takesWrittenSpelling = true),
            FileSystemNaming.MAC.copy(storesDecomposed = true),
        )
        val PICTURES = (1..4).map { number -> namedFile("picture $number", ".png") }
        val ATTACHMENTS = (1..2).map { number -> namedFile("attachment $number", ".pdf") }

        /** A day behind, a day ahead, and one stepping back an hour after every run of its device. */
        val CLOCK_OFFSETS = listOf(-(1.days), 1.days, -(1.hours))

        fun namedFile(text: String, extension: String) = text.encodeToByteArray().let { bytes ->
            NamedFile(SyncKey(kind = LibraryFileKind.FILE, name = localContentHash(bytes).take(16) + extension), bytes)
        }

        fun namedIn(text: String) = libraryFilesNamedBy(text)

        fun isIntact(key: SyncKey, bytes: ByteArray) = LibraryFiles.isLibraryFileNamedFor(key.name, localContentHash(bytes))

        val SETLIST_NAMES = listOf("gig.setlist.json", "Gig.setlist.json", "gig (2).setlist.json", "rehearsal.setlist.json")
    }
}
