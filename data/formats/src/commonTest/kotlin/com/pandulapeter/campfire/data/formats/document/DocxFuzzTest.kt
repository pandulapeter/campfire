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

import com.pandulapeter.campfire.data.formats.zip.Mutator
import com.pandulapeter.campfire.data.formats.zip.ZipEntry
import com.pandulapeter.campfire.data.formats.zip.ZipWriter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.fail

/**
 * Word documents built from Word's own elements at random and then damaged, as markup and as archives: each is read or
 * refused with an [Exception], never failing in any other way.
 */
class DocxFuzzTest {

    @Test
    fun `generated and damaged markup is read or refused`() = runTest {
        repeat(DOCUMENTS) { seed ->
            val random = Random(seed)
            var xml = document(random)
            var styles = STYLES
            val damages = random.nextInt(4)
            repeat(damages) {
                if (random.nextBoolean()) xml = damage(random, xml) else styles = damage(random, styles)
            }
            // An undamaged document is read whole, so the generator stays a source of documents worth damaging.
            if (damages == 0) DocxTextExtractor.fromXml(xml, styles) else read(seed) { DocxTextExtractor.fromXml(xml, styles) }
        }
    }

    @Test
    fun `damaged archives are read or refused`() = runTest {
        val archives = List(4) { seed ->
            ZipWriter.write(
                listOf(
                    ZipEntry("[Content_Types].xml", "<Types/>".encodeToByteArray()),
                    ZipEntry("word/document.xml", document(Random(seed)).encodeToByteArray()),
                    ZipEntry("word/styles.xml", STYLES.encodeToByteArray()),
                ),
            )
        }
        repeat(DOCUMENTS) { seed ->
            val mutator = Mutator(seed)
            val archive = mutator.mutate(archives[seed % archives.size])
            read(seed) { DocxTextExtractor.extract(archive) }
        }
    }

    private suspend fun read(seed: Int, extract: suspend () -> Unit) {
        try {
            extract()
        } catch (exception: CancellationException) {
            fail("Seed $seed was cancelled", exception)
        } catch (_: Exception) {
        } catch (error: Throwable) {
            fail("Seed $seed failed with $error", error)
        }
    }

    /** A body of paragraphs, runs, tables, and content controls, nested as deep as the generator happens to go. */
    private fun document(random: Random): String {
        fun text() = TEXTS[random.nextInt(TEXTS.size)]
        fun run(): String = "<w:r>" + (if (random.nextBoolean()) RUN_PROPERTIES[random.nextInt(RUN_PROPERTIES.size)] else "") +
            List(random.nextInt(1, 4)) { RUN_CONTENT[random.nextInt(RUN_CONTENT.size)].replace("TEXT", text()) }.joinToString("") + "</w:r>"
        fun paragraph(): String = "<w:p>" + (if (random.nextBoolean()) PARAGRAPH_PROPERTIES[random.nextInt(PARAGRAPH_PROPERTIES.size)] else "") +
            List(random.nextInt(0, 5)) { run() }.joinToString("") + "</w:p>"
        fun block(depth: Int): String = when (if (depth > 3) 0 else random.nextInt(5)) {
            0, 1 -> paragraph()
            2 -> "<w:tbl>" + List(random.nextInt(1, 4)) {
                "<w:tr>" + List(random.nextInt(1, 4)) { "<w:tc>" + block(depth + 1) + "</w:tc>" }.joinToString("") + "</w:tr>"
            }.joinToString("") + "</w:tbl>"
            3 -> "<w:sdt><w:sdtPr/><w:sdtContent>" + block(depth + 1) + "</w:sdtContent></w:sdt>"
            else -> "<w:p><w:r><mc:AlternateContent><mc:Choice><w:drawing><w:txbxContent>" + block(depth + 1) +
                "</w:txbxContent></w:drawing></mc:Choice><mc:Fallback>" + paragraph() + "</mc:Fallback></mc:AlternateContent></w:r></w:p>"
        }
        val body = List(random.nextInt(1, 30)) { block(0) }.joinToString("")
        return "<?xml version='1.0'?><w:document xmlns:w='urn:w' xmlns:mc='urn:mc'><w:body>$body<w:sectPr/></w:body></w:document>"
    }

