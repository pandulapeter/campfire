/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.scales

import com.pandulapeter.campfire.chordpro.chords.ChordProChords
import com.pandulapeter.campfire.chordpro.model.Chord
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.chordpro.model.GridToken
import com.pandulapeter.campfire.chordpro.syntax.ChordProTokens
import com.pandulapeter.campfire.chordpro.tabs.ChordProTabTransposer

/**
 * The scales a song can be played over, part by part: a song that one scale fits whole is one group, and a song that
 * modulates for its solo, or whose chorus leaves the verse's scale, is several, each a set of its sections with the
 * scales that fit them all.
 *
 * Groups are decided by a key, a home scale holding most of the chords with the rest named as exceptions, or, last, an
 * eight-note scale (see [fitOf]), so a song splits where its parts need different ones. Sections are grouped in the order
 * the song plays them, each joining the group whose chords it can share an answer with ([canJoin]): first one holding a
 * section of its own kind (a second verse goes with the first), then the group the section before it is in, then any
 * other, so that a verse after the solo goes back to the verses rather than starting a group of its own. A section that
 * fits no group starts one, and one no home scale holds two thirds of is left out. The root of a group is
 * the key in force at its first section (the song's `{key}`, or the key the last `{transpose}` before it names) where
 * that key's scale holds its chords, else the root its chords are heard in, scored by how often, where, and after what
 * each is played (see [fitOf]).
 *
 * The song is read as it stands: every chord as written, and a `{transpose}` only for the key it names, never as a move
 * to make. The transposer has already moved the chords after one, and renamed its key, by the time a page shows them;
 * a song it has not been through reads as written, its chords and its `{transpose}` keys agreeing just the same.
 */
public object SongScaleGroups {

    /** The groups of [song], which is in the standard notation, in the order their first sections are played. */
    public fun of(song: ChordProSong): List<ScaleGroup> {
        val parts = partsOf(song)
        if (parts.isEmpty()) return emptyList()
        fitOf(parts)?.takeIf { it.level == Level.TONAL }?.let { return listOf(it.groupOf(parts)) }
        val groups = mutableListOf<Group>()
        parts.forEach { part ->
            val own = fitOf(listOf(part)) ?: return@forEach
            val candidates = groups.sortedBy { group ->
                when {
                    group.parts.any { it.kind == part.kind } -> 0
                    group === groups.last() -> 1
                    else -> 2
                }
            }
            val joined = candidates.firstNotNullOfOrNull { group ->
                fitOf(group.parts + part)?.takeIf { canJoin(group.fit, own, it) }?.let { group to it }
            }
            if (joined != null) {
                joined.first.parts += part
                joined.first.fit = joined.second
            } else {
                groups += Group(mutableListOf(part), own)
            }
        }
        return groups.map { it.fit.groupOf(it.parts) }
    }

    /**
     * Whether a group fit as [group] and a part fit as [part] are one group fit as [combined]. Two parts each in a key
     * of their own stay apart unless one key holds both, so that a major verse and a Mixolydian chorus are two answers,
     * though a home scale would hold most of both. A part only a home scale with exceptions fits joins one the same
     * home on the same root fits, so a verse with a secondary dominant joins its key's chorus and a bridge in another
     * key does not. The eight-note answers are kept among themselves, since their passing tone would join anything.
     */
    private fun canJoin(group: Fit, part: Fit, combined: Fit) = when {
        group.level == Level.TONAL && part.level == Level.TONAL -> combined.level == Level.TONAL
        group.level == Level.PASSING || part.level == Level.PASSING -> group.level == Level.PASSING && part.level == Level.PASSING
        else -> combined.level != Level.PASSING && listOf(group, part).all {
            it.root.pitchClass == combined.root.pitchClass && familyOf(it.home) == familyOf(combined.home)
        }
    }

    /** What a home scale is of, for [canJoin]: the minor family is one home, every other scale its own. */
    private fun familyOf(scale: Scale) = if (scale.id in minorFamilyIds) NATURAL_MINOR_ID else scale.id

