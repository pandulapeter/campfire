/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.widget

import com.pandulapeter.campfire.data.model.catalogue.CoverArtResult

/**
 * Hands the widgets a snapshot ([files]), with the cover thumbnails its songs name. The covers come the way the songs
 * list's do ([getCoverArt]: the device's copy, or downloaded once and kept), and each is made into a thumbnail
 * ([makeThumbnail]) of the widgets' own, since they cannot read the app's copies (an iOS widget extension sees only the
 * App Group's container); a cover that cannot be had leaves its song without one.
 */
internal class WidgetPublisher(
    private val files: WidgetFiles,
    private val getCoverArt: suspend (url: String) -> CoverArtResult,
    private val makeThumbnail: suspend (bytes: ByteArray) -> ByteArray?,
) {

    /**
     * Writes [snapshot] with the covers of [coverAddresses] (each song's address by its file name) that already have a
     * thumbnail, so that it reaches the widgets at once; then deletes the thumbnails no song names any more, and makes the
     * missing ones, writing the snapshot again with them where it made any.
     *
     * The order keeps a widget from naming a file that is not there: a thumbnail is named only once it is written, and
     * deleted only once the snapshot that stops naming it has replaced the one before it.
     */
    suspend fun publish(snapshot: WidgetSnapshot, coverAddresses: Map<String, String>) {
        val addressesByName = coverAddresses.values.associateBy(::widgetCoverName)
        val present = files.coverNames()
        val available = present.filterTo(mutableSetOf()) { it in addressesByName }
        files.writeSnapshot(snapshot.withCovers(addresses = coverAddresses, available = available))
        val unused = present - addressesByName.keys
        unused.forEach { files.deleteCover(it) }
        var madeCount = 0
        var failedCount = 0
        addressesByName.filterKeys { it !in available }.forEach { (name, url) ->
            val thumbnail = (getCoverArt(url) as? CoverArtResult.Image)?.let { makeThumbnail(it.bytes) }
            if (thumbnail != null && files.writeCover(name = name, bytes = thumbnail)) {
                available += name
                madeCount++
            } else {
                failedCount++
            }
        }
        if (unused.isNotEmpty() || madeCount > 0 || failedCount > 0) {
            println("Widget covers: $madeCount made, $failedCount without one, ${unused.size} removed, ${available.size} in use")
        }
        if (madeCount > 0) {
            files.writeSnapshot(snapshot.withCovers(addresses = coverAddresses, available = available))
        }
    }
}
