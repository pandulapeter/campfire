/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.formats.document

import com.pandulapeter.campfire.data.formats.zip.ByteArrayBuilder
import com.pandulapeter.campfire.data.formats.zip.Deflater
import com.pandulapeter.campfire.data.formats.zip.Mutator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Valid documents damaged at random, byte by byte and token by token: each is read or refused with an [Exception], which
 * the import reports as an unreadable file. An [Error] (a stack overflow, running out of memory) is a crash on Android
 * and a trap on the web, and a document that never finishes is a hang; the readers' own limits must stop both.
 */
class PdfFuzzTest {

    @Test
    fun `damaged documents are read or refused`() = runTest {
        val documents = documents()
        documents.forEach { assertTrue(PdfTextExtractor.extract(it).pages.isNotEmpty()) }
        repeat(MUTATIONS) { seed ->
            val mutator = Mutator(seed)
            val document = documents[seed % documents.size]
            val damaged = if (mutator.random.nextBoolean()) mutator.mutate(document) else damageTokens(mutator, document)
            try {
                PdfTextExtractor.extract(damaged)
            } catch (exception: CancellationException) {
                fail("Seed $seed was cancelled", exception)
            } catch (_: Exception) {
            } catch (error: Throwable) {
                fail("Seed $seed failed with $error", error)
            }
        }
    }

    /** Rewrites the numbers, references, names, and keywords the structure of a document hangs on. */
    private fun damageTokens(mutator: Mutator, document: ByteArray): ByteArray {
        val random = mutator.random
        var text = document.latin1()
        repeat(1 + random.nextInt(3)) {
            text = when (random.nextInt(6)) {
                0 -> text.replaceRandom(random, NUMBER) { HOSTILE_NUMBERS[random.nextInt(HOSTILE_NUMBERS.size)] }
                1 -> text.replaceRandom(random, REFERENCE) { "${random.nextInt(12)} 0 R" }
                2 -> text.replaceRandom(random, NAME) { NAMES[random.nextInt(NAMES.size)] }
                3 -> text.replaceRandom(random, KEYWORD) { if (random.nextBoolean()) "" else it.value + " " + it.value }
                4 -> {
                    // An object defined again, after itself or anywhere else, as an incremental update or a broken one does.
                    val objects = OBJECT.findAll(text).toList()
                    if (objects.isEmpty()) text else {
                        val copied = objects[random.nextInt(objects.size)].value
                        val at = if (random.nextBoolean()) text.length else random.nextInt(text.length)
                        text.substring(0, at) + "\n" + copied + "\n" + text.substring(at)
                    }
                }
                else -> {
                    val at = random.nextInt(text.length)
                    text.substring(0, at) + OPENERS[random.nextInt(OPENERS.size)].repeat(random.nextInt(1, 100)) + text.substring(at)
                }
            }
        }
        return ByteArray(text.length) { text[it].code.toByte() }
    }

    private fun String.replaceRandom(random: Random, regex: Regex, replacement: (MatchResult) -> String): String {
        val matches = regex.findAll(this).toList()
        if (matches.isEmpty()) return this
        val match = matches[random.nextInt(matches.size)]
        return substring(0, match.range.first) + replacement(match) + substring(match.range.last + 1)
    }

