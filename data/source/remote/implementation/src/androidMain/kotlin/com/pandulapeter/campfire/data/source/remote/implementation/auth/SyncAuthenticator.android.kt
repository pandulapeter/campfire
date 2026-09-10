package com.pandulapeter.campfire.data.source.remote.implementation.auth

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.pandulapeter.campfire.data.source.remote.api.SyncAuthenticator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.selects.select
import org.koin.core.scope.Scope

internal actual fun Scope.createSyncAuthenticator(): SyncAuthenticator = AndroidSyncAuthenticator(get<Context>().applicationContext)

/**
 * The consent page is opened in the user's own browser, and the redirect comes back as an intent on the custom
 * scheme, which the launcher activity forwards here.
 *
 * The browser rather than a WebView on purpose: a page that asks for a password must be somewhere the user can see
 * the address bar and their own saved credentials, and an embedded WebView is exactly what a phishing page would use.
 *
 * Nothing tells an app that the user backed out of a browser it launched, so waiting for the redirect alone would
 * leave a cancelled authorization waiting forever with no way back. What is observable is the app itself coming
 * forward again, which is what [awaitAbandoned] watches for.
 */
internal class AndroidSyncAuthenticator(
    private val context: Context
) : SyncAuthenticator {

    override suspend fun prepareRedirectUri() = "$REDIRECT_SCHEME://$REDIRECT_HOST"

    override suspend fun authorize(authorizationUrl: String): SyncAuthenticator.AuthorizationOutcome = coroutineScope {
        discardStaleRedirects()
        val application = context as? Application
        val hasReturned = CompletableDeferred<Unit>()
        // Registered before the browser is launched, or the app could be backgrounded before anything is watching.
        val callbacks = application?.watchForReturnToForeground(hasReturned)
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(authorizationUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            val redirect = async { redirects.receive() }
            val abandoned = async { if (application == null) awaitCancellation() else awaitAbandoned(hasReturned) }
            try {
                select {
                    redirect.onAwait { SyncAuthenticator.AuthorizationOutcome.Received(it) }
                    abandoned.onAwait { SyncAuthenticator.AuthorizationOutcome.Cancelled("The browser was closed before the service answered.") }
                }
            } finally {
                redirect.cancel()
                abandoned.cancel()
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            SyncAuthenticator.AuthorizationOutcome.Cancelled(exception.message)
        } finally {
            callbacks?.let { application?.unregisterActivityLifecycleCallbacks(it) }
        }
    }

    /** Android delivers the redirect to the running app, so there is never one waiting at start up. */
    override suspend fun consumePendingRedirect(): String? = null

    /**
     * The redirect intent brings the activity forward too, so coming back is not by itself proof that the user gave
     * up - it is only that once the redirect has had a moment to arrive and has not.
     */
    private suspend fun awaitAbandoned(hasReturned: CompletableDeferred<Unit>) {
        hasReturned.await()
        delay(REDIRECT_GRACE_MILLIS)
    }

    private fun Application.watchForReturnToForeground(hasReturned: CompletableDeferred<Unit>) =
        object : Application.ActivityLifecycleCallbacks {

            // The app has to have gone away first: the pause caused by launching the browser is what starts this.
            private var hasBeenBackgrounded = false

            override fun onActivityPaused(activity: Activity) {
                hasBeenBackgrounded = true
            }

            override fun onActivityResumed(activity: Activity) {
                if (hasBeenBackgrounded) {
                    hasReturned.complete(Unit)
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        }.also(::registerActivityLifecycleCallbacks)

    /**
     * Anything still waiting belongs to an attempt the user abandoned, and would answer this one with a stale
     * redirect, so only the authorization that is about to start can complete.
     */
    private fun discardStaleRedirects() {
        var result = redirects.tryReceive()
        while (result.isSuccess) {
            result = redirects.tryReceive()
        }
    }

    companion object {

        private val redirects = Channel<String>(Channel.CONFLATED)

        /**
         * Called by the activity that receives the redirect intent. Conflated rather than buffered: only the most
         * recent redirect can possibly be the answer to the authorization that is waiting.
         */
        fun onRedirectReceived(uri: String) {
            redirects.trySend(uri)
        }
    }
}

/** How long the redirect has to arrive after the app comes forward before the attempt counts as abandoned. */
private const val REDIRECT_GRACE_MILLIS = 750L