    /**
     * The scales to play over [parts] and the root they are heard in, or null where nothing fits them, the first of:
     *
     * - **A key** ([Level.TONAL], [tonalFitOf]): a scale of at most seven notes holds every chord, or they are a dominant
     *   blues. A harmonic or melodic minor home is said as natural minor, the chords it leaves out named with the minor
     *   scale that holds them, since a player hears a minor song's major V as an exception, not as another scale.
     * - **The minor family** ([Level.TONAL]): natural, harmonic, or melodic minor on the minor key's root (else the best
     *   scored one) holds each chord, the home natural minor and the rest named as in a key.
     * - **A home with exceptions** ([Level.LOOSE]): on the key's root, the best scored one, or the first chord's, the
     *   first where major, natural minor (the minor family holding a chord counting), Dorian, or Mixolydian holds at
     *   least two thirds of the chords played; the chords it leaves out are named with the scales to play over each.
     *   Only these roots, never further down the scores, or `C A7 Dm G7` would read as D minor.
     * - **A passing tone** ([Level.PASSING]): an eight-note scale on the best scored root, which holds a passing tone
     *   no scale of seven does. It answers what scale a part is in no better than none, so it never joins a key.
     */
    private fun fitOf(parts: List<Part>): Fit? {
        val harmony = Harmony(parts)
        return tonalFitOf(harmony)?.let { fit -> (if (fit.home.id in raisedMinorIds) minorHomeFitOn(harmony, fit.root, Level.TONAL) else null) ?: fit }
            ?: minorFamilyFitOf(harmony)
            ?: looseFitOf(harmony)
            ?: harmony.ranked.first().let { root ->
                PlayableScales.suggestionFor(root.pitchClass, harmony.chords)?.let { Fit(root, it.scales, Level.PASSING, it.scales.first()) }
            }
    }

    /**
     * The fit of a scale of at most seven notes holding every chord of [harmony], or a dominant blues, on the root its
     * chords are heard in, its scales led by the scale that decided the root (the fit's home), or null where none is.
     *
     * The root is, in this order: the key in force at the first part where its own scale holds the chords (major, or
     * natural then harmonic minor for a minor key); the best scored root ([rankedRoots]) a major, natural minor, or
     * harmonic minor scale holds the chords on, unless the first chord's root or the key's ranks above it and Mixolydian
     * or Dorian holds them there; else the best scored root with a fit. Lydian, Phrygian, and Locrian are never chosen
     * as a centre, since a chart almost never sits on them, and a mode is taken only where the part starts on it or the
     * key names it, which every modal song a player calls modal does.
     */
    private fun tonalFitOf(harmony: Harmony): Fit? {
        val key = harmony.key
        key?.let { harmony.holding(it.root, if (it.isMinor) minorKeyScaleIds else majorKeyScaleIds) }?.let { return tonalFitOn(harmony, key.root, it) }
        val ranked = harmony.ranked
        val homeIndex = ranked.indexOfFirst { harmony.holding(it, homeScaleIds) != null }
        val modeCandidates = listOfNotNull(harmony.roots.first().pitchClass, key?.root?.pitchClass)
        val modeRoots = (ranked.filter { it.pitchClass in modeCandidates } + listOfNotNull(key?.root))
            .distinctBy { it.pitchClass }
            .filter { homeIndex == -1 || ranked.indexOfFirst { root -> root.pitchClass == it.pitchClass } in 0 until homeIndex }
        modeRoots.firstNotNullOfOrNull { root -> harmony.holding(root, modeScaleIds)?.let { tonalFitOn(harmony, root, it) } }?.let { return it }
        if (homeIndex != -1) ranked[homeIndex].let { root -> return tonalFitOn(harmony, root, harmony.holding(root, homeScaleIds)!!) }
        return ranked.firstNotNullOfOrNull { root ->
            PlayableScales.suggestionFor(root.pitchClass, harmony.chords)?.takeIf { it.isFit }?.let { suggestion ->
                // A dominant blues is a fit no scale holds; its home is then the first it suggests.
                val home = suggestion.scales.firstOrNull { it.intervals.size <= HOME_SCALE_SIZE && harmony.holds(it, root) } ?: suggestion.scales.first()
                Fit(root, suggestion.scales, Level.TONAL, home)
            }
        }
    }

    private fun tonalFitOn(harmony: Harmony, root: Root, home: Scale) =
        PlayableScales.suggestionFor(root.pitchClass, harmony.chords)?.let { Fit(root, listOf(home) + (it.scales - home), Level.TONAL, home) }

