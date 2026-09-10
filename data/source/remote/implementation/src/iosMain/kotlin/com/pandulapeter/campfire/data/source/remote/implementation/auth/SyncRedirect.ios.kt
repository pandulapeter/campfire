package com.pandulapeter.campfire.data.source.remote.implementation.auth

/**
 * Whether [uri] is a sync redirect rather than a file someone opened with Campfire.
 *
 * Nothing is expected to arrive: `ASWebAuthenticationSession` answers the app directly rather than through
 * `onOpenURL`. It is still worth recognising one, so that a URL on this scheme is quietly ignored instead of being
 * read as a song and reported to the user as a file that could not be imported.
 */
fun isSyncRedirect(uri: String) = uri.startsWith("$REDIRECT_SCHEME://$REDIRECT_HOST")
