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

import com.pandulapeter.campfire.data.source.remote.implementation.network.isPublicHttpsAddress
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.Foundation.NSURLErrorCancelled
import platform.Foundation.NSURLRequest
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWebsiteDataStore
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * A page in a `WKWebView` the size of a phone's screen that is added to no window, so it is never seen. Touched only on
 * the main thread, as WebKit's views must be.
 *
 * Its data store is a non-persistent one, so no cookie, cache, or storage outlives the read. Every navigation, the
 * first one, each redirect, and each frame's, is asked of [WebKitNavigation] and refused unless it is a public `https`
 * address (a frame may also be `about:`, `data:`, or `blob:`, which go nowhere). WebKit offers no way to refuse a
 * script's or a picture's request short of a content rule list, so those are left to the page, as they are in Safari.
 */
@OptIn(ExperimentalForeignApi::class)
internal class WebKitBrowser private constructor(
    private val webView: WKWebView,
    private val navigation: WebKitNavigation,
) : OffscreenBrowser {

    override suspend fun open(address: String) = withContext(Dispatchers.Main) {
        val url = NSURL.URLWithString(address)
        if (url == null) {
            navigation.hasFailed = true
        } else {
            webView.loadRequest(NSURLRequest.requestWithURL(url))
        }
        Unit
    }

    override suspend fun look(): PageLook = withContext(Dispatchers.Main) {
        val json = suspendCancellableCoroutine { continuation ->
            webView.evaluateJavaScript(PAGE_SNAPSHOT_SCRIPT) { result, _ -> continuation.resume(result as? String) }
        }
        PageLook(snapshot = PageSnapshot.decode(json), isLoaded = navigation.isLoaded, hasFailed = navigation.hasFailed)
    }

    override suspend fun close() = withContext(Dispatchers.Main) {
        webView.stopLoading()
        webView.navigationDelegate = null
    }

    companion object {

        /** Built on the main thread; the view holds its navigation delegate weakly, so the browser holds it. */
        suspend fun create() = withContext(Dispatchers.Main) {
            val configuration = WKWebViewConfiguration().apply { websiteDataStore = WKWebsiteDataStore.nonPersistentDataStore() }
            val webView = WKWebView(frame = CGRectMake(0.0, 0.0, WIDTH, HEIGHT), configuration = configuration)
            val navigation = WebKitNavigation()
            webView.navigationDelegate = navigation
            WebKitBrowser(webView, navigation)
        }

        /** A current iPhone's screen in points, which is what a responsive page lays itself out for. */
        private const val WIDTH = 393.0
        private const val HEIGHT = 852.0
    }
}

/** Refuses every navigation that is not to a public `https` address, and follows how the page's own load is going. */
private class WebKitNavigation : NSObject(), WKNavigationDelegateProtocol {

    var isLoaded = false
    var hasFailed = false

    override fun webView(
        webView: WKWebView,
        decidePolicyForNavigationAction: WKNavigationAction,
        decisionHandler: (WKNavigationActionPolicy) -> Unit,
    ) {
        val url = decidePolicyForNavigationAction.request.URL
        val isMainFrame = decidePolicyForNavigationAction.targetFrame?.mainFrame ?: true
        val isAllowed = url?.absoluteString?.let(::isPublicHttpsAddress) == true ||
            !isMainFrame && url?.scheme?.lowercase() in FRAME_SCHEMES
        decisionHandler(if (isAllowed) WKNavigationActionPolicy.WKNavigationActionPolicyAllow else WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didStartProvisionalNavigation: WKNavigation?) {
        isLoaded = false
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
        isLoaded = true
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFailProvisionalNavigation: WKNavigation?, withError: NSError) = failed(withError)

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFailNavigation: WKNavigation?, withError: NSError) = failed(withError)

    /** A load cancelled because another took its place (a script's redirect) is no failure. */
    private fun failed(error: NSError) {
        if (error.code != NSURLErrorCancelled) hasFailed = true
    }
}

/** What a frame inside the page may load besides a public `https` address: nothing that leaves the device. */
private val FRAME_SCHEMES = setOf("about", "data", "blob")
