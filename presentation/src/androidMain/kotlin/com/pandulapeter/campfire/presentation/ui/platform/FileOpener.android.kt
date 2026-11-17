/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.platform

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.pandulapeter.campfire.data.model.library.ExportedContent
import com.pandulapeter.campfire.data.model.library.ExportedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@Composable
internal actual fun rememberFileOpener(): FileOpener {
    val context = LocalContext.current.applicationContext
    return remember(context) { FileOpener { file -> open(context, file) } }
}

/**
 * `ACTION_VIEW` on a `FileProvider` address, under the type its name says, readable by the app that takes it, offered to
 * every app but this one ([withoutCampfire]). The copy is moved into `cacheDir/shared` first, the one directory the
 * provider exposes, where a share's file goes too, under a name no file there has yet ([freeFileIn]): the app the last
 * copy of that name went to may still be reading it.
 */
private suspend fun open(context: Context, file: ExportedFile): Boolean {
    val uri = withContext(Dispatchers.IO) {
        val target = freeFileIn(File(context.cacheDir, SHARED_DIRECTORY).apply { mkdirs() }, file.name)
        when (val content = file.content) {
            is ExportedContent.Bytes -> target.writeBytes(content.bytes)
            is ExportedContent.TemporaryFile -> Files.move(File(content.path).toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        FileProvider.getUriForFile(context, "${context.packageName}.files", target)
    }
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, file.mimeType)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        .withoutCampfire(context) ?: return false
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

/**
 * This intent with Campfire left out of the apps that take it, or null where no other app does. The alias that imports
 * what other apps open Campfire with takes plain text, ABC tunes, pictures, and any file sent as a byte stream, so it
 * would be offered for many attachments, and choosing it would only say there is nothing to import. One other app is
 * started directly, an app the user made the default for the type is left to the system, and several are offered in a
 * chooser without this app's components. A query that sees no app at all (package visibility narrower than the
 * manifest's `<queries>` expects) keeps the plain intent, so that this only ever takes Campfire off a list, never another
 * app.
 */
private fun Intent.withoutCampfire(context: Context): Intent? {
    val handlers = context.packageManager.activitiesFor(this)
    val (own, others) = handlers.partition { it.activityInfo.packageName == context.packageName }
    if (handlers.isEmpty() || own.isEmpty()) return this
    val default = context.packageManager.defaultActivityFor(this)?.activityInfo
    return when {
        others.isEmpty() -> null
        others.size == 1 -> Intent(this).setComponent(others.single().componentName)
        others.any { it.activityInfo.packageName == default?.packageName && it.activityInfo.name == default.name } -> this
        else -> Intent.createChooser(this, null)
            .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, own.map { it.componentName }.toTypedArray())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

private val ResolveInfo.componentName get() = ComponentName(activityInfo.packageName, activityInfo.name)

private fun PackageManager.activitiesFor(intent: Intent): List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
} else {
    @Suppress("DEPRECATION")
    queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
}

/** The activity the system would start for [intent] without asking, or its resolver where the user picked no default. */
private fun PackageManager.defaultActivityFor(intent: Intent): ResolveInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
} else {
    @Suppress("DEPRECATION")
    resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
}

/** [name] in [directory], or where it is taken, the first free one numbered before its extension as the exports number it. */
private fun freeFileIn(directory: File, name: String): File {
    val extensionStart = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
    return generateSequence(1) { it + 1 }
        .map { index -> File(directory, if (index == 1) name else name.substring(0, extensionStart) + "_" + index + name.substring(extensionStart)) }
        .first { !it.exists() }
}

private const val SHARED_DIRECTORY = "shared"
