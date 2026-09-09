package com.pandulapeter.campfire.chordpro

/**
 * Splits a file that contains several songs separated by `{new_song}` / `{ns}` directives.
 */
object ChordProSplitter {

    fun split(text: String): List<String> {
        val parts = mutableListOf<MutableList<String>>(mutableListOf())
        ChordProSyntax.splitLines(text).forEach { rawLine ->
            val directive = ChordProSyntax.matchDirective(rawLine.trim())
            if (directive != null && (directive.name == "new_song" || directive.name == "ns")) {
                parts += mutableListOf<String>()
            } else {
                parts.last() += rawLine
            }
        }
        return parts.map { part -> part.dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }.joinToString("\n") }
            .filter { it.isNotBlank() }
    }
}
