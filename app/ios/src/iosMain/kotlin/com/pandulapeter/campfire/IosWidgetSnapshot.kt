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

import com.pandulapeter.campfire.presentation.ui.widget.WidgetSnapshot
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.Foundation.NSBundle
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

/** One write at a time, in the order the snapshots came, so that an older one never lands over a newer one. */
private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

/**
 * Writes [snapshot] as `Widget/snapshot.json` in the App Group's container, where the widget extension
 * (`CampfireWidgets`) reads it, and then has [reloadTimelines] ask WidgetKit to draw the widgets again. A snapshot equal
 * to the file already there is not written, nor are the widgets reloaded: every launch hands over the library's first
 * snapshot, which is usually what the last run wrote, and WidgetKit budgets reloads.
 *
 * @param reloadTimelines Swift's `WidgetCenter.shared.reloadAllTimelines()`, which has no Objective-C API this module
 *   could call. Called on the main thread.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun publishWidgetSnapshot(snapshot: WidgetSnapshot, reloadTimelines: () -> Unit) {
    widgetScope.launch {
        val fileManager = NSFileManager.defaultManager
        val group = NSBundle.mainBundle.objectForInfoDictionaryKey(APP_GROUP_KEY) as? String ?: return@launch
        val folder = fileManager.containerURLForSecurityApplicationGroupIdentifier(group)?.URLByAppendingPathComponent(WIDGET_FOLDER) ?: return@launch
        val folderPath = folder.path ?: return@launch
        val path = "$folderPath/$SNAPSHOT_NAME"
        val json = snapshot.encode()
        if (NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null) == json) return@launch
        fileManager.createDirectoryAtPath(folderPath, withIntermediateDirectories = true, attributes = null, error = null)
        // On the folder rather than the file, which every atomic write replaces: it names every song and setlist.
        folder.setResourceValue(true, NSURLIsExcludedFromBackupKey, null)
        if (!NSString.create(string = json).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)) {
            println("The widget snapshot could not be written.")
            return@launch
        }
        println("Widget snapshot written: ${snapshot.songs.size} songs, ${snapshot.setlists.size} setlists.")
        withContext(Dispatchers.Main) { reloadTimelines() }
        println("Widget timelines reload requested.")
    }
}

private const val WIDGET_FOLDER = "Widget"
private const val SNAPSHOT_NAME = "snapshot.json"
