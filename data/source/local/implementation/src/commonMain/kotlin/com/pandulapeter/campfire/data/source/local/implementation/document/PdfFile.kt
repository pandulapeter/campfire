/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.document

import com.pandulapeter.campfire.data.model.domain.ImportLimits
import com.pandulapeter.campfire.data.source.local.implementation.zip.ZipException

/** Lazy indirect objects, with classic/stream cross references and a sequential recovery scan. */
internal class PdfFile(input: ByteArray) {
    /**
     * The file from its header on, since a header may follow a line a mail gateway or a generator put in front of it and
     * every offset in the file counts from the header. Declared first, since everything below is built over it.
     */
    private val bytes = run {
        require(input.size <= ImportLimits.MAX_DOCUMENT_FILE_SIZE)
        val header = headerOffset(input)
        require(header >= 0)
        if (header == 0) input else input.copyOfRange(header, input.size)
    }
    private data class Location(val offset: Int = -1, val stream: Int = -1)
    /** An object stream's header, read once however many of its objects are asked for. */
    private class ObjectStream(val source: PdfStream, val data: ByteArray, val first: Int, val numbers: IntArray, val offsets: IntArray) {
        fun parse(index: Int) = PdfSyntax(data, first + offsets[index]).next() ?: error("PDF compressed object")
    }
    private val locations = mutableMapOf<PdfReference, Location>()
    private val objects = mutableMapOf<PdfReference, PdfValue>()
    private val resolving = mutableSetOf<PdfReference>()
    private val decoded = mutableMapOf<PdfStream, ByteArray>()
    private val objectStreams = mutableMapOf<PdfReference, ObjectStream>()
    private val parsedStreams = mutableSetOf<Int>()
    /** Where the recovery scan found each definition, so that a later one replaces an earlier one wherever it is. */
    private val definedAt = mutableMapOf<PdfReference, Int>()
    private var decodedSize = 0L
    private var encodedSize = 0L
    private var fontTableEntries = 0L
    private var scanned = false
    private var root: PdfValue? = null
    private var encrypted = false
    /** Found by a lazy recovery scan too, from inside whatever page or font asked, which must not read on regardless. */
    val isEncrypted get() = encrypted
    private val streamEnds = PdfStreamEnds(bytes)

    init {
        val tail = bytes.latin1(maxOf(0, bytes.size - 4096))
        val start = Regex("startxref\\s+([0-9]+)").findAll(tail).lastOrNull()?.groupValues?.get(1)?.toIntOrNull()
        try {
            require(start != null)
            xref(start, mutableSetOf())
        } catch (_: IllegalArgumentException) { scan() }
        catch (_: IllegalStateException) { scan() }
        catch (_: ZipException) { scan() }
        require(!encrypted) { "Encrypted PDF" }
    }

    fun catalog(): PdfDictionary {
        (resolve(root) as? PdfDictionary)?.let { return it }
        scan()
        require(!encrypted)
        return objects.values.filterIsInstance<PdfDictionary>().lastOrNull { it["Type"].name() == "Catalog" }
            ?: error("PDF has no catalog")
    }

    fun resolve(value: PdfValue?): PdfValue? {
        if (value !is PdfReference) return value
        objects[value]?.takeUnless { it is PdfReference }?.let { return it }
        require(resolving.size < 64 && resolving.add(value)) { "Cyclic PDF reference" }
        try {
            objects[value]?.let { return resolve(it) }
            var location = locations[value]
            if (location == null) { scan(); objects[value]?.let { return resolve(it) }; location = locations[value] }
            if (location == null) return null
            val parsed = if (location.stream >= 0) compressed(location, value) else {
                try { indirect(location.offset, value) } catch (_: IllegalArgumentException) { scan(); objects[value] }
                catch (_: IllegalStateException) { scan(); objects[value] }
            } ?: return null
            requireWithinLimit(objects.size < MAX_OBJECTS) { "PDF object limit" }
            objects[value] = parsed
            return if (parsed is PdfReference) resolve(parsed) else parsed
        } finally { resolving.remove(value) }
    }

    fun dictionary(value: PdfValue?) = when (val resolved = resolve(value)) {
        is PdfDictionary -> resolved
        is PdfStream -> resolved.dictionary
        else -> null
    }
    fun array(value: PdfValue?) = resolve(value).array()
    fun number(value: PdfValue?, default: Double = 0.0) = resolve(value).number(default)
    fun stream(value: PdfValue?): ByteArray? = (resolve(value) as? PdfStream)?.let(::decode)

