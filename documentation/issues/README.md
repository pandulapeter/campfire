<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# The 38th review: the scales feature

Reviewed at `59acf3e03` on `scale-library` (2026-10-19). Angle: the scale library as shipped, with the scale suggestions
for a song's sections judged by what a working musician would say for real songs (about 130 progressions probed through
`SongScaleGroups.of` and `PlayableScales.over`), the catalogue checked row by row as theory, the song page, the Scales
tab, the sheet, navigation, and the docs. Five reviewers, a regression read, two plan writers, two challengers.

## Headlines

- **A mid-song `{transpose}` was applied twice** on the song page: the second verse of a song modulating up two steps
  read as E major instead of D. Fixed during the review.
- **Songs with one chord outside the key get no suggestion at all** (a secondary dominant, a diminished passing chord, a
  minor key's V7 beside its IV, Hotel California): the section silently vanishes from the Scales section, or only an
  eight-note bebop scale is offered. Plan 02 names a home scale with its exceptions ("A harmonic minor over E7").
- **The tonal centre is guessed from the first chord**, so a song starting on IV reads as Lydian (`F G C Am` → F
  Lydian). Plan 01 scores the roots.
- The Scales tab's address was missing from the web's offline route lists; a scale could not be searched by a typed `#`
  or `b`; every scale chip read "not selected" to a screen reader; the Roman numerals of a scale's own chords ignored the
  scale's degrees (`#IV` on a Locrian `b5`). All fixed during the review.

## Fixed during the review

`59acf3e03..004857aca`, 25 commits, not pushed; the tree also holds the user's own uncommitted work under `tools/`,
untouched:

- `a83245d1c` Name every reader of a tuning's strings in the instrument notes.
- `7280199ba` Stop moving the chords after a mid-song transpose a second time when suggesting scales.
- `a0322f593` Serve the Scales tab's address offline and on the web dev server.
- `97c55bbeb` Find a scale by its sharp, flat, or natural sign typed as # or b.
- `b67661ffd` Mark the onboarding's Scale suggestions card with the scales icon.
- `83f8f3333` Stop screen readers calling a scale chip not selected.
- `804b66a9f` Disable the Scale suggestions switch while chords are off.
- `8d71860a2` Say that the bundled Inter carries the accidental signs.
- `09f7df54b` Mark the ukulele, banjo, and mandolin necks at the tenth fret and draw no inlays on a violin.
- `b04fd5592` Animate a scale cell's height between a neck and a keyboard.
- `73a7021bd` Describe a scale group's order by what suggests it.
- `924dd4745` Say where the Scales section stands when a song has no Chords section.
- `8a985a861` Test that every tuning's neck shows every note of every scale.
- `34e102ad4` Name the dominant diminished scale in full in Hungarian.
- `66ba1cba2` Show which scale the oriental, Neapolitan, blues, hexatonic, and bebop Dorian scales are modes of.
- `cbae643a5` Number a scale's chords by its own degrees, so a flat fifth reads bV rather than #IV.
- `4ca442a7a` Name a scale group's sections in play order and keep a lone heading's number.
- `94c5b65d4` Note that the Scales section is never printed or exported.
- `c03f4e523` Remove the unused relative major string.
- `b0d093eab` Spell the tritone scale's fourth sharp, so no key repeats a letter.
- `9cd055aa2` Check that a scale's chord reads back as exactly the notes it stacks.
- `2ff6fdd38` Fade the Scales heading between finding scales and finding none.
- `d13859f58` Say what a scale group's kind keeps of a lone numbered heading.
- `3e6183a4b` Translate the other names a scale is known by, on the sheet and in the search (asked by the user during
  the review; the Hungarian list `scale_aliases_*` is a first draft for the user to check).
- `004857aca` Name every scale cell of the Scales tab in its key, as the Chords tab names its chords (asked by the user
  during the review).

Verified by `:chordpro:desktopTest`, `:presentation:desktopTest` (the scale tests), `:app:desktop:compileKotlin`,
`:app:web:compileKotlinWasmJs`, the web's Node tests, Spotless, and a regression read of the whole range (no behaviour
break; one KDoc reworded in the last commit).

