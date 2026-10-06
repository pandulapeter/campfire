/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.source

import com.pandulapeter.campfire.data.model.domain.decodeLibraryText
import com.pandulapeter.campfire.data.source.local.api.SetlistComparison
import com.pandulapeter.campfire.data.source.local.implementation.model.SetlistDocument
import com.pandulapeter.campfire.data.source.local.implementation.model.SetlistDocumentFormat
import org.koin.core.annotation.Single

/**
 * Compares documents rather than models: reading a model names each song once, which would make two different files
 * equal. [SetlistDocument.priority] is left out as well, since it is read and dropped either way.
 */
@Single
internal class SetlistComparisonImpl : SetlistComparison {

    override fun isSameApartFromDate(first: ByteArray, second: ByteArray): Boolean {
        val firstDocument = first.decodedOrNull() ?: return false
        val secondDocument = second.decodedOrNull() ?: return false
        return firstDocument.copy(date = null, priority = 0) == secondDocument.copy(date = null, priority = 0)
    }

    override fun withoutDate(bytes: ByteArray) = bytes.decodedOrNull()
        ?.let { SetlistDocumentFormat.encode(it.copy(date = null)).encodeToByteArray() }

    private fun ByteArray.decodedOrNull(): SetlistDocument? = try {
        SetlistDocumentFormat.decode(decodeLibraryText())
    } catch (_: Exception) {
        null
    }
}
