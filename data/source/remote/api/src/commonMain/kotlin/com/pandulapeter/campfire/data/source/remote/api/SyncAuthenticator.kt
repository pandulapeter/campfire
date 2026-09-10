package com.pandulapeter.campfire.data.source.remote.api

import com.pandulapeter.campfire.data.source.remote.api.model.AuthorizationCompletionPage

/**
 * How a platform gets the user through the service's consent page and back.
 *
 * The four platforms do this in genuinely different ways, and one of them cannot return a value at all: the web
 * navigates away from the running app, so the answer arrives at the *next* start. That is why the result is an
 * outcome rather than a URL, and why [consumePendingRedirect] exists - modelling the awkward case in the interface
 * keeps it from becoming a special case in the sync engine.
 */
interface SyncAuthenticator {

    /**
     * Opens whatever this platform needs in order to be redirected to, and returns the URI the service has to send
     * the user back to. Null where the platform cannot receive a redirect at all, which asks the provider for its
     * copy-a-code flow instead.
     *
     * Separate from [authorize] because the redirect URI has to be part of the authorization URL, and on the desktop
     * it is not known until a socket has been opened to receive it.
     */
    suspend fun prepareRedirectUri(): String?

    /**
     * Opens [authorizationUrl] and waits for the service to send the user back, where the platform can wait.
     *
     * @param completionPage What to tell the user once the service has redirected. Only the desktop shows anything:
     *   it answers the browser itself and has to put words on that page. Everywhere else the browser closes.
     */
    suspend fun authorize(authorizationUrl: String, completionPage: AuthorizationCompletionPage): AuthorizationOutcome

    /**
     * A redirect this platform received while the app was not running, consumed exactly once. Only the web ever
     * returns anything; every other platform answers null.
     */
    suspend fun consumePendingRedirect(): String?

    sealed interface AuthorizationOutcome {

        /** The browser came back, and this is the redirect URI it came back with. */
        data class Received(val redirectUri: String) : AuthorizationOutcome

        /** The app navigated away. Whatever happens next happens after a restart, see [consumePendingRedirect]. */
        data object Redirected : AuthorizationOutcome

        /** The user closed the browser, or the service said no. */
        data class Cancelled(val message: String? = null) : AuthorizationOutcome
    }
}
