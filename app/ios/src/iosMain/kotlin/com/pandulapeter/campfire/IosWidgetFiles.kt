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

import com.pandulapeter.campfire.presentation.ui.widget.WidgetFiles
import com.pandulapeter.campfire.presentation.ui.widget.WidgetSnapshot
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

/**
 * `Widget/` in the App Group's container, where the widget extension (`CampfireWidgets`) reads the snapshot
 * (`snapshot.json`) and the cover thumbnails its songs name (`covers/`). Excluded from backups as a folder, rather than
 * file by file, since every atomic write replaces a file: it names every song and setlist.
 *
 * @param reloadTimelines Swift's `WidgetCenter.shared.reloadAllTimelines()`, which has no Objective-C API this module
 *   could call. Called on the main thread.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class IosWidgetFiles(private val reloadTimelines: () -> Unit) : WidgetFiles {

    /**
     * A snapshot equal to the file already there is not written, nor are the widgets reloaded: every launch hands over
     * the library's first snapshot, which is usually what the last run wrote, and WidgetKit budgets reloads. The write is
     * the process's, one at a time in the order they came, so that an older one never lands over a newer one.
     */
    override suspend fun writeSnapshot(snapshot: WidgetSnapshot) = widgetScope.launch {
        val folder = folder() ?: return@launch
        val folderPath = folder.path ?: return@launch
        val path = "$folderPath/$SNAPSHOT_NAME"
        val json = snapshot.encode()
        if (NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null) == json) return@launch
        if (!createFolder(folder)) return@launch
        if (!NSString.create(string = json).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)) {
            println("The widget snapshot could not be written.")
            return@launch
        }
        println("Widget snapshot written: ${snapshot.songs.size} songs, ${snapshot.setlists.size} setlists.")
        withContext(Dispatchers.Main) { reloadTimelines() }
        println("Widget timelines reload requested.")
    }.join()

    override suspend fun coverNames() = widgetScope.async {
        val path = folder()?.URLByAppendingPathComponent(COVERS_FOLDER)?.path ?: return@async emptySet()
        NSFileManager.defaultManager.contentsOfDirectoryAtPath(path, null)?.filterIsInstance<String>()?.toSet().orEmpty()
    }.await()

    override suspend fun writeCover(name: String, bytes: ByteArray) = widgetScope.async {
        val folder = folder() ?: return@async false
        val covers = folder.URLByAppendingPathComponent(COVERS_FOLDER) ?: return@async false
        val path = covers.path ?: return@async false
        if (!createFolder(folder)) return@async false
        NSFileManager.defaultManager.createDirectoryAtPath(path, withIntermediateDirectories = true, attributes = null, error = null)
        val data = if (bytes.isEmpty()) NSData() else bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
        data.writeToFile("$path/$name", atomically = true).also { if (!it) println("A widget cover could not be written.") }
    }.await()

    override suspend fun deleteCover(name: String) = widgetScope.async {
        val path = folder()?.URLByAppendingPathComponent(COVERS_FOLDER)?.path ?: return@async
        NSFileManager.defaultManager.removeItemAtPath("$path/$name", null)
        Unit
    }.await()

    private fun folder(): NSURL? {
        val group = NSBundle.mainBundle.objectForInfoDictionaryKey(APP_GROUP_KEY) as? String ?: return null
        return NSFileManager.defaultManager.containerURLForSecurityApplicationGroupIdentifier(group)?.URLByAppendingPathComponent(WIDGET_FOLDER)
    }

    private fun createFolder(folder: NSURL): Boolean {
        val path = folder.path ?: return false
        NSFileManager.defaultManager.createDirectoryAtPath(path, withIntermediateDirectories = true, attributes = null, error = null)
        folder.setResourceValue(true, NSURLIsExcludedFromBackupKey, null)
        return true
    }

    private companion object {
        /** One write at a time, in the order they came, which is the order [WidgetPublisher] relies on. */
        val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
        const val WIDGET_FOLDER = "Widget"
        const val SNAPSHOT_NAME = "snapshot.json"
        const val COVERS_FOLDER = "covers"
    }
}
