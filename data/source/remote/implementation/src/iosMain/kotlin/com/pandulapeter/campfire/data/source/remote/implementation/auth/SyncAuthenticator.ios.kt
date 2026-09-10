/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
@file:OptIn(BetaInteropApi::class)

package com.pandulapeter.campfire.data.source.remote.implementation.auth

import com.pandulapeter.campfire.data.source.remote.api.SyncAuthenticator
import com.pandulapeter.campfire.data.source.remote.api.model.AuthorizationCompletionPage
import kotlinx.cinterop.BetaInteropApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.koin.core.scope.Scope
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import kotlin.coroutines.resume

internal actual fun Scope.createSyncAuthenticator(): SyncAuthenticator = IosSyncAuthenticator()

/**
 * The consent page in an `ASWebAuthenticationSession`, which is the only way on iOS to find out that the user backed
 * out of it.
 *
 * Opening Safari with `openURL` and waiting for the redirect on the custom scheme also works, but only when the user
 * says yes: closing the browser tells the app nothing at all, so a cancelled authorization would sit on "waiting for
 * the browser" forever with no way back. The session hands its completion handler a `null` URL and an error instead,
 * which is what turns that dead end into a [SyncAuthenticator.AuthorizationOutcome.Cancelled].
 *
 * It is also the better thing for the user: the sheet stays inside the app, and it shares Safari's cookies, so
 * somebody already signed in to Dropbox is not asked to sign in again.
 */
internal class IosSyncAuthenticator : SyncAuthenticator {

    private val presentationContextProvider = PresentationAnchorProvider()

    override suspend fun prepareRedirectUri() = "$REDIRECT_SCHEME://$REDIRECT_HOST"

    /**
     * The session has to be created and started on the main thread, and the completion handler comes back on it.
     * Cancelling the coroutine (the "Cancel" row in Settings) dismisses the sheet through `invokeOnCancellation`.
     */
    /** The page is ignored: the browser closes itself, so nothing of Campfire's is ever rendered in it. */
    override suspend fun authorize(authorizationUrl: String, completionPage: AuthorizationCompletionPage): SyncAuthenticator.AuthorizationOutcome =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val url = NSURL.URLWithString(authorizationUrl)
                if (url == null) {
                    continuation.resume(SyncAuthenticator.AuthorizationOutcome.Cancelled("The authorization URL could not be read."))
                    return@suspendCancellableCoroutine
                }
                // The handler can only answer once, and `start` returning false must not answer a second time.
                var hasFinished = false
                val session = ASWebAuthenticationSession(
                    uRL = url,
                    callbackURLScheme = REDIRECT_SCHEME
                ) { callbackUrl, error ->
                    if (!hasFinished) {
                        hasFinished = true
                        val redirect = callbackUrl?.absoluteString
                        continuation.resume(
                            if (redirect == null) {
                                SyncAuthenticator.AuthorizationOutcome.Cancelled(error?.localizedDescription)
                            } else {
                                SyncAuthenticator.AuthorizationOutcome.Received(redirect)
                            }
                        )
                    }
                }
                session.presentationContextProvider = presentationContextProvider
                continuation.invokeOnCancellation { session.cancel() }
                if (!session.start() && !hasFinished) {
                    hasFinished = true
                    continuation.resume(SyncAuthenticator.AuthorizationOutcome.Cancelled("The consent page could not be opened."))
                }
            }
        }

    /** The session answers the app directly, so there is never a redirect waiting at start up. */
    override suspend fun consumePendingRedirect(): String? = null
}

/** The window the sheet is presented from, which is the one thing `ASWebAuthenticationSession` cannot work out itself. */
private class PresentationAnchorProvider : NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {

    @Suppress("DEPRECATION")
    override fun presentationAnchorForWebAuthenticationSession(session: ASWebAuthenticationSession): ASPresentationAnchor =
        UIApplication.sharedApplication.keyWindow ?: UIWindow()
}
