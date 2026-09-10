package com.pandulapeter.campfire.data.source.remote.implementation.auth

/**
 * The way in for the redirect Android delivers as an intent. Public because the activity that receives it lives in
 * `:app:android`, which is the composition root and the only place that knows about intents at all.
 *
 * @param uri The full redirect URI, `campfire://oauth?code=...`.
 */
fun onSyncRedirectReceived(uri: String) = AndroidSyncAuthenticator.onRedirectReceived(uri)

/** Whether [uri] is a redirect meant for sync rather than, say, a file someone opened with Campfire. */
fun isSyncRedirect(uri: String) = uri.startsWith("$REDIRECT_SCHEME://$REDIRECT_HOST")
