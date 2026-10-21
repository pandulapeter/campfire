/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.implementation.network

import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.util.AttributeKey

/**
 * Whether [url] may be asked without the user asking: `https`, and a host named in public DNS. Refused: plain `http`
 * (Android, iOS for most hosts, and the web refuse it already), an IP literal of either family, a name without a dot
 * (`localhost`, an intranet name, a number the resolver reads as an address), and the suffixes kept for local
 * networks. A public name that resolves to a private address is not caught; the request is a blind GET either way.
 */
internal fun isPublicHttpsAddress(url: Url): Boolean {
    if (url.protocol != URLProtocol.HTTPS) return false
    val host = url.host.lowercase().trimEnd('.')
    if (host.isEmpty() || ':' in host || host.startsWith('[') || '.' !in host) return false
    val lastLabel = host.substringAfterLast('.')
    // A public top-level domain is never numeric, so a numeric last label is an address (`127.0.0.1`, `0x7f.1`).
    if (lastLabel.all { it.isDigit() } || lastLabel.startsWith("0x")) return false
    return LOCAL_SUFFIXES.none { host == it || host.endsWith(".$it") }
}

/** Marks a request whose every hop must be [isPublicHttpsAddress]; a hop that is not ends it with [RefusedAddressException]. */
internal val PublicHostsOnly = AttributeKey<Unit>("PublicHostsOnly")

internal class RefusedAddressException : IllegalStateException("Not a public https address.")

/**
 * Checks [PublicHostsOnly] on every hop: `HttpRedirect`, installed before the user plugins, calls the `Send` hook once
 * per hop and copies the request's attributes onto each follow-up. In the browser, which follows redirects itself, only
 * the first address is seen, and the browser's own private-network rules cover the rest.
 */
internal val PublicHostsOnlyPlugin = createClientPlugin("PublicHostsOnly") {
    on(Send) { request ->
        if (request.attributes.contains(PublicHostsOnly) && !isPublicHttpsAddress(request.url.build())) throw RefusedAddressException()
        proceed(request)
    }
}

private val LOCAL_SUFFIXES = listOf("local", "localhost", "internal", "lan", "home.arpa")
