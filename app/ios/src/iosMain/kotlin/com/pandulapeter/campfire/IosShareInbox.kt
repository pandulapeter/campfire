/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire

import com.pandulapeter.campfire.data.model.importing.ImportBudget
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.presentation.ui.InboxShare
import com.pandulapeter.campfire.presentation.ui.platform.SharedTextPayload
import com.pandulapeter.campfire.presentation.ui.platform.decodeShareInboxManifest
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.Foundation.NSBundle
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.timeIntervalSinceNow

/**
 * Shares the share extension left, kept apart from files opened with the app because each is deleted from the inbox
 * only once the app has taken it.
 */
private val pendingShares = Channel<InboxShare>(Channel.BUFFERED)

internal val inboxSharesToImport = pendingShares.receiveAsFlow()

/**
 * Held for a whole drain. The one-at-a-time scope limits threads, not coroutines: while one drain waits for a share to
 * be taken, the next would list the same folder, not deleted yet, and hand that share over a second time.
 */
private val drainLock = Mutex()

/**
 * Called from Swift whenever the app comes to the front, and when the share extension says it has written a share while
 * the app stays on screen (an iPad's Split View). Hands the finished shares in the App Group's inbox to the import one
 * at a time, oldest first, one batch each, and deletes each once the app has taken it (see [InboxShare.taken]), so
 * that a process ended while a review or a New song form is up offers that share and the ones after it again. The
 * inbox's shape is the extension's `ShareInboxWriter`'s.
 *
 * Read on the same scope as a file opened with the app, under [drainLock], so that a drain asked for twice in a row
 * hands each share over once. An "Open with" that arrives while a share waits for its answer goes ahead of the shares
 * not yet handed over.
 */
@Suppress("unused")
@OptIn(ExperimentalForeignApi::class)
fun drainShareInbox() {
    inboxScope.launch { drainLock.withLock { drain() } }
}

@OptIn(ExperimentalForeignApi::class)
private suspend fun drain() {
    val fileManager = NSFileManager.defaultManager
    val group = NSBundle.mainBundle.objectForInfoDictionaryKey(APP_GROUP_KEY) as? String ?: return
    val inbox = fileManager.containerURLForSecurityApplicationGroupIdentifier(group)?.URLByAppendingPathComponent(INBOX_NAME) ?: return
    val inboxPath = inbox.path ?: return
    if (!fileManager.fileExistsAtPath(inboxPath)) return
    // Set by the extension as it creates the folder; again here for one an older build or a restore made.
    inbox.setResourceValue(true, NSURLIsExcludedFromBackupKey, null)
    val names = fileManager.contentsOfDirectoryAtPath(inboxPath, error = null)?.filterIsInstance<String>().orEmpty().sorted()
    names.filter { it.endsWith(PARTIAL_SUFFIX) }.forEach { name ->
        // An extension killed in the middle of a share never renames its folder. One that young may still be writing.
        val modified = fileManager.attributesOfItemAtPath("$inboxPath/$name", error = null)?.get(NSFileModificationDate) as? NSDate
        if (modified != null && -modified.timeIntervalSinceNow > ABANDONED_SHARE_SECONDS) {
            fileManager.removeItemAtPath("$inboxPath/$name", error = null)
        }
    }
    names.filterNot { it.endsWith(PARTIAL_SUFFIX) }.forEach { name -> importShare("$inboxPath/$name") }
}

@OptIn(ExperimentalForeignApi::class)
private suspend fun importShare(folderPath: String) {
    val fileManager = NSFileManager.defaultManager
    val manifest = NSString.stringWithContentsOfFile("$folderPath/$MANIFEST_NAME", NSUTF8StringEncoding, null)?.let(::decodeShareInboxManifest)
    if (manifest == null) {
        // The rename that finishes a share comes after its manifest is written, so only a failing disk gets here, and
        // nothing in what is left says what the user shared.
        println("Could not read the share in \"$folderPath\"; it is deleted.")
        fileManager.removeItemAtPath(folderPath, error = null)
        return
    }
    if (manifest.isFromNewerVersion) return
    val budget = ImportBudget()
    val files = manifest.files.mapNotNull { path ->
        val url = urlInShare(folderPath, path) ?: return@mapNotNull null
        url.readImportedFile(budget) ?: ImportedFile.unread(url.lastPathComponent.orEmpty())
    }
    val texts = manifest.texts.mapNotNull { path ->
        urlInShare(folderPath, path)?.readImportedFile(budget)?.takeIf { it.bytes.isNotEmpty() }?.bytes?.decodeToString()
    }
    if (files.isNotEmpty() || texts.isNotEmpty()) {
        val share = InboxShare(
            files = files,
            texts = texts.takeIf { files.isEmpty() }?.let { SharedTextPayload(texts = it, subject = manifest.subject) },
            taken = CompletableDeferred(),
        )
        pendingShares.send(share)
        share.taken.await()
    }
    fileManager.removeItemAtPath(folderPath, error = null)
}

/** A path of the manifest inside its share's folder, and nothing that climbs out of it. */
private fun urlInShare(folderPath: String, path: String) = path.takeUnless { it.startsWith("/") || it.split('/').contains("..") }
    ?.let { NSURL.fileURLWithPath("$folderPath/$it") }

/** The key of both `Info.plist`s that names the group, set from `APP_GROUP_ID` in `Config.xcconfig`. */
private const val APP_GROUP_KEY = "CampfireAppGroup"
private const val INBOX_NAME = "ShareInbox"
private const val PARTIAL_SUFFIX = ".partial"
private const val MANIFEST_NAME = "share.json"
private const val ABANDONED_SHARE_SECONDS = 60.0 * 60.0
