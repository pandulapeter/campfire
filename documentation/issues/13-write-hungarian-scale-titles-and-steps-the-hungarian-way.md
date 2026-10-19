# Write scale titles as each language writes a key ("C-dúr", "C major"), and Hungarian steps as "1 1 ½" rather than letters that read as notes

**Kind:** design  ·  **Severity:** low  ·  **Platforms:** all
**Files:**
`presentation/src/commonMain/composeResources/values/strings.xml`,
`presentation/src/commonMain/composeResources/values-hu/strings.xml`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/scales/ScaleTexts.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/scales/ScaleNamesTest.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/scales/CLAUDE.md` (one line)

**Challenged:** amended — the recommended Hungarian steps "1 1 ½" trade one confusion for another: the steps line is drawn directly under the formula line (`ScaleFormula`: degrees "1  2  3  4  5  6  7" in `titleMedium`, then the steps in `bodyMedium`), so a run of "1"s reads as degrees; added to step 4 for the user's decision. Everything else holds: every caller of `scaleName` / `scaleTitle` is the one the plan names (`ScaleLibraryCell`, `ScalesScreen`'s search names, `ScaleSheet`, `ui/chords/ScaleChip.kt`, `scaleDiagramDescription`), the search folds case, and no test or screenshot asserts a title.

**Decision:** taken 2026-10-19, see below.

## Problem

`ScaleTexts.kt` builds every title as root + name:

```kotlin
internal fun scaleTitle(scale: Scale, rootName: String, notation: ChordNotation) =
    stringResource(Res.string.scales_sheet_title, shownChordName(rootName, notation), scaleName(scale))
