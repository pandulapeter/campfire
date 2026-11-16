/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.formats.image

/** Tells pictures Campfire refuses apart from files that are no picture at all, so the refusal can name the format. */
object ImageFormats {

    /** The `ftyp` brands of HEIF still images, HEIC and AVIF included; a video's brands are not among them. */
    private val HEIF_BRANDS = setOf("heic", "heix", "heim", "heis", "hevc", "hevx", "hevm", "hevs", "mif1", "msf1", "avif", "avis")

    /** How far into the text an SVG's root element may start, past an XML declaration, comments, and a doctype. */
    private const val SVG_PROLOG_BYTES = 4 * 1024

    /** Whether [bytes] hold a HEIC, HEIF, AVIF, or SVG picture, the formats a song cannot show. Never throws. */
    fun isRefusedImage(bytes: ByteArray): Boolean = isHeif(HeaderBytes(bytes)) || isSvg(bytes)

    private fun isHeif(header: HeaderBytes): Boolean {
        if (!header.startsWith("ftyp", offset = 4)) return false
        val boxEnd = minOf(header.u32be(0), header.size.toLong()).toInt()
        // The major brand, then (past the minor version) the compatible ones, four bytes each.
        val brands = listOf(8) + (16..boxEnd - 4 step 4)
        return brands.any { offset -> offset + 4 <= boxEnd && brandAt(header, offset) in HEIF_BRANDS }
    }

    private fun brandAt(header: HeaderBytes, offset: Int) = (offset until offset + 4).map { header.u8(it).toChar() }.joinToString("")

    private fun isSvg(bytes: ByteArray): Boolean {
        val text = bytes.decodeToString(endIndex = minOf(bytes.size, SVG_PROLOG_BYTES)).removePrefix("\uFEFF")
        var index = 0
        while (true) {
            while (index < text.length && text[index].isWhitespace()) index++
            val terminator = when {
                text.startsWith("<?", index) -> "?>"
                text.startsWith("<!--", index) -> "-->"
                text.startsWith("<!DOCTYPE", index) -> ">"
                else -> break
            }
            val end = text.indexOf(terminator, index)
            if (end < 0) return false
            index = end + terminator.length
        }
        if (!text.startsWith("<svg", index)) return false
        val next = text.getOrNull(index + 4) ?: return false
        return next.isWhitespace() || next == '>' || next == '/'
    }
}