    /** The minor family's fit on the minor key's root, else the best scored one, where each chord is held by one of it. */
    private fun minorFamilyFitOf(harmony: Harmony): Fit? {
        val root = harmony.key?.takeIf { it.isMinor }?.root ?: harmony.ranked.first()
        if (harmony.chords.any { chord -> minorFamily.none { holdsChord(it, root.pitchClass, chord) } }) return null
        return minorHomeFitOn(harmony, root, Level.TONAL)
    }

    /** [harmony] said as natural minor on [root], or null where it holds none of its chords. */
    private fun minorHomeFitOn(harmony: Harmony, root: Root, level: Level): Fit? {
        val home = naturalMinor
        val held = harmony.chords.filter { holdsChord(home, root.pitchClass, it) }
        if (held.isEmpty()) return null
        return Fit(root, listOf(home) + (PlayableScales.over(root.pitchClass, held) - home), level, home, exceptionsOf(harmony, root, home))
    }

    /** The home scale holding two thirds of [harmony]'s chords with the rest as exceptions, see [fitOf]. */
    private fun looseFitOf(harmony: Harmony): Fit? {
        val roots = listOfNotNull(harmony.key?.root, harmony.ranked.first(), harmony.roots.first()).distinctBy { it.pitchClass }
        val total = harmony.chords.size
        return roots.firstNotNullOfOrNull { root ->
            val counts = looseHomes.map { home ->
                val holders = if (home.id == NATURAL_MINOR_ID) minorFamily else listOf(home)
                harmony.chords.count { chord -> holders.any { holdsChord(it, root.pitchClass, chord) } }
            }
            val best = counts.indexOf(counts.max())
            if (counts[best] * 3 < total * 2) return@firstNotNullOfOrNull null
            val home = looseHomes[best]
            if (home.id == NATURAL_MINOR_ID) return@firstNotNullOfOrNull minorHomeFitOn(harmony, root, Level.LOOSE)
            val held = harmony.chords.filter { holdsChord(home, root.pitchClass, it) }
            // The chords left out are what makes a rock riff, so its minor pentatonic and blues are asked of all of them.
            val riff = if (PlayableScales.isRockRiff(root.pitchClass, harmony.chords)) PlayableScales.rockRiffScales else emptyList()
            val scales = (listOf(home) + riff + PlayableScales.over(root.pitchClass, held)).distinct()
            Fit(root, scales, Level.LOOSE, home, exceptionsOf(harmony, root, home))
        }
    }

    /**
     * Every root [parts] play, the one they are most likely heard in first. A root scores one for each chord on it but a
     * dominant seventh (a chord that leaves, rarely a tonic), one for the first chord and one for the last, and two for
     * every cadence into it, a chord with a major third a fifth above it followed by a chord on it, each part read as a
     * loop too, since a section is repeated. Ties go to more chords that are not a dominant seventh, then the first
     * chord's root, then the last one's.
     */
    private fun rankedRoots(parts: List<Part>): List<Root> {
        val played = parts.flatMap { it.roots }
        val first = played.first().pitchClass
        val last = played.last().pitchClass
        val scores = mutableMapOf<Int, Int>()
        val counts = mutableMapOf<Int, Int>()
        parts.forEach { part ->
            part.chords.forEachIndexed { index, chord ->
                if (!chord.isDominantSeventh) {
                    scores[chord.root] = (scores[chord.root] ?: 0) + 1
                    counts[chord.root] = (counts[chord.root] ?: 0) + 1
                }
                val next = part.chords[(index + 1) % part.chords.size]
                if (MAJOR_THIRD in chord.intervals && (chord.root - next.root).mod(12) == FIFTH) {
                    scores[next.root] = (scores[next.root] ?: 0) + CADENCE_SCORE
                }
            }
        }
        scores[first] = (scores[first] ?: 0) + 1
        scores[last] = (scores[last] ?: 0) + 1
        return played.distinctBy { it.pitchClass }.sortedWith(
            compareByDescending<Root> { scores[it.pitchClass] ?: 0 }
                .thenByDescending { counts[it.pitchClass] ?: 0 }
                .thenByDescending { it.pitchClass == first }
                .thenByDescending { it.pitchClass == last },
        )
    }

    private val Chord.isDominantSeventh get() = intervals.map { it.mod(12) }.let { MAJOR_THIRD in it && MINOR_SEVENTH in it }

