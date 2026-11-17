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
import android.content.Context
import android.content.Intent
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
 * `ACTION_VIEW` on a `FileProvider` address, under the type its name says, readable by the app that takes it. The copy
 * is moved into `cacheDir/shared` first, the one directory the provider exposes, where a share's file goes too, under a
 * name no file there has yet ([freeFileIn]): the app the last copy of that name went to may still be reading it.
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
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

/** [name] in [directory], or where it is taken, the first free one numbered before its extension as the exports number it. */
private fun freeFileIn(directory: File, name: String): File {
    val extensionStart = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
    return generateSequence(1) { it + 1 }
        .map { index -> File(directory, if (index == 1) name else name.substring(0, extensionStart) + "_" + index + name.substring(extensionStart)) }
        .first { !it.exists() }
}

private const val SHARED_DIRECTORY = "shared"
