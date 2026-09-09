package com.pandulapeter.campfire.data.source.local.implementation.storage.file

import android.content.Context
import org.koin.core.scope.Scope

/** The app-private `files` directory, which is backed up with the app and removed when it is uninstalled. */
internal actual fun Scope.createFileStorage(): FileStorage = JvmFileStorage(get<Context>().applicationContext.filesDir)
