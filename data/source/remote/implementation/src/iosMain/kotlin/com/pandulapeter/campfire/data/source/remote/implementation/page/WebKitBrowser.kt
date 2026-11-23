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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSURLErrorCancelled
import platform.Foundation.NSURLErrorCannotConnectToHost
import platform.Foundation.NSURLErrorCannotFindHost
import platform.Foundation.NSURLErrorDNSLookupFailed
import platform.Foundation.NSURLErrorDataNotAllowed
import platform.Foundation.NSURLErrorDomain
import platform.Foundation.NSURLErrorInternationalRoamingOff
import platform.Foundation.NSURLErrorNetworkConnectionLost
import platform.Foundation.NSURLErrorNotConnectedToInternet
import platform.Foundation.NSURLErrorTimedOut
import platform.Foundation.NSURLRequest
import platform.Foundation.NSUserDomainMask
import platform.WebKit.WKContentRuleList
import platform.WebKit.WKContentRuleListStore
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
 * address (a frame may also be `about:`, `data:`, or `blob:`, which go nowhere). A content rule list, WebKit's only way
 * to refuse a subresource, refuses pictures, media, `http:`, and `ws:`, so a read for the text spends no mobile data on
 * what the page draws; a rule cannot say "a public host", so the page's other requests are its own, as in Safari.
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
        PageLook(snapshot = PageSnapshot.decode(json), isLoaded = navigation.isLoaded, hasFailed = navigation.hasFailed, isUnreachable = navigation.isUnreachable)
    }

    override suspend fun close() = withContext(Dispatchers.Main) {
        webView.stopLoading()
        webView.navigationDelegate = null
    }

    companion object {

        /**
         * The compiled [RULES], once per process: null until the first view asks for it, and only ever read or written on
         * the main thread. WebKit's completion handler settles it, never a read's coroutine, so a read cancelled while it
         * compiles leaves it to settle for the next one.
         */
        private var ruleList: CompletableDeferred<WKContentRuleList?>? = null

        /** Built on the main thread; the view holds its navigation delegate weakly, so the browser holds it. */
        suspend fun create() = withContext(Dispatchers.Main) {
            val rules = (ruleList ?: compileRuleList().also { ruleList = it }).await()
            val configuration = WKWebViewConfiguration().apply {
                websiteDataStore = WKWebsiteDataStore.nonPersistentDataStore()
                // Without the list (it failed to compile) a page is still read, only with its pictures.
                rules?.let(userContentController::addContentRuleList)
            }
            val webView = WKWebView(frame = CGRectMake(0.0, 0.0, WIDTH, HEIGHT), configuration = configuration)
            val navigation = WebKitNavigation()
            webView.navigationDelegate = navigation
            WebKitBrowser(webView, navigation)
        }

        /** Compiled into Caches, which no device backup holds and the system may empty; it is then compiled again. */
        private fun compileRuleList() = CompletableDeferred<WKContentRuleList?>().also { deferred ->
            val folder = (NSFileManager.defaultManager.URLsForDirectory(NSCachesDirectory, NSUserDomainMask).firstOrNull() as? NSURL)
                ?.URLByAppendingPathComponent("CampfireWebViewRules")
            val store = folder?.let { WKContentRuleListStore.storeWithURL(it) }
            if (store == null) {
                println("The page reader's content rule list has no store to compile into.")
                deferred.complete(null)
            } else {
                store.compileContentRuleListForIdentifier("campfire", RULES) { list, error ->
                    if (list == null) println("The page reader's content rule list did not compile: ${error?.localizedDescription}")
                    deferred.complete(list)
                }
            }
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
    var isUnreachable = false

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

    /** The page's process ended (out of memory, or crashed): what it drew is gone, so the load has failed. */
    override fun webViewWebContentProcessDidTerminate(webView: WKWebView) {
        hasFailed = true
    }

    /** A load cancelled because another took its place (a script's redirect) is no failure. */
    private fun failed(error: NSError) {
        if (error.code != NSURLErrorCancelled) {
            hasFailed = true
            isUnreachable = error.domain == NSURLErrorDomain && error.code in UNREACHABLE_ERROR_CODES
        }
    }
}

/** The errors the network causes rather than the page: no connection, a host not found or not answering, a timeout. */
private val UNREACHABLE_ERROR_CODES = setOf(
    NSURLErrorNotConnectedToInternet,
    NSURLErrorCannotFindHost,
    NSURLErrorCannotConnectToHost,
    NSURLErrorDNSLookupFailed,
    NSURLErrorNetworkConnectionLost,
    NSURLErrorTimedOut,
    NSURLErrorInternationalRoamingOff,
    NSURLErrorDataNotAllowed,
)

/** What a frame inside the page may load besides a public `https` address: nothing that leaves the device. */
private val FRAME_SCHEMES = setOf("about", "data", "blob")

/**
 * Pictures, media, and anything plain `http:` or `ws:` the page asks for are blocked; a filter takes no disjunction, hence
 * a rule per scheme. The macOS helper's `RULES` (`desktopMain/native/macos/CampfireWebView.m`) is its twin: keep the
 * two in step.
 */
private const val RULES = """[
    {"trigger": {"url-filter": ".*", "resource-type": ["image", "media"]}, "action": {"type": "block"}},
    {"trigger": {"url-filter": "^http:"}, "action": {"type": "block"}},
    {"trigger": {"url-filter": "^ws:"}, "action": {"type": "block"}}
]"""
