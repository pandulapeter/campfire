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

import com.pandulapeter.campfire.data.model.domain.ImportBudget
import com.pandulapeter.campfire.data.model.domain.ImportedFile
import com.pandulapeter.campfire.data.source.remote.implementation.auth.isSyncRedirect
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

/**
 * Files iOS hands over, which arrive through Swift's `onOpenURL` rather than through anything Compose can see. The
 * channel is a top level one because the URL can arrive before the view controller (and with it the view model)
 * exists: a cold start caused by opening a file delivers it while the UI is still being built.
 */
private val pendingImports = Channel<List<ImportedFile>>(Channel.BUFFERED)

internal val filesToImport = pendingImports.receiveAsFlow()

/**
 * One thing at a time, in the order it was asked for. The reads are off the main thread because a file that is
 * large, or not on the device yet, would otherwise hold up the very launch it caused; they are not side by side
 * because [cleanImportInbox] must not get ahead of a read that was asked for before it.
 */
private val inboxScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

/**
 * Called from Swift for every URL iOS hands the app, which in practice means a file opened with Campfire.
 *
 * A sync redirect is answered by the `ASWebAuthenticationSession` itself and never reaches this, but one is
 * recognised and ignored anyway: read as a song it would fail, and the user would be told a file could not be
 * imported that they never tried to import.
 *
 * A file that cannot be read is passed on empty rather than dropped, so that the import counts it among the
 * skipped ones: the user asked for something, and an app that comes to the front and says nothing has not
 * answered.
 */
@Suppress("unused")
@OptIn(ExperimentalForeignApi::class)
fun openUrl(url: NSURL) {
    if (isSyncRedirect(url.absoluteString.orEmpty())) return
    inboxScope.launch {
        val file = url.readImportedFile(ImportBudget())
        pendingImports.send(listOf(file ?: ImportedFile.unread(url.lastPathComponent.orEmpty())))
        if (file != null && url.isInboxCopy()) {
            NSFileManager.defaultManager.removeItemAtURL(url, error = null)
        }
    }
}

/**
 * Called from Swift when the app goes to the background. Removes what is left in the inbox: the copy of a file
 * that could not be read, or of one the app was killed before reading.
 *
 * Not done as the app starts, which would be the obvious moment, because the file a cold start was caused by is
 * already in the inbox by then and is only handed over afterwards - there is no telling it from a leftover. By
 * the time the app leaves the screen, everything it was handed is either read or queued ahead of this.
 */
@Suppress("unused")
@OptIn(ExperimentalForeignApi::class)
fun cleanImportInbox() {
    inboxScope.launch {
        val fileManager = NSFileManager.defaultManager
        val inboxPath = inboxPath() ?: return@launch
        fileManager.contentsOfDirectoryAtPath(inboxPath, error = null)
            ?.filterIsInstance<String>()
            ?.forEach { fileManager.removeItemAtPath("$inboxPath/$it", error = null) }
    }
}

/**
 * Whether this is the copy iOS made for the app, which is the app's to delete, rather than the user's own file
 * opened in place - a song in the Files app, which may well be one in Campfire's own library folder.
 */
private fun NSURL.isInboxCopy(): Boolean {
    val inboxPath = inboxPath() ?: return false
    val path = URLByResolvingSymlinksInPath?.path ?: return false
    return path.startsWith("$inboxPath/")
}

/** `Documents/Inbox`, where iOS puts a copy of a file that comes from an app that cannot share it in place. */
@OptIn(ExperimentalForeignApi::class)
private fun inboxPath() = NSFileManager.defaultManager
    .URLForDirectory(directory = NSDocumentDirectory, inDomain = NSUserDomainMask, appropriateForURL = null, create = false, error = null)
    ?.URLByAppendingPathComponent("Inbox")
    ?.URLByResolvingSymlinksInPath
    ?.path
