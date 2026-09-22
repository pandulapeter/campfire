/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
@file:OptIn(ExperimentalWasmJsInterop::class)

package com.pandulapeter.campfire.presentation.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import kotlinx.browser.window
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.w3c.dom.events.Event
import kotlin.js.ExperimentalWasmJsInterop

/**
 * Opens the app on the address it was loaded from, see [BrowserRoutes]. Called while the web shell is first composed,
 * which is what [CampfireViewModel.navigateOnLaunch] asks of its caller; an address that names nothing the library
 * holds opens the songs, and [BrowserHistoryEffect] then writes the address of the songs over it.
 */
internal fun CampfireViewModel.navigateToBrowserAddress() {
    val path = currentBrowserPath()
    if (path.isNullOrEmpty()) return
    navigateOnLaunch { songs, setlists -> BrowserRoutes.resolve(path = path, songs = songs, setlists = setlists, current = navigationState) }
}

/**
 * Keeps the browser's history in step with the app's own navigation, in both directions.
 *
 * **The app decides, and the history follows.** The entries the history should hold are a function of the app's
 * state ([BrowserRoutes.paths]), and whenever that changes the entries are made to match it: one level deeper is a
 * `pushState`, the same depth a `replaceState` (a tab, a song paged to in a setlist, a renamed file), and a shallower
 * one a `history.go` back to that depth, whose `popstate` is waited for before the entry it lands on is corrected.
 * Every entry is stamped with its depth, which is how a `popstate` says where the browser went.
 *
 * **The browser's Back is the app's own back.** A `popstate` to a shallower entry is not taken as an address to show:
 * it is sent into the navigation event dispatcher, exactly like Escape and the system gesture of the other platforms,
 * and whatever answers it answers it - a dialog, a menu, a sheet, a search, the editor asking about unsaved text, or
 * the back stack. The history is then made to match what the app did, so a back that only closed a dialog, or that
 * the editor turned into a question, puts the entry the browser had already left back on top. A Back that skipped
 * several entries at once (the long press menu) is taken one step at a time, stopping at the first that did not
 * simply leave a screen.
 *
 * **Forward is a return to where the user was.** The state of the app is recorded with each entry as it is left, and
 * a `popstate` to a deeper entry puts it back, validated against the library since then ([BrowserRoutes.validate]);
 * an entry the app knows nothing about is opened from its address instead, and one that cannot be is left again.
 *
 * Only the entries this page wrote are ever traversed. The page starts as the bottom entry of its own however deep in
 * the tab's history it was loaded, because the entries under it belong to documents that are gone - a reload leaves
 * the ones the previous document wrote behind it - and stepping into one of those reloads the app.
 *
 * Nothing starts before [CampfireViewModel.isLaunchNavigationPending] has settled, or the address the page was opened
 * on would be written over with the songs a moment before the app got to open it.
 */
@Composable
internal fun BrowserHistoryEffect(viewModel: CampfireViewModel) {
    val dispatcher = LocalNavigationEventDispatcherOwner.current?.navigationEventDispatcher ?: return
    LaunchedEffect(viewModel, dispatcher) {
        viewModel.isLaunchNavigationPending.first { !it }
        val input = DirectNavigationEventInput()
        val events = Channel<HistoryEvent>(Channel.UNLIMITED)
        // Read as the event arrives: the events are handled one at a time, and by the time this one's turn comes the
        // browser may have moved on.
        val listener: (Event) -> Unit = { events.trySend(HistoryEvent.Traversed(depth = currentHistoryDepth(), path = currentBrowserPath())) }
        dispatcher.addInput(input)
        window.addEventListener(EVENT_POP_STATE, listener)
        try {
            launch {
                // The searches are flows rather than snapshot states, so they are combined in rather than read.
                combine(snapshotFlow { BrowserRoutes.paths(viewModel) }, viewModel.songsSearch.isOpen, viewModel.setlistsSearch.isOpen) { _, _, _ -> BrowserRoutes.paths(viewModel) }
                    .distinctUntilChanged()
                    .collect { events.send(HistoryEvent.Changed) }
            }
            BrowserHistory(viewModel = viewModel, input = input, events = events).run()
        } finally {
            window.removeEventListener(EVENT_POP_STATE, listener)
            dispatcher.removeInput(input)
        }
    }
}

private sealed interface HistoryEvent {

