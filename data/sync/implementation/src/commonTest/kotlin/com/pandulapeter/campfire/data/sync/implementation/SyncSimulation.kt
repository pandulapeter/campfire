/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation

import com.pandulapeter.campfire.data.model.library.LibraryFileKind
import com.pandulapeter.campfire.data.model.library.LibraryFiles
import com.pandulapeter.campfire.data.model.sync.SyncDeletionDirection
import com.pandulapeter.campfire.data.model.sync.SyncDeletionPolicy
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncNetworkException
import com.pandulapeter.campfire.data.sync.implementation.run.DeletionGuard
import com.pandulapeter.campfire.data.sync.implementation.run.SyncEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.yield
import kotlin.random.Random

/**
 * Two or three devices sharing one cloud folder, driven by [seed] through [steps] random things users and the network
 * do: songs and setlists created, edited, renamed, and deleted, under names that differ only by case or Unicode form
 * too; runs on any device, some of them stopped, failed, or raced part way by a save or by another device's run. Every
 * step checks what sync promises at all times, and the end lets every device sync until nothing moves and checks that
 * they all hold one library. Odd seeds stand a service with exact names in for Dropbox. What it checks:
 * - no version a user saved, and nobody replaced or deleted since, is gone from every device and the folder: an edit
 *   beats a deletion, and a conflict keeps both versions;
 * - no device and no folder holds one name in two spellings;
 * - a run nobody answered a question for deleted no more than the deletion guard lets through;
 * - every device, settled, holds what the folder does, names compared the way the service compares them, and another
 *   round of runs moves nothing.
 *
 * What a user saved is a version no other save has, so that whether it survived is a question of its bytes alone.
 */
internal class SyncSimulation(private val seed: Int, private val steps: Int) {

    private val random = Random(seed)
    private val cloud = FakeSyncProvider(ignoresCase = seed % 2 == 0, resolvesListedNames = seed % 2 != 0)
    private val devices = List(2 + random.nextInt(2)) { Device() }
    private val log = mutableListOf<String>()

    /** Every version a user saved that no user has replaced or deleted since: what sync must never lose. */
    private val saved = mutableSetOf<String>()
    private var versions = 0
    private var fault: Fault? = null
    private var faultCountdown = 0
    private var running: Job? = null
    private var runningDevice: Device? = null
    private val lostLibraries = mutableSetOf<Device>()

    init {
        cloud.onList = { onRequest(key = null) }
        cloud.onDownload = { key ->
            onRequest(key)
            // Lets the run's other transfers go first now and then, so that they finish in another order.
            if (random.nextInt(3) == 0) yield()
        }
        cloud.onUpload = { key -> dueFault(canRunElsewhere = false)?.let { strike(it, key) } }
        cloud.onDelete = { dueFault(canRunElsewhere = false)?.let { strike(it, key = null) } }
    }

    suspend fun run(scope: TestScope) {
        try {
            repeat(steps) {
                val device = devices.random(random)
                val roll = random.nextInt(100)
                when {
                    roll == 0 -> loseLibrary(device)
                    roll < 50 -> userStep(device)
                    else -> syncStep(scope, device)
                }
                checkAlways()
            }
            log += "-- every device syncs until nothing moves"
            settle(scope)
            checkSettled()
        } catch (throwable: Throwable) {
            throw AssertionError("Seed $seed: ${throwable.message}\n${describeWorld()}", throwable)
        }
    }

    private val Device.label get() = "ABC"[devices.indexOf(this)].toString()

