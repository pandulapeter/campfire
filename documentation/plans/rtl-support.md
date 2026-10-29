<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Plan: right-to-left support, Hebrew first

**Status:** steps 1, 2, 3, 4, 5, 6, and 7 landed (2026-10-29). Written against the code on 2026-10-29 (abba88cf1). `ui/` is
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/`, as in the root `CLAUDE.md`.

The goal is a Hebrew UI that reads right to left on every platform, and Hebrew songs that read correctly whatever the
UI language is: on screen, in the editor, in the PDF, and when imported. Hebrew is the only RTL language planned, but
the work is built for RTL as such, so that Arabic, Persian, Urdu, or Yiddish later costs a font, a translation, and
the few script-specific rules listed under "Open to other RTL languages", never another pass over the layout code.

## What already works

- **RTL lyrics with chords** (a682bb1ef): `SongLyrics` sets `TextDirection.Content`, and `SongLineWithChords` places
  chords with `getHorizontalPosition` and spreads them leftwards on an RTL paragraph. Wrapping uses the text layout.
- **RTL text in the PDF's text layer** (8346a0161, 736ffc7ec): RTL runs carry `ActualText`, and the PDF importer reads
  Campfire's own exports back in reading order.
- **Mirrored arrows** (f9a0b4c37): `ic_back`, `ic_previous`, `ic_next`, `ic_undo`, and `ic_redo` are `autoMirrored`.
- **Most layout is logical**: no `absoluteOffset`, `absolutePadding`, `Arrangement.Absolute`, or `Alignment.*Left`
  anywhere; padding goes through `calculateStartPadding`; custom layouts use `placeRelative`; `SectionPlacement`,
  `ChordRows`, `SongDetailsPager`, `EdgeFade`, and `PaddingValuesSides` already read `LocalLayoutDirection`.
  `HorizontalPager` reverses itself.
- **Hebrew plurals**: the hyperether selector already picks `two` for 2 when the item exists. Only our own gates
  (`language.py`, the `add-language` skill) refuse it.
- **File names** keep Hebrew letters (`LibraryFiles.normalizedName`), and the code-point sort is alphabetical for א–ת.

## What is missing

1. **Nothing ties layout direction to the language.** `ApplyLanguagePreference` only switches the string tables. The
   Android manifest has `android:supportsRtl="false"`, iOS lists no `he` in `CFBundleLocalizations`, the web's
   `index.html` is `lang="en"` with no `dir`, and the desktop wraps the window in
   `LocalLayoutDirection.providesDefault(LayoutDirection.Ltr)` (a 2023 leftover).
2. **No Hebrew glyphs.** Inter and JetBrains Mono are subset to Latin, Greek, and Cyrillic. Android, Apple, and Windows
   fall back to system fonts. The web (Skia with no system fonts) and Linux probably show boxes today, even for a
   Hebrew song title in the English UI.
3. **Absolute motion**: `NavigationTransitions` slides `Left` forward and `Right` back (predictive back too);
   `CampfireScreens` and `ExportHost` hand-roll the export screen's `translationX` with fixed signs;
   `OnboardingPageMotion`'s parallax, `ListItemAnimation`'s grid offsets, and `SettingsTabRow`'s indicator spans
   still need checking against a reversed pager and RTL tab positions.
4. **Arrow keys**: `SongKeyboardShortcuts` and `PrintPages` map Left to previous and Right to next, which points the
   wrong way once the pager reverses.
5. **Song direction is decided line by line** from the first strong character, so in a Hebrew song a line starting
   with a Latin word or a chord-only line turns LTR and its chords hang the other way. Chord names inherit
   `TextDirection.Content`, so a Nashville `4/5` resolves RTL in an RTL context. In a mixed line, an LTR run's chord
   is anchored at the wrong edge, and `padLyricsToFitChords`' neutral padding can open the gap on the wrong side.
6. **The editor** (`ChordProTextField`) has no `textDirection`. Almost every ChordPro line starts with `[`, `{`, or a
   Latin directive, so a Hebrew lyric line becomes an LTR paragraph and its words show in reverse order between the
   chords. `textStart` adds the start padding as a left offset.
7. **The PDF layout is left to right**: `ExportScreen` builds the measurer with `LayoutDirection.Ltr`; `PrintRows`
   draws the lyric at x = 0, sums widths for chord x, and wraps an overflowing chord to the left edge; `PrintLayout`'s
   headings, comments, grids, and diagram rows are left-anchored.
8. **Importing another app's Hebrew PDF** comes out in visual (reversed) order: `PdfLineLayout` sorts glyphs by x and
   only `ActualText` reverses them. `ChordSheetConverter`'s position matching assumes x grows with the character index.
9. **Strings**: arguments (titles, artists, file names, chord names, keys) go into sentences unisolated; separators
   (`" · "`, `"label: value"` in the PDF) are built in code.
10. **Search and sort ignore niqqud**: `Char.isCombiningMark()` covers only U+0300–036F, so a search for "שלום" misses
    "שָׁלוֹם".
11. **Language codes**: Android reports `iw`, the others `he`; `AppLocale.findByCode` matches exactly.

## Decisions

- **No code names Hebrew.** Direction comes from data and from Unicode, never from a `"he"` check: a language is RTL
  because its entry says so, a character is RTL because its bidi class is R or AL, a mark is folded because of its
  script's entry in one table, and a font is used because it covers a script. Hebrew is the first row of each table.
- **The app's language decides the UI's direction, never the system's.** `AppLanguage` gets a `layoutDirection`
  (one property, set per entry), and one `CompositionLocalProvider(
  LocalLayoutDirection provides …)` in `CampfireApp`, derived from the resolved `AppLanguage`, so an in-app switch flips
  the layout at once with no restart, on every platform alike. Android gets `supportsRtl="true"` only so that system
  surfaces (dialogs, the IME's handles) agree; the desktop's `providesDefault` goes.
- **A song has one direction, and its body follows it.** `Song.textDirection` (pure, in `:chordpro`): the song's
  `{meta: language}` when it names a language in `:chordpro`'s table of RTL language codes (the CLDR list `language.py`
  already holds: `ar`, `fa`, `he`, `ur`, `yi`, …, legacy codes folded), else a majority vote over the lyrics' strong
  characters, LTR on a tie. "Strong RTL" is one function, `isStrongRtl(codePoint)`, covering every R and AL block
  (Hebrew, Arabic and its supplements and presentation forms, Syriac, Thaana, N'Ko, Samaritan, Mandaic), shared by
  the song, the editor, the PDF writer's `ActualText`, and the PDF importer. The song's body (sections, columns, chord diagrams, lyric paragraphs) is laid out in
  that direction, whatever the UI's. The first section's playing controls and the screen's chrome stay in the UI's.
  Lyric text gets the resolved `TextDirection.Rtl` or `Ltr`, not `Content`.
- **Music is always LTR**: chord names, tabs, grids, ABC notation, scale note lists, and the tuner meter and fretboard
  drawings are pinned to `TextDirection.Ltr` / `LayoutDirection.Ltr`, mirrored neither by the UI nor by the song.
- **Arrow keys are physical**: Left goes to whatever lies to the left. In an RTL pager that is the next item.
- **Nothing is written into files.** No RLM/LRM inserted by the editor or the importer (the converter already strips
  them); direction lives in `{meta: language}`, which the user sets.
- **Fonts are a table of script faces.** `ui/theme` holds a list of fallback faces, each naming the script ranges it
  covers; Noto Sans Hebrew (OFL), subset to the Hebrew block, three weights (~100 KB in all), is its first row. Every
  face is bundled for every platform and registered as a **fallback**, so a Hebrew song renders in any UI language. On
  the web a face is fetched only when the UI language's script or the library's text needs it, never on every launch,
  so a second script's face costs nothing to users who never meet it. Monospace: a face with Hebrew and a fixed
  advance is chosen in step 2; if none fits, Hebrew in the editor falls back to proportional and the editor's
  sideways alignment is accepted as imperfect for Hebrew lines.
- **Script marks are folded for search and sorting, not for file names**, from one table of foldable mark ranges per
  script (Hebrew points and cantillation first). Changing `normalizedName` would rename nothing existing (names are
  never migrated) but would make one title produce two names over time.
- **Plurals go through one seam.** Every plural lookup, the direct `pluralStringResource` calls included, goes through
  Campfire's `pluralTextResource`, which picks the CLDR category itself from a per-language rule table. Hebrew's rule
  (one, two, other) is its first row; English and the current languages keep one and other. This replaces the
  plugin's selector, which cannot express Arabic's few and many.

## Steps

Each step is one commit, verified by tests and, where it shows, a Hebrew `Shot` in `tools/screenshots`.

1. **Direction plumbing and a test hook.** Map `iw`/`ji`/`in` before `AppLocale.findByCode` (one legacy-code map,
   shared with `:chordpro`'s language codes). Add `AppLanguage.layoutDirection` and `isStrongRtl` with tests. Provide
   `LocalLayoutDirection` from the app language in `CampfireApp`; remove the desktop's `providesDefault(Ltr)`; flip
   `supportsRtl`. Teach `tools/screenshots` to render RTL (the direction now follows the shot's language; until
   `values-he` exists, a test-only override in `CampfireAppHost`). Nothing visible changes for current languages.
2. **Fonts.** The script-face table, and a `tools/fonts` script that subsets a face to the ranges its row names (so
   the next script is a row and a run). Subset Noto Sans Hebrew (and pick the monospace answer), wire the table as
   fallbacks in `interfaceTypography()` / `monospaceFontFamily()` per platform, the web's preload made conditional, and credit it
   (`OpenSourceComponents.kt`, its OFL text under `files/licenses`, `THIRD_PARTY_SOFTWARE.md`). A spike first on the
   web: `LocalFontFamilyResolver.preload` as a Skia fallback.
3. **Chrome motion and keys.** `SlideDirection.Start`/`End` in `NavigationTransitions` (predictive back from the
   start edge); the export screen's `translationX` signed by direction; arrow keys physical in
   `SongKeyboardShortcuts` and `PrintPages`; onboarding parallax, `ListItemAnimation`, and `SettingsTabRow` checked in
   an RTL shot and fixed where they double-mirror. Decide per icon whether `ic_sort`, `ic_label`, `ic_setlists`,
   `ic_sound`, and `ic_log` mirror.
4. **Song direction.** `Song.textDirection` with tests; `SongLyrics` and `SongSectionsLayout` take it instead of
   `LocalLayoutDirection.current` (`SongChordsSection` too), the first section's controls re-provided the UI's; chord,
   tab, grid, and notation styles pinned LTR (`SongNotations`' measurer included).
5. **Mixed-script lines.** In `SongLineWithChords`, the anchor edge from `getBidiRunDirection` at the chord's offset,
   the push-apart still by paragraph direction; padding that takes the paragraph's direction. The padding character
   becomes a per-script choice behind one function (NBSP for every script today), since a joining script widens a
   word with a tatweel, not a space that breaks its letters apart. Tests with Hebrew lines holding Latin words and
   numbers.
6. **The editor.** The field's paragraphs get the song's direction (an RTL paragraph keeps `[Am]` and directives as
   LTR runs inside it, which is right); if a spike shows per-line paragraph styles are possible through
   `ChordProOutputTransformation`, a directive line stays LTR in an RTL song. Fix `textStart` and check
   `revealOffset` and horizontal scrolling for right-aligned lines.
7. **The PDF.** The measurer in the song's direction; `PrintRows` lays an RTL row from the right edge, chords from
   `getHorizontalPosition` on the fragment's layout, pushed apart and wrapped leftwards; `PrintLayout`'s headings,
   comments, grids, and diagram rows mirrored for an RTL song; `"label: value"` from strings. Then
   `ChordSheetConverter`'s exact-position match uses right edges for RTL rows, so an export still imports back
   unchanged (the round-trip tests extended to Hebrew).
8. **Importing other apps' Hebrew PDFs.** A bidi reordering pass after `PdfLineLayout`'s x sort (runs reversed, run
   order by line direction, runs found with `isStrongRtl`, so it reorders any RTL script), with a small corpus of real Hebrew chord-sheet PDFs. The same corpus decides whether
   Hebrew plain-text sheets align chords logically or visually, and the converter follows what it shows.
9. **Strings.** First-strong isolation (U+2068/U+2069) of every argument in `textResource` / `String.withTexts`, and
   the few `stringResource(key, args)` calls with user text; separators built in code moved into strings or isolated.
   Every plural call moved onto `pluralTextResource` and its rule table (the screenshot tool's `getPlural` too).
10. **Search and sorting.** The foldable-marks table, Hebrew points and cantillation (U+0591–U+05C7) its first row,
    used by `NormalizeTextUseCaseImpl` and `NormalizeSearchTextUseCaseImpl`; final letters sort with their base letter.
11. **Unblock the language.** `language.py` and the `add-language` skill: RTL stops being a blocker and becomes a
    checklist any RTL language runs (`dir` on the web's loading screen, the iOS `.lproj`, each store's code for the
    language, as Play's `iw-IL`); a missing glyph blocks only when no row of the script-face table covers the script;
    a plural category is allowed when the language's rule table names it, and a language with no row is the blocker
    instead; the legacy-code blocker goes, the shared map handling it. Then Hebrew is added by the skill like any other language,
    which also covers `index.html`'s `lang`/`dir`, the iOS projects, the share extension, and the store listings.

Steps 1 and 2 come first; 3, 4–7, 8, 9, and 10 are independent of one another after that; 11 is last. Hebrew songs
(steps 2, 4–8, and 10) help users of every UI language and can ship before the Hebrew UI does.

## Open to other RTL languages

What another RTL language adds once this plan has landed, and nothing else:

- a row in each table: its font face (Noto Sans Arabic is ~150–250 KB a weight, fetched on the web only when needed),
  its plural rule (Arabic: zero, one, two, few, many, other), and its foldable marks (Arabic harakat, U+064B–U+065F
  and U+0670);
- for a joining script, the tatweel as its chord padding (step 5's seam), checked in Skia's FreeType path on the web
  and Linux, where joining is untested;
- digits only if wanted: the app writes Western digits everywhere, which Arabic, Persian, and Urdu readers accept;
  Eastern digits would be a display choice of its own;
- the `add-language` skill's run, as for any language.

## Not in this plan
- **The website** (`CampfireWebsite`: no RTL CSS, Inter only) is release-time work.
- **A Hebrew calendar** in the setlist date picker: Material 3's picker is Gregorian only, which is Israel's civil use.