    /** Something the history is made of has changed in the app. Carries nothing: the state is read when it is handled. */
    data object Changed : HistoryEvent

    /**
     * The browser moved to another entry of this page, by the user or by [goInHistory].
     *
     * @param depth The depth the entry was stamped with, or -1 for one the app did not write.
     */
    data class Traversed(val depth: Int, val path: String?) : HistoryEvent
}

/**
 * One entry of the history this page wrote, as far as the app knows it.
 *
 * @param path The address the entry holds, or null where it is not known.
 * @param state Where the app was when the entry was last on top, which is what Forward goes back to. Null for the
 *   entries that were written in passing, several at a time, and never shown.
 */
private data class Entry(
    val path: String?,
    val state: NavigationState? = null,
)

private class BrowserHistory(
    private val viewModel: CampfireViewModel,
    private val input: DirectNavigationEventInput,
    private val events: Channel<HistoryEvent>,
) {
    private val entries = mutableListOf<Entry>()
    private var current = 0

    suspend fun run() {
        // The entry keeps its address, query string included: an authorization answer is still waiting in it to be
        // read by the sync authenticator, which takes it out itself.
        stampHistoryEntry(0)
        entries += Entry(path = currentBrowserPath())
        synchronize()
        for (event in events) {
            when (event) {
                HistoryEvent.Changed -> synchronize()
                is HistoryEvent.Traversed -> onTraversed(event)
            }
        }
    }

    private suspend fun onTraversed(event: HistoryEvent.Traversed) {
        val depth = event.depth
        when {
            // An entry of this document the app did not write, which only a changed fragment makes. It is taken to be
            // the one the app is on, and gets that one's address.
            depth < 0 -> {
                stampHistoryEntry(current)
                entries[current] = entries[current].copy(path = null)
                synchronize()
            }

            depth < current -> goBack(to = depth, path = event.path)
            depth > current -> goForward(to = depth, path = event.path)
            else -> synchronize()
        }
    }

    private suspend fun goBack(to: Int, path: String?) {
        val steps = current - to
        current = to
        entries[current] = entries[current].copy(path = path)
        for (step in 1..steps) {
            val depth = BrowserRoutes.paths(viewModel).size
            input.backCompleted()
            if (step == steps) break
            // The next back has to reach whatever is on top once this one has been answered, and the handlers are
            // registered by the composition: two frames, since the first one resumes while it is still being built.
            repeat(2) { withFrameNanos { } }
            if (BrowserRoutes.paths(viewModel).size != depth - 1) break
        }
        synchronize()
    }

    private suspend fun goForward(to: Int, path: String?) {
        val entry = entries.getOrNull(to)
        val songs = viewModel.allSongs.value
        val setlists = viewModel.setlists.value
        val state = entry?.state?.let { BrowserRoutes.validate(state = it, songs = songs, setlists = setlists) }
            ?: (path ?: entry?.path)?.let { BrowserRoutes.resolve(path = it, songs = songs, setlists = setlists, current = viewModel.navigationState) }
        // Refused or not, the browser is on that entry now: if the app stays where it was, synchronizing takes the
        // browser back to it.
        while (entries.size <= to) entries += Entry(path = null)
        current = to
        entries[current] = entries[current].copy(path = path)
        if (state != null) viewModel.restoreNavigationState(state)
        synchronize()
    }

    /**
     * Makes the history match [BrowserRoutes.paths]. Only the entry on top and the ones above it are written: the ones
     * under it cannot be changed without going there, and they are corrected when they are gone back to - which is
     * where the address of a file renamed further down the stack is brought up to date.
     *
     * Entries above the current one that already hold the addresses wanted are gone forward to rather than written
     * again, which is what happens after a Back the app answered without leaving anything (a dialog closed, the
     * editor's question asked): Chrome marks an entry that added another without a user gesture as one its Back
     * button skips, so pushing the entry that was just left would have the next Back jump over the one under it -
     * off the page, where nothing was pushed from under it. A traversal adds nothing and marks nothing.
     */
    private suspend fun synchronize() {
        repeat(MAX_TRAVERSALS) {
            val paths = BrowserRoutes.paths(viewModel)
            val target = paths.lastIndex
            val canGoForward = target > current &&
                    entries[current].path == paths[current] &&
                    (current + 1..target).all { entries.getOrNull(it)?.path == paths[it] }
            if (target < current || canGoForward) {
                // The entry being left keeps the state it was recorded with when it was last on top: the app has
                // already moved on, and what it is showing now belongs to the entry the browser is taken to.
                if (traverse(target - current)) return@repeat
                if (target < current) return
            }
            if (entries[current].path != paths[current]) {
                replaceHistoryEntry(depth = current, path = paths[current])
                // The entries above keep their addresses in the browser, but not what they were recorded as: a
                // Forward to one of them is opened from its address.
                for (depth in current + 1..entries.lastIndex) entries[depth] = entries[depth].copy(state = null)
            }
            entries[current] = Entry(path = paths[current])
            if (target > current) {
                entries.subList(current + 1, entries.size).clear()
                for (depth in current + 1..target) {
                    pushHistoryEntry(depth = depth, path = paths[depth])
                    entries += Entry(path = paths[depth])
                }
                current = target
            }
            entries[current] = entries[current].copy(state = viewModel.navigationState)
            return
        }
    }

    /**
     * Moves the browser by [delta] entries and waits for it to get there, answering whether it moved at all. The
     * browser says where it went; without an answer, the stamp of the entry it is on says it instead.
     */
    private suspend fun traverse(delta: Int): Boolean {
        goInHistory(delta)
        val arrival = awaitTraversal()
        val depth = (arrival?.depth ?: currentHistoryDepth()).takeIf { it >= 0 } ?: return false
        if (depth == current) return false
        while (entries.size <= depth) entries += Entry(path = null)
        current = depth
        entries[current] = entries[current].copy(path = arrival?.path ?: currentBrowserPath())
        return true
    }

    /**
     * The `popstate` of a traversal this class asked for. Changes of the app that arrive meanwhile are dropped, since
     * the synchronization that is waiting reads the state again anyway. A browser that never answers - a traversal
     * past the end of the history is ignored without a word - is given up on rather than waited for forever.
     */
    private suspend fun awaitTraversal() = withTimeoutOrNull(TRAVERSAL_TIMEOUT_MILLIS) {
        var traversal: HistoryEvent.Traversed? = null
        while (traversal == null) {
            traversal = events.receive() as? HistoryEvent.Traversed
        }
        traversal
    }
}

