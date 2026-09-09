package com.pandulapeter.campfire.data.source.local.implementation.storage.file

import org.koin.core.scope.Scope
import java.io.File

/** The place each desktop operating system expects an application to keep the data it owns. */
internal actual fun Scope.createFileStorage(): FileStorage = JvmFileStorage(desktopDataDirectory())

private fun desktopDataDirectory(): File {
    val userHome = File(System.getProperty("user.home").orEmpty())
    val operatingSystem = System.getProperty("os.name").orEmpty().lowercase()
    return when {
        operatingSystem.contains("mac") || operatingSystem.contains("darwin") -> File(userHome, "Library/Application Support/$APPLICATION_NAME")
        operatingSystem.contains("win") -> System.getenv("APPDATA").orEmpty()
            .let { if (it.isEmpty()) File(userHome, "AppData/Roaming") else File(it) }
            .let { File(it, APPLICATION_NAME) }

        else -> System.getenv("XDG_DATA_HOME").orEmpty()
            .let { if (it.isEmpty()) File(userHome, ".local/share") else File(it) }
            .let { File(it, APPLICATION_NAME.lowercase()) }
    }
}

private const val APPLICATION_NAME = "Campfire"
