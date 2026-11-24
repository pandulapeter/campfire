/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.shortcuts

import android.content.Context
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.presentation.ui.shortcuts.LauncherShortcut
import com.pandulapeter.campfire.presentation.ui.shortcuts.LauncherShortcuts

/**
 * Hands the launcher the app's shortcuts as dynamic ones, the only kind whose labels and set can follow the app's
 * language, its Features switches, and read only mode. Each opens its app link ([appLinkIntent]).
 */
internal object LauncherShortcutPublisher {

    /** The shortcut an intent was built for, read back by [openAppLink] to report its use. */
    const val EXTRA_SHORTCUT_ID = "com.pandulapeter.campfire.extra.SHORTCUT_ID"

    /** The shortcuts last published, for [republish]. */
    @Volatile
    private var published: LauncherShortcuts? = null

    fun publish(context: Context, shortcuts: LauncherShortcuts) {
        published = shortcuts
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        // More than the activity may have is refused whole; the launcher shows the first few anyway.
        val offered = shortcuts.offered.take(manager.maxShortcutCountPerActivity)
        val offeredIds = offered.map { it.shortcut.id }
        try {
            // A shortcut pinned to the home screen outlives the list, which cannot take it away, so one no longer offered
            // is disabled, saying why, and enabled again once it is offered again: a disabled one is never updated.
            manager.enableShortcuts(offeredIds)
            manager.disableShortcuts(LauncherShortcut.entries.map { it.id } - offeredIds.toSet(), shortcuts.withdrawnMessage)
            val isPublished = manager.setDynamicShortcuts(
                offered.mapIndexed { rank, item ->
                    ShortcutInfo.Builder(context, item.shortcut.id)
                        .setShortLabel(item.label)
                        .setLongLabel(item.label)
                        .setIcon(Icon.createWithResource(context, item.shortcut.icon))
                        .setIntent(appLinkIntent(context = context, link = item.shortcut.link).putExtra(EXTRA_SHORTCUT_ID, item.shortcut.id))
                        .setRank(rank)
                        .build()
                },
            )
            // Refused when the app is publishing too often in the background, which the next change or launch answers again.
            println(if (isPublished) "Launcher shortcuts published: ${offered.size}" else "Launcher shortcuts not published: rate limited")
        } catch (exception: IllegalStateException) {
            // Refused while the user is locked, which the next change or launch answers again.
            println("Could not publish the launcher shortcuts: ${exception.message}")
        }
    }

    /**
     * Publishes the last shortcuts again, after the launcher icon has moved to another entry (`AppIconSwitcher`): a
     * dynamic shortcut belongs to the entry that was the app's main one when it was published, and the system removes
     * it with that entry.
     */
    fun republish(context: Context) {
        published?.let { publish(context = context, shortcuts = it) }
    }

    /** Tells the launcher [id] was used, which it learns from what to offer first. */
    fun reportUsed(context: Context, id: String) = context.getSystemService(ShortcutManager::class.java)?.reportShortcutUsed(id)

    private val LauncherShortcut.id get() = name.lowercase()

    private val LauncherShortcut.icon
        get() = when (this) {
            LauncherShortcut.NEW_SONG -> R.drawable.ic_shortcut_new_song
            LauncherShortcut.PASTE_SONG -> R.drawable.ic_shortcut_paste_song
            LauncherShortcut.SETLISTS -> R.drawable.ic_shortcut_setlists
            LauncherShortcut.METRONOME -> R.drawable.ic_shortcut_metronome
            LauncherShortcut.TUNER -> R.drawable.ic_shortcut_tuner
        }
}
