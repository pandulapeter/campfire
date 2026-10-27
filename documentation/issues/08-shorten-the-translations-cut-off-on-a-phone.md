# Shorten the German, Spanish, Romanian, and French labels a 360 dp phone cuts short or breaks

**Kind:** translation  ·  **Severity:** low  ·  **Platforms:** all (phones above all)
**Files:** `presentation/src/commonMain/composeResources/values-de/strings.xml`,
`presentation/src/commonMain/composeResources/values-es/strings.xml`,
`presentation/src/commonMain/composeResources/values-ro/strings.xml`,
`presentation/src/commonMain/composeResources/values-fr/strings.xml`,
`tools/screenshots/src/test/kotlin/com/pandulapeter/campfire/presentation/ui/screens/settings/LabelsFitTest.kt` (new),
`tools/screenshots/src/test/kotlin/com/pandulapeter/campfire/screenshots/TextFit.kt` (from plan 10),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/controls/SegmentedChoice.kt` and
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/CLAUDE.md` (only with the
decision's recommendation)

**Challenged:** amended — the Romanian accidentals description reads "Lăsat pe „Original”", and the Romanian theme
pair became a user decision, since "Închisă" alone on a segment also reads as "closed" (off).

**Runs after plan 10** in lane L (it uses plan 10's `TextFit.kt` helpers).

**Decision (user):** the Romanian theme labels. "Deschisă" is what Romanian apps say for a light theme, but
"Închisă" standing alone on a segment reads as "closed" as readily as "dark"; "Întunecată", the platforms' word, is cut
at 1.3 (93.6 against 86), as English "As written" and "Sharps (♯)" are in the accidentals row, which no string fixes.
Recommended: "Deschisă / Întunecată", and plan 11's `ShrinkToFit` on `SegmentedChoice`'s label (`maxLines = 1`, the
ellipsis kept, floor 75% as the navigation labels'), which also makes the English accidentals whole at 1.3; lane L
then runs 11 before 08. Alternative: the plan as written ("Deschisă / Închisă"), strings only.

## Problem

A live run rendered the app at 360 × 640 dp in en/ro/de/es at text scale 1.0 and 1.3 and read every text node's layout.
Widths below are Inter Medium at `labelLarge` (14 sp, 0.1 sp tracking), measured from the bundled font; they match the
run's measured widths within 1 dp ("Luminoasă" 73.7 vs 74, "Gitarre" 46.4 vs 47).

1. **Accidentals** (Settings → Preferences, `SegmentedChoice` in `ui/songDisplay/ChordsSection.kt`): three segments of
   a 336 dp row leave each label about **86 dp** (60 dp where the row keeps the selected check mark, which
   `hasRoomForCheckMarks` gives up for the whole row when any label is wider). Ellipsized at 1.0:
   - de `settings_accidentals_original` "Wie geschrieben" (114.5)
   - es `settings_accidentals_original` "Como está escrito" (123.3), es `settings_accidentals_sharps` "Sostenidos (♯)" (97.7)
   - ro `settings_accidentals_original` "Ca în original" (88.6)
   - fr fits ("Tel qu'écrit" 76.1, "Bémols (♭)" 70.2, "Dièses (♯)" 67.9); hu fits.
2. **Theme** (Settings → General and onboarding page 2, `SegmentedChoice`): ro `settings_user_interface_theme_light`
   "Luminoasă" (73.7) and `settings_user_interface_theme_dark` "Întunecată" (72.0) drop the check mark at 1.0 and are
   ellipsized at 1.3 ("Lumino…", "Întunec…": `sheets/onboarding-02-130.png`; 95.8 and 93.6 against 86).
3. **Paste** (`songs_paste_from_clipboard`, the songs hint's pill in `AddSongsTips.kt`, the empty list's action in
   `ListPlaceholder.kt`, the New menu in `NewItemMenu.kt`): de "Aus Zwischenablage einfügen" (205) wraps to two lines in
   its ~192 dp pill at 1.0 (`sheets/songs-100.png`) and in the New menu at 1.3; fr "Coller depuis le presse-papiers"
   (212) does the same (French was added after the run).
4. **Menus at 1.3** (`labelLarge` at 18.2 sp): ro `songs_edit_song` "Editează fișierul cântecului" (237) wraps in the
   220 dp song menu; ro `song_details_text_size` "Dimensiunea textului" (184) wraps beside the song menu's text size
   stepper (173 dp).

## Fix

Strings only; each change is the one language's file. The descriptions that quote a label change with it.

| File | Key | Now | New | Width at 1.0 / 1.3 |
|---|---|---|---|---|
| de | `settings_accidentals_original` | Wie geschrieben | Original | 52.7 / 68.5 |
| de | `settings_accidentals_description` | … Bei „Wie geschrieben“ wählt Campfire, … | … Bei „Original“ wählt Campfire, … | |
| es | `settings_accidentals_original` | Como está escrito | Original | 52.7 / 68.5 |
| es | `settings_accidentals_flats` | Bemoles (♭) | Bemoles | 58.3 |
| es | `settings_accidentals_sharps` | Sostenidos (♯) | Sostenidos | 75.5 |
| es | `settings_accidentals_description` | … Si se deja como está escrito, Campfire elige … | … Con «Original», Campfire elige … | |
| ro | `settings_accidentals_original` | Ca în original | Original | 52.7 / 68.5 |
| ro | `settings_accidentals_description` | … Lăsat ca în original, Campfire alege … | … Lăsat pe „Original”, Campfire alege … | |
| ro | `settings_user_interface_theme_light` | Luminoasă | Deschisă | 62.2 / 80.8 |
| ro | `settings_user_interface_theme_dark` | Întunecată | Închisă (only with the decision's alternative; recommended: unchanged) | 48.5 / 63.0 |
| de | `songs_paste_from_clipboard` | Aus Zwischenablage einfügen | Song einfügen | 98.9 / 128.5 |
| fr | `songs_paste_from_clipboard` | Coller depuis le presse-papiers | Coller une chanson | 131.0 / 170.4 |
| ro | `songs_edit_song` | Editează fișierul cântecului | Editează fișierul | — / 140.8 |
| ro | `song_details_text_size` | Dimensiunea textului | Mărimea textului | — / 147.0 |

Notes for the translator:
- "Original" is the Hungarian label's sense already ("Eredeti"), and what a player calls the file's own spelling.
  Spanish keeps the description's examples (`B♭`, `A♯`) for the symbols the labels give up; "Sostenidos ♯" (87.1) would
  still miss by a hair.
- Romanian "deschisă / închisă" are the shade words (a light or dark colour), agreeing with "temă" as the old ones did;
  "temă deschisă" is the common Romanian name of a light theme, while a lone "Închisă" also reads as "closed" (see the
  decision). "Deschisă" (62.2) still misses the check mark room (60) at 1.0 by 2 dp, so the row keeps showing no mark,
  as it does now; it is whole at 1.3.
- With the recommended decision, `SegmentedChoice`'s label (`ui/components/controls/SegmentedChoice.kt`, `SegmentedRow`)
  takes `autoSize = ShrinkToFit(minFontSize = fontSize * 0.75f, maxFontSize = fontSize)` (`fontSize` from
  `LocalTextStyle.current`, as `NavigationItemLabel` does), and `hasRoomForCheckMarks` keeps measuring at the full size,
  so the check mark rule is unchanged. Its KDoc and `ui/components/CLAUDE.md` say a label shrinks before it is cut.
- German "Song einfügen" and French "Coller une chanson" say what is pasted, which reads right in all three places
  (the hint, the empty list, the New menu). Keep "Zwischenablage" / "presse-papiers" in `paste_clipboard_empty`.
- Keep the Romanian "Double drop %1$s": Romanian keeps "Drop %1$s" and "Open %1$s" as guitar jargon too, as German and
  French do.

Not fixed here unless the decision's recommendation is taken (layout, not wording; every language including English
is cut):
- At 1.3 the accidentals row cuts English too ("As written" 89.4, "Sharps (♯)" 91.0 against 86); with the
  recommendation, `ShrinkToFit` makes them whole and the first test below also runs at 1.3.
- At 1.3 the selected tool's name in the Tools button group (`ui/chrome/ToolsButtonGroup.kt`) is cut in every language
  ("Metron…" in English, `sheets/tools-130.png`; "Stimmg…" in German). A shorter German "Stimmgerät" would not help.

## Tests

`tools/screenshots/.../ui/screens/settings/LabelsFitTest.kt`, using plan 10's `TextFit.kt` (`textLayout`, `isWhole`, `APP_LANGUAGES`):

- `` `the accidentals are named in full on the smallest phone in every language` ``: for each language at text scale 1,
  open Settings → Preferences (`settings_preferences`), `performScrollTo` the node of `settings_accidentals_original`,
  and assert each of the three labels whole. Find each label's own `Text` with `useUnmergedTree = true`.
- `` `the theme choices are named in full at large text in every language` ``: text scale 1.3, Settings → General,
  scroll to `settings_user_interface_theme_light`, assert the three theme labels whole. (With the recommended decision
  it fails on HEAD for ro through "Întunecată" until `ShrinkToFit` is on the label, and for "Luminoasă" either way.)
- `` `the paste action stays on one line on the smallest phone in every language` ``: text scale 1, on the Songs list
  (the demo library has two songs, so the hint shows), scroll to `songs_paste_from_clipboard` and assert it whole and
  on one line.

Each fails on HEAD for the language named above. (The menu wraps at 1.3 are two-line menu items, which Material allows;
they are fixed for looks and need no test.)
