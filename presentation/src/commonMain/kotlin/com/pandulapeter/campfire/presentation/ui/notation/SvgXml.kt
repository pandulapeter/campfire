/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.notation

/** A node of an XML document as [parseSvgXml] reads it. */
internal sealed interface SvgNode

/** Character data, entities resolved. */
internal data class SvgText(val text: String) : SvgNode

/** An element, attribute names as written (`xlink:href`), values with their entities resolved. */
internal data class SvgElement(
    val name: String,
    val attributes: Map<String, String>,
    val children: List<SvgNode>,
) : SvgNode {
    val elements get() = children.filterIsInstance<SvgElement>()
    val text: String get() = children.joinToString(separator = "") { if (it is SvgText) it.text else (it as SvgElement).text }
}

/**
 * Reads the root element of an SVG document, or returns null for anything that is not well-formed: enough XML for what
 * abc2svg writes (elements, attributes in either quote, character data, the five named entities and numeric ones),
 * skipping a declaration, comments, and processing instructions. CDATA, a DOCTYPE, and namespaces are not read; a
 * document that has them is not one abc2svg wrote, and the caller shows the tune as its text.
 */
internal fun parseSvgXml(xml: String): SvgElement? = try {
    SvgXmlReader(xml).readDocument()
} catch (_: SvgXmlException) {
    null
}

private class SvgXmlException : Exception()

private class SvgXmlReader(private val xml: String) {

    private var position = 0

    fun readDocument(): SvgElement {
        skipMisc()
        val root = readElement()
        skipMisc()
        if (position != xml.length) throw SvgXmlException()
        return root
    }

    /** Whitespace, comments, and processing instructions (the XML declaration among them), wherever they may stand. */
    private fun skipMisc() {
        while (true) {
            while (position < xml.length && xml[position].isWhitespace()) position++
            when {
                xml.startsWith("<!--", position) -> position = indexAfter("-->")
                xml.startsWith("<?", position) -> position = indexAfter("?>")
                else -> return
            }
        }
    }

    private fun readElement(): SvgElement {
        expect('<')
        val name = readName()
        val attributes = LinkedHashMap<String, String>()
        while (true) {
            skipWhitespace()
            when {
                xml.startsWith("/>", position) -> {
                    position += 2
                    return SvgElement(name, attributes, emptyList())
                }

                peek() == '>' -> {
                    position++
                    return SvgElement(name, attributes, readChildren(name))
                }

                else -> {
                    val attribute = readName()
                    skipWhitespace()
                    expect('=')
                    skipWhitespace()
                    val quote = peek()
                    if (quote != '"' && quote != '\'') throw SvgXmlException()
                    position++
                    val end = xml.indexOf(quote, position).takeIf { it >= 0 } ?: throw SvgXmlException()
                    attributes[attribute] = decodeEntities(xml.substring(position, end))
                    position = end + 1
                }
            }
        }
    }

    private fun readChildren(name: String): List<SvgNode> {
        val children = mutableListOf<SvgNode>()
        while (true) {
            if (position >= xml.length) throw SvgXmlException()
            when {
                xml.startsWith("</", position) -> {
                    position += 2
                    if (readName() != name) throw SvgXmlException()
                    skipWhitespace()
                    expect('>')
                    return children
                }

                xml.startsWith("<!--", position) -> position = indexAfter("-->")
                xml.startsWith("<?", position) -> position = indexAfter("?>")
                xml.startsWith("<!", position) -> throw SvgXmlException()
                peek() == '<' -> children += readElement()
                else -> {
                    val end = xml.indexOf('<', position).takeIf { it >= 0 } ?: throw SvgXmlException()
                    children += SvgText(decodeEntities(xml.substring(position, end)))
                    position = end
                }
            }
        }
    }

    private fun readName(): String {
        val start = position
        while (position < xml.length && (xml[position].isLetterOrDigit() || xml[position] in NAME_PUNCTUATION)) position++
        if (position == start) throw SvgXmlException()
        return xml.substring(start, position)
    }

    private fun skipWhitespace() {
        while (position < xml.length && xml[position].isWhitespace()) position++
    }

    private fun peek() = if (position < xml.length) xml[position] else throw SvgXmlException()

    private fun expect(character: Char) {
        if (peek() != character) throw SvgXmlException()
        position++
    }

    private fun indexAfter(terminator: String) = xml.indexOf(terminator, position).takeIf { it >= 0 }?.plus(terminator.length) ?: throw SvgXmlException()

    private fun decodeEntities(raw: String): String {
        if ('&' !in raw) return raw
        val decoded = StringBuilder(raw.length)
        var index = 0
        while (index < raw.length) {
            val character = raw[index]
            if (character != '&') {
                decoded.append(character)
                index++
                continue
            }
            val end = raw.indexOf(';', index).takeIf { it > index } ?: throw SvgXmlException()
            val entity = raw.substring(index + 1, end)
            when {
                entity.startsWith("#x") -> decoded.appendCodePoint(entity.drop(2).toIntOrNull(16))
                entity.startsWith("#") -> decoded.appendCodePoint(entity.drop(1).toIntOrNull())
                else -> decoded.append(NAMED_ENTITIES[entity] ?: throw SvgXmlException())
            }
            index = end + 1
        }
        return decoded.toString()
    }

    private fun StringBuilder.appendCodePoint(codePoint: Int?) {
        if (codePoint == null || codePoint !in 0..0x10FFFF) throw SvgXmlException()
        if (codePoint < 0x10000) {
            append(codePoint.toChar())
        } else {
            val offset = codePoint - 0x10000
            append((0xD800 + (offset shr 10)).toChar())
            append((0xDC00 + (offset and 0x3FF)).toChar())
        }
    }

    private companion object {
        const val NAME_PUNCTUATION = ":-_."
        val NAMED_ENTITIES = mapOf("amp" to '&', "lt" to '<', "gt" to '>', "quot" to '"', "apos" to '\'')
    }
}
