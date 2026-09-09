package com.pandulapeter.campfire.chordpro.model

/**
 * A parsed ChordPro song: the directives that describe it plus the blocks that make up its body.
 */
data class ChordProSong(
    val metadata: ChordProMetadata,
    val blocks: List<ChordProBlock>
) {

    /** True if any lyrics or grid line contains at least one real chord (annotations don't count). */
    val hasChords: Boolean
        get() = blocks.any { block ->
            block is ChordProBlock.Section && block.lines.any { line ->
                when (line) {
                    is ChordProLine.Lyrics -> line.chords.any { !it.isAnnotation }
                    is ChordProLine.Grid -> line.tokens.any { it is GridToken.Chord }
                    is ChordProLine.Tab -> false
                    ChordProLine.Blank -> false
                }
            }
        }
}
