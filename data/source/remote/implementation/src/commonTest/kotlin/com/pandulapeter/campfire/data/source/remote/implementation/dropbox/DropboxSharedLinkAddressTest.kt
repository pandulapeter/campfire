/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.implementation.dropbox

import com.pandulapeter.campfire.data.model.sync.SyncProviderId
import com.pandulapeter.campfire.data.source.remote.api.sync.model.SharedLinkResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DropboxSharedLinkAddressTest {

    @Test
    fun `a link of the measured form gives its file id and its key`() = assertEquals(
        expected = SharedLinkResult.Created(provider = SyncProviderId.DROPBOX, parts = listOf("abcdefghij0123456789k", "abcdefghijklmnopqrstu1234")),
        actual = parseDropboxSharedLink("https://www.dropbox.com/scl/fi/abcdefghij0123456789k/k3jq.zip?rlkey=abcdefghijklmnopqrstu1234&dl=0"),
    )

    @Test
    fun `the name and the other parameters are ignored wherever the key stands`() = assertEquals(
        expected = SharedLinkResult.Created(provider = SyncProviderId.DROPBOX, parts = listOf("Ab_1-x", "Key_2-y")),
        actual = parseDropboxSharedLink("https://dropbox.com/scl/fi/Ab_1-x?st=xyz&rlkey=Key_2-y&dl=0"),
    )

    @Test
    fun `a link without a key is not read`() = assertNull(parseDropboxSharedLink("https://www.dropbox.com/scl/fi/abc/name.zip?dl=0"))

    @Test
    fun `an older link of another form is not read`() =
        assertNull(parseDropboxSharedLink("https://www.dropbox.com/s/abc123/name.zip?dl=0"))

    @Test
    fun `a link on another host is not read`() =
        assertNull(parseDropboxSharedLink("https://www.dropbox.com.example.com/scl/fi/abc/name.zip?rlkey=key&dl=0"))

    @Test
    fun `a link that is not https is not read`() = assertNull(parseDropboxSharedLink("http://www.dropbox.com/scl/fi/abc/name.zip?rlkey=key"))

    @Test
    fun `a key outside the link alphabet is not read`() =
        assertNull(parseDropboxSharedLink("https://www.dropbox.com/scl/fi/abc/name.zip?rlkey=k%2Fy&dl=0"))

    @Test
    fun `a file id longer than a link may carry is not read`() =
        assertNull(parseDropboxSharedLink("https://www.dropbox.com/scl/fi/${"a".repeat(65)}/name.zip?rlkey=key"))
}
