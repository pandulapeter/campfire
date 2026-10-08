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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

/**
 * Every state of the view model and its holders is kept up to date from the moment it is created, rather than only
 * while a screen collects it, and that is the one way states are made there.
 *
 * A state that is started by its first collector hands that collector [initialValue] first and its real value a
 * moment later, and a screen answers the difference as a change: the song list's rows fade in, the "New" button
 * expands into the app bar and pushes the search action aside, a settings row is inserted while the screen is still
 * fading in, the lyrics reflow into a different number of columns. Some of the states are also acted on rather than
 * drawn - the writes build what they save out of the preferences and the setlists, leaving the editor asks whether
 * anything is unsaved, the Android shell stops the sync service when it sees no run - and those have to be right
 * whether or not a screen happens to be looking. Letting a state stop only moves the problem: one that keeps its
 * last value comes back with an answer the library may have outgrown meanwhile (a first sync fills it from the
 * settings screen), and one that forgets it comes back to [initialValue].
 *
 * What it costs is that the states doing real work - normalizing every title, artist and tag, grouping the song list,
 * matching the setlists against the library - also do it for changes to the library made while their screen is not
 * showing, which is work those screens would otherwise do the moment they were opened. Those states do it on
 * [Dispatchers.Default] (a `flowOn` before this), since the view model's scope would otherwise run it on the main thread.
 */
internal fun <T> Flow<T>.asState(scope: CoroutineScope, initialValue: T) = distinctUntilChanged().stateIn(
    scope = scope,
    started = SharingStarted.Eagerly,
    initialValue = initialValue,
)