```

`scales_sheet_title` is `%1$s %2$s` in both locales, and the names are capitalized standalone labels (`scale_major` "Major" / "Dúr", `scale_natural_minor` "Natural minor" / "Természetes moll", `scale_dorian` "Dorian" / "Dór").

**Hungarian titles read wrong.** The sheet's title, the "mode of" and "relative minor" lines, the scale chips of the song's Scales section and both chord sheets (`ui/chords/ScaleChip.kt`), and the diagram's screen reader description all read "C Dúr", "A Természetes moll", and "D Dór". Hungarian writes a key with a hyphen and a lowercase name: "C-dúr", "a-moll", "D-dór". Traditionally a minor key also takes a lowercase root ("a-moll"). The app writes roots as chord names, though, and a chord name's root is a capital (`ChordProNotation.shownName`), so "A-moll" is acceptable and consistent with the chords on the same page.

**English mixes case.** The same pattern gives "C Major" but "C Natural minor" and "C Harmonic minor". English writes "C major", "C natural minor", "D Dorian", and "C Phrygian dominant": quality words lowercase, names from places and peoples capitalized.

**Hungarian step letters look like notes.** `scaleStepsText` joins `scales_steps_whole` / `scales_steps_half` / `scales_steps_augmented`, which are "E" / "F" / "3" in Hungarian (egész / fél). The steps line sits right under the notes, so a major scale reads "E E F E E E F", the same letters as the notes E and F.

**Decision:** taken 2026-10-19 — the recommendation ("C-dúr", lowercase names, "1 1 ½" steps), on the condition that the
½ sign is in the bundled font on every platform. Checked at the reviewed tree: U+00BD is in the cmap of all three subset
Inter files (`inter_regular.ttf`, `inter_medium.ttf`, `inter_bold.ttf`), so it is drawn by the app's own font everywhere,
never by a system fallback; U+2153 (⅓) is **not**, so no other fraction sign may be used.

## Fix

Use one mechanism for both languages. **The name strings hold the form a title uses; a name standing alone is capitalized in code.**

1. **`ScaleTexts.kt`.** `scaleTitle` keeps `scales_sheet_title` with the raw name. `scaleName`, which is used where a name stands alone (the Scales tab's cells, `ScaleLibraryCell`, and the search's `nameOf`), returns the resource with its first letter upper-cased. Do this through a small pure function, so it is tested:
   ```kotlin
   /** A scale's name as it stands alone, on a cell: the title form the strings hold, its first letter a capital. */
   internal fun standaloneScaleName(titleForm: String) = titleForm.replaceFirstChar { it.titlecase() }
   ```
   - `scaleTitle` must then read the resource directly (`scaleNameResource(scale.id)?.let { stringResource(it) } ?: scale.id`) rather than through `scaleName`.
   - The search already ignores case, so `scaleLibraryResults` is unaffected. Its KDoc example "`dur` the major scale in Hungarian" stays true.
   - Family labels (`scale_family_*`) and headings stand alone and are not touched.
2. **English (`values/strings.xml`).**
   - Lowercase the first letter of every name whose first word is a quality: "major", "natural minor", "harmonic minor", "melodic minor", "harmonic major", "double harmonic", "major pentatonic", "minor pentatonic", "blues", "major blues", "major hexatonic", "minor hexatonic", "whole tone", "augmented", "diminished (whole–half)", "dominant diminished (half–whole)", "chromatic", "bebop dominant", "bebop major", "bebop Dorian", "bebop melodic minor", "bebop harmonic minor", "altered", "ultralocrian", "major Locrian", "leading whole tone", and "tritone".
   - Keep the capital where the first word is a proper adjective or a name: Dorian, Phrygian, Lydian, Mixolydian, Locrian (and their ♮/♯/♭ variants), Ionian ♯5, Phrygian dominant, Lydian augmented, Lydian dominant, Lydian minor, Hungarian minor/major, Neapolitan major/minor, Egyptian, Man Gong, Ritusen, Hirajoshi, In, Insen, Iwato, Kumoi, Chinese, Prometheus, Persian, Enigmatic, Oriental, Flamenco, and Spanish eight-tone.
   - "Ultralocrian", "altered", "bebop", and "tritone" are lowercase in most theory texts; confirm with the user.
   - Cells read exactly as today ("Major", "Natural minor"), and titles become "C major", "C natural minor", "D Dorian".
3. **Hungarian (`values-hu/strings.xml`).**
   - `scales_sheet_title` → `%1$s-%2$s`.
   - Lowercase every name except proper names: Prométheusz, Man Gong, Hirajoshi, In, Insen, Iwato, Kumoi, Ritusen (**the user decides** whether the Japanese names are lowercase in Hungarian).
   - Resulting titles: "C-dúr", "A-természetes moll", "D-dór", "C-fríg domináns", "C-dúr pentaton", "C-bebop dór", "C-Prométheusz".
   - **The user decides** whether multi-word names take the hyphen ("A-természetes moll") or a space ("A természetes moll"); the pattern can only do one for all names.
   - Cells keep "Dúr", "Természetes moll" through the capitalization in step 1.
4. **Hungarian steps.**
   - Recommended: `scales_steps_whole` "1", `scales_steps_half` "½", and `scales_steps_augmented` "1½". This is the notation Hungarian solfège teaching uses (egész hang = 1, félhang = ½). A major scale reads "1 1 ½ 1 1 1 ½", and the chromatic scale's twelve "½" fit 360 dp.
   - Spelled out ("egész egész fél …") is rejected. A bebop scale's eight words are ~45 characters (~315 dp of `bodyMedium`), and the chromatic scale's twelve "fél" would wrap.
   - Update the English comment over these strings to say a language may use numbers.
   - **Caveat for the user:** the steps line sits right under the degrees line (`ScaleFormula`), so "1 1 ½ 1 1 1 ½"
     under "1 2 3 4 5 6 7" can read as a second row of degrees, as "E E F" reads as notes today. The ½ marks it as steps
     only where a half step is. If that is judged worse, the alternatives are "e e f" (lowercase, which notes never are
     in this app) or "1 1 ½" kept and accepted.
5. **Strings that carry a title** and need checking, not changing: `scales_mode_of` (HU `%2$s, %1$s. modus` → "C-dúr, 2. modus"), `scales_relative_minor` (HU `Párhuzamos moll: %1$s` → "Párhuzamos moll: A-természetes moll"), and `scales_diagram` (`%1$s: %2$s`).
   - The user may prefer `%2$s %1$s. modusa` in Hungarian ("C-dúr 2. modusa").
   - Every other `scale_*` key changes as listed in steps 2 and 3.
   - Both files keep their keys in the same commented group.
6. **`screens/scales/CLAUDE.md`**: add a line saying the name strings hold the form a title uses ("C major", "C-dúr"), capitalized where a name stands alone (`standaloneScaleName`).

## Tests

- In `ScaleNamesTest`, add `` `a scale's name standing alone starts with a capital` ``:
  - `assertEquals("Natural minor", standaloneScaleName("natural minor"))`;
  - `assertEquals("Dúr", standaloneScaleName("dúr"))`;
  - `assertEquals("Dorian", standaloneScaleName("Dorian"))`.
- No UI test. The titles are strings read from the resources, and a test asserting "C-dúr" would only repeat the string file. The user checks the Hungarian forms when deciding.