## Index

| # | Plan | Kind | Severity | Lane |
|---|---|---|---|---|
| 01 | Guess a part's tonal centre by scoring its chord roots | bug | high | S |
| 02 | Suggest a home scale with the chords it leaves out named as exceptions | design | high | S |
| 03 | Offer the pentatonic and blues scales where players use them, rare scales last | design | medium | S |
| 04 | Rank a chord sheet's scales by the chord's quality, blues off altered dominants | bug | medium | S |
| 05 | Count a chorus recalled after a key change as a part of its own | bug | medium | S |
| 07 | Keep two scale groups from sharing a title | bug | low | S |
| 08 | Skip the scale groups for a songbook | performance | low | U |
| 09 | Turn the scale sheet's Degrees / Notes choice into one icon toggle, two-line titles after | design | medium | T |
| 10 | Put the Scales tab's family chips in one scrolling row | design | medium | T |
| 11 | Group the tools on any rail too short for every item | bug | low | T |
| 12 | Size the scale sheet's dot labels so the widest fits its dot | bug | medium | T |
| 13 | Write scale titles as each language writes a key, Hungarian steps as "1 1 ½" | design | low | T |
| 14 | File the Neapolitan and double harmonic modes beside their parents | design | low | T |
| 15 | Test the Scales tab's search keys and the sheet's chord shapes | test-only | low | T |
| 16 | Keep the click action of the Scales and Chords tabs' cells for screen readers | bug | medium | T |

## Lanes

| Lane | Area | Plans, in execution order | Files owned |
|---|---|---|---|
| S | scale suggestions (`:chordpro` scales, the song page's groups) | 01, 02, 03, 04, 05, 07 | `chordpro/.../scales/*`, `chordpro/.../scales/CLAUDE.md`, `presentation/.../songView/model/SongScaleGroups.kt`, `ScaleChipRows.kt`, `SongScalesSection.kt`, `RenderSection.kt`, `chords/ScaleChip*.kt`, their tests |
| U | the lyrics model | 08 | `presentation/.../songView/model/SongLyricsModel.kt` and its test |
| T | the Scales tab, the sheet, the chrome, strings | 09, 10, 11, 12, 13, 14, 15, 16 | `presentation/.../screens/scales/*`, `dialogs/scales/*`, `scales/*`, `chords/ChordLibraryCell.kt`, `chrome/NavigationBarItems.kt`, `chordpro/.../scales/Scales.kt` (14 only), `tools/screenshots` tests, `NavigatorTest` |

**Merge order: S, then U, then T.** S is the lane the others' premises rest on (02 changes what a group carries, 05 what
a part is) and it holds every plan that awaits a decision on the suggestions; U is one small plan on a file S does not
touch; T touches the most shared UI files and the strings, so it merges last.

Shared files:

- `presentation/src/commonMain/composeResources/values[-hu]/strings.xml`: S (02, 07) adds keys in the
  `song_details_scales_*` group; T (09, 10, 13) edits and adds keys in the `scales_*` group. A conflict is a word-level
  three-way merge keeping every key of both sides.
- `presentation/.../songView/CLAUDE.md`: S rewrites the Scales section's bullets; U appends one sentence (08). Both kept.
- `chordpro/.../scales/Scales.kt`: S (03 reorders `common`) and T (14 moves four scales between families): disjoint
  lines, both kept.
- `tools/screenshots/.../AppTestNavigation.kt`: T only (09 adds the opener 10 reuses).

## Decisions (awaiting the user; the recommended default first)

1. **02, the design where no one scale fits** — **decided 2026-10-19: (a) a home scale with each outside chord named
   with its own scales, plus (c) the minor family, with the two-thirds floor** (Creep and a full All of Me get no group).
2. **03, the `Scales.common` order** — **decided: the player's order** major, natural minor, minor pentatonic, major
   pentatonic, blues, Dorian, Mixolydian, harmonic minor, melodic minor, major blues, chromatic; **and eight-note scales
   leave a song's five chips.**
