/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.implementation.page

import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.model.importing.LoadedPage
import com.pandulapeter.campfire.data.source.remote.api.PageSource
import com.pandulapeter.campfire.data.source.remote.implementation.network.isPublicHttpsAddress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * A [PageSource] over a platform's web view ([newBrowser], one per page). A page is looked at every [POLL_INTERVAL] and
 * taken once it has loaded and its text has not changed for [QUIET_PERIOD], so a site that draws its song with its own
 * scripts after the load, or puts a bot check up that goes away by itself, is waited for; past [TIME_LIMIT] the last
 * readable look is taken as it is. Every look is taken as text only: nothing of the page is ever shown.
 */
internal class OffscreenPageSource(
    private val newBrowser: suspend () -> OffscreenBrowser,
    private val logger: Logger,
) : PageSource {

    override val canLoadPages = true

    override suspend fun load(address: String): LoadedPage {
        val trimmed = address.trim()
        if (!isPublicHttpsAddress(trimmed)) return LoadedPage.NotRead(LoadedPage.NotRead.Reason.UNREADABLE)
        var latest: PageSnapshot? = null
        var lastLook: PageLook? = null
        val browser = newBrowser()
        try {
            browser.open(trimmed)
            withTimeoutOrNull(TIME_LIMIT) {
                var quietFor = Duration.ZERO
                while (true) {
                    delay(POLL_INTERVAL)
                    val look = browser.look()
                    lastLook = look
                    if (look.hasFailed) {
                        // What a failed load shows is the web view's own error page, never the site's.
                        latest = null
                        break
                    }
                    val snapshot = look.snapshot ?: continue
                    quietFor = if (look.isLoaded && snapshot.isReadable && snapshot == latest) quietFor + POLL_INTERVAL else Duration.ZERO
                    latest = snapshot
                    if (quietFor >= QUIET_PERIOD) break
                }
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            logger.log("Could not read the page: ${exception::class.simpleName}")
            return LoadedPage.NotRead(LoadedPage.NotRead.Reason.UNREADABLE)
        } finally {
            withContext(NonCancellable) { browser.close() }
        }
        return latest?.takeIf { it.isReadable }?.toLoadedPage() ?: LoadedPage.NotRead(notReadReasonOf(lastLook, latest))
    }

    /**
     * Why a page with nothing readable was not read, by what the user can do about it. A failure the network caused, and a
     * page still loading with nothing of it drawn once the time is up (a connection too slow to bring it), are worth
     * trying again; a bot check, an error page, or a page that loaded with no text are not, whatever the wait.
     */
    private fun notReadReasonOf(lastLook: PageLook?, latest: PageSnapshot?) = when {
        lastLook == null -> LoadedPage.NotRead.Reason.UNREACHABLE
        lastLook.hasFailed -> if (lastLook.isUnreachable) LoadedPage.NotRead.Reason.UNREACHABLE else LoadedPage.NotRead.Reason.UNREADABLE
        latest?.isBotCheck() == true || lastLook.isLoaded -> LoadedPage.NotRead.Reason.UNREADABLE
        else -> LoadedPage.NotRead.Reason.UNREACHABLE
    }

    internal companion object {
        val POLL_INTERVAL = 500.milliseconds
        val QUIET_PERIOD = 1500.milliseconds
        val TIME_LIMIT = 20.seconds
    }
}
