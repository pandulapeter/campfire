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

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.webkit.CookieManager
import android.webkit.ServiceWorkerClient
import android.webkit.ServiceWorkerController
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import com.pandulapeter.campfire.data.source.remote.implementation.network.isPublicHttpsAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.coroutines.resume

/**
 * A page in a `WebView` laid out at the screen's size but attached to no window, so it is drawn as a phone would show it
 * and never seen. Touched only on the main thread, as a `WebView` must be.
 *
 * Scripts run, since most chord sites draw their song with them; files, content addresses, and geolocation stay off, and
 * nothing the page asks of a host that is not a public `https` one is fetched: its own navigations are refused in
 * [WebViewClient.shouldOverrideUrlLoading], and every other request (a script, a frame, a picture) is answered empty in
 * [WebViewClient.shouldInterceptRequest] (and a service worker's in its [ServiceWorkerClient]), `data:` and `blob:`
 * addresses aside, which go nowhere. Cookies are the app's own `WebView` jar, which nothing else in the app uses;
 * third-party ones are refused. The jar, the page's storage, and the cache are process-wide and kept on disk, so [close]
 * empties them: nothing a page left outlives its read.
 */
@SuppressLint("SetJavaScriptEnabled")
internal class WebViewBrowser private constructor(private val webView: WebView) : OffscreenBrowser {

    @Volatile
    private var isLoaded = false

    @Volatile
    private var hasFailed = false

    @Volatile
    private var isUnreachable = false

    init {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            setGeolocationEnabled(false)
            mediaPlaybackRequiresUserGesture = true
            blockNetworkImage = true
            cacheMode = WebSettings.LOAD_NO_CACHE
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)
        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = !isPublicHttpsAddress(request.url.toString())

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) = refusalOf(request)

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                isLoaded = false
            }

            override fun onPageFinished(view: WebView, url: String?) {
                isLoaded = true
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    hasFailed = true
                    isUnreachable = error.errorCode in UNREACHABLE_ERROR_CODES
                }
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                // A site's bot check answers 403 or 503 with a page that may go away by itself, so only a missing page fails.
                if (request.isForMainFrame && errorResponse.statusCode in GONE_STATUS_CODES) hasFailed = true
            }
        }
    }

    override suspend fun open(address: String) = withContext(Dispatchers.Main) {
        webView.loadUrl(address)
    }

    override suspend fun look(): PageLook = withContext(Dispatchers.Main) {
        val json = suspendCancellableCoroutine { continuation ->
            webView.evaluateJavascript(PAGE_SNAPSHOT_SCRIPT) { result -> continuation.resume(result) }
        }
        // The script's string comes back as a JSON literal of that string, "null" where it threw.
        val snapshot = json?.takeIf { it != "null" }?.let { literal -> runCatching { Json.decodeFromString<String>(literal) }.getOrNull() }
        PageLook(snapshot = PageSnapshot.decode(snapshot), isLoaded = isLoaded, hasFailed = hasFailed, isUnreachable = isUnreachable)
    }

    override suspend fun close() = withContext(Dispatchers.Main) {
        webView.stopLoading()
        webView.clearCache(true)
        webView.clearHistory()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        webView.destroy()
    }

    companion object {

        /** Set once a process, on the main thread: the controller is process-wide. */
        private var isServiceWorkerGuarded = false

        private val GONE_STATUS_CODES = setOf(404, 410)

        /**
         * The errors the network causes rather than the page: no connection (which Chromium reports as a host it could
         * not look up), a host not found, not answering, or dropping the connection, and a request that timed out.
         */
        private val UNREACHABLE_ERROR_CODES = setOf(
            WebViewClient.ERROR_HOST_LOOKUP,
            WebViewClient.ERROR_CONNECT,
            WebViewClient.ERROR_TIMEOUT,
            WebViewClient.ERROR_IO,
        )

        /** Built on the main thread and laid out at the screen's size, which is what `innerText` and responsive pages measure. */
        suspend fun create(context: Context) = withContext(Dispatchers.Main) {
            val metrics = context.resources.displayMetrics
            val webView = WebView(context)
            webView.measure(
                View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.EXACTLY),
            )
            webView.layout(0, 0, metrics.widthPixels, metrics.heightPixels)
            if (!isServiceWorkerGuarded) {
                // A service worker's requests never reach the WebViewClient, so a page's worker gets the same refusal.
                ServiceWorkerController.getInstance().setServiceWorkerClient(
                    object : ServiceWorkerClient() {
                        override fun shouldInterceptRequest(request: WebResourceRequest) = refusalOf(request)
                    },
                )
                isServiceWorkerGuarded = true
            }
            WebViewBrowser(webView)
        }
    }
}

/** Null where [request] may go out (a public `https` address, or a `data:` or `blob:` one, which go nowhere), else an empty 403. */
private fun refusalOf(request: WebResourceRequest): WebResourceResponse? {
    val scheme = request.url.scheme?.lowercase()
    return if (scheme == "data" || scheme == "blob" || isPublicHttpsAddress(request.url.toString())) {
        null
    } else {
        WebResourceResponse("text/plain", "utf-8", 403, "Refused", emptyMap(), ByteArray(0).inputStream())
    }
}