    /**
     * The chords of [harmony] that [home] on [root] does not hold, the most played first, then in play order, each with
     * the scales to play over it. With a natural-minor home, the minor scales on its root that hold it (harmonic minor
     * and Dorian change one note of it, melodic minor two): a minor song's major V and IV are its own raised seventh and
     * sixth. Otherwise the scales of seven notes on the chord's own root that hold it, the common ones first, each kind
     * the closest to the home's notes first; the pentatonics and the blues a lone chord is offered would clash with the
     * key around it. A chord nothing holds is not named.
     */
    private fun exceptionsOf(harmony: Harmony, root: Root, home: Scale): List<ScaleException> {
        val homeNotes = Scales.pitchClasses(home, root.pitchClass)
        return harmony.chords.indices
            .filter { !homeNotes.containsAll(notesOf(harmony.chords[it])) }
            .groupBy { harmony.chords[it] }
            .entries
            .sortedByDescending { it.value.size }
            .mapNotNull { (chord, indices) ->
                val name = harmony.names[indices.first()]
                if (home.id == NATURAL_MINOR_ID) {
                    minorExceptionScales.filter { holdsChord(it, root.pitchClass, chord) }.takeIf { it.isNotEmpty() }?.let {
                        return@mapNotNull ScaleException(chordName = name, rootName = root.name, scales = it)
                    }
                }
                PlayableScales.over(chord)
                    .filter { it.intervals.size == HOME_SCALE_SIZE && holdsChord(it, chord.root, chord) }
                    .sortedWith(compareBy({ it !in Scales.common }, { scale -> Scales.pitchClasses(scale, chord.root).count { it !in homeNotes } }))
                    .takeIf { it.isNotEmpty() }
                    ?.let { ScaleException(chordName = name, rootName = harmony.roots[indices.first()].name, scales = it) }
            }
    }

    private fun notesOf(chord: Chord) = chord.intervals.mapTo(mutableSetOf()) { (chord.root + it).mod(12) }

    private fun holdsChord(scale: Scale, root: Int, chord: Chord) = Scales.pitchClasses(scale, root).containsAll(notesOf(chord))

    /**
     * Every section of [song] that plays a chord, its continuations with it, a chorus recall left out since it plays
     * the chorus again, each with the notes of its chords and the roots it plays, and the key in force at it.
     */
    private fun partsOf(song: ChordProSong): List<Part> {
        var key = song.metadata.key?.let(::keyOf)
        val parts = mutableListOf<Part>()
        song.blocks.forEach { block ->
            when (block) {
                is ChordProBlock.Transpose -> key = block.key?.let(::keyOf) ?: key
                is ChordProBlock.Section -> {
                    val chords = block.lines.flatMap { it.chordNames() }.mapNotNull { name ->
                        ChordProChords.parse(name)?.let { chord -> rootOf(name)?.let { PlayedChord(name, it, chord) } }
                    }
                    val previous = parts.lastOrNull()?.takeIf { block.isContinuation && it.sections.last().type == block.type }
                    when {
                        previous != null -> {
                            previous.sections += block
                            previous.add(chords)
                        }
                        chords.isNotEmpty() -> parts += Part(mutableListOf(block), key).apply { add(chords) }
                    }
                }
                else -> Unit
            }
        }
        return parts.filter { it.roots.isNotEmpty() }
    }

    /** The chord names one line plays, by what kind of line it is: the walk `ChordProChords.namesIn` takes. */
    private fun ChordProLine.chordNames(): List<String> = when (this) {
        is ChordProLine.Lyrics -> chords.filter { !it.isAnnotation }.map { it.name }
        is ChordProLine.Grid -> tokens.filterIsInstance<GridToken.Chord>().flatMap { ChordProTokens.cellChords(it.name) }
        is ChordProLine.Tab -> ChordProTabTransposer.chordNames(listOf(text))
        ChordProLine.Blank -> emptyList()
    }

    /**
     * The key [text] names, minor where what follows its root starts with a lowercase `m` other than `ma` (`Am`,
     * `A minor`, `A-moll`), so that `Amaj`, `A major`, and `A mayor` stay major.
     */
    private fun keyOf(text: String): Key? = rootOf(text)?.let { root ->
        val mode = text.trim().drop(root.name.length).trimStart('-', ' ')
        Key(root, isMinor = mode.startsWith('m') && mode.getOrNull(1) != 'a')
    }

