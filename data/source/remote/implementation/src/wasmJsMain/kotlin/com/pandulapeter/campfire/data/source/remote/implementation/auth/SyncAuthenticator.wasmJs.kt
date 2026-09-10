package com.pandulapeter.campfire.data.source.remote.implementation.auth

import com.pandulapeter.campfire.data.source.remote.api.SyncAuthenticator
import org.koin.core.scope.Scope

internal actual fun Scope.createSyncAuthenticator(): SyncAuthenticator = WebSyncAuthenticator()

/**
 * The web is the platform the interface had to bend for: there is no second window to wait on and no scheme to be
 * called back on, so the page navigates to the consent screen and the running app ends. The answer is in the query
 * string when the app starts again, which is what [consumePendingRedirect] is for - and why the PKCE verifier is
 * kept in storage rather than in memory.
 */
internal class WebSyncAuthenticator : SyncAuthenticator {

    /** The page itself, without any query or fragment: the exact string registered with the provider. */
    override suspend fun prepareRedirectUri() = currentPageUrl()

    override suspend fun authorize(authorizationUrl: String): SyncAuthenticator.AuthorizationOutcome {
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

private fun currentPageUrl(): String = js("window.location.origin + window.location.pathname")

private fun currentPageSearch(): String = js("window.location.search")

private fun navigateTo(url: String) {
    js("window.location.assign(url)")
}

private fun clearQueryString() {
    js("window.history.replaceState(null, '', window.location.origin + window.location.pathname)")
}
