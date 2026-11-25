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

import com.pandulapeter.campfire.data.model.importing.LoadedPage
import com.pandulapeter.campfire.data.source.remote.implementation.network.isPublicHttpsAddress
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** One look at a page an offscreen web view is drawing: what [PAGE_SNAPSHOT_SCRIPT] answers. */
@Serializable
internal data class PageSnapshot(
    val text: String = "",
    val title: String = "",
    val address: String = "",
) {

    /** Whether the page has text worth waiting on to settle: some, and not a bot check standing in for the page. */
    val isReadable get() = text.isNotBlank() && !isBotCheck()

    /**
     * Whether this is a page a site puts up instead of its own while it checks for a robot (Cloudflare's "Just a moment",
     * Imperva's "Pardon Our Interruption", a CAPTCHA). Asked only of a short page, since such a check says little else,
     * and a song or its comments may well use one of the phrases.
     */
    fun isBotCheck(): Boolean {
        if (text.length > BOT_CHECK_MAX_LENGTH) return false
        val words = (title + "\n" + text).lowercase()
        return BOT_CHECK_PHRASES.any { it in words }
    }

    /** The page as a [LoadedPage]: [LoadedPage.NotRead] where it is not [isReadable] or ended up off a public host. */
    fun toLoadedPage(): LoadedPage = if (isReadable && isPublicHttpsAddress(address)) {
        LoadedPage.Read(text = text, title = title.trim().takeIf { it.isNotEmpty() }, address = address)
    } else {
        LoadedPage.NotRead(LoadedPage.NotRead.Reason.UNREADABLE)
    }

    companion object {

        /** What [PAGE_SNAPSHOT_SCRIPT] answered, or null for anything else (a page that replaced `JSON` or `document`). */
        fun decode(json: String?): PageSnapshot? = try {
            json?.let { snapshotJson.decodeFromString<PageSnapshot>(it) }
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}

/** A bot check is a page of a few sentences; past this many characters it is the site's own page. */
private const val BOT_CHECK_MAX_LENGTH = 3_000

private val BOT_CHECK_PHRASES = listOf(
    "just a moment",
    "attention required",
    "checking your browser",
    "checking if the site connection is secure",
    "verify you are human",
    "verifying you are human",
    "are you a robot",
    "not a robot",
    "pardon our interruption",
    "request unsuccessful",
    "access denied",
    "captcha",
    "enable javascript and cookies to continue",
    "please enable js and disable any ad blocker",
    "ddos protection",
)

private val snapshotJson = Json { ignoreUnknownKeys = true }
