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

/**
 * What a failed load the desktop's web view helper reports comes to. Each engine names its errors its own way, so the
 * helper hands over the error's domain and code untouched (`NSURLErrorDomain` and WebKit's own on macOS, [WEB_VIEW2]
 * with a `COREWEBVIEW2_WEB_ERROR_STATUS` on Windows, a `GError`'s domain and code on Linux) and they are read here.
 */
internal enum class NativeLoadFailure {

    /** No failure at all: a load cancelled because another took its place (a script's redirect, a stop). */
    NONE,

    /** The network's: no connection, a host not found, not answering, or dropping the connection, a timeout. */
    UNREACHABLE,

    /** The page's or the address's: a certificate, a refused navigation, an engine that could not start or crashed. */
    FAILED,
    ;

    companion object {

        /** The domain the Windows helper names WebView2's `COREWEBVIEW2_WEB_ERROR_STATUS` codes by. */
        const val WEB_VIEW2 = "WebView2"

        fun of(domain: String, code: Int): NativeLoadFailure = when {
            code in CANCELLED_CODES[domain].orEmpty() -> NONE
            code in UNREACHABLE_CODES[domain].orEmpty() || domain == RESOLVER_DOMAIN -> UNREACHABLE
            else -> FAILED
        }

        /** A host name that could not be looked up, which is also how Linux reports no connection at all. */
        private const val RESOLVER_DOMAIN = "g-resolver-error-quark"

        private val CANCELLED_CODES = mapOf(
            // NSURLErrorCancelled.
            "NSURLErrorDomain" to setOf(-999),
            // COREWEBVIEW2_WEB_ERROR_STATUS_OPERATION_CANCELED.
            WEB_VIEW2 to setOf(14),
            // WEBKIT_NETWORK_ERROR_CANCELLED.
            "WebKitNetworkError" to setOf(302),
            // G_IO_ERROR_CANCELLED.
            "g-io-error-quark" to setOf(19),
        )

        private val UNREACHABLE_CODES = mapOf(
            // Not connected, cannot find or connect to the host, DNS lookup failed, connection lost, timed out,
            // international roaming off, and data not allowed: the codes the iOS app reads as the network's.
            "NSURLErrorDomain" to setOf(-1009, -1003, -1004, -1006, -1005, -1001, -1018, -1020),
            // Server unreachable, timeout, connection aborted, reset, disconnected, cannot connect, host name not resolved.
            WEB_VIEW2 to setOf(6, 7, 9, 10, 11, 12, 13),
            // WEBKIT_NETWORK_ERROR_TRANSPORT.
            "WebKitNetworkError" to setOf(300),
            // Timed out, host not found, host or network unreachable, connection refused, broken pipe, not connected.
            "g-io-error-quark" to setOf(24, 28, 37, 38, 39, 44, 45),
        )
    }
}
