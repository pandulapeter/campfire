# Use the app's Hungarian terms and the strings it already has

**Kind:** localization  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/composeResources/values/strings.xml`, `values-hu/strings.xml`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/PrintExportSheet.kt`
**Challenged:** amended — two of the claimed duplicates are not duplicates (`print_songs`, `print_time`) and one proposed Hungarian word ("Kották") appears nowhere in the file; only "Dalcsokor" is actually wrong; the labels edit must be made by symbol because lane A plan 07 edits the same lines.

Runs last in its lane (other plans add and remove `print_*` keys).

## Problem

- Hungarian calls a setlist "lista" everywhere (`setlists_*`, `import_result`); `print_setlist_content` says "Dalcsokor
  tartalma". ("Dalsorrend" for the running order and "Dallapok" for song sheets are ordinary words for what they name
  and introduce no second word for a setlist; they stay.) `print_page` reads "%1$d. oldal, összesen %2$d".
- Keys that duplicate existing ones, verified against both files:

  | print key | existing key | English | Hungarian |
  |---|---|---|---|
  | `print_preview` | `song_editor_preview` | Preview | Előnézet |
  | `print_select_none` | `songs_tags_clear` | Clear selection | Kijelölés törlése |
  | `print_key` | `song_editor_insert_key` | Key | Hangnem |
  | `print_capo` | `song_editor_insert_capo` | Capo | Capo |
  | `print_tempo` | `song_editor_insert_tempo` | Tempo | Tempó |
  | `print_chorus` | `song_details_section_chorus` | Chorus | Refrén |
  | `print_bridge` | `song_details_section_bridge` | Bridge | Átkötés |

  **Not duplicates**: `print_songs` is "Songs to export" / "Exportálandó dalok" against `songs` = "Songs" / "Dalok";
  `print_time` is "Time" against `song_editor_insert_time` = "Time signature" (Hungarian "Ütemmutató" matches, English
  does not). Both stay.

## Fix

- Delete the seven keys above in both files and use the existing ones. Make the code edit **by symbol** (each
  `Res.string.print_key` → `Res.string.song_editor_insert_key` and so on), never by copying whole lines of the
  `PrintLabels(` call: lane A plan 07 removes the `print_verse` argument and the defaults of `PrintLabels` from the
  same lines, and the same goes for `strings.xml`, where `print_verse` sits next to `print_chorus` / `print_bridge`.
  When the lanes meet, the end state is: `print_verse` gone, the seven keys gone, everything else untouched.
  Where plan 24 already removed `print_preview`, there is nothing to swap.
- Hungarian: `print_setlist_content` → "A lista tartalma"; `print_page` → "%1$d. oldal / %2$d". Reword nothing else
  without the author's say, and take no word that the file does not already use.
- Grep for every removed key in both files and in code before finishing (the code-style skill's clean-up rule).

## Tests

None; the build fails on a missing key.

## Manual check

Read the sheet through once in Hungarian, and the PDF's key / capo / tempo / chorus / bridge labels in both languages.