    /** Small documents over every part of the reader: both kinds of cross reference, object streams, filters, fonts, and forms. */
    private suspend fun documents(): List<ByteArray> {
        val courier = "/Type /Font /Subtype /Type1 /BaseFont /Courier /Encoding /WinAnsiEncoding"
        val song = "BT /F1 10 Tf 1 0 0 1 50 700 Tm (Am) Tj 1 0 0 1 92 700 Tm (C) Tj 1 0 0 1 50 688 Tm (Hello world) Tj ET"
        val objectStream = PdfTestWriter().apply {
            add("<< /Type /Catalog /Pages 2 0 R >>")
            add("<< /Type /Pages /Kids [3 0 R] /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> >>")
            add("<< /Type /Page /Contents 5 0 R >>")
            add("null")
            stream("BT /F1 10 Tf 50 700 Td (Compressed) Tj ET")
            stream("4 0 << $courier >>", "/Type /ObjStm /N 1 /First 4")
        }
        val pages = PdfTestWriter().apply {
            add("<< /Type /Catalog /Pages 2 0 R >>")
            add("<< /Type /Pages /Kids [3 0 R 6 0 R] /Count 3 /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> /XObject << /X 7 0 R >> >> >>")
            add("<< /Type /Page /Parent 2 0 R /Contents [5 0 R 5 0 R] /Rotate 90 >>")
            add("<< $courier >>")
            stream("BT /F1 10 Tf 50 700 Td (Page) Tj ET /X Do")
            add("<< /Type /Pages /Parent 2 0 R /Kids [8 0 R] >>")
            stream("/Span << /ActualText <FEFF05D005D1> >> BDC BT /F1 10 Tf 50 650 Td (Form) Tj ET EMC BI /W 1 /H 1 ID x EI", "/Subtype /Form /Matrix [1 0 0 1 10 10]")
            add("<< /Type /Page /Parent 6 0 R /Contents 9 0 R /MediaBox [612 792 0 0] >>")
            stream(zlib(song.encodeToByteArray()).toHex() + ">", "/Filter [/ASCIIHexDecode /FlateDecode]")
        }
        val type0 = PdfTestWriter.song(
            content = "BT /F1 10 Tf 50 700 Td <00010002000300040005> Tj ET",
            font = "/Type /Font /Subtype /Type0 /BaseFont /Embedded-Bold /Encoding /Identity-H /DescendantFonts [6 0 R] /ToUnicode 7 0 R",
            extra = { writer ->
                writer.add("<< /Type /Font /Subtype /CIDFontType2 /DW 500 /W [1 [1000 200] 3 5 600] >>")
                writer.stream(
                    "1 begincodespacerange <0000> <FFFF> endcodespacerange 1 beginbfchar <0001> <0151> endbfchar " +
                        "1 beginbfrange <0002> <0003> <0041> endbfrange 1 beginbfrange <0004> <0005> [<00660069> <03A9>] endbfrange",
                )
            },
        )
        val differences = PdfTestWriter.song(
            content = "BT /F1 12 Tf 50 700 Td (\\001\\002ab) Tj ET",
            font = "/Type /Font /Subtype /Type1 /BaseFont /Times-Italic /FirstChar 1 /Widths [500 500] /Encoding << /BaseEncoding /MacRomanEncoding /Differences [1 /Aacute /uni0151] >>",
        )
        val flate = PdfTestWriter().apply {
            add("<< /Type /Catalog /Pages 2 0 R >>")
            add("<< /Type /Pages /Kids [3 0 R] /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> >>")
            add("<< /Type /Page /Contents 5 0 R >>")
            add("<< $courier >>")
            stream(zlib(song.encodeToByteArray()), "/Filter /FlateDecode")
        }
        return listOf(
            PdfTestWriter.song(song),
            PdfTestWriter.song(song, brokenXref = true),
            objectStream.writeXrefStream(mapOf(4 to (6 to 0)), predictor = false),
            objectStream.writeXrefStream(mapOf(4 to (6 to 0)), predictor = true),
            pages.write(),
            type0,
            differences,
            flate.write(),
        )
    }

    private suspend fun zlib(data: ByteArray) = ByteArrayBuilder().also { builder ->
        builder.u8(0x78)
        builder.u8(0x01)
        Deflater().deflate(data, builder::u8)
    }.build()

    private fun ByteArray.toHex() = joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }

    private companion object {
        const val MUTATIONS = 2_000
        val NUMBER = Regex("-?[0-9]+(\\.[0-9]+)?")
        val REFERENCE = Regex("[0-9]+ [0-9]+ R")
        val NAME = Regex("/[A-Za-z0-9]+")
        val KEYWORD = Regex("\\b(obj|endobj|stream|endstream|xref|trailer|startxref|BT|ET|Tf|Tj|TJ|Td|Tm|Do|BDC|EMC|BI|ID|EI|q|Q|cm|begincodespacerange|beginbfchar|beginbfrange|endbfrange)\\b|<<|>>|\\[|\\]")
        val OBJECT = Regex("[0-9]+ [0-9]+ obj[\\s\\S]*?endobj")
        val HOSTILE_NUMBERS = listOf("-1", "0", "1", "2147483647", "2147483648", "4294967295", "99999999999999999999", "1e308", "-0", "65535", ".5", "100000", "1000001", "359")
        val NAMES = listOf(
            "/Type", "/Pages", "/Page", "/Kids", "/Contents", "/Font", "/Filter", "/FlateDecode", "/LZWDecode", "/ASCII85Decode", "/ObjStm", "/XRef",
            "/Prev", "/XRefStm", "/Length", "/Resources", "/XObject", "/Form", "/Subtype", "/DecodeParms", "/Predictor", "/W", "/Index", "/First", "/N",
            "/ToUnicode", "/DescendantFonts", "/Encoding", "/Differences", "/Root", "/Encrypt", "/Parent", "/MediaBox", "/Matrix", "/Type0", "/Identity-H",
        )
        val OPENERS = listOf("[", "<<", "(", "q ", "BT ", "/Span << /ActualText (x) >> BDC ", "<< /Type /Pages /Kids [", "1 0 obj\n")
    }
}
