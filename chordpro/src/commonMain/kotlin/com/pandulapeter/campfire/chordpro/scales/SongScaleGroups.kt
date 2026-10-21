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

import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProSong

/**
 * The scales a song can be played over, part by part: a song that one scale fits whole is one group, and a song that
 * modulates for its solo, or whose chorus leaves the verse's scale, is several, each a set of its sections with the
 * scales that fit them all.
 *
 * Groups are decided by a key, a home scale holding most of the chords with the rest named as exceptions, or, last, an
 * eight-note scale (see [fitOf]), so a song splits where its parts need different ones. Sections are grouped in the order
 * the song plays them, each joining the group whose chords it can share an answer with ([canJoin]): first one holding a
 * section with the same chords (a bridge that replays the intro goes with the intro), then one holding a section of its
 * own kind (a second verse goes with the first), then the group the section before it is in, then any other, so that a
 * verse after the solo goes back to the verses rather than starting a group of its own. A section that fits no group
 * starts one, and one no home scale holds two thirds of is left out. A group only a home with exceptions fits, of at
 * most three distinct chords, is a guess (two of three chords on a home), left out where the song has another group; a
 * rock riff is not one, its minor pentatonic being the answer. The root of a group is
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
    public fun of(song: ChordProSong): List<ScaleGroup> = playedGroupsOf(song).map { it.group }

    /** [of] with the chords each group plays, for [SongScaleSuggestions] to weigh them by. */
    internal fun playedGroupsOf(song: ChordProSong): List<PlayedGroup> {
        val parts = SongParts.of(song)
        if (parts.isEmpty()) return emptyList()
        fitOf(parts)?.takeIf { it.level == Level.TONAL }?.let { return listOf(it.playedGroupOf(parts)) }
        val groups = mutableListOf<Group>()
        parts.forEach { part ->
            val own = fitOf(listOf(part)) ?: return@forEach
            val chords = part.chords.toSet()
            val candidates = groups.sortedBy { group ->
                when {
                    group.parts.any { it.chords.toSet() == chords } -> 0
                    group.parts.any { it.kind == part.kind } -> 1
                    group === groups.last() -> 2
                    else -> 3
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
        val answers = groups.filter { !it.isGuess }
        return (answers.ifEmpty { groups }).map { it.fit.playedGroupOf(it.parts) }
    }

    /**
     * Whether [block] plays a chord [of] reads: a section with one among its lines, or a chorus recall whose sections
     * have one. What a viewer counts a song's parts over, so that a part no group holds is counted the same way.
     */
    public fun playsChords(block: ChordProBlock): Boolean = when (block) {
        is ChordProBlock.Section -> SongParts.playedIn(listOf(block)).isNotEmpty()
        is ChordProBlock.ChorusRecall -> SongParts.playedIn(block.blocks.filterIsInstance<ChordProBlock.Section>()).isNotEmpty()
        else -> false
    }

    /**
     * Whether a group fit as [group] and a part fit as [part] are one group fit as [combined]. Two parts each in a key
     * of their own stay apart unless one key holds both, so that a major verse and a Mixolydian chorus are two answers,
     * though a home scale would hold most of both. A part only a home scale with exceptions fits joins one the same
     * home on the same root fits, so a verse with a secondary dominant joins its key's chorus and a bridge in another
     * key does not, and a rock riff, led by the minor pentatonic rather than its home, joins only another. The eight-note
     * answers are kept among themselves, since their passing tone would join anything.
     */
    private fun canJoin(group: Fit, part: Fit, combined: Fit) = when {
        group.level == Level.TONAL && part.level == Level.TONAL -> combined.level == Level.TONAL
        group.level == Level.PASSING || part.level == Level.PASSING -> group.level == Level.PASSING && part.level == Level.PASSING
        // Half the chords answers a part on its own, a song of borrowed chords like G B C Cm, but a join of two parts needs
        // two thirds, or the floor would let a rock verse and a pre-chorus in another key share a home holding neither.
        combined.level == Level.THIN && (group.level != Level.THIN || part.level != Level.THIN) -> false
        // Riffs on one root are played over its minor pentatonic whatever home their chords lean to.
        listOf(group, part, combined).all { it.isRockRiff } -> listOf(group, part).all { it.root.pitchClass == combined.root.pitchClass }
        else -> combined.level != Level.PASSING && listOf(group, part).all {
            it.root.pitchClass == combined.root.pitchClass && familyOf(it.home) == familyOf(combined.home) && it.isRockRiff == combined.isRockRiff
        }
    }

    /** What a home scale is of, for [canJoin]: the minor family is one home, every other scale its own. */
    private fun familyOf(scale: Scale) = if (scale.id in minorFamilyIds) NATURAL_MINOR_ID else scale.id

    /**
     * The scales to play over [parts] and the root they are heard in, or null where nothing fits them. The key in force
     * at the first part (`{key}`, or the key a `{transpose}` names) is the author's word for the root, so every answer
     * on it is tried first ([keyFitOf]); only where none holds the part, a modulated section, is the root guessed from
     * the chords ([guessedFitOf]), a vamp on one chord answered as the chord's own sheet is ([oneChordFitOf]).
     */
    private fun fitOf(parts: List<SongPart>): Fit? {
        val harmony = Harmony(parts)
        return powerRiffFitOf(harmony) ?: harmony.key?.let { keyFitOf(harmony, it) } ?: oneChordFitOf(harmony) ?: guessedFitOf(harmony)
    }

    /**
     * The fit of a part that plays one chord, on its root: what the chord's sheet offers, its quality's textbook scales
     * first ([ChordScaleChoices]), so a `Dm7` vamp is Dorian before the minor key, as every player names it, and an
     * `E9` groove Mixolydian. A key in force says otherwise and is read before this.
     */
    private fun oneChordFitOf(harmony: Harmony): Fit? {
        val chord = harmony.chords.distinct().singleOrNull() ?: return null
        val scales = PlayableScales.over(chord)
        val home = scales.firstOrNull { it.intervals.size <= HOME_SCALE_SIZE } ?: return null
        return Fit(harmony.roots.first(), scales, Level.TONAL, home)
    }

    /**
     * The rock riff fit of a part where half the chords have no third, on the key's root (else the best scored one),
     * read before any scale holding it: chords without a third fit a dozen modes, none of them the style. A riff of
     * triads keeps its third, a G F C vamp being Mixolydian, so it is read in turn ([looseFitOf]).
     */
    private fun powerRiffFitOf(harmony: Harmony): Fit? {
        if (harmony.chords.count { it.hasNoThird } * 2 < harmony.chords.size) return null
        val root = harmony.key?.root ?: harmony.ranked.first()
        if (!PlayableScales.isRockRiff(root.pitchClass, harmony.chords)) return null
        return looseFitOf(harmony, listOf(root to looseCountsOn(harmony, root)))
    }

    /** [fit] said as natural minor where its home is harmonic or melodic minor, see [guessedFitOf]. */
    private fun saidAsNaturalMinor(harmony: Harmony, fit: Fit) =
        (if (fit.home.id in raisedMinorIds) minorHomeFitOn(harmony, fit.root, Level.TONAL) else null) ?: fit

    /**
     * The fit on the root of [key], the first of: a scale of [key]'s third holding every chord ([Level.TONAL]: major
     * or Mixolydian for a major key; natural minor, harmonic minor, or Dorian for a minor one), the minor family
     * holding each chord of a minor key, a dominant blues, and a home with exceptions ([looseFitOf]): the key's own
     * scale, major or the minor family, or its mode (Mixolydian, Dorian) where the key's tonic chord is played and the
     * mode accounts for [MIN_MODE_LEAD] chords more than the key's scale, or the tonic is mostly a dominant seventh (any
     * home for a rock riff, whose third is the style's, not the key's): given a key of C and a chart of mostly C, F, and
     * G, a player wants C major, a passing `C7` or a ♭VI–♭VII cadence being a moment, not a change of mode. A part in the relative minor of a
     * major key is then said in the key, its V named as an exception, since its notes are the key's but that one; a mode
     * with exceptions and no tonic chord is no reading of a key, or capo shapes in C under `{key: D}` would read as D
     * Mixolydian.
     */
    private fun keyFitOf(harmony: Harmony, key: SongKey): Fit? {
        val root = key.root
        harmony.holding(root, if (key.isMinor) minorKeyScaleIds + DORIAN_ID else majorKeyScaleIds + MIXOLYDIAN_ID)?.let { home ->
            return tonalFitOn(harmony, root, home)?.let { saidAsNaturalMinor(harmony, it) }
        }
        if (key.isMinor) minorFamilyFitOn(harmony, root)?.let { return it }
        if (PlayableScales.isDominantBlues(root.pitchClass, harmony.chords)) suggestedFitOn(harmony, root)?.let { return it }
        val isRockRiff = PlayableScales.isRockRiff(root.pitchClass, harmony.chords)
        val third = if (key.isMinor) MINOR_THIRD else MAJOR_THIRD
        val playsTonic = harmony.chords.any { it.root == root.pitchClass && third in it.intervals }
        val mode = if (key.isMinor) DORIAN_ID else MIXOLYDIAN_ID
        val homes = setOfNotNull(if (key.isMinor) NATURAL_MINOR_ID else MAJOR_ID, mode.takeIf { playsTonic })
        val counts = looseCountsOn(harmony, root) { isRockRiff || it.id in homes }
        val keyScale = looseHomes.indexOfFirst { it.id == (if (key.isMinor) NATURAL_MINOR_ID else MAJOR_ID) }
        val modeIndex = looseHomes.indexOfFirst { it.id == mode }
        val onTonic = harmony.chords.filter { it.root == root.pitchClass }
        val tonicIsSeventh = onTonic.count { it.isDominantSeventh } > onTonic.count { !it.isDominantSeventh }
        val preferred = if (isRockRiff || tonicIsSeventh || counts[modeIndex] >= counts[keyScale] + MIN_MODE_LEAD) {
            counts
        } else {
            counts.mapIndexed { index, count -> if (index == modeIndex) -1 else count }
        }
        return looseFitOf(harmony, listOf(root to preferred))
    }

    /**
     * The fit of a part whose root nothing names or the key's root fits nothing of, the first of:
     *
     * - **A key** ([Level.TONAL], [tonalFitOf]): a scale of at most seven notes holds every chord, or they are a dominant
     *   blues. A harmonic or melodic minor home is said as natural minor, the chords it leaves out named with the minor
     *   scale that holds them, since a player hears a minor song's major V as an exception, not as another scale.
     * - **The minor family** ([Level.TONAL]): natural, harmonic, or melodic minor on the best scored root holds each
     *   chord, the home natural minor and the rest named as in a key.
     * - **A home with exceptions** ([Level.LOOSE], [Level.THIN] below two thirds): on the best scored root or the first
     *   chord's, whichever a home scale holds more of by itself, the minor family and the chords leading into a home
     *   left out (the scores' favourite on a tie, as half the chords is a floor it often clears with a worse home than
     *   the first chord's: F major for C F G E). Only these roots, never further down the scores, or `C A7 Dm G7` would
     *   read as D minor.
     * - **A passing tone** ([Level.PASSING]): an eight-note scale on the best scored root, which holds a passing tone
     *   no scale of seven does. It answers what scale a part is in no better than none, so it never joins a key.
     */
    private fun guessedFitOf(harmony: Harmony): Fit? {
        val roots = listOfNotNull(harmony.ranked.first(), harmony.roots.first())
            .distinctBy { it.pitchClass }
            .map { it to looseCountsOn(harmony, it) }
            // Sorted by what a home scale itself holds: with the leaders counted, `C F G E` would tie F major with C major
            // and the tie would go to F, the scores' favourite, and with the minor family as one home, A's harmonic minor
            // holding the E7 would put Fly Me to the Moon on A before C.
            .sortedByDescending { (root, _) -> looseHomes.maxOf { home -> scaleCountOn(harmony, root, home) } }
        return tonalFitOf(harmony)?.let { saidAsNaturalMinor(harmony, it) }
            ?: minorFamilyFitOn(harmony, harmony.ranked.first())
            ?: looseFitOf(harmony, roots)
            ?: harmony.ranked.first().let { root ->
                PlayableScales.suggestionFor(root.pitchClass, harmony.chords)?.let { Fit(root, it.scales, Level.PASSING, it.scales.first()) }
            }
    }

    /**
     * The fit of a scale of at most seven notes holding every chord of [harmony], or a dominant blues, on the root its
     * chords are heard in, its scales led by the scale that decided the root (the fit's home), or null where none is.
     *
     * The root is the best scored root ([rankedRoots]) a major, natural minor, or harmonic minor scale holds the chords
     * on, unless the first chord's root ranks above it and Mixolydian or Dorian holds them there; else the best scored
     * root with a fit. Lydian, Phrygian, and Locrian are never chosen as a centre, since a chart almost never sits on
     * them, and a mode is taken only where the part starts on it, which every modal song a player calls modal does.
     */
    private fun tonalFitOf(harmony: Harmony): Fit? {
        val ranked = harmony.ranked
        val homeIndex = ranked.indexOfFirst { harmony.holding(it, homeScaleIds) != null }
        val modeRoot = ranked.firstOrNull { it.pitchClass == harmony.roots.first().pitchClass }
            ?.takeIf { homeIndex == -1 || ranked.indexOf(it) < homeIndex }
        modeRoot?.let { root -> harmony.holding(root, modeScaleIds)?.let { return tonalFitOn(harmony, root, it) } }
        if (homeIndex != -1) ranked[homeIndex].let { root -> return tonalFitOn(harmony, root, harmony.holding(root, homeScaleIds)!!) }
        return ranked.firstNotNullOfOrNull { root -> suggestedFitOn(harmony, root) }
    }

    /** The fit of [home] holding every chord on [root], or of the blues where it is a major scale holding a blues. */
    private fun tonalFitOn(harmony: Harmony, root: SpelledNote, home: Scale): Fit? {
        if (home.id == MAJOR_ID && PlayableScales.isDominantBlues(root.pitchClass, harmony.chords)) return suggestedFitOn(harmony, root)
        return PlayableScales.suggestionFor(root.pitchClass, harmony.chords)?.let { Fit(root, listOf(home) + (it.scales - home), Level.TONAL, home) }
    }

    /**
     * What [PlayableScales] suggests on [root] where it is a fit, a dominant blues being one no scale need hold. A blues's
     * home is the first scale it suggests, its minor pentatonic, so it never reads as a rock riff where a major scale
     * holds it; a jazz blues names what its Mixolydian leaves out, the IV7 and V7 too, so they keep their moments ahead
     * of a passing diminished chord, while a plain one has no exceptions, its IV7 and V7 being the shape itself.
     */
    private fun suggestedFitOn(harmony: Harmony, root: SpelledNote) = PlayableScales.suggestionFor(root.pitchClass, harmony.chords)?.takeIf { it.isFit }?.let { suggestion ->
        val isBlues = PlayableScales.isDominantBlues(root.pitchClass, harmony.chords)
        val home = if (isBlues) {
            suggestion.scales.first()
        } else {
            suggestion.scales.firstOrNull { it.intervals.size <= HOME_SCALE_SIZE && harmony.holds(it, root) } ?: suggestion.scales.first()
        }
        val isJazzBlues = isBlues && !PlayableScales.isPlainBlues(root.pitchClass, harmony.chords)
        Fit(root, suggestion.scales, Level.TONAL, home, if (isJazzBlues) harmony.exceptionsTo(mixolydian, root) else emptyList())
    }

    /** The minor family's fit on [root], where each chord is held by one of it. */
    private fun minorFamilyFitOn(harmony: Harmony, root: SpelledNote): Fit? {
        if (harmony.chords.any { chord -> minorFamily.none { holdsChord(it, root.pitchClass, chord) } }) return null
        return minorHomeFitOn(harmony, root, Level.TONAL)
    }

    /** [harmony] said as natural minor on [root], or null where it holds none of its chords. */
    private fun minorHomeFitOn(harmony: Harmony, root: SpelledNote, level: Level): Fit? {
        val home = naturalMinor
        val held = harmony.chords.filter { holdsChord(home, root.pitchClass, it) }
        if (held.isEmpty()) return null
        return Fit(root, listOf(home) + (PlayableScales.over(root.pitchClass, held) - home), level, home, harmony.exceptionsTo(home, root))
    }

    /** How many chords of [harmony] [home] on [root] holds by itself, for [guessedFitOf] to weigh its roots by. */
    private fun scaleCountOn(harmony: Harmony, root: SpelledNote, home: Scale) = harmony.chords.count { holdsChord(home, root.pitchClass, it) }

    /**
     * How many chords of [harmony] each of [looseHomes] accounts for on [root], -1 for a home [isAllowed] rules out: the
     * chords it holds and, where [Harmony.countsLeadersOn] says so, those leading into it ([leadsInto]) and the ii before
     * such a leader ([Harmony.isTwoOfLeaderInto]), the key's own harmony though the home leaves their notes out.
     */
    private fun looseCountsOn(harmony: Harmony, root: SpelledNote, isAllowed: (Scale) -> Boolean = { true }): List<Int> {
        val withLeaders = harmony.countsLeadersOn(root)
        return looseHomes.map { home ->
            val holders = if (home.id == NATURAL_MINOR_ID) minorFamily else listOf(home)
            if (!isAllowed(home)) {
                -1
            } else {
                harmony.chords.indices.count { index ->
                    val chord = harmony.chords[index]
                    holders.any { holdsChord(it, root.pitchClass, chord) } ||
                        withLeaders && (leadsInto(home, root.pitchClass, chord) || harmony.isTwoOfLeaderInto(home, root.pitchClass, index))
                }
            }
        }
    }

    /**
     * The home scale holding at least half of [harmony]'s chords with the rest as exceptions, on the first of [roots]
     * one does, each with what [looseCountsOn] counts on it: the first of major, natural minor (the minor family holding
     * a chord counting), Dorian, or Mixolydian accounting for the most, leaving out at most two distinct chords or a
     * third of them, except on the key's root, where the author's word stands however many it leaves out; the chords it
     * leaves out are named with the scales to play over each. A chord leading into the home is the home's for that cap
     * and for the two-thirds level, never for the half floor: a key holds half the chords by itself, or `E A B E` under
     * `{key: C}` would be C major, each chord leading into a note of it.
     */
    private fun looseFitOf(harmony: Harmony, roots: List<Pair<SpelledNote, List<Int>>>): Fit? {
        val total = harmony.chords.size
        return roots.firstNotNullOfOrNull { (root, counts) ->
            val isRockRiff = PlayableScales.isRockRiff(root.pitchClass, harmony.chords)
            // Of the homes holding as many of a riff's chords, Mixolydian (its major chords' third and flat seventh) is its
            // sound, or natural minor where no chord has a major third.
            val riffHome = if (harmony.chords.any { MAJOR_THIRD in it.intervals }) mixolydian else naturalMinor
            val best = looseHomes.indexOf(riffHome).takeIf { isRockRiff && counts[it] == counts.max() } ?: counts.indexOf(counts.max())
            val holdersOfBest = if (looseHomes[best].id == NATURAL_MINOR_ID) minorFamily else listOf(looseHomes[best])
            val own = harmony.chords.count { chord -> holdersOfBest.any { holdsChord(it, root.pitchClass, chord) } }
            if (own * 2 < total) return@firstNotNullOfOrNull null
            val level = if (counts[best] * 3 < total * 2) Level.THIN else Level.LOOSE
            val home = looseHomes[best]
            // The floor counts how often a chord is played, and so do the distinct chords the home leaves out, the minor
            // family as one home in both: more than two of them must stay within a third of the distinct chords, past
            // which the home says nothing about the part. A home holding less than two thirds counts only itself, or
            // the family's three scales between them would hold half of any minor chords at all (C Ebm F#m A Dbm Em).
            val distinct = harmony.chords.distinct()
            val holders = if (home.id == NATURAL_MINOR_ID && level == Level.LOOSE) minorFamily else listOf(home)
            val withLeaders = harmony.countsLeadersOn(root)
            val outside = distinct.count { chord ->
                holders.none { holdsChord(it, root.pitchClass, chord) } && !(withLeaders && leadsInto(home, root.pitchClass, chord))
            }
            val isKeyRoot = withLeaders && harmony.key?.root?.pitchClass == root.pitchClass
            if (!isKeyRoot && outside > MAX_LOOSE_EXCEPTIONS && outside * 3 > distinct.size) return@firstNotNullOfOrNull null
            val held = harmony.chords.filter { holdsChord(home, root.pitchClass, it) }
            // The chords left out are what makes a rock riff: its minor pentatonic and blues are played over all of them,
            // the flat third against the chords' major one being the style, so they lead and nothing is an exception.
            // The home still decides what the part joins.
            if (isRockRiff) {
                val scales = (PlayableScales.rockRiffScales + home + PlayableScales.over(root.pitchClass, held)).distinct()
                return@firstNotNullOfOrNull Fit(root, scales, level, home)
            }
            if (home.id == NATURAL_MINOR_ID) return@firstNotNullOfOrNull minorHomeFitOn(harmony, root, level)
            Fit(root, listOf(home) + (PlayableScales.over(root.pitchClass, held) - home), level, home, harmony.exceptionsTo(home, root))
        }
    }

    private class Group(val parts: MutableList<SongPart>, var fit: Fit) {
        /** A home leaving out one of at most three chords, see [of]; a rock riff, which has no exceptions, is no guess. */
        val isGuess get() = (fit.level == Level.LOOSE || fit.level == Level.THIN) && fit.exceptions.isNotEmpty() && parts.flatMap { it.chords }.distinct().size <= MAX_GUESSED_CHORDS
    }

    /**
     * What [fitOf] answers: the [scales] on [root], the [home] scale that decided them, and the chords it leaves out.
     */
    private class Fit(
        val root: SpelledNote,
        val scales: List<Scale>,
        val level: Level,
        val home: Scale,
        val exceptions: List<ScaleException> = emptyList(),
    ) {
        /** Whether this is a rock riff's fit, led by the minor pentatonic rather than its home. */
        val isRockRiff get() = home != scales.first()

        fun playedGroupOf(parts: List<SongPart>) = PlayedGroup(
            group = ScaleGroup(parts.flatMap { it.blocks }, root.name, scales, exceptions),
            rootPitchClass = root.pitchClass,
            names = parts.flatMap { it.names },
            rootNames = parts.flatMap { part -> part.roots.map { it.name } },
            chords = parts.flatMap { it.chords },
            parts = parts.map { part ->
                val centre = rankedRoots(listOf(part)).first()
                PlayedPart(
                    blocks = part.blocks,
                    // The rest of a section a `{transpose}` cut is a part of its own, played once like any other.
                    plays = part.blocks.withIndex().count { (index, block) -> index == 0 || block !is ChordProBlock.Section || !block.isContinuation },
                    names = part.names.toSet(),
                    chords = part.chords.toList(),
                    centre = centre.pitchClass,
                    centreName = centre.name,
                )
            },
        )
    }

    /** How sure a fit is of a part's key, see [fitOf]. */
    private enum class Level { TONAL, LOOSE, THIN, PASSING }

    private const val HOME_SCALE_SIZE = 7
    private const val MINOR_THIRD = 3
    private const val MAJOR_THIRD = 4
    private const val MAX_LOOSE_EXCEPTIONS = 2
    private const val MAX_GUESSED_CHORDS = 3
    private const val MIN_MODE_LEAD = 2
    private const val NATURAL_MINOR_ID = "natural_minor"
    private const val MAJOR_ID = "major"
    private const val DORIAN_ID = "dorian"
    private const val MIXOLYDIAN_ID = "mixolydian"
    private val naturalMinor = Scales.fromId(NATURAL_MINOR_ID)!!
    private val majorKeyScaleIds = listOf("major")
    private val minorKeyScaleIds = listOf(NATURAL_MINOR_ID, "harmonic_minor")
    private val homeScaleIds = listOf("major", NATURAL_MINOR_ID, "harmonic_minor")
    private val modeScaleIds = listOf("mixolydian", "dorian")
    private val raisedMinorIds = setOf("harmonic_minor", "melodic_minor")
    private val minorFamilyIds = listOf(NATURAL_MINOR_ID, "harmonic_minor", "melodic_minor")
    private val minorFamily = minorFamilyIds.map(Scales::fromId).requireNoNulls()
    private val looseHomes = listOf("major", NATURAL_MINOR_ID, "dorian", "mixolydian").map(Scales::fromId).requireNoNulls()
    private val mixolydian = looseHomes.last()
}
