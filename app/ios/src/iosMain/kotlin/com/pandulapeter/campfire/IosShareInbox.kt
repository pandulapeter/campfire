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
import com.pandulapeter.campfire.presentation.ui.platform.SharedTextPayload
import com.pandulapeter.campfire.presentation.ui.platform.decodeShareInboxManifest
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
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

/** Texts the share extension left, kept apart from files because one text is a song to make rather than a file to import. */
private val pendingSharedTexts = Channel<SharedTextPayload>(Channel.BUFFERED)

internal val sharedTextsToImport = pendingSharedTexts.receiveAsFlow()

/**
 * Called from Swift whenever the app comes to the front, and when the share extension says it has written a share while
 * the app stays on screen (an iPad's Split View). Hands every finished share in the App Group's inbox to the import,
 * oldest first, one batch each, and deletes it; the inbox's shape is the extension's `ShareInboxWriter`'s.
 *
 * Read on the same one-at-a-time scope as a file opened with the app, so that a drain asked for twice in a row reads
 * each share once, and an "Open with" and a share keep the order they came in.
 */
@Suppress("unused")
@OptIn(ExperimentalForeignApi::class)
fun drainShareInbox() {
    inboxScope.launch {
        val fileManager = NSFileManager.defaultManager
        val group = NSBundle.mainBundle.objectForInfoDictionaryKey(APP_GROUP_KEY) as? String ?: return@launch
        val inbox = fileManager.containerURLForSecurityApplicationGroupIdentifier(group)?.URLByAppendingPathComponent(INBOX_NAME) ?: return@launch
        val inboxPath = inbox.path ?: return@launch
        if (!fileManager.fileExistsAtPath(inboxPath)) return@launch
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
    if (files.isNotEmpty()) pendingImports.send(files)
    if (texts.isNotEmpty()) pendingSharedTexts.send(SharedTextPayload(texts = texts, subject = manifest.subject))
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