3. **06, the relative pentatonic** — **decided: no**; every chip stays on the part's root. Plan 06 is withdrawn (its
   file removed); the data shape stays one root per group.
4. **09, the sheet's Degrees / Notes choice** — **decided: one icon toggle in the header** (against the recommendation
   to move the choice under the diagram); plan 09 is rewritten to it.
5. **10, the Scales tab's first phone screen** — **decided: the family chips in one scrolling row** (most of a cell on
   the first screen; the key row stays as it is).
6. **13, Hungarian titles and steps** — **decided: "C-dúr" with lowercase names and "1 1 ½" steps**, on the condition
   that the ½ sign is in the bundled Inter on every platform (checked: U+00BD is in all three subset weights, see plan
   13; it never falls back to a system font).
7. **14, the families** — **decided: move the four modes beside their parents**, keeping the "Other harmonic" label.
8. **02, an exception's first scale** — **decided: the common holders first** (F Dorian over the `Fm` of `C Am F Fm C`,
   not "F Lydian ♯2"), then the rest by closeness to home.
9. **03, rock riffs** (`E G A E`, `C Eb F C`) — **decided: yes**, a rule beside the dominant blues one adds the minor
   pentatonic and blues on the root for major chords on I, ♭III, IV, V, ♭VII with a ♭III or ♭VII among them.

## Challenge

Two fresh agents challenged the plans, one per group of lanes.

**Lanes T (09–15)**: 12 sound; 09, 10, 11, 13, 14, 15 amended; none dropped.
- 09: the UI test matched cells by `hasClickAction()`, which no cell has (see 16); it now matches by the description
  prefix and scrolls the grid to the pinned search first, since at HEAD the result cell lands under the navigation bar.
- 10: "C Major" as a substring also matched the pentatonic and blues cells; the scroll state must live in `ScalesScreen`,
  outside the grid's items, or it resets when the controls scroll away; the promo footage's `FAMILY_CHANGES` picks
  families a one-line row leaves off screen, so a release-time step retakes it (both files carry the user's uncommitted
  edits, so it stays out of the change).
- 11: the rail metrics hold against Material 3 1.12.0-alpha03; the insets placeholder is filled in with
  `NavigationRailDefaults.windowInsets`; the capacity becomes a `RailCapacity(rail, expandedRail).of(kind)` holder since
  `CampfireContent` does not know the chrome's kind and a handover composes both; the expanded rail groups up to 24 dp
  early, accepted.
- 13: the "1 1 ½" steps line sits right under the degrees line "1 2 3 4 5 6 7", so a run of 1s reads as degrees: a
  caveat for the decision.
- 14: the moved scales come before the non-common pentatonic, blues, symmetric, and bebop scales in `scalesContaining`,
  so they can push one of those out of a song group capped at five; `PlayableScales`' home-scale pick is unchanged.
- 15: the shape test walks every fretted tuning through `ChordVoicingSearch.hasTableShapes` (the session cache could
  make a sheet-level test pass falsely), splitting into a running and an `@Ignore`d test if only some tunings fail.
- New finding from the challenge: plan 16 (the cells clear their own click action).

