/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.messages

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Where the messages are drawn: at the root of the app while no sheet is open, else in the topmost open sheet. Every
 * bottom sheet is a window of its own on Android (and a layer above the app's everywhere else), so a message drawn at
 * the root while one is open would play out under it, unseen. The queue and its one [hostState] stay at the root
 * ([Messages]); only the place that draws them ([MessagesPresenter]) moves.
 */
@Stable
internal class MessagePlaces {

    val hostState = SnackbarHostState()

    /** The sheets that are open and not closing, in the order they opened, so the last one is on top. */
    private val sheets = mutableStateListOf<Any>()

    /** Where the message on screen started, which keeps drawing it while it is still open. */
    private var pinnedPlace by mutableStateOf<Any?>(null)

    fun open(sheet: Any) {
        if (sheet !in sheets) sheets.add(sheet)
    }

    fun close(sheet: Any) {
        sheets.remove(sheet)
    }

    /**
     * Whether [place] ([ROOT] or a sheet that [open]ed itself) draws the messages now. A message already on screen stays
     * where it started while that place can still draw it: a host composed anew runs its enter animation again, which
     * would show the same message twice. A place that went away (a sheet that started closing) hands it to the new top.
     */
    fun isPresentedIn(place: Any): Boolean {
        val pinned = pinnedPlace
        val presenter = if (pinned != null && (pinned === ROOT || pinned in sheets)) pinned else sheets.lastOrNull() ?: ROOT
        return presenter === place
    }

    suspend fun showSnackbar(message: String, actionLabel: String?, duration: SnackbarDuration): SnackbarResult {
        pinnedPlace = sheets.lastOrNull() ?: ROOT
        return try {
            hostState.showSnackbar(message = message, actionLabel = actionLabel, duration = duration)
        } finally {
            pinnedPlace = null
        }
    }

    companion object {

        /** The place under every sheet, above the navigation chrome. */
        val ROOT = Any()
    }
}

/** The app's [MessagePlaces], null where no app is composed around a sheet (which then draws no messages). */
internal val LocalMessagePlaces = staticCompositionLocalOf<MessagePlaces?> { null }
