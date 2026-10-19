# Guess a part's tonal centre by scoring its chord roots, trying the home scales before the modes, instead of taking the first root any scale fits

**Challenged:** amended — step 5 takes a mode only on the first chord's root or the key's root (All Along the
Watchtower `Am G F G` was G Mixolydian), and also where no root has a home scale (`Am D`, `Dm G` fell to melodic minor);
the twelve-bar claim corrected; tests added for both.

**Kind:** bug  ·  **Severity:** high  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/scales/SongScaleGroups.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/scales/SongScaleGroupsTest.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/scales/CLAUDE.md`

## Problem

Every song's Scales section (and the root of every chip in it) is decided by `SongScaleGroups.fitOf`, which walks the
candidate roots in a fixed order and takes the first one on which *any* suggested scale fits:

```kotlin
// SongScaleGroups.kt:74
private fun fitOf(parts: List<Part>, isStrict: Boolean): Fit? {
    val chords = parts.flatMap { it.chords }
    return rootsOf(parts).firstNotNullOfOrNull { root ->
        PlayableScales.suggestionFor(root.pitchClass, chords)?.takeIf { !isStrict || it.isFit }?.let { Fit(root, it.scales) }
    }
}

// SongScaleGroups.kt:81
private fun rootsOf(parts: List<Part>): List<Root> {
    val played = parts.flatMap { it.roots }
    val byCount = played.groupingBy { it.pitchClass }.eachCount()
    return (
        parts.mapNotNull { it.key } +
            listOfNotNull(parts.first().roots.firstOrNull(), parts.last().roots.lastOrNull()) +
            played.sortedByDescending { byCount[it.pitchClass] }
        ).distinctBy { it.pitchClass }
}
```

So the first chord's root wins whenever any mode on it holds the notes, Lydian and Mixolydian included, and a `{key}`
wins even for a section in another key. Measured at HEAD (a throwaway probe through `SongScaleGroups.of`; first five
chips shown):

| Song | Today | A player says |
|---|---|---|
| `F G C Am` | F: major_pentatonic, lydian | C major |
| `F G C F G C` | F: major_pentatonic, lydian | C major |
| Wish You Were Here, intro `C D Am G D C Am G` + verse `Em G Em G Em A7sus4` | C: major_pentatonic, lydian | G major |
| `G A D Bm` | G: major_pentatonic, lydian | D major |
| `{key: C}`, verse `C F G C`, chorus `G D Em C` | chorus on C: major_pentatonic, lydian | chorus in G major |
| `{key: C}`, verse `C F G C`, chorus `F Bb C F` | chorus on C: mixolydian, … | chorus in F major |
| `{key: D}` over `C Am F G` | D: dorian, minor_pentatonic, … | C major |

"major_pentatonic before lydian" is a second symptom: Lydian is not in `Scales.common`, so `PlayableScales` puts the
pentatonic inside it first (plan 03 reorders that list; this plan makes Lydian stop being the answer).

A3, Stairway intro `{key: Am}` `Am Am/G# Am/G D/F# Fmaj7 G Am` → root **D**, `bebop_dorian` only: its chords play eight
pitch classes (F and F♯), no seven-note scale holds them, A has no eight-note one either, so the walk falls through to
the next root that anything fits. That is fixed by plan 02 (A minor with an exception over the D); this plan only stops
the fall to an unrelated root.

Kept right today and must stay so: `E D A E` → E Mixolydian, Sweet Home Alabama `D C G` → D Mixolydian, Hey Jude's
chorus `F Eb Bb F` → F Mixolydian, every test in `SongScaleGroupsTest` and `PlayableScalesTest`.

## Fix

Replace `rootsOf` + the root walk in `fitOf` with a scored choice. All of it stays private to `SongScaleGroups`.

**1. Read the key's mode.** `Part.key` becomes a `Key(root: Root, isMinor: Boolean)`. `isMinor` is true where what
follows the root (`Am`, `A minor`, `A-moll`, `Amoll`) — after dropping a leading `-` or spaces — starts with a lowercase
`m` not followed by `a` (`maj`, `major`, `mayor`, `majeur` stay major). Both `metadata.key` and `Transpose.key` are read
this way.

**2. The key decides only where its own scale holds the chords.** For a part list whose first part has a key: the
major scale on it (a major key), else natural minor, then harmonic minor (a minor key). The first that holds every
chord is the fit (its suggestion from `PlayableScales.suggestionFor(root, chords)`, which lists that scale first). A
key whose scale does not hold them is ignored, and the score decides. (Today's `parts.mapNotNull { it.key }` tried
every part's key; use the first part's, which is the key in force where the group starts.)

