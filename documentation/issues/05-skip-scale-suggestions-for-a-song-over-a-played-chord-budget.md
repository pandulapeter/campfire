# Skip scale suggestions for a song that plays more chords than a fixed budget, as a songbook already skips them

**Kind:** performance (main-thread freeze, ANR) · **Severity:** medium · **Platforms:** all
**Challenged:** amended — the budget removes the multi-second cases but does not bound the cost to ~0.2 s: what is left
under it grows with sections × groups, not chords, and is bounded only by the songbook gate (~0.3–0.4 s on the desktop
for 200 random-chord sections at 400–1,000 chords; 2.8 s for 500 sections, which the gate keeps out of the viewer but
not out of the public `SongScaleGroups.of`). Claims corrected; the fix itself is unchanged.
**Files:**
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/scales/SongScaleGroups.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/scales/SongScaleGroupsTest.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/scales/CLAUDE.md`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songView/model/SongLyricsModel.kt` (KDoc of `suggestsScales`),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songView/model/LayoutBudget.kt` (KDoc of `MAX_CUT_SECTION_COUNT`),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/songView/CLAUDE.md` (The Scales section)

## Problem

Scale suggestions are on by default. They are worked out in `prepareSongLyrics`. Nothing limits them except the
songbook gate (`SongLyricsModel.kt:113`, `:126`):

```kotlin
val isSongbook = standardSong.blocks.count { it is ChordProBlock.Section } > LayoutBudget.MAX_CUT_SECTION_COUNT
...
scaleGroups = if (shouldShowChords && suggestsScales && !isSongbook) songScaleGroupsOf(standardSong, labels, notation) else emptyList(),
```

The first model of the page being read is built in composition, on the main thread
(`RememberSongLyricsModel.kt:52`):

```kotlin
val state = remember { mutableStateOf(if (buildsInPlace) inputs to prepare(inputs, false) else null) }
```

`SongScaleGroups.playedGroupsOf` (`SongScaleGroups.kt:46-84`) fits every part on its own. Then, for each existing group,
it fits the group's parts plus the new part again, building a new `Harmony` over all their chords each time:

```kotlin
else -> fitOf(group.parts + part)?.takeIf { canJoin(group.fit, own, it) }?.let { group to it }
```

Each `fitOf` walks every chord occurrence several times, once per scale and root. Examples are `looseCountsOn` (`:383-397`:
`harmony.chords.indices.count { … holders.any { holdsChord(it, root.pitchClass, chord) } … }` for each of five homes)
and `scaleCountOn` (`:369-370`). `holdsChord` (`ChordNotes.kt:27`) calls `Scales.pitchClasses(scale, root)`, which
allocates a new set on every call (`Scales.kt:150`). The cost therefore grows with sections × chord occurrences.

The song's size is bounded only by `LayoutBudget` (3,000 lines, 200,000 characters) and the 200-section gate. Neither
bounds the chord count. Measured on the desktop JVM (`SongScaleSuggestions.of`, warm, chordpro jar of 2026-10-21):