**Lanes S and U (01–08)**: 08 sound; 01–07 amended; none dropped. The challenger prototyped 01's and 02's rules in
probes over the review's progressions and more (the table is in plan 01).
- 01: `Am G F G` came out G Mixolydian (G scored two chords plus the last), so a mode is considered only on the first
  chord's or the key's root; triad Dorian vamps (`Am D`) have no home root and fell to melodic minor, so the mode step
  runs with no home too (01 no longer depends on 03's reorder); the twelve-bar's claim "E scores highest" corrected.
- 02: the home scale is the one that decided the fit (else a dominant blues's home was the minor pentatonic, every chord
  an exception); exception scales are limited to seven-note scales holding the chord; a natural-minor home offers Dorian
  over a chord its raised sixth holds (House of the Rising Sun's D → A Dorian, Hotel California's E → B Dorian); the loose
  level also tries the first chord's root (`C F G E` had no group); presentation fixtures need `exceptions`.
- 03: `PlayableScalesTest`'s `isDominantBlues(C, F7)` assertion contradicts fix 2 and goes; the order now says where a
  dominant blues's extras go when Mixolydian holds the chords.
- 04: the alteration check looks only at chords on the I, or one `B7b9` turnaround took the blues from a whole song.
- 05: a recall part takes its chorus's kind (`Part.kind` read `sections.first()`), so two transposed recalls join.
- 06: dropping `SongScaleGroup.rootName` breaks two presentation tests, now in its Tests.
- 07: two verses in one group stay "Verse"; a serial-comma variant string for several names before "and the parts
  without a heading".
- Two questions no plan's fix covered are decisions 8 and 9 below.

## Checked and found solid

- Every one of the 62 scales' intervals, degree labels, and recorded mode relations; no two scales share a note set; the
  aliases are standard; every triad and seventh of every seven-note scale in 17 roots parses back to its stacked notes.
- The Hungarian names (dór, fríg, líd, mixolíd, lokriszi, összhangzatos and dallamos moll, pentaton…).
- The reader's transposition and the capo: the scales follow the transposed, written-shape chords as the diagrams do.
- Slash chords: leaving the bass out is right for C/E, G/B, D/F♯ and descending Am/G lines.
- Diatonic songs with or without a `{key}`, power chords, extensions (`Cmaj9`, `C6/9`, `Csus2`), dominant blues with a
  quick change, Hey Jude (F major verse, F Mixolydian chorus), Sweet Home Alabama (D Mixolydian).
- The grouping heuristic (own kind, then the last group, then any): no musically wrong grouping found.
- The Scales section's cutting between chip rows, its hiding with each switch, the fold, the sheet's one-hop rule and
  process-death behaviour, the chips' heading semantics, both locales complete.
- The neck geometry for every tuning (frets 0–12 hold every note on every full-length string; the banjo drone from the
  fifth fret; the ukulele's re-entrant g; the violin fretless), the two-octave keyboard, the fonts' ♭ ♮ ♯, dark mode.
- Navigation (`/scales`, the Tools grouping at 7, 6, 5 and 4 screens), the search keys, the shared key state, the
  preferences (not synced, like Chord diagrams), the Chords tab refactor (behaviour preserved).

## Dropped after verification

- A `b5` triad in the chord tables: `Gb5` reads as a G♭ power chord to the parser, and a bare flat-five triad is rarely
  charted; the seventh rows keep `7b5`.
- "Blues minor pentatonic" / "Blues major pentatonic" / "Hexatonic" aliases: real names, kept for the search.
- `Imaj7#5` rather than `I+maj7` on the sheet's seventh rows: the app's Nashville convention, consistent.
- The "4" among W and H in a scale's steps (Hirajoshi, In): shown deliberately; the Hungarian "E F" letters are plan 13.
- The sheet's header scrolling in a short window (plan 09's brief): it never does, the sheet has no text field.
- German "Fis" overflowing a dot (plan 12's brief): German notation renames only B and B♭; Latin names and "♭♭7" do.
- The Scales tab names map rebuilt per recomposition: 62 lookups, cheap.
- The onboarding Features page now seven cards long: the onboarding is to be redone separately (standing decision).
- The dated `documentation/plans/scale-library.md` quoting the tritone's old formula: a historical plan, left.

Noted, no plan: a What's new line that six or more screens now put the tools behind one item; the family row's title
"Scales" could read "Family"; the family chip stays highlighted during a search.

## Measurements

- `SongScaleGroups.of` on the desktop JVM: 9 ms at 50 sections, 92 ms at 400, 341 ms at 800 (plan 08).
- Of about 45 real songs or progressions probed, 14 got no suggestion and 1 a bebop-only answer on the wrong root;
  many more parts got only an eight-note scale (plan 02).