    /** Changes [device]'s library the way the app does: never two files whose names differ only by case or form. */
    private fun userStep(device: Device) {
        if (device in lostLibraries) return
        val files = device.library.files
        val existing = files.keys.sortedBy { it.path }
        when {
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
        device.library.files.clear()
        lostLibraries += device
        log += "${device.label} loses its library folder"
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
        val version = "v${++versions} by ${device.label}"
        device.library.files.put(key, version.encodeToByteArray())?.let { saved -= it.decodeToString() }
        saved += version
        log += "${device.label} saves ${key.name} = $version"
    }

    private fun delete(device: Device, key: SyncKey) {
        device.library.files.remove(key)?.let { saved -= it.decodeToString() }
        log += "${device.label} deletes ${key.name}"
    }

    private fun rename(device: Device, key: SyncKey) {
        val names = if (key.kind == LibraryFileKind.SONG) SONG_NAMES else SETLIST_NAMES
        val target = SyncKey(kind = key.kind, name = names.random(random))
        val files = device.library.files
        if (target == key || files.keys.any { it != key && it.isSameFileAs(target) }) return
        files[target] = files.remove(key)!!
        log += "${device.label} renames ${key.name} to ${target.name}"
    }

    private suspend fun syncStep(scope: TestScope, device: Device) {
        if (random.nextInt(2) == 0) {
            fault = Fault.entries.random(random)
            faultCountdown = 1 + random.nextInt(5)
        }
        val described = fault?.let { ", $it at request $faultCountdown" }.orEmpty()
        val indexSize = device.indexSize
        val localCount = device.library.files.size
        val isPlain = fault == null
        val result = runOn(scope, device, SyncDeletionPolicy.ASK, described)
        if (isPlain && result is Attempt.Ended) checkGuard(result.result, indexSize = indexSize, localCount = localCount)
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
        val job = scope.launch {
            attempt = try {
                Attempt.Ended(device.attempt(cloud, policy))
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Attempt.Failed(exception.message.orEmpty())
            }
        }
        running = job
        runningDevice = device
        job.join()
        running = null
        runningDevice = null
        fault = null
        device.library.onRead = {}
        device.library.onWrite = {}
        log += "  ${device.label}'s run ${attempt.described()}"
        if ((attempt as? Attempt.Ended)?.result is SyncEngine.Result.Completed) lostLibraries -= device
        return attempt
    }

    /** A request that can suspend, about the file [key] where it is about one. */
    private suspend fun onRequest(key: SyncKey?) {
        val due = dueFault(canRunElsewhere = true) ?: return
        if (due != Fault.ANOTHER_DEVICE_SYNCS) return strike(due, key)
        // Never the device whose run this is: a device runs one sync at a time.
        val other = devices.filter { it !== runningDevice }.random(random)
        userStep(other)
        log += "  meanwhile ${other.label} syncs"
        val attempt = try {
            other.attempt(cloud, SyncDeletionPolicy.ASK).let(Attempt::Ended)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Attempt.Failed(exception.message.orEmpty())
        }
        log += "  meanwhile ${other.label}'s run ${attempt.described()}"
        if ((attempt as? Attempt.Ended)?.result is SyncEngine.Result.Completed) lostLibraries -= other
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
    private fun strike(fault: Fault, key: SyncKey?) {
        when (fault) {
            Fault.OFFLINE -> throw SyncNetworkException("Offline")
            Fault.REFUSED -> throw IllegalStateException("Refused")
            Fault.STOPPED -> running?.cancel()
            Fault.SAVE_DURING_RUN -> {
                // The file the request is about, as often as not, since that is where a save meets the run's own change.
                val device = runningDevice!!.takeIf { it !in lostLibraries } ?: return
                val local = key?.let { device.library.files.keys.firstOrNull { file -> file.isSameFileAs(it) } }
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
     * A run that asked nothing deleted no more on either side than the guard lets through: more than half of what the
     * index knew (at least [DeletionGuard.MIN_DELETIONS_TO_ASK]) or all of it asks, and so does any remote deletion from
     * a device whose library lists nothing. The index a plan is made from is never larger than the one the device kept,
     * so a count that is too many against that one was too many for the plan as well.
     */
    private fun checkGuard(result: SyncEngine.Result, indexSize: Int, localCount: Int) {
        val summary = (result as? SyncEngine.Result.Completed)?.summary ?: return
        fun Int.isTooMany() = this > 0 && (this == indexSize || (this >= DeletionGuard.MIN_DELETIONS_TO_ASK && this * 2 > indexSize))
        check(!summary.deletedLocally.isTooMany()) { "${summary.deletedLocally} of $indexSize files deleted locally without asking" }
        check(!summary.deletedRemotely.isTooMany()) { "${summary.deletedRemotely} of $indexSize files deleted remotely without asking" }
        check(localCount > 0 || indexSize == 0 || summary.deletedRemotely == 0) {
            "An empty library deleted from the folder without asking"
        }
    }

    /** What holds between any two steps: no saved version is gone from everywhere, and no name is held twice. */
    private fun checkAlways() {
        val everywhere = devices.flatMap { it.library.files.values } + cloud.files.values.map { it.first }
        val held = everywhere.mapTo(mutableSetOf()) { it.decodeToString() }
        check(held.containsAll(saved)) { "Saved versions lost from every device and the folder: ${saved - held}" }
        (devices.map { it.label to it.library.files.keys } + ("cloud" to cloud.files.keys)).forEach { (label, keys) ->
            val twice = keys.groupBy { it.folded() }.filterValues { it.size > 1 }
            check(twice.isEmpty()) { "$label holds one name in several spellings: ${twice.values}" }
        }
    }

    /**
     * Rounds of a run on every device until one moves nothing. A question about deletions is answered by deleting: every
     * deletion here is one a user made. A library that settles does so in a few rounds; one that does not is a bug.
     */
    private suspend fun settle(scope: TestScope) {
        repeat(MAXIMUM_SETTLING_ROUNDS) {
            val before = worldState()
            devices.forEach { device ->
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
        val folder = cloud.files.entries.associate { (key, file) -> key.folded() to file.first.decodeToString() }
        devices.forEach { device ->
            val library = device.library.files.entries.associate { (key, bytes) -> key.folded() to bytes.decodeToString() }
            check(library == folder) { "${device.label} settled on another library than the folder's" }
        }
        check(folder.values.containsAll(saved)) { "Saved versions lost: ${saved - folder.values.toSet()}" }
    }

    private fun worldState() = devices.map { it.library.files.entries.associate { (key, bytes) -> key.path to bytes.decodeToString() } } +
        listOf(cloud.files.entries.associate { (key, file) -> key.path to file.first.decodeToString() + "@" + file.second })

    private fun describeWorld() = buildString {
        appendLine("Steps:")
        log.forEach { appendLine("  $it") }
        devices.forEach { device ->
            appendLine("${device.label}: ${device.library.files.entries.joinToString { (key, bytes) -> "${key.name}=${bytes.decodeToString()}" }}")
        }
        appendLine("Cloud: ${cloud.files.entries.joinToString { (key, file) -> "${key.name}=${file.first.decodeToString()}" }}")
        append("Saved: ${saved.sorted()}")
    }

    private fun SyncKey.isSameFileAs(other: SyncKey) = kind == other.kind && LibraryFiles.isSameLibraryName(name, other.name)

    private fun SyncKey.folded() = "${kind.name}/${LibraryFiles.identityKey(name)}"

    private sealed interface Attempt {
        data class Ended(val result: SyncEngine.Result) : Attempt
        data class Failed(val message: String) : Attempt
        data object Stopped : Attempt
    }

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
    }

    /** What may happen to a run part way, at the request a step picks: the failures of the storage strike its next call. */
    private enum class Fault { OFFLINE, REFUSED, STOPPED, READ_FAILS, WRITE_FAILS, SAVE_DURING_RUN, ANOTHER_DEVICE_SYNCS }

    private companion object {
        const val MAXIMUM_SETTLING_ROUNDS = 6

        // Escaped, so that no editor or formatter folds the composed and the decomposed spelling into one.
        val SONG_NAMES = listOf(
            "song_1.cho", "song_2.cho", "song_3.cho", "song_4.cho", "song_5.cho", "song_6.cho", "song_1 (2).cho",
            "hallelujah.cho", "Hallelujah.cho", "HALLELUJAH.cho", "café.cho", "café.cho", "Café.cho",
        )
        val SETLIST_NAMES = listOf("gig.setlist.json", "Gig.setlist.json", "gig (2).setlist.json", "rehearsal.setlist.json")
    }
}