    fun decode(stream: PdfStream): ByteArray = decoded.getOrPut(stream) {
        // The encoded range is copied before it is inflated, and many streams that fall back to one far endstream each
        // have a range as long as the file while what they decode to stays small.
        encodedSize += stream.length
        requireWithinLimit(encodedSize <= PdfTextExtractor.MAX_INTERPRETED_BYTES) { "PDF stream input limit" }
        PdfFilters.decode(stream, ::resolve).also {
            decodedSize += it.size
            requireWithinLimit(decodedSize <= ImportLimits.MAX_IMPORT_SIZE) { "PDF decoded streams too large" }
        }
    }

    /**
     * Charges map writes of a font's widths or ToUnicode table to the document rather than to the font: fonts are built
     * once per dictionary, but thousands of tiny font dictionaries can all name one shared `/W` array or CMap stream.
     */
    fun chargeFontTables(entries: Int) {
        fontTableEntries += entries
        requireWithinLimit(fontTableEntries <= MAX_FONT_TABLE_ENTRIES) { "PDF font tables too large" }
    }

    private fun indirect(offset: Int, expected: PdfReference? = null): PdfValue {
        require(offset in bytes.indices)
        val parser = PdfSyntax(bytes, offset, streamEnds) { number(it, -1.0).toInt().takeIf { length -> length >= 0 } }
        val number = parser.word().toInt()
        val generation = parser.word().toInt()
        require(parser.word() == "obj" && (expected == null || expected == PdfReference(number, generation)))
        return parser.next() ?: error("Truncated PDF object")
    }

    private fun trailer(dictionary: PdfDictionary) {
        if (root == null) root = dictionary["Root"]
        if (dictionary["Encrypt"]?.let { it !is PdfKeyword || it.value != "null" } == true) encrypted = true
    }

    private fun locate(reference: PdfReference, location: Location) {
        if (reference !in locations) locations[reference] = location
    }

    private fun xref(offset: Int, seen: MutableSet<Int>) {
        require(seen.size < 64 && seen.add(offset) && offset in bytes.indices) { "PDF xref chain" }
        val parser = PdfSyntax(bytes, offset, streamEnds)
        val dictionary: PdfDictionary
        if (parser.word() == "xref") {
            while (true) {
                val word = parser.word()
                if (word == "trailer") break
                val start = word.toInt()
                val count = parser.word().toInt()
                require(start >= 0 && count in 0..MAX_OBJECTS && start.toLong() + count <= Int.MAX_VALUE)
                repeat(count) { index ->
                    val position = parser.word().toInt()
                    val generation = parser.word().toInt()
                    val kind = parser.word()
                    require(kind == "f" || kind == "n")
                    if (kind == "n") locate(PdfReference(start + index, generation), Location(position))
                }
                require(locations.size <= MAX_OBJECTS)
            }
            dictionary = parser.next() as? PdfDictionary ?: error("PDF trailer")
        } else {
            val stream = indirect(offset) as? PdfStream ?: error("PDF xref stream")
            dictionary = stream.dictionary
            require(dictionary["Type"].name() == "XRef")
            val widths = dictionary["W"].array().map { it.number().toInt() }
            require(widths.size == 3 && widths.all { it in 0..4 } && widths.sum() > 0)
            val ranges = dictionary["Index"].array().ifEmpty { listOf(PdfNumber(0.0), dictionary["Size"] ?: error("PDF xref size")) }
            require(ranges.size % 2 == 0)
            val data = decode(stream)
            var at = 0
            fun field(width: Int): Long {
                var value = 0L
                repeat(width) { require(at < data.size); value = (value shl 8) or (data[at++].toLong() and 255) }
                return value
            }
            for (range in ranges.chunked(2)) {
                val start = range[0].number().toInt()
                val count = range[1].number().toInt()
                require(start >= 0 && count in 0..MAX_OBJECTS && start.toLong() + count <= Int.MAX_VALUE)
                repeat(count) { index ->
                    val type = if (widths[0] == 0) 1 else field(widths[0]).toInt()
                    val first = field(widths[1]); val second = field(widths[2])
                    require(first <= Int.MAX_VALUE && second <= Int.MAX_VALUE)
                    when (type) {
                        1 -> locate(PdfReference(start + index, second.toInt()), Location(first.toInt()))
                        2 -> locate(PdfReference(start + index), Location(stream = first.toInt()))
                    }
                }
                require(locations.size <= MAX_OBJECTS)
            }
        }
        trailer(dictionary)
        // Hybrid files use a classic table supplemented by an xref stream at the same revision.
        dictionary["XRefStm"]?.number()?.toInt()?.let { xref(it, seen) }
        dictionary["Prev"]?.number()?.toInt()?.let { xref(it, seen) }
    }

