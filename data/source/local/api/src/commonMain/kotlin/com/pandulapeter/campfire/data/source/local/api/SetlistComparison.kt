/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.api

/**
 * What sync needs to know about the inside of a setlist file, without seeing its format: [LibraryFileLocalSource] never
 * looks inside a file, and the document a setlist is stored as belongs to the implementation.
 *
 * Every device gives a setlist file that names no day the day it first reads it on, so two devices that read one undated
 * setlist on different days hold two files that differ in nothing but that day. These are the two questions that lets
 * sync answer as one setlist rather than a conflict.
 */
interface SetlistComparison {

    /**
     * Whether [first] and [second] are the same setlist document apart from the day they name - which every device that
     * read an undated file gave it on its own. False when either is not a setlist document. Everything else the document
     * holds counts, the fields this version does not know included.
     */
    fun isSameApartFromDate(first: ByteArray, second: ByteArray): Boolean

    /**
     * [bytes] as this version writes the document with no day in it, or null when they are not a setlist document: what
     * an undated file was before a read gave it a day, byte for byte, for a file the app wrote. A file written by hand
     * comes back in the app's own formatting, so it is never mistaken for one the app dated.
     */
    fun withoutDate(bytes: ByteArray): ByteArray?
}