    /** The root a chord name or a key starts with, spelled as written: `Eb` for `Ebm7` and for `Eb minor`. */
    private fun rootOf(name: String): Root? {
        val trimmed = name.trim()
        val letter = trimmed.firstOrNull()?.uppercaseChar()?.takeIf { it in 'A'..'G' } ?: return null
        val accidental = trimmed.getOrNull(1)?.takeIf { it == '#' || it == 'b' }
        val spelled = letter + (accidental?.toString() ?: "")
        return ChordProChords.pitchClass(spelled)?.let { Root(it, spelled) }
    }

    private class Part(val sections: MutableList<ChordProBlock.Section>, val key: Key?) {
        val names = mutableListOf<String>()
        val chords = mutableListOf<Chord>()
        val roots = mutableListOf<Root>()

        /** What a section is of, so that a second verse finds the first: its type, and its label without a number. */
        val kind get() = sections.first().let { it.type to it.label?.trimEnd { char -> char.isDigit() || char.isWhitespace() }?.lowercase() }

        fun add(played: List<PlayedChord>) = played.forEach { (name, root, chord) ->
            names += name
            roots += root
            // The bass of a slash chord is left out: it is a voicing, and a passing bass note is often outside the scale.
            chords += Chord(root = chord.root, intervals = chord.intervals)
        }
    }

    private data class PlayedChord(val name: String, val root: Root, val chord: Chord)

    /** The chords of a list of parts, read once for every fit tried on them. */
    private class Harmony(parts: List<Part>) {
        val key = parts.first().key
        val names = parts.flatMap { it.names }
        val chords = parts.flatMap { it.chords }
        val roots = parts.flatMap { it.roots }
        val ranked by lazy { rankedRoots(parts) }
        private val notes = chords.flatMapTo(mutableSetOf()) { notesOf(it) }

        fun holds(scale: Scale, root: Root) = Scales.pitchClasses(scale, root.pitchClass).containsAll(notes)

        /** The first of the scales [ids] names that holds every chord on [root]. */
        fun holding(root: Root, ids: List<String>) = ids.firstNotNullOfOrNull { id -> Scales.fromId(id)?.takeIf { holds(it, root) } }
    }

    private class Group(val parts: MutableList<Part>, var fit: Fit)

    /**
     * What [fitOf] answers: the [scales] on [root], the [home] scale that decided them, and the chords it leaves out.
     */
    private class Fit(
        val root: Root,
        val scales: List<Scale>,
        val level: Level,
        val home: Scale,
        val exceptions: List<ScaleException> = emptyList(),
    ) {
        fun groupOf(parts: List<Part>) = ScaleGroup(parts.flatMap { it.sections }, root.name, scales, exceptions)
    }

    /** How sure a fit is of a part's key, see [fitOf]. */
    private enum class Level { TONAL, LOOSE, PASSING }

    private class Root(val pitchClass: Int, val name: String)

    private class Key(val root: Root, val isMinor: Boolean)

    private const val HOME_SCALE_SIZE = 7
    private const val MAJOR_THIRD = 4
    private const val FIFTH = 7
    private const val MINOR_SEVENTH = 10
    private const val CADENCE_SCORE = 2
    private const val NATURAL_MINOR_ID = "natural_minor"
    private val naturalMinor = Scales.fromId(NATURAL_MINOR_ID)!!
    private val majorKeyScaleIds = listOf("major")
    private val minorKeyScaleIds = listOf(NATURAL_MINOR_ID, "harmonic_minor")
    private val homeScaleIds = listOf("major", NATURAL_MINOR_ID, "harmonic_minor")
    private val modeScaleIds = listOf("mixolydian", "dorian")
    private val raisedMinorIds = setOf("harmonic_minor", "melodic_minor")
    private val minorFamilyIds = listOf(NATURAL_MINOR_ID, "harmonic_minor", "melodic_minor")
    private val minorFamily = minorFamilyIds.map(Scales::fromId).requireNoNulls()
    private val looseHomes = listOf("major", NATURAL_MINOR_ID, "dorian", "mixolydian").map(Scales::fromId).requireNoNulls()
    private val minorExceptionScales = listOf("harmonic_minor", "dorian", "melodic_minor").map(Scales::fromId).requireNoNulls()
}
