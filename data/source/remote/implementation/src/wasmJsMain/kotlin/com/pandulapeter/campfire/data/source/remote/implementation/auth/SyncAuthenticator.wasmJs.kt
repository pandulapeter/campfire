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

package com.pandulapeter.campfire.data.source.remote.implementation.auth

import com.pandulapeter.campfire.data.source.remote.api.SyncAuthenticator
import com.pandulapeter.campfire.data.source.remote.api.model.AuthorizationCompletionPage
import org.koin.core.annotation.Single
import kotlin.js.ExperimentalWasmJsInterop

/**
 * The web is the platform the interface had to bend for: there is no second window to wait on and no scheme to be
 * called back on, so the page navigates to the consent screen and the running app ends. The answer is in the query
 * string when the app starts again, which is what [consumePendingRedirect] is for - and why the PKCE verifier is
 * kept in storage rather than in memory.
 */
@Single
internal class WebSyncAuthenticator : SyncAuthenticator {

    /**
     * The page itself, without any query or fragment, and as the folder it is served from: the exact string registered
     * with the provider, which matches redirect URIs character for character. `…/campfire/index.html` is the same page
     * as `…/campfire/`, but not the same redirect URI.
     */
    override suspend fun prepareRedirectUri() = currentPageUrl()

    /** The page is ignored: the service redirects back to the app itself, which is the page. */
    override suspend fun authorize(
        authorizationUrl: String,
        completionPage: AuthorizationCompletionPage,
    ): SyncAuthenticator.AuthorizationOutcome {
        navigateTo(authorizationUrl)
        return SyncAuthenticator.AuthorizationOutcome.Redirected
    }

    /**
     * The query string, if it carries an authorization answer. It is taken out of the address bar as it is read, so
     * that a reload cannot replay a code that has already been spent and so that the code does not sit in the
     * user's history.
     */
    override suspend fun consumePendingRedirect(): String? {
        val search = currentPageSearch()
        if (!search.contains("code=") && !search.contains("error=")) return null
        clearQueryString()
        return currentPageUrl() + search
    }
}

/**
 * The folder the page is served from rather than the page's own address, which names the screen the app is on
 * (`…/campfire/settings/library`): the page's base, which `index.html` writes as the folder it was loaded from before
 * the app changes the address for the first time.
 */
private fun currentPageUrl(): String {
    val folder = currentPageFolder().removeSuffix("index.html")
    return currentPageOrigin() + if (folder.endsWith('/')) folder else "$folder/"
}

private fun currentPageOrigin(): String = js("window.location.origin")

private fun currentPageFolder(): String = js("new URL(document.baseURI).pathname")

private fun currentPageSearch(): String = js("window.location.search")

private fun navigateTo(url: String) {
    js("window.location.assign(url)")
}

/** Keeps the history entry's state, which is the depth the app's own history handling stamped the entry with. */
private fun clearQueryString() {
    js("window.history.replaceState(window.history.state, '', window.location.origin + window.location.pathname)")
}