/**
 * The depth the app stamped the current history entry with, or -1 for an entry it did not write.
 */
private fun currentHistoryDepth(): Int = js("(history.state && typeof history.state.campfireDepth === 'number') ? history.state.campfireDepth : -1")

/**
 * The address of the current history entry relative to the folder the page is served from (the `<base>` index.html
 * writes), still percent-encoded, or null for one outside it.
 */
private fun currentBrowserPath(): String? = js(
    """(function () {
        var base = new URL(document.baseURI).pathname;
        return location.pathname.indexOf(base) === 0 ? location.pathname.substring(base.length) : null;
    })()"""
)

/**
 * Stamps the current entry with [depth] and leaves its address alone. Every write to the history is wrapped: Safari
 * throws once a page has written it too often in a short while, and an address that did not change is no reason for
 * the app to stop.
 */
private fun stampHistoryEntry(depth: Int) {
    js(
        """(function () {
            try {
                history.replaceState({ campfireDepth: depth }, '', location.href);
            } catch (error) {
                console.warn('Could not stamp the history entry: ' + error);
            }
        })()"""
    )
}

/** Keeps the query string, which only ever holds an authorization answer the sync authenticator has yet to take. */
private fun replaceHistoryEntry(depth: Int, path: String) {
    js(
        """(function () {
            try {
                history.replaceState({ campfireDepth: depth }, '', new URL(path || './', document.baseURI).pathname + location.search);
            } catch (error) {
                console.warn('Could not replace the history entry: ' + error);
            }
        })()"""
    )
}

private fun pushHistoryEntry(depth: Int, path: String) {
    js(
        """(function () {
            try {
                history.pushState({ campfireDepth: depth }, '', new URL(path || './', document.baseURI).pathname);
            } catch (error) {
                console.warn('Could not add a history entry: ' + error);
            }
        })()"""
    )
}

private fun goInHistory(delta: Int) {
    js("history.go(delta)")
}

private const val EVENT_POP_STATE = "popstate"
private const val MAX_TRAVERSALS = 3
private const val TRAVERSAL_TIMEOUT_MILLIS = 1000L