    /** Markup cut, repeated, or interrupted by an element, an entity, or a character reference a reader is least prepared for. */
    private fun damage(random: Random, xml: String): String {
        val at = random.nextInt(xml.length + 1)
        return when (random.nextInt(5)) {
            0 -> xml.substring(0, at)
            1 -> xml.substring(0, at) + xml.substring(minOf(xml.length, at + random.nextInt(1, 40)))
            2 -> {
                val end = minOf(xml.length, at + random.nextInt(1, 400))
                xml.substring(0, end) + xml.substring(at, end).repeat(random.nextInt(1, 20)) + xml.substring(end)
            }
            else -> xml.substring(0, at) + INSERTIONS[random.nextInt(INSERTIONS.size)] + xml.substring(at)
        }
    }

    private companion object {
        const val DOCUMENTS = 1_500
        val TEXTS = listOf("Am", "[C]river", "light &amp; home", "שלום", "árvíztűrő", " ", "", "&#x1F3B8;", "G/B")
        val RUN_CONTENT = listOf(
            "<w:t>TEXT</w:t>", "<w:t xml:space='preserve'>TEXT </w:t>", "<w:tab/>", "<w:br/>", "<w:br w:type='page'/>", "<w:cr/>",
            "<w:noBreakHyphen/>", "<w:softHyphen/>", "<w:lastRenderedPageBreak/>", "<w:del><w:r><w:t>TEXT</w:t></w:r></w:del>",
        )
        val RUN_PROPERTIES = listOf(
            "<w:rPr><w:b/></w:rPr>", "<w:rPr><w:b w:val='0'/><w:sz w:val='NaN'/></w:rPr>", "<w:rPr><w:rStyle w:val='Chord'/></w:rPr>",
            "<w:rPr><w:rFonts w:ascii='Courier New'/><w:sz w:val='1e308'/></w:rPr>", "<w:rPr><w:vertAlign w:val='superscript'/></w:rPr>",
        )
        val PARAGRAPH_PROPERTIES = listOf(
            "<w:pPr><w:pStyle w:val='Title'/></w:pPr>", "<w:pPr><w:pStyle w:val='Loop'/></w:pPr>", "<w:pPr><w:pageBreakBefore/></w:pPr>",
            "<w:pPr><w:tabs><w:tab w:pos='720'/><w:tab w:pos='-5'/><w:tab w:pos='1e400'/><w:tab w:val='clear' w:pos='10'/></w:tabs></w:pPr>",
            "<w:pPr><w:sectPr><w:type w:val='nextPage'/></w:sectPr></w:pPr>",
        )
        val STYLES = "<w:styles xmlns:w='urn:w'><w:docDefaults><w:rPrDefault><w:rPr><w:sz w:val='24'/></w:rPr></w:rPrDefault></w:docDefaults>" +
            "<w:style w:type='paragraph' w:default='1' w:styleId='Normal'><w:name w:val='Normal'/></w:style>" +
            "<w:style w:type='paragraph' w:styleId='Title'><w:name w:val='Title'/><w:basedOn w:val='Normal'/><w:rPr><w:b/><w:sz w:val='40'/></w:rPr></w:style>" +
            "<w:style w:type='paragraph' w:styleId='Loop'><w:basedOn w:val='Loop2'/></w:style><w:style w:type='paragraph' w:styleId='Loop2'><w:basedOn w:val='Loop'/></w:style>" +
            "<w:style w:type='character' w:styleId='Chord'><w:rPr><w:rFonts w:hAnsi='Consolas'/></w:rPr></w:style></w:styles>"
        val INSERTIONS = listOf(
            "<w:p>", "</w:p>", "<w:tbl><w:tr><w:tc>", "<w:sdt><w:sdtContent>", "&#xD800;", "&#99999999999;", "&#x;", "&unknown;", "&amp",
            "<!DOCTYPE x>", "<![CDATA[", "]]>", "<!--", "<?pi", "<w:t a='1' a='2'>", "<w:t a=\"", "\u0000", "﻿", "<w:tab/>".repeat(50),
        )
    }
}
