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

/** A bounded XML pull reader. DTDs and non-predefined entities are deliberately unsupported. */
internal class XmlPullParser(private val xml: String) {
    sealed interface Event {
        data class Start(val name: String, val attributes: Map<String, String>) : Event
        data class End(val name: String) : Event
        data class Text(val value: String) : Event
    }
    private var position = 0
    private var pendingEnd: String? = null
    private val stack = mutableListOf<String>()
    private var count = 0

    init { require(xml.length <= 8 shl 20) { "XML too large" } }

    fun next(): Event? {
        require(++count <= 200_000) { "Too many XML events" }
        pendingEnd?.let { name -> pendingEnd = null; return Event.End(name) }
        while (position < xml.length) {
            if (xml[position] != '<') {
                val end = xml.indexOf('<', position).takeIf { it >= 0 } ?: xml.length
                val text = entities(xml.substring(position, end))
                position = end
                return Event.Text(text)
            }
            when {
                xml.startsWith("<!--", position) -> skip("-->", 4)
                xml.startsWith("<?", position) -> skip("?>", 2)
                xml.startsWith("<![CDATA[", position) -> {
                    val end = xml.indexOf("]]>", position + 9)
                    require(end >= 0)
                    val text = xml.substring(position + 9, end)
                    position = end + 3
                    return Event.Text(text)
                }
                xml.startsWith("<!", position) -> error("DTD is not supported")
                xml.startsWith("</", position) -> {
                    position += 2
                    val name = name()
                    whitespace()
                    require(xml.getOrNull(position++) == '>')
                    require(stack.removeAt(stack.lastIndex) == name) { "Mismatched XML element" }
                    return Event.End(name)
                }
                else -> {
                    position++
                    val name = name()
                    val attributes = mutableMapOf<String, String>()
                    whitespace()
                    while (xml.getOrNull(position) !in listOf('>', '/')) {
                        require(attributes.size < 100)
                        val key = name()
                        whitespace()
                        require(xml.getOrNull(position++) == '=')
                        whitespace()
                        val quote = xml.getOrNull(position++)
                        require(quote == '\'' || quote == '"')
                        val end = xml.indexOf(quote, position)
                        require(end >= 0)
                        require(attributes.put(key, entities(xml.substring(position, end))) == null)
                        position = end + 1
                        whitespace()
                    }
                    if (xml.getOrNull(position) == '/') { position++; pendingEnd = name }
                    else { stack += name; require(stack.size <= 64) { "XML too deep" } }
                    require(xml.getOrNull(position++) == '>')
                    return Event.Start(name, attributes)
                }
            }
        }
        require(stack.isEmpty()) { "Truncated XML" }
        return null
    }

    private fun whitespace() { while (xml.getOrNull(position)?.isWhitespace() == true) position++ }
    private fun name(): String {
        val start = position
        while (xml.getOrNull(position)?.let { it.isLetterOrDigit() || it in "_:.-" } == true) position++
        require(position > start) { "Missing XML name" }
        return xml.substring(start, position)
    }
    private fun skip(endMarker: String, prefix: Int) {
        val end = xml.indexOf(endMarker, position + prefix)
        require(end >= 0)
        position = end + endMarker.length
    }
    private fun entities(text: String): String = buildString {
        var index = 0
        while (index < text.length) {
            if (text[index] != '&') { append(text[index++]); continue }
            val end = text.indexOf(';', index)
            require(end in (index + 1)..(index + 16))
            val entity = text.substring(index + 1, end)
            when (entity) {
                "amp" -> append('&')
                "lt" -> append('<')
                "gt" -> append('>')
                "quot" -> append('"')
                "apos" -> append('\'')
                else -> {
                    require(entity.startsWith('#')) { "Unknown XML entity" }
                    val code = if (entity.startsWith("#x")) entity.drop(2).toInt(16) else entity.drop(1).toInt()
                    require(code in 1..0x10ffff && code !in 0xd800..0xdfff)
                    if (code <= 0xffff) append(code.toChar()) else {
                        append((0xd800 + ((code - 0x10000) shr 10)).toChar())
                        append((0xdc00 + ((code - 0x10000) and 1023)).toChar())
                    }
                }
            }
            index = end + 1
        }
    }
}

internal data class XmlElement(val name: String, val attributes: Map<String, String>, val children: MutableList<XmlElement> = mutableListOf(), var text: String = "") {
    val localName get() = name.substringAfter(':')
    fun child(name: String) = children.firstOrNull { it.localName == name }
    fun attribute(name: String) = attributes.entries.firstOrNull { it.key.substringAfter(':') == name }?.value
}

internal fun parseXml(xml: String): XmlElement {
    val reader = XmlPullParser(xml.removePrefix("\ufeff"))
    val stack = mutableListOf<XmlElement>()
    // A comment or a CDATA section ends one text event and starts another, so an element's text may arrive in a great
    // many pieces, and concatenating them one by one would copy it again for every piece.
    val texts = mutableListOf<StringBuilder>()
    var root: XmlElement? = null
    while (true) when (val event = reader.next() ?: break) {
        is XmlPullParser.Event.Start -> {
            val element = XmlElement(event.name, event.attributes)
            if (stack.isEmpty()) { require(root == null); root = element } else stack.last().children += element
            stack += element
            texts += StringBuilder()
        }
        is XmlPullParser.Event.End -> {
            require(stack.last().name == event.name)
            val element = stack.removeAt(stack.lastIndex)
            val text = texts.removeAt(texts.lastIndex)
            if (text.isNotEmpty()) element.text = text.toString()
        }
        is XmlPullParser.Event.Text -> {
            if (stack.isEmpty()) require(event.value.isBlank()) else texts.last().append(event.value)
        }
    }
    return requireNotNull(root)
}
