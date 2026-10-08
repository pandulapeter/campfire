/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.buildLogic.tasks

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The manifest is the contract the page's offline code checks every file against, so it is pinned to the text the
 * finisher wrote before it moved into build-logic, computed for these files on their own.
 */
class WebBuildManifestTest {

    private val template = "<script>const build = /*{{BUILD}}*/null;</script>\n"

    private val files = listOf(
        "skiko.wasm" to "skiko".toByteArray(),
        "composeResources/a \"q\\.txt" to "hello".toByteArray(),
        "campfire.wasm" to byteArrayOf(0, 'a'.code.toByte(), 's'.code.toByte(), 'm'.code.toByte(), 1, 0, 0, 0),
    )

    private val fileMap = "\"campfire.wasm\":{\"sha256\":\"93a44bbb96c751218e4c00d479e4c14358122a389acca16205b1e4d0dc5f9476\",\"size\":8}," +
        "\"composeResources/a \\\"q\\\\.txt\":{\"sha256\":\"2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824\",\"size\":5}," +
        "\"skiko.wasm\":{\"sha256\":\"0b041b443ea5a5f9b95a9ca5b68e5561315ff77cddd323fafb920586a540a379\",\"size\":5}"

    @Test
    fun `the ID is sixteen hex digits of the map and the page`() {
        assertEquals("d35a7fb980b863e0", webBuildManifest(files, template).id)
    }

    @Test
    fun `the page manifest names every file in path order and counts the binaries`() {
        assertEquals(
            "{\"id\":\"d35a7fb980b863e0\",\"binaryCount\":2,\"binaryBytes\":13,\"files\":{$fileMap}}",
            webBuildManifest(files, template).pageManifest,
        )
    }

    @Test
    fun `build JSON carries the digest of the finished page`() {
        val manifest = webBuildManifest(files, template)
        val page = template.replace("/*{{BUILD}}*/null", manifest.pageManifest)
        assertEquals(
            "{\"id\":\"d35a7fb980b863e0\",\"page\":\"ff064438f34e759ca5f5e40aa295cf13f14386747eba1e2e63fc3ef9c4e01bd5\",\"files\":{$fileMap}}",
            manifest.buildJson(page.toByteArray().sha256()),
        )
    }
}
