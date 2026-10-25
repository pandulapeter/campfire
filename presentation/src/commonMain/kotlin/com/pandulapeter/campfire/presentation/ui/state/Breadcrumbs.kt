/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state

import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.domain.api.library.SongFilter
import com.pandulapeter.campfire.metronome.api.model.MetronomePlayback
import com.pandulapeter.campfire.presentation.CAMPFIRE_VERSION_NAME
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogHost
import com.pandulapeter.campfire.presentation.ui.navigation.Navigator
import com.pandulapeter.campfire.presentation.ui.platform.appBuild
import com.pandulapeter.campfire.tuner.api.model.TunerListening
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.launch

/**
 * Writes into the process log (`ui/platform`) what the user did, from the few places every action passes through
 * rather than from each button, so that a report reads as the steps that led to it and a new feature is covered
 * without anybody remembering to: the screen on top whenever another arrives, every dialog, which settings changed,
 * the export format picked, the metronome and the tuner starting and stopping, imports, and the song filter. The library's writes say so
 * themselves (`MessageSink.launchLibraryChange`), as do exports and shared links.
 *
 * Kinds and names only, never what a destination, a setting, or a filter holds: a destination can carry a whole song.
 * The export format is the exception, a choice among three fixed ones that a report about an export needs.
 * Nothing of it leaves the device unless the user sends the log.
 */
internal fun CoroutineScope.logBreadcrumbs(
    navigator: Navigator,
    dialogHost: DialogHost,
    userPreferences: Flow<UserPreferences?>,
    metronomePlayback: Flow<MetronomePlayback>,
    tunerListening: Flow<TunerListening>,
    isImporting: Flow<Boolean>,
    songFilter: Flow<SongFilter>,
) {
    println("Campfire $CAMPFIRE_VERSION_NAME (${appBuild.name.lowercase()}) started")
    navigator.addOnBackStackChanged { previousTop, stack ->
        val top = stack.lastOrNull()
        if (top != null && top != previousTop) println("Screen: ${top::class.simpleName}")
    }
    dialogHost.addAfterDialogChange { _, next ->
        println(if (next == null) "Dialog closed" else "Dialog: ${next::class.simpleName}")
    }
    launch {
        // A change synced from another device reads the same, which the sync lines around it tell apart.
        userPreferences.filterNotNull().map { it.toString() }.distinctUntilChanged()
            .runningFold(null as Pair<String?, String>?) { last, text -> last?.second to text }
            .filterNotNull()
            .collect { (before, after) ->
                if (before != null) {
                    val names = changedPropertyNames(before, after)
                    println(if (names == null) "Settings changed" else "Settings changed: ${names.joinToString()}")
                }
            }
    }
    launch {
        userPreferences.filterNotNull().map { it.printSettings.format }.distinctUntilChanged().drop(1)
            .collect { println("Export format: ${it.id}") }
    }
    launch {
        metronomePlayback.map { (it is MetronomePlayback.Playing) to (it as? MetronomePlayback.Stopped)?.reason }.distinctUntilChanged().drop(1)
            .collect { (isPlaying, reason) -> println(if (isPlaying) "Metronome started" else "Metronome stopped${reason?.let { " ($it)" }.orEmpty()}") }
    }
    launch {
        tunerListening.map { (it is TunerListening.Hearing) to (it as? TunerListening.Stopped)?.reason }.distinctUntilChanged().drop(1)
            .collect { (isHearing, reason) -> println(if (isHearing) "Tuner listening" else "Tuner stopped${reason?.let { " ($it)" }.orEmpty()}") }
    }
    launch {
        isImporting.distinctUntilChanged().drop(1).collect { println(if (it) "Import started" else "Import finished") }
    }
    launch {
        songFilter.map { it.selectedTags.size + it.selectedLanguages.size }.distinctUntilChanged().drop(1)
            .collect { println("Song filter: $it selected") }
    }
}