**3. Score every root played** (pitch class), over the chords in play order:

- +1 per chord on it, **except a dominant seventh** (a major third and a minor seventh, interval 4 and 10): a V7 is a
  chord that leaves, rarely a tonic, and counting it let All of Me's `D7`s outweigh C;
- +1 if the part list's first chord stands on it, +1 if its last does;
- +2 per **cadence** into it: a chord with a major third whose root is a fifth above it (interval 7), directly followed
  by a chord on it. Each part's chords are read as a loop too (its last chord into its first), since a section is
  repeated: `C A7 Dm G7` cadences G7 → C on the way round.

Order the roots by score, ties by the non-dominant chord count, then the first chord's root, then the last's.

**4. Home scales first.** Walk that order and take the first root on which major, natural minor or harmonic minor
(in that order) holds every chord: the *home*.

**5. A mode only on a root that ranks above the home, and only where the part starts on it or the key names it.** The
mode candidates are the first chord's root and the key's root (where there is a key). If a candidate ranked *before* the
home root (or any candidate, where no root has a home scale) is held by Mixolydian or Dorian (in that order), it wins
with that scale instead; Lydian, Phrygian and Locrian are never chosen here, as a song chart almost never sits on them,
and `F C G F` would otherwise be F Lydian.

- *Why the candidates are limited*: without it All Along the Watchtower's `Am G F G` scores G 3 (two chords and the
  last) over A 2 and reads **G Mixolydian**; a player says A minor. Every modal song a player names as modal starts on
  its centre or names it as the key (`E D A E`, `D C G`, `F Eb Bb F`, `Am7 D7`, `{key: E}` over `D A E E`).
- *Why it applies without a home too*: a Dorian vamp of triads (`Am D`, `Dm G`, `Em A`) has no home scale among its
  roots (G major is not played), so step 6 took the first suggested scale, which is **melodic minor** (it holds the
  triads and comes before Dorian in `Scales.common`). The step must not depend on plan 03's reorder, which is a pending
  decision.

**6. Otherwise, today's fallback with the new order**: the first root in score order with a suggestion that `isFit`
(a seven-note-or-fewer scale or a dominant blues). A non-strict fit (an eight-note scale only, `isStrict = false`) is
taken **on the top-ranked root only**, never on another: Stairway then has no group until plan 02, rather than a D
bebop scale.

