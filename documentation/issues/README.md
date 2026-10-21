<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# The 41st review: scale suggestions as a gigging player judges them

Reviewed at `56437d3de` on `master` (the user committed `925359ccc`, four chips a row, and the goldens `62ed0a79c`
while the review ran); the fixes below start at `62ed0a79c`, and the plans quote code at `0a0babf27`. Angle: the
suggestions a live improviser gets for any part of any song — is the home right, are the parts split only where a
player changes scale, is the first chip the one most players reach for, does any real chart end up with nothing.
Method: a probe over about 200 real charts
(`chordpro/src/desktopTest/kotlin/com/pandulapeter/campfire/chordpro/scales/ProbeSongsTest.kt`, run with
`PROBE_CORPUS=<file> ./gradlew :chordpro:desktopTest --tests "*ProbeSongsTest*" --rerun`), two area reviewers (parts and roots; chips and exceptions), a
regression read of the fixes, two plan writers.

**Challenge:** run 2026-10-21 by three fresh agents, each fix applied to a throwaway copy of `3f693bab8` (lane G stacked
in order) and run against the full test suite and a probe corpus. All 15 plans **amended**, none dropped; each plan
says what changed under its header. The main changes: 01 no leader on a minor home's own root, none in a rock riff,
and none on the key's root where the part sits in the V's key (`Harmony.countsLeadersOn`); 02 the jazz shape needs the
I most played and no maj7 on I/IV/V, the turnaround needs a dominant seventh, and a dominant blues held by the major
puts the blues scales first; 03 the tonic-seventh rule takes the first chord if a seventh, else the most played, and
the `guessedFitOf` sort counts held chords only; 04 a start check, an unwrapped follow, and HEAD's order of checks
(the mode before the minor family, so Dorian under a minor key holds); 05 rule (ii) only for non-major homes, vamp
joins only without an exception on the vamp's chords; 06 a dominant seventh on the root is no cadence; 07 Phrygian
dominant counts no leaders (else `C F G C C F Db C` turns), `mixolydian` pinned by id, one shared gate helper; 08
the shifted key only where the part's best root is it, and Here Comes the Sun's bridge stays the riff on A (the
written key keeps its word); 09 a headless missing row is named by its opening words, and short own-key parts show
there too; 10 `tonalFitOn` passes the home only where a chord has a third or the home is the key's root; 11 the
example is `C Fm G C` (`C F Fm G` is already major); 12 a dim7 leads from any of its four notes, plus part (d), the
Phrygian over a Phrygian-dominant home's ♭III that plan 07 asked lane S for; 13 a joined set that is the home root's
major, natural minor, Dorian, or Mixolydian is named on the home root (the backdoor reads C natural minor), sorted by
group then index; 14 the first two chips stay (one-chord vamps keep theirs); 15 claims corrected only.

## Headlines

1. **A `{key}` chart can get no suggestion at all** (plan 01): the home may leave out two distinct chords or a third,
   so Blue Bossa, a gospel turnaround, a country verse with A7/G7/Cm, All of Me, and Ipanema's bridge (still dropped after plan 01;
   plan 09 names it) get nothing or a silently dropped part, though the key holds half or more of the plays.
2. **A jazz blues reads on the wrong root** (plans 02, 03): the tonic I7 scores nothing in `rankedRoots`, the IV's
   cadence wins, and the minor family absorbs the secondary dominants — F blues → "G natural minor".
3. **A part in the IV key reads as the key's Mixolydian** with minor pentatonic and blues chips (plan 04), and a
   modulation to the IV becomes the home.
4. **Smoke on the Water is three rows** and, with its key, loses its chorus (plan 05); flamenco falsetas are dropped
   (plan 07); dropped parts are invisible on the page (plan 09).

## Fixed during the review (`500940bb6..0a0babf27`, 15 commits, not pushed)