| sections | chords played | file size | time |
| --- | --- | --- | --- |
| 10 | 400 | 2 KB | 22 ms |
| 200 | 1,000 | 11 KB | 113–200 ms |
| 200 | 2,000 | 15 KB | 220–300 ms |
| 200 | 15,000 | 67 KB (inside `LayoutBudget`) | 0.9–1.3 s |
| 200 | 45,000 | 181 KB (inside `LayoutBudget`) | 2.3–2.8 s |
| 50 | 25,000 (reviewer's case, 1.25 MB) | | 25 s |
| 100 | 50,000 (reviewer's case) | | 94 s |

A phone is several times slower than the desktop JVM. A shared or synced file of this kind freezes the song details
screen on every device it reaches (an ANR on Android) when the song opens. For scale, the demo's House of the Rising
Sun plays 112 chords.

## Fix

Options considered:

1. **A budget on played chords (recommended).** Add a constant to `SongScaleGroups` and gate on it right after the
   parts are read in `playedGroupsOf`:

   ```kotlin
   internal fun playedGroupsOf(song: ChordProSong): List<PlayedGroup> {
       val parts = SongParts.of(song)
       if (parts.isEmpty() || parts.sumOf { it.chords.size } > MAX_PLAYED_CHORDS) return emptyList()
       ...
   }

   /**
    * The most chords a song may play for its scales to be worked out: grouping fits every part against every group, so
    * the work grows with the sections times the chords, and past this a song details page would freeze opening. Several
    * times the longest real song (House of the Rising Sun plays 112).
    */
   internal const val MAX_PLAYED_CHORDS = 1_000
   ```

   This is `SongParts.of`'s count: a chorus recall of the same chords is not counted again, which matches what is fitted.
   `SongScaleSuggestions.of` already returns an empty list when `playedGroupsOf` does. `songScaleGroupsOf` then
   returns an empty list too (`presentation/…/songView/model/SongScaleGroups.kt:71`), and `withScalesSection` adds no
   section for no groups (`ScaleSuggestions.kt:37`). It behaves exactly like the songbook gate. Gating in `chordpro`
   rather than next to `isSongbook` bounds `SongScaleGroups.of` (public) against the chord-heavy case, but not against
   many small sections: measured (desktop JVM, warm, chordpro jar of 2026-10-21), 500 sections of two random chords
   (1,000 chords, 56 groups) take 2.8 s. Only the viewer's songbook gate (200 sections) keeps that out of the app; no
   other caller exists, so the KDoc of `of` should say the caller bounds the sections. `SongParts.of` itself is a
   linear pass. No song under the budget changes its result, so the tuning of the 38th and 41st reviews is untouched.
2. Distinct chords with multiplicities, or memoizing `Scales.pitchClasses` per scale and root. Memoizing gives the same
   output and cuts allocations, but it only lowers the constant factor: the sections × chords growth stays.
   Working on distinct chords is unsafe. `rankedRoots`, `isTwoOfLeaderInto`, `otherKeyFitOf`'s `onTonic`, and the
   first/last-chord rules all read chords in play order, so results could change. Not recommended, and not needed once
   the budget holds.
3. Moving the computation off the in-place pass. The in-place model would then lack the Scales section, and the
   background model would add it a moment later. That is the incidental pop-in the first-frame rule forbids, so
   rejected. Under the budget, the in-place cost for a pathological file at the
   200-section gate is ~0.1–0.4 s on the desktop (measured: 200 sections of 2–5 random chords, 400–1,000 chords,
   29–42 groups, 0.17–0.42 s; 200 sections cycling 12 keys, 0.14 s). That residue comes from sections × groups and is
   the songbook gate's, the same at 400 chords as at 1,000, so the budget's number barely moves it; a real song stays
   in the tens of milliseconds.

Documentation:

- `chordpro/.../scales/CLAUDE.md`: under `SongScaleGroups`, add "Nothing is worked out for a song playing more than
  `MAX_PLAYED_CHORDS` chords (a recall of the same chords counted once): grouping grows with sections times chords".
- `songView/CLAUDE.md` (The Scales section): "none for a songbook (over `MAX_CUT_SECTION_COUNT` sections)" becomes
  "none for a songbook (over `MAX_CUT_SECTION_COUNT` sections) or a song playing over `MAX_PLAYED_CHORDS` chords".
- Update the KDoc of `suggestsScales` (`SongLyricsModel.kt:93-95`) and of `MAX_CUT_SECTION_COUNT` (`LayoutBudget.kt:33-38`)
  to name the second gate.

**Decision for the user:** the number. The recommendation is 1,000 (about nine times House of the Rising Sun). The
number is one constant, `SongScaleGroups.MAX_PLAYED_CHORDS`; 2,000 also works. Either way the worst file left at the
200-section gate costs ~0.4 s on the desktop (roughly 1–2 s on a slow phone), set by its sections, not by this number.

## Tests

In `SongScaleGroupsTest` (commonTest, timing-independent, the same shape as `RenderSectionsTest`'s
`a songbook has no scales section`):

```kotlin
@Test
fun `a song playing more chords than the budget gets no groups`() {
    fun groupsOf(chordCount: Int) = groups(
        "{key: G}\n{start_of_verse: Verse}\n" +
            (0 until chordCount).joinToString(" ") { "[${listOf("G", "C", "D")[it % 3]}]la" } +
            "\n{end_of_verse}",
    )
    assertEquals(1, groupsOf(SongScaleGroups.MAX_PLAYED_CHORDS).size)
    assertTrue(groupsOf(SongScaleGroups.MAX_PLAYED_CHORDS + 1).isEmpty())
}
```

Without the fix, the constant does not exist and the test does not compile. With only the constant added, the second
assertion fails, because the song is in G major. The existing `SongScaleGroupsTest` and `SongScaleSuggestionsTest`
fixtures all stay far under the budget, and must pass unchanged. That proves no result moved.