The strict/non-strict split in `of` and the joining of groups stay as they are (plan 02 replaces them). A dominant
blues stays a strict fit through `PlayableScales.suggestionFor`, so the twelve-bar test is unaffected — but not because E
scores highest: each I7 → IV7 is a cadence into the IV, so a full twelve-bar `E7 A7 E7 E7 A7 A7 E7 E7 B7 A7 E7 B7`
scores A 4, E 3 (the test's shorter chart ties them, the first chord breaking the tie). E wins because no scale of seven
notes holds the I7's G♯ and the IV7's G on A, and no Mixolydian or Dorian on E holds them either, so step 6 reaches E's
dominant blues. Keep it that way: a step that accepted a looser fit on the top root would hand a blues to its IV.

Measured with a prototype of exactly these rules (throwaway probe; every row below came out as stated):

| Chords (key) | Root and first scale |
|---|---|
| `F G C Am` | C major |
| `F G C F G C` | C major |
| `C D Am G D C Am G Em G Em G Em A7sus4` | G major |
| `G A D Bm` | D major |
| chorus `G D Em C` (`{key: C}`) | G major |
| chorus `F Bb C F` (`{key: C}`) | F major |
| `C Am F G` (`{key: D}`) | C major |
| `E D A E` / `E7 D A E7` / `E D A E D A` | E Mixolydian |
| `D C G` and `D C G D C G D` | D Mixolydian |
| `F Eb Bb F` (`{key: F}`) | F Mixolydian |
| `C G Am F`, `F C G F`, Let It Be `C G Am F C G F C` | C major |
| `Am F C G` | A natural minor |
| Zombie `Em C G D`, Horse with No Name `Em D`, Wonderwall `Em7 G Dsus4 A7sus4` | E natural minor |
| Knockin' `G D Am G D C` | G major |
| `Am Dm E7` | A harmonic minor |
| `E F` | E Phrygian dominant (fallback, step 6) |

Re-measured by the challenger with step 5 as amended (throwaway probe, no `{key}` unless given; every row above still
holds):

| Chords (key) | Root and first scale | Note |
|---|---|---|
| `Am G F G` (All Along the Watchtower) | A natural minor | G Mixolydian before the amendment |
| `Am D`, `Dm G`, `Em A`, `Am7 D7`, `Em7 A7`, `Dm7 G7` | A / D / E Dorian | triads were melodic minor before |
| `Am C G D`, `Em G D A`, `Dm F C G`, `Bm A E Bm` | Dorian on the first chord | the IV major is the Dorian sign |
| `Dm G7 C`, `Dm7 G7 Cmaj7 Fmaj7`, `C Am Dm G7`, `Fmaj7 Em7 Dm7 Cmaj7` | C major | |
| `G7 C F C` | C major | the V7 is not counted |
| `Dm Am E7 Am` | A harmonic minor | natural minor + E7 exception after 02 |
| `C G Am F`, `G D Em C`, `D A Bm G`, `A D E`, `E A B` | the first chord's major | |
| `Bm G D A` | B natural minor | ties with A; B wins as the first chord. A player accepts "B minor" (it starts and loops on Bm) |
| `Gm Eb Bb F` | G natural minor | |
| `C Bb F C`, `G F C G`, `A G D A` | Mixolydian on the first chord | |
| `Dm C Dm C` | D natural minor | no sixth degree is played, so Dorian and Aeolian are the same notes; Dorian is second in its list |
| `E F E F` | E Phrygian dominant | the E *major* chord's G♯ is not in Phrygian; `Em F Em F` → E with Phrygian holding |
| `E7 A7 E7 E7 A7 A7 E7 E7 B7 A7 E7 B7` | E dominant blues (step 6) | A outscores E, see above |
| `D A E E` / with `{key: E}` | A major / E Mixolydian | same notes; the key decides |
| `{key: C}` chorus `G D Em C` | G major | a player hears the chorus move to G; C major would put F against the D chord |
| `{key: A}` over `E D A E` | A major (the key holds) | the author's key wins where its scale holds |

Known limits, accepted: a two-chord vamp is ambiguous (`C Fm` reads F harmonic minor, as V–i); a jazz chart that
tonicizes its ii (rhythm changes, a compressed All of Me) can outscore its tonic, which plan 02's floor then handles.

Update the `SongScaleGroups` KDoc (its last paragraph names the root order) and the scales `CLAUDE.md` bullet "Roots:
the key in force (…), then the first, last, and most played chord roots" to the new rule in one or two sentences (the
package file must stay under 600 words).

## Tests

In `SongScaleGroupsTest` (a helper `fun rootAndFirst(text) = groups(text).map { it.rootName to it.scales.first().id }`):

- `a key is read from the chords, not from the first chord` — `F G C Am`, `F G C F G C`, `G A D Bm` and the Wish You
  Were Here intro + verse each give one group: (C, major), (C, major), (D, major), (G, major).
- `a mode is the answer only on a root that outweighs the home key` — `E D A E` → (E, mixolydian); `D C G` → (D,
  mixolydian); `F C G F` → (C, major); `Am F C G` → (A, natural_minor).
- `the key names the centre only where its scale holds the section` — `{key: C}` verse `C F G C` + chorus `G D Em C`
  → [(C, major), (G, major)]; the same with chorus `F Bb C F` → [(C, major), (F, major)]; `{key: D}` over
  `C Am F G` → (C, major); `{key: Am}` over `Am Dm Em` → (A, natural_minor).
- `a mode needs the part to start on its root or the key to name it` — `Am G F G` → (A, natural_minor); `G F C G` →
  (G, mixolydian); `{key: E}` over `D A E E` → (E, mixolydian), without the key (A, major).
- `a Dorian vamp of triads is Dorian, not melodic minor` — `Am D` → (A, dorian); `Dm G Dm G` → (D, dorian); `Em G D A`
  → (E, dorian).
- `a minor key is read from its m` — `{key: A minor}` and `{key: Am}` over `Am Dm E` both → (A, harmonic_minor);
  `{key: Amaj}` over `A D E` → (A, major).
- `an eight-note answer is never taken on another root` — the Stairway intro under `{key: Am}` gives no group with root D
  (plan 02 changes this to an A group; that plan updates the assertion).
- Keep every existing test green unchanged.
