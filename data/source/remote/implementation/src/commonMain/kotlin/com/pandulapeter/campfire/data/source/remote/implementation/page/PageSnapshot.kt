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
import com.pandulapeter.campfire.data.model.importing.PageLineMarks
import com.pandulapeter.campfire.data.source.remote.implementation.network.isPublicHttpsAddress
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * One look at a page an offscreen web view is drawing: what [PAGE_SNAPSHOT_SCRIPT] answers. Its [marks] are kept as the
 * script wrote them and decoded only for the look that is taken ([toLoadedPage]); one the page broke is no reason to lose
 * the text.
 */
@Serializable
internal data class PageSnapshot(
    val text: String = "",
    val title: String = "",
    val address: String = "",
    val marks: JsonArray? = null,
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

    /**
     * Whether [other] reads as this look: the same text, title, and address. The marks are left out, since a style
     * changing alone (an animation's weight or size) is no page still drawing its text.
     */
    fun readsAs(other: PageSnapshot?) = other != null && text == other.text && title == other.title && address == other.address

    /** The page as a [LoadedPage]: [LoadedPage.NotRead] where it is not [isReadable] or ended up off a public host. */
    fun toLoadedPage(): LoadedPage = if (isReadable && isPublicHttpsAddress(address)) {
        LoadedPage.Read(text = text, title = title.trim().takeIf { it.isNotEmpty() }, address = address, marks = marks?.let(::decodedMarks))
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

/**
 * The script's marks, one a line: null for a line with nothing printed (`{}`, no `size`) or one it could not align
 * (`unaligned`), as the page model was trained to read them (`tools/page-model`'s `lines.py`). Null for all of them where
 * any is not what the script writes, since a page that replaced `JSON` or an array's methods may have written anything.
 */
private fun decodedMarks(marks: JsonArray): List<PageLineMarks?>? = try {
    snapshotJson.decodeFromJsonElement<List<SnapshotLineMarks>>(marks).map(SnapshotLineMarks::toPageLineMarks)
} catch (_: SerializationException) {
    null
} catch (_: IllegalArgumentException) {
    null
}

/** One line's marks as [PAGE_SNAPSHOT_SCRIPT] writes them: numbers, each flag 0 or 1. */
@Serializable
private data class SnapshotLineMarks(
    val size: Double? = null,
    val unaligned: Int = 0,
    val link: Int = 0,
    val newBlock: Int = 0,
    val landmark: Int = 0,
    val bold: Int = 0,
    val italic: Int = 0,
    val monospace: Int = 0,
    val heading: Int = 0,
) {
    fun toPageLineMarks() = if (size == null || unaligned != 0) {
        null
    } else {
        PageLineMarks(
            size = size.toFloat(),
            isBold = bold != 0,
            isItalic = italic != 0,
            isMonospace = monospace != 0,
            isHeading = heading != 0,
            isLink = link != 0,
            isNewBlock = newBlock != 0,
            isLandmark = landmark != 0,
        )
    }
}