    private fun objectStream(reference: PdfReference): ObjectStream {
        val stream = resolve(reference) as? PdfStream ?: error("PDF object stream")
        objectStreams[reference]?.takeIf { it.source === stream }?.let { return it }
        require(stream.dictionary["Type"].name() == "ObjStm")
        val count = number(stream.dictionary["N"]).toInt()
        val first = number(stream.dictionary["First"]).toInt()
        val data = decode(stream)
        require(count in 1..MAX_OBJECTS && first in data.indices)
        val header = PdfSyntax(data)
        val numbers = IntArray(count)
        val offsets = IntArray(count)
        for (index in 0 until count) {
            numbers[index] = header.word().toInt()
            offsets[index] = header.word().toInt()
            require(offsets[index] >= 0 && first.toLong() + offsets[index] < data.size && numbers[index] > 0)
        }
        require(header.position <= first)
        return ObjectStream(stream, data, first, numbers, offsets).also { objectStreams[reference] = it }
    }

    /**
     * The object [reference] names, found by its number wherever the stream's header lists it: an xref entry whose
     * index names another object is either dead or off by one, and in neither case is that other object the answer.
     */
    private fun compressed(location: Location, reference: PdfReference): PdfValue? {
        if (location.stream !in parsedStreams) {
            val stream = objectStream(PdfReference(location.stream))
            for (index in stream.numbers.indices) {
                val contained = PdfReference(stream.numbers[index])
                if (objects.containsKey(contained)) continue
                val actual = locations[contained]
                if (actual != null && actual.stream != location.stream) continue
                requireWithinLimit(objects.size < MAX_OBJECTS) { "PDF object limit" }
                objects[contained] = stream.parse(index)
            }
            parsedStreams += location.stream
        }
        return objects[reference]
    }

    private fun scan() {
        if (scanned) return
        scanned = true
        val text = bytes.latin1()
        val marker = Regex("(?m)(?:^|[\\r\\n\\s])([0-9]+) +([0-9]+) +obj\\b|\\btrailer\\b")
        var at = 0
        var count = 0
        while (true) {
            val match = marker.find(text, at) ?: break
            require(++count <= MAX_OBJECTS)
            val after = match.range.last + 1
            val parser = PdfSyntax(bytes, after, streamEnds)
            try {
                val value = parser.next()
                if (match.groupValues[1].isNotEmpty() && value != null) {
                    val reference = PdfReference(match.groupValues[1].toInt(), match.groupValues[2].toInt())
                    objects[reference] = value
                    definedAt[reference] = match.range.first
                    locations[reference] = Location(match.range.first)
                    if (value is PdfStream && value.dictionary["Type"].name() == "XRef") trailer(value.dictionary)
                } else if (value is PdfDictionary) {
                    value["Root"]?.let { root = it }
                    trailer(value)
                }
                at = maxOf(after, parser.position)
            } catch (_: IllegalArgumentException) { at = after }
            catch (_: IllegalStateException) { at = after }
        }
        require(!encrypted) { "Encrypted PDF" }
        // Recovery also discovers compressed objects; unreferenced image streams are never decoded. The streams are read in
        // the order they appear, and a definition replaces whatever was found before it, as the plain objects do, since an
        // incremental update appends its objects after the ones it supersedes. An object resolved through the xref before
        // this scan ran is left as it is.
        val streams = definedAt.entries.filter { (reference, _) ->
            val value = objects[reference]
            value is PdfStream && value.dictionary["Type"].name() == "ObjStm" && value.dictionary["N"].number() > 0
        }.map { it.key to it.value }.sortedBy { it.second }
        for ((streamReference, streamOffset) in streams) {
            val stream = objectStream(streamReference)
            for (index in stream.numbers.indices) {
                val reference = PdfReference(stream.numbers[index])
                val previous = definedAt[reference]
                if (previous == null && objects.containsKey(reference) || previous != null && previous > streamOffset) continue
                requireWithinLimit(objects.size < MAX_OBJECTS) { "PDF object limit" }
                objects[reference] = stream.parse(index)
                definedAt[reference] = streamOffset
            }
        }
    }

    private companion object {
        const val MAX_OBJECTS = 100_000
        /**
         * Every font is cached for the whole document and every entry it writes stays in memory, at about 80 bytes per
         * boxed map entry, so this is roughly 80 MB at worst, while a real document with dozens of fonts writes far fewer.
         */
        const val MAX_FONT_TABLE_ENTRIES = 1 shl 20
        const val HEADER = "%PDF-"
        /** How far into the file the header may start, as the specification and every other reader allow. */
        const val MAX_HEADER_OFFSET = 1_024

        fun headerOffset(bytes: ByteArray): Int {
            for (index in 0..minOf(MAX_HEADER_OFFSET, bytes.size) - HEADER.length) {
                if (HEADER.indices.all { bytes[index + it].toInt() and 255 == HEADER[it].code }) return index
            }
            return -1
        }
    }
}