| Hash | Message |
|---|---|
| `500940bb6` | Read a key written as "D Minor" or "B♭" for the scale suggestions. |
| `1bfd70337` | Suggest scales in the new key for the rest of a section after a mid-section transpose. |
| `f5bafffcb` | Keep the song's key as the home when its last choruses are recalled after a transpose. |
| `e30fe8fc1` | Read a riff with a power chord on the flat fifth as a rock riff, so the blues note is no exception. |
| `50945dbdf` | Never read a song with a major seventh on its tonic as a blues. |
| `776488109` | Offer Mixolydian before the major blues scale over a dominant blues. |
| `2e9bfc430` | Suggest the key's Phrygian over a flat second chord in a minor song. |
| `45183786c` | Read a dominant with a flat ninth as the secondary dominant it is. |
| `e8e44dc32` | Suggest the parallel natural minor, never a harmonic minor, over a borrowed chord in a major song. |
| `6c1b13c24` | Suggest a one-chord vamp's textbook scale first, Dorian over a minor seventh. |
| `2e6d9b21b` | Add the textbook scales for augmented, minor-major seventh, sharp ninth, and sharp eleventh chords to their sheets. |
| `f70a58f89` | Find the Phrygian dominant and Lydian dominant scales by the names players use for them. |
| `b956af72b` | Read a key named with a mode, "A Mixolydian", as major for the scale suggestions. |
| `087532697` | Count the rest of a section cut by a transpose as played once, so its chords can be moments. |
| `0a0babf27` | Bring the scale suggestion docs and the Hungarian aliases in line with the review's fixes. |

The last three came from the regression read of the first twelve. A side effect worth knowing: with the ♭5 riff fix,
Smoke on the Water under `{key: Gm}` now drops its chorus (`C Ab G5`) as a guess beside the riff and verse groups,
where before it joined the riff as a loose natural-minor fit; plan 05 restores it. The Hungarian aliases "Fríg dúr",
"Líd ♭7", "Mixolíd ♯4" are drafts for the user to check.

## Index

| # | Plan | Severity | Lane |
|---|---|---|---|
| 01 | A song with a `{key}` is always answered on the key's own scale, its chords leading into the key counted as the key's | high | G |
| 02 | A jazz blues and a twelve-bar of triads with a V–IV turnaround are a blues, and a lone passing I7 is not | high | G |
| 03 | A tonic seventh scores for its root, a cadence into a secondary dominant scores nothing, and a guessed root is weighed by its home scale | medium | G |
| 04 | A part sitting in another key, the song's tonic heard as its dominant, is read in that key, not as the key's mode | medium | G |
| 05 | A rock riff's group takes the parts in its root's modes and the key's guesses on it, and a vamp on one root joins whatever sits on that root | medium | G |
| 06 | A part that cadences into the home's root is no centre of its own | medium | S |
| 07 | Phrygian dominant is a home for a part with a major tonic and a major flat second | medium | G |
| 08 | A capo chart whose `{key}` is the sounding key is read in the key of its shapes (decision) | low | G |
| 09 | Chorded parts no scale row holds are named, not silently covered by the home | medium | U |
| 10 | A home's row is built on the home, so a minor home never offers the parallel major and its pentatonic | medium | G |
| 11 | An exotic seven-note scale never leads a part or makes it a fit | medium | G |
| 12 | A secondary dominant targets only a note of the key, and a diminished chord is its target's harmonic minor or the diminished scale | medium | S |
| 13 | Exceptions sharing one set of notes are one moment, named by the parent key | medium | S |
| 14 | A song's row keeps a non-common seven-note scale only as its first chip | low | U |
| 15 | The minor 6 pentatonic and the dominant pentatonic join the catalogue (decision) | low | U |

## Lanes

| Lane | Area | Plans in execution order | Files owned |
|---|---|---|---|
| G | the engine's fits, roots, and rows | 01, 02, 03, 04, 05, 07, 08, 10, 11 | `chordpro/.../scales/SongScaleGroups.kt`, `SongParts.kt`, `PlayableScales.kt`, `SongScaleGroupsTest.kt`, `PlayableScalesTest.kt` |
| S | moments and exception chips | 06, 12, 13 | `chordpro/.../scales/ScaleExceptions.kt`, `SongScaleSuggestions.kt`, `ScaleExceptionsTest.kt`, `SongScaleSuggestionsTest.kt` |
| U | the page and the catalogue | 09, 14, 15 | `presentation/.../ui/songView/model/SongScaleGroups.kt` and its test, the songView `CLAUDE.md`, `chordpro/.../scales/Scales.kt`, `ScalesTest.kt`, both `strings.xml`, `presentation/.../ui/scales/ScaleNames.kt` |

