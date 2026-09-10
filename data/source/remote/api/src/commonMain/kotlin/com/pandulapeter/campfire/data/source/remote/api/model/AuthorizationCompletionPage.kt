package com.pandulapeter.campfire.data.source.remote.api.model

/**
 * What the page the browser lands on after consent should say.
 *
 * It exists because of the desktop, which has no custom scheme to be redirected to and so answers the browser with
 * a page of its own. That page is the only piece of Campfire's own text that is rendered outside the app, and the
 * data layer can see neither the translations nor the language the user picked - so the words are handed down from
 * the UI, like any other string the user reads.
 *
 * Every other platform ignores this: their browser closes itself.
 */
data class AuthorizationCompletionPage(
    val title: String,
    val message: String
)