**Merge order: G, S, U.** G first because S's plan 13 shows its value once G's plan 01 lands; U last because its plan
09 names the parts the engine still drops and its plan 14 cuts the rows G's plan 10 reorders; lane U is cut from
`HEAD` after G and S merged. Shared file: the scales package `CLAUDE.md`, which every lane may amend on the lines its
plans name (word-level three-way merge, every sentence kept). Two cross-lane test edits are called out inside the
plans: plan 02 changes the home assertion of `ScaleExceptionsTest`'s `a jazz blues is Mixolydian with the IV and V
named` to `minor_pentatonic` (lane G makes that edit, lane S's cherry-pick must keep it), and plan 07 says what lane S
must add to `ScaleExceptions` for the falseta's G to read "E Phrygian" (until then it reads "G Mixolydian").

## Decisions (taken by the user 2026-10-20: every recommended default, as listed first below)

- **02 (b)** A twelve-bar written with triads and a V7 only (`E E E E A A E E B7 A E B7`) reads as E major today. Read a
  V→IV move in a part half on its tonic as the blues turnaround → minor pentatonic and blues first? Recommended: yes.
- **02 (c)** A lone passing I7 among plain tonics (`G G G7 C C G G D7 G G`, `C F G C7 F G C`) leads with the minor
  pentatonic today. Recommended: not a blues; the key's home with the I7 as a moment, the V→IV turnaround being the
  tie-breaker that keeps `A A A A7 D D A A E D A E` a blues.
- **06** Drop the "centre" row for a part that cadences V→I into the home's root (Hotel California's chorus → stays B
  minor). The plan found that the key-E bridge `A A C#m C#m A A B B` has the same shape as the settled IV bridge
  `C Am C Am C Am D D`, so no chord rule separates them: option 1 (recommended) keeps both as centres; option 2 (the
  centre's scale must hold at least as many chords as the home's lead) drops both and the two vi bridges too.
- **08** With `{capo}` set and the written key holding nothing of the shapes, try the key shifted down by the capo
  (`{key: G}{capo: 5}` with D shapes → D major). Recommended: yes.
- **09** Parts no row holds: name the home row by its sections and add a chip-less row for the missing parts with a
  short text ("No scale above holds these chords"), or only name the row. Recommended: the first.
- **15** Add the minor 6 pentatonic and the dominant pentatonic to the catalogue, browse-only at first. Recommended:
  yes, browse-only.

- **Challenge, 03** The rhythm-changes bridge `D7 D7 G7 G7 C7 C7 F7 F7` (nothing today) gets a passing row "D: minor
  pentatonic, blues" after plan 03. Taken 2026-10-21: accepted.
- **Challenge, 11** `C Cm` still reads Lydian ♯2 (a harmonic-minor mode) after plan 11. Taken 2026-10-21: left as is,
  not barred.

## Checked and found solid

- The catalogue: every interval set, degree label, mode rotation, family, and the `common` list (aliases aside).
- `{transpose}` between sections, in-key recalls counted once, grids and tabs contributing chords, plain paragraphs as
  parts, headless rows told apart by their opening words, the three-moment budget, the join order, the guess rule.
- Twelve-bars with sevenths with or without a key, Hey Joe, Sweet Home Alabama (D Mixolydian), Seven Nation Army, Teen
  Spirit, Wonderwall's shapes, Hotel California's verse (B minor, F♯ → harmonic minor, E → Dorian), Autumn Leaves,
  Summertime, Take the A Train, Mad World and Wicked Game (Dorian), minor blues with ♭VI7–V7, parallel-key songs, the
  relative minor said in the key, Dorian vamps, the dominant-blues rows, rock-riff rows, slash chords read without
  the bass, sus dominants held by the key.
- `ChordScaleChoices` rules and `over(chord)` keeping only the first choices the chord's notes allow.

## Dropped after verification

- A 7♯5 secondary dominant answered as its own Mixolydian ♭6 rather than the target's harmonic minor: the same notes
  as the target's melodic minor, as right; left as is.
- Aliases "Whole-half" and "Half-whole": already in the two diminished scales' names.
- The tritone substitution as a simple fix ("the target must be a root the part plays"): it would break `C F G E →
  A harmonic minor`, the settled deceptive V/vi; became plan 12 with the home-note rule instead.
- Giant Steps getting nothing: three keys in eight bars, the per-chord sheets are the answer; accepted.
- A body `{key}` directive as a modulation: settled, `{transpose}` is the modulation.
- The centre rows listing the pentatonic before the home: a 38th-review decision.
- A circle-of-fifths bridge `E7 A7 D7` read as a dominant blues on A: low, not planned.
