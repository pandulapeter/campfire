<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# 00 — Core functional

This document covers everything that behaves the same on all four platforms. Run it **once**, on the macOS desktop
build: it is the quickest build to rebuild, and its library is a plain folder, so files can be dropped in and
inspected. The platform documents (01–06) repeat only the parts that differ per platform. A few checks in here
need a touch screen. They are marked *(touch)*: do them on the phone while running [01](01-android.md) or
[02](02-ios.md).

## Before you start

- **Build:** `./gradlew :app:desktop:run` from the checkout (debug, starts in about a minute), or a packaged build
  from `./gradlew :app:desktop:packageDistributionForCurrentOS`. Write down `git rev-parse --short HEAD`.
- **A clean library:** quit Campfire, then move `~/Library/Application Support/Campfire` aside (for example rename it
  to `Campfire.backup`). The next launch is a first run. Move it back when you are done.
  Settings → Library → Location opens the folder.
- **Fixtures:** run `fixtures/make_fixtures.py` as described in the [README](README.md), then keep
  `campfire-fixtures/` open in Finder. "Import" below means the app's Import files menu entry (from the "New song"
  dropdown, or the empty state). "Drop" means dragging from Finder onto the window.
- **Useful tools:** a text editor that shows line endings, `xxd` (`xxd file.cho | head`), `unzip -l`, and `chmod` for
  the unreadable-file tests.
- **Time:** about 4 to 5 hours for everything, or 1 hour for the P0s.
- **Write down** every failure as described in the README, together with the song file involved.

---

## 1. First run, demo library, preferences

- [ ] **CORE-001** (P0) A first run plants the demo library once
  1. Start with no data folder (see above).
  2. Launch.
  3. Check the Songs and Setlists tabs, and whether a snackbar appears.
  4. Delete both demo songs and the demo setlist, then quit and relaunch.

  **Expected:** On the first launch there are two demo songs ("Home on the Range", "House of the Rising Sun") and
  one setlist, "Getting started", with no snackbar. `preferences/preferences.json` exists. After the relaunch the
  library stays empty and nothing is planted again.
  <sub>CLAUDE, r1-42, r2-30</sub>

- [ ] **CORE-002** (P0) A library that was imported into and then emptied is not taken for a fresh installation
  1. Start with no data folder, then quit before the demo is planted: close the window as soon as it opens. If that
     is too quick to catch, use the next variant.
  2. Variant: start fresh, import `import/library-archive.zip`, delete everything (the demo songs included), quit and
     relaunch.

  **Expected:** Once the library has been emptied by hand, a relaunch never plants the demo again.
  <sub>r1-42</sub>

- [ ] **CORE-003** (P1) "Add the demo songs" appears only while a demo song is missing
  1. On a library that has the demo, open Settings → Library. There is no offer to add the demo songs.
  2. Delete "House of the Rising Sun". Check Settings → Library again.
  3. Tap **Add the demo songs**.
  4. Delete every song and look at the empty Songs screen.

  **Expected:** The offer appears after the delete. Tapping it brings back only the missing song, with no `_2` copy
  of the other. The empty state has three buttons: Create a song, Import files, Add the demo songs.
  <sub>CLAUDE, presentation</sub>

- [ ] **CORE-004** (P1) A file opened on the very first launch goes into the demo library
  1. With no data folder, open `songs/every-directive.cho` with Campfire: drag it onto the Dock icon, or run
     `open -a Campfire file` with a packaged build.

  **Expected:** The song opens. The library holds it plus the two demo songs and the demo setlist. The only
  "Songs imported" snackbar is the one for your file.
  <sub>r4-41</sub>

- [ ] **CORE-005** (P1) A demo song edited elsewhere asks before it replaces anything
  1. On a library that has the demo, export "House of the Rising Sun", change one lyric in the exported file, and
     import it.

  **Expected:** The conflicts question appears. **Keep both** adds a `_2` copy. **Replace** overwrites the demo
  song. **Skip** and **Cancel** change nothing.
  <sub>r4-41, r2-02</sub>

- [ ] **CORE-006** (P0) A corrupt preferences file costs only the setting that was broken
  1. Quit. In `preferences/preferences.json`, set `"fontScale": "big"` and one transposition value to `"2x"`, with
     the language set to Hungarian and a non-default theme beforehand.
  2. Launch.
  3. Change any setting.

  **Expected:** The language, the theme and the other transpositions survive, and the text size is back to the
  default. `preferences.json.bad` holds the text you edited. After the change, `preferences.json` is valid JSON
  again.
  <sub>r2-30</sub>

- [ ] **CORE-007** (P0) A torn preferences file never replants the demo
  1. Empty the library by hand, then quit.
  2. Truncate `preferences.json` to `{"fontScale": 1.` and launch.

  **Expected:** The app opens with the default settings. The library is still empty, and no demo is planted.
  <sub>r2-30</sub>

- [ ] **CORE-008** (P1) Unreadable preferences leave the library alone
  1. `chmod 000 preferences/preferences.json`, then launch.
  2. Try to change the theme.
  3. `chmod 644`, then press Retry on either list.

  **Expected:** The app opens in its defaults. Both lists show their error state with Retry. After Retry, the theme,
  language and transpositions come back, and the file is never rewritten meanwhile (compare its modification time).
  <sub>r2-30</sub>

## 2. Songs screen

- [ ] **CORE-010** (P1) Sorting by title handles digits, punctuation and other scripts
  1. Import every `songs/sort-*.cho`, and add an emoji-first title with the editor.
  2. Sort by title.
  3. Quit and relaunch.

  **Expected:** Nothing crashes. A single "0 - 9" section comes first and holds `7 Years`, `¿Dónde estás?`,
  `…And Justice` and the emoji title. Then `A`, then `I` (holding both `ırmak` and any `I…` title), and so on.
  `Катюша` is under `К`, after `Z`. The same order after the relaunch.
  <sub>r2-03</sub>

- [ ] **CORE-011** (P1) Sorting by artist
  1. With the same songs, sort by artist.
  2. Switch the sort mode back and forth quickly a few times.

  **Expected:** "Unknown artist", `2Pac` and `¡Forward, Russia!` come first. The fast scroller bubble reads `#` for
  that section and never again further down. Switching quickly does not crash, the list scrolls to the top, and the
  headers animate.
  <sub>r2-03</sub>

- [ ] **CORE-012** (P1) Search ignores case, accents, spaces and punctuation
  1. Open search from the app bar button (the magnifier morphs into a close mark). Also try Cmd+F.
  2. Type `ymca`, then `gesi`, then `KATYUSHA` and `катюша`.
  3. Close search, switch to Setlists and back.

  **Expected:** `ymca` finds `Y.M.C.A.`, and `gesi` finds `Gęsi za wodą`. The results have no section headers, and
  the list scrolls to the top on every query change. A closed search narrows nothing. A search left open keeps its
  text, with the caret at the end.
  <sub>CLAUDE, r2-03, r4-20, r3-36</sub>

- [ ] **CORE-013** (P1) Search fields are capped at 100 characters
  1. Paste a very long text (300,000 characters) into the Songs search, the Setlists search, the song picker, the
     setlist picker and the language picker.

  **Expected:** Each keeps only the first 100 characters. Typing stays responsive, and nothing crashes.
  <sub>r3-18</sub>

- [ ] **CORE-014** (P1) Tag and language filters
  1. Import `songs/every-directive.cho` (tags "Test" and "Every Directive", languages en + hu), `percent-100-sure.cho`,
     and a few more songs. Give one song the tag `Rock` and another `rock`.
  2. Open the sort and filter controls.
  3. Select one tag, then a second one.
  4. Delete every song carrying the second tag.

  **Expected:** The tags are counted and ordered most used first. `Rock` and `rock` are one chip, `Rock (2)`. The
  any/every choice appears only once two selected tags both still exist in the library. The language group appears
  only while the library holds more than one language (with "Unknown" for songs that declare none). The filter
  action shows a badge while a filter is on. The filter is forgotten on the next launch.
  <sub>CLAUDE, presentation, r2-35</sub>

- [ ] **CORE-015** (P2) Labels every song carries are not drawn under rows
  1. Put the same tag on every song.

  **Expected:** That tag no longer appears under each row. It still appears after filtering by it. The same goes for
  a single-language library.
  <sub>presentation</sub>

- [ ] **CORE-016** (P2) The row shows the key the song sounds in
  1. Look at `every-directive` (key G) in the list.
  2. Transpose it +2 on the details screen and come back.
  3. Put it in a setlist, transpose it there by −1, and look at the setlist.
  4. Turn lyrics-only mode on.

  **Expected:** Next to the artist, in the accent color: `G`, then `A` on the Songs screen, and `F#` or `Gb` inside
  the setlist. A song with no chords says "Lyrics only". In lyrics-only mode that place is empty.
  <sub>presentation</sub>

- [ ] **CORE-017** (P1) Two songs with the same title and artist keep their places
  1. Create two songs, both "Same" by "Artist" (the second becomes `artist-same_2.cho`).
  2. Toggle a tag on the first.
  3. Open a setlist's song picker.

  **Expected:** The rows do not swap. The picker lists the two in the same order as the Songs screen, before and
  after the tag change.
  <sub>r2-35</sub>

- [ ] **CORE-018** (P1) Fast scroller
  1. With the large library (`--large 2000 50`), use the mouse wheel, drag the thumb, and press on the track (it
     jumps and keeps dragging). Go to both ends.
  2. Search for something that leaves 3 songs.
  3. Hover over the scroller column.
  4. Start dragging the thumb and, while holding it, make the list shorter than one screen (select a tag filter with
     Tab and Space). Release, then widen the window.

  **Expected:** The bubble shows the section letter on Songs only. The thumb disappears when the list fits the
  window. Hovering shows a faint track. After step 4 the scroller is idle and follows the list, not stuck.
  <sub>r2-62, r3-14</sub>

- [ ] **CORE-019** (P2) The app bar tints when content scrolls under it
  1. On Songs, Setlists, song details and the editor, scroll a little. Also click a sticky section header, type a
     new search, and drag the fast scroller.
  2. Leave Songs scrolled, switch tabs and come back.

  **Expected:** The bar tints and lifts whenever content is under it, and is flat at the top. It is already tinted
  on the first frame when you come back to a scrolled Songs screen.
  <sub>r2-62</sub>

- [ ] **CORE-020** (P2) Scroll positions survive a tab switch
  1. Scroll Songs, Setlists and each Settings tab, then switch around.

  **Expected:** Each screen comes back where you left it.
  <sub>presentation</sub>

- [ ] **CORE-021** (P2) Flings and deletions animate cleanly
  1. Fling to the very top and the very bottom.
  2. Delete or rename a song while the list is at rest.

  **Expected:** No row slides in from outside the list after a fling. After the delete or rename, the rows slide
  into place.
  <sub>r2-62, r1-62</sub>

- [ ] **CORE-022** (P1) A large library stays usable
  1. Import `large-library.zip` (2,000 songs).
  2. Launch again and watch the list fill.
  3. Sort by title and by artist, filter by a tag, a language and "no chords".
  4. Tick songs quickly in a setlist's song picker.

  **Expected:** The list fills in a handful of steps. Sorting and filtering are quick and give consistent results.
  Ticking stays smooth, and changing the setlist order does not scroll the song list.
  <sub>r3-43, r1-44, r1-45</sub>

## 3. Song details

- [ ] **CORE-030** (P1) 🆕 Right-to-left lyrics start at the right edge, with each chord over its word
  1. Import `songs/rtl-hebrew.cho` and open it.
  2. Import `songs/rtl-arabic.cho` and open it. Narrow the window until its long line wraps, then widen it again.
  3. Open a demo song at the smallest and at the largest text size (Cmd + scroll wheel).

  **Expected:** The Hebrew lines are right-aligned. `Am` sits over שלום (rightmost), then `C`, `G`, and `F` over יפה
  (leftmost). The chords are spread across the line with none stacked at the right edge and none overlapping. On the
  wrapped Arabic line, the chords follow their words onto the second visual line, which also starts from the right.
  Left-to-right songs look exactly as before. Repeat once on the web build (06) and once on a phone (01/02); each
  uses a different text engine.
  <sub>r5-35</sub>

- [ ] **CORE-031** (P1) Everything the file says is drawn
  1. Open `songs/every-directive.cho`.

  **Expected:** The header shows one language chip covering both languages (English, Hungarian), the tags, and an
  "Add tag" chip. The
  capo, tempo and time are on an accent line. The composer, lyricist, album, year and duration are on a quieter line
  under it. The subtitle is in parentheses after the title in the bar. The three comment styles and the highlight
  are drawn. The chorus is a card, and `{chorus}` recalls it. The tab is monospaced, the grid is drawn as bars, and
  the ABC block is plain lines. The `chorus-guitar` block shows as a chorus. Nothing reads as raw directive text.
  <sub>presentation, r4-26, r4-27, r4-28</sub>

- [ ] **CORE-032** (P1) The columns follow the window
  1. Open a long song. Resize the window slowly across the one/two-column point and back.
  2. Turn on "Read across columns" (Settings → Songs), and resize again.
  3. Open `{title: x}` (a song with no sections).

  **Expected:** Sections animate between layouts and never split. Across mode fills rows across the page. Narrow
  windows show a single centered column. The empty song opens without a crash, on an empty page.
  <sub>r2-56, r2-61</sub>

- [ ] **CORE-033** (P1) A very tall section opens instead of crashing
  1. Import `songs/tall-section.cho`, open it, and scroll to line 6000.
  2. Set the text size to maximum and back. Make the window narrow, then wide.
  3. Open it in the editor's Preview.

  **Expected:** It may open slowly, but it opens and scrolls to the end. Nothing crashes. A short first verse
  elsewhere still animates, while the tall one jumps between layouts. The preview renders.
  <sub>r2-54</sub>

- [ ] **CORE-034** (P2) Tapping a section header stops just under the app bar
  1. Tap a section header pill at 100% and at 250% text size, on `every-directive` (capo, tempo, composer lines), in
     performance mode, and in the editor preview.

  **Expected:** The pill ends 8dp under the app bar every time.
  <sub>r3-37</sub>

- [ ] **CORE-035** (P1) Text size
  1. Use the stepper, pinch (on a trackpad), and Cmd + scroll wheel, one notch at a time.
  2. Relaunch.

  **Expected:** Each wheel notch changes the size by about 5%, and never jumps to 50% or 250%. Pinching is damped.
  The size is kept after the relaunch.
  <sub>r3-21, features</sub>

- [ ] **CORE-036** (P1) Lyrics-only mode and performance mode
  1. Turn on lyrics-only mode and open `every-directive`.
  2. Turn it off, then turn on performance mode (Settings → General) and go through every screen.
  3. While in performance mode, drop a `.cho` onto the window.

  **Expected:** Lyrics-only mode drops the chords, the tab and grid sections, and the transposition control.
  Performance mode removes the two New buttons, the song menu (and its long press), the setlist header menu, the
  drag handles, row swipes, "Add to setlist", the tag and language chips, and the transposition stepper. Search,
  sort, filters, text size and lyrics-only remain. Settings keeps every row, with the two library actions disabled.
  The dropped file is still imported.
  <sub>presentation, features</sub>

- [ ] **CORE-037** (P1) Arrow keys page and scroll
  1. Open a song from a setlist of 4 or more songs.
  2. Hold Down, then Up.
  3. Press Right twice quickly, then Left.
  4. At the second-to-last song, press Right three times. Hold Right from the first song.

  **Expected:** Up and Down scroll smoothly, and continuously while held. Two quick presses of Right move two songs.
  The last song stops the pager and disables Next. Holding Right runs to the end without an error. At the ends and
  on a single song, the keys do nothing.
  <sub>r3-32, features</sub>

- [ ] **CORE-038** (P1) 🆕 Arrow keys with a modifier are left to the system
  1. On the song details screen, press Cmd+Left, Cmd+Right, Alt+Left and Ctrl+Left.
  2. Press Escape.
  3. Press Cmd+F from a list screen.

  **Expected:** None of the modified arrows pages or scrolls the song. Plain arrows still do. Escape still closes the
  screen, and Cmd+F still opens the list search. (In a browser, Alt+Left and Cmd+Left are the browser's Back: see 06.)
  <sub>r5-34</sub>

- [ ] **CORE-039** (P1) A song that disappears under an open screen
  1. Open a song, then delete its file in Finder and bring the app to the front (which rescans).
  2. Open the only song of a setlist, then delete that file the same way.

  **Expected:** Neither screen sits on a loading indicator forever. The setlist case goes back to Setlists and is not
  stuck.
  <sub>r1-52</sub>

- [ ] **CORE-040** (P2) A theme change while a song is open
  1. Open a song and switch the theme and the color.
  2. Transpose through 12 steps.

  **Expected:** The chord colors cross-fade. No chord overlaps its neighbour at any step, and wrapping stays right.
  <sub>r2-57</sub>

## 4. Chords, transposition, notation

- [ ] **CORE-050** (P0) 🆕 Bracketed words are never transposed, not even in the file
  1. Import `songs/bracket-labels.cho` and open it. Transpose +2 on the details screen.
  2. Open the editor, transpose +2 there, and save. Open the file in a text editor.
  3. Set Accidentals to Sharps, then to Flats.

  **Expected:** `[Intro] [Break] [Chorus 2x] [Bass] [Ebony]` stay as they are on the details screen, in the editor,
  and in the saved file. `[G] [C] [D]` and `[ G ]` move. `[ *softly]` is an annotation (italic, not a chord), and
  `[]` is not colored as a chord.
  <sub>r5-08, r5-14</sub>

- [ ] **CORE-051** (P1) 🆕 A lowercase bass note follows its chord
  1. Import `songs/bass-notes.cho`. Transpose +2 on the details screen, then +2 in the editor, and save.

  **Expected:** `D/f#` becomes `E/g#`, `d/f#` becomes `e/g#`, `A/c#` becomes `B/d#`, and `Am7/G` becomes `Bm7/A`. The
  bass note keeps its case, both on the screen and in the saved file.
  <sub>r5-09</sub>

- [ ] **CORE-052** (P1) 🆕 German notation reads `C/h` as C over B
  1. Import `songs/german-bass.cho`. Turn the German notation setting off, then on. Transpose +1.
  2. Import `songs/german-notation.cho` and do the same.

  **Expected:** The file is detected as German (it has an `H`). `[C/h]` reads `C/H` with the setting on and `C/B`
  with it off, and transposes as C over B. `Boci boci tarka` shows `Bb F B7` (key `Bb`) with the setting off and
  `B F H7` (key `B`) with it on. +2 gives `C G C#7`, key `C`, either way. Editor up then down restores `[B] [F] [H7]`
  exactly.
  <sub>r5-09, r2-38</sub>

- [ ] **CORE-053** (P1) Lowercase roots are minor chords
  1. Import `songs/lowercase-minors.cho` and transpose it.

  **Expected:** They show as `Am Em Hm C` (German, since `h` is present) and transpose with the rest. The editor
  keeps them lowercase in the file.
  <sub>r4-23</sub>

- [ ] **CORE-054** (P0) The editor's transpose round-trips byte for byte
  1. Write a song with no `{key}`: `[Eb]a [Bb]b [Cm]c`. In the editor, transpose up to +5 and back to 0, then save.
  2. Write `{key: E}` with `[E] [A] [B]`, and transpose +2 on the details screen.

  **Expected:** The first file is byte-identical to the original. The second shows key `F#` and `F# B C#`, in the
  header and in the list row.
  <sub>r2-41</sub>

- [ ] **CORE-055** (P1) Unicode accidentals
  1. Import `songs/unicode-accidentals.cho`. Transpose +1 and back. Open it in the editor.
  2. Export it and look at the exported file.

  **Expected:** The list says `F#m`, and the screen shows `Bb` and `F#m7b5` with no empty boxes. The editor source
  keeps `B♭`, with no pending save. The export still contains `♭ ♯`.
  <sub>r2-42</sub>

- [ ] **CORE-056** (P1) Directive spellings other apps write
  1. Import `songs/colonless-directives.cho` and `songs/meta-directives.cho`.
  2. Transpose the second one +2 in the editor, and save.

  **Expected:** "Wonderwall" by Oasis, file `oasis-wonderwall.cho`, with a "Verse 1" section. `{Verse 2}` stays a
  lyric line. "Amazing Grace" by John Newton, key G, file `john_newton-amazing_grace.cho`. The editor toolbar no
  longer offers Title or Artist for it. The saved file says `{meta: key A}`.
  <sub>r4-19, r4-21</sub>

- [ ] **CORE-057** (P1) 🆕 Non-breaking spaces in tabs and grids
  1. Import `songs/nbsp-tab-grid.cho`, transpose +2, and compare with the same file on the web build (06).

  **Expected:** The tab columns stay aligned and the grid bars read as bars. The two builds show identical text.
  <sub>r5-11</sub>

- [ ] **CORE-058** (P1) 🆕 A wrapped tab row is never cut through a character
  1. Import `songs/wide-tab-multibyte.cho`. Narrow the window step by step until the tab wraps into several rows.

  **Expected:** At no width does a `�` or half a glyph appear at either edge of a row. The emoji, the `𝄞` and the
  `é` land whole on one row.
  <sub>r5-12</sub>

- [ ] **CORE-059** (P0) 🆕 A CR-only file keeps its line endings
  1. Import `songs/cr-only.cho` and open it in the editor.
  2. Add a tag from the details header. Check the file with `xxd`.
  3. Transpose it in the editor and save. Check the file again.

  **Expected:** The editor colors the directives. The `#` line is a comment, and `[x]` inside the tab is not a chord.
  After each change, only the edited line differs, and every line still ends in `0d` (CR) with no `0a`. Repeat with
  `songs/crlf.cho`: exactly one added line, still CRLF.
  <sub>r5-10, r1-05</sub>

- [ ] **CORE-060** (P1) Grids
  1. Open the demo "House of the Rising Sun", and a grid with margin labels, `:|` and `C~G` (`every-directive`).
     Transpose +2 on the details screen and in the editor.

  **Expected:** The labels are in the lyrics color and the bars in the bar color. `C~D` becomes `D~E`. Labels such
  as `Coda` do not move.
  <sub>r4-22, r2-37</sub>

- [ ] **CORE-061** (P1) Hostile input
  1. Import `songs/crafted-comment.cho`, and open it on the details screen and in the editor. Type in the editor.

  **Expected:** It imports at once, and the odd line shows as lyrics. Typing does not stall. The German setting on
  or off makes no difference.
  <sub>r2-39, r2-40</sub>

## 5. Editor

- [ ] **CORE-070** (P0) Unsaved text is always asked about
  1. Type in the editor, then leave by each route in turn: Escape, Close, the Back arrow, closing the window, and
     Cmd+Q.
  2. Answer Cancel, then Discard, then Save.

  **Expected:** Every route asks Save, Discard or Cancel. Cancel keeps you in the editor. Save leaves only once the
  file has been written.
  <sub>r1-54, r2-05, features</sub>

- [ ] **CORE-071** (P0) A failed save never loses the text
  1. `chmod a-w <library>/songs`. Type in the editor, press Escape, then Save.
  2. Close the window, answer Save.
  3. `chmod u+w`, then Save.

  **Expected:** The dialog goes away, the editor stays with your text, "Could not save the song" appears, and Save is
  still enabled. On the window close, the app does **not** quit. The final Save writes the file.
  <sub>r2-05</sub>

- [ ] **CORE-072** (P0) The file disappears while the editor is open
  1. Open the editor on a song and type. Delete the file in Finder, then bring the app back to the front.
  2. Press Escape and answer Save.

  **Expected:** A snackbar says the file is gone. Save stays enabled and Revert is disabled. Saving recreates the
  file with your text, and the song is back in the list.
  <sub>r2-46</sub>

- [ ] **CORE-073** (P1) The first read fails
  1. `chmod a-r` a song, then open the editor from its menu.
  2. `chmod u+r`, then press Retry.

  **Expected:** An error state with Retry and Close, not an endless spinner. Retry opens the song.
  <sub>r2-46</sub>

- [ ] **CORE-074** (P1) Two quick saves land in order
  1. Press Cmd+S, change the text, and press Cmd+S again right away.
  2. On the details screen, click the ✕ of two tags in quick succession.

  **Expected:** The file holds the second text, and both tags are gone.
  <sub>r1-51</sub>

- [ ] **CORE-075** (P1) Transposing keeps the caret
  1. Put the caret after a word near the end of a song and transpose +4. Type a letter.
  2. Select a word and transpose. Undo four times. Then Revert.

  **Expected:** The letter lands after that word, and the selection stays on the same word. Undo restores the
  original.
  <sub>r3-30</sub>

- [ ] **CORE-076** (P1) Each pane keeps its scroll position
  1. Scroll the editor, switch to Preview and back.
  2. On a wide window, switch between Split and a single pane.

  **Expected:** Each pane comes back where it was.
  <sub>r3-31</sub>

- [ ] **CORE-077** (P1) 🆕 Highlighting follows every keystroke, CR-only and empty brackets included
  1. Type inside a chord, a directive, a comment and a tab block.
  2. Undo, redo, Revert and transpose.
  3. Type `[ *softly]`, `[]` and `[ G ]`.

  **Expected:** Chords are bold in the accent color, directive names are bold, and comments are italic. Nothing
  inside a tab block is colored as a chord. `[ *softly]` is colored as an annotation. `[]` is not colored. `[ G ]`
  is a chord.
  <sub>r2-59, r5-14</sub>

- [ ] **CORE-078** (P1) The editor's controls
  1. Open the editor on narrow and wide windows.
  2. Insert every metadata button, then try each one again.

  **Expected:** The editor slides up, with Close instead of Back. Undo and redo work. The only item in the overflow
  menu is Revert, and it asks first. The metadata buttons insert into the header and are disabled once present,
  except Tag and Language, which repeat. The toolbars are folded when the window's shorter side is under 600dp. The
  "Shortcuts" label shows when the window is wider than compact. Split is offered only on an expanded window. The
  preview follows the text size but not lyrics-only mode.
  <sub>presentation</sub>

- [ ] **CORE-079** (P2) Pasted line breaks in a tag or a title
  1. Paste two CRLF lines as a tag in the "Add tag" dialog.
  2. Paste two CRLF lines as the title of a new song.

  **Expected:** The tag reads "Christmas Carols" on one line, the song body is unchanged, and removing the tag leaves
  the file byte-identical. The title is joined with a space, there are no stray lyric lines, and there is no
  "Update file name" entry.
  <sub>r3-39</sub>

- [ ] **CORE-080** (P2) A double-click on Close
  1. Double-click, then triple-click, the editor's Close.

  **Expected:** The editor closes and the song stays open. It is not closed too.
  <sub>r3-12</sub>

## 6. Tags and languages

- [ ] **CORE-090** (P1) Tags are written into the file
  1. Add a tag from the header, check the file, and remove it.
  2. Add a 60-character tag.

  **Expected:** Adding writes a `{tag: …}` line and removing takes it away. The long tag is ellipsized, and its ✕
  still removes it.
  <sub>CLAUDE, r4-42</sub>

- [ ] **CORE-091** (P1) User text with `%` in it
  1. Import `songs/percent-100-sure.cho`. Delete it, and cancel.
  2. Create a setlist named `50% off`, duplicate it, and delete it.
  3. Switch to Hungarian and repeat.

  **Expected:** The dialogs name `100% sure` and `50% off (copy)` as written. `{composer: 50% Dylan}`,
  `{tempo: 120%}` and the tag `100% live` are shown verbatim, and the row still shows a key. The same in Hungarian.
  <sub>r2-63</sub>

- [ ] **CORE-092** (P1) The language picker
  1. Open the language chip. Search `HUN`, then `hu-HU`, then `magyar`. Tick two languages and confirm.
  2. Switch the app to Hungarian and look at the chips.

  **Expected:** Each search finds Hungarian. The song's own languages are held at the top while the picker is open.
  Confirming writes once. With the app in Hungarian, the names follow ("angol"). An unknown code is shown in capitals.
  <sub>presentation, CLAUDE</sub>

## 7. Setlists

- [ ] **CORE-100** (P1) The whole life of a setlist
  1. Create a setlist from the Setlists screen. The song picker opens on it: tick 5 songs.
  2. Edit its title and description. Duplicate it (the copy is named before it is made). Archive it and unarchive it.
  3. Search it by its description, and by the title of one of its songs.
  4. Filter the Songs screen by a tag that hides some of its songs.
  5. Delete it.

  **Expected:** The picker lists the setlist's songs first, in its order, then the rest alphabetically. The
  description shows under the header. Archiving fades the icon into the pill, archived setlists come last, and "Show
  archived" is off by default. The search shows the setlist whole. The Songs filters never hide songs in a setlist.
  <sub>CLAUDE, presentation</sub>

- [ ] **CORE-101** (P1) Setlists in an empty library
  1. Delete every song but keep the demo setlist.
  2. Delete the setlist.
  3. Create a setlist, then press "Add songs".
  4. Import `import/setlist-missing-song.setlist.json` alone.

  **Expected:** The Setlists tab lists the setlist with its menu and an "Add songs" row. After the delete: "No
  setlists yet", with two buttons. The picker says "Your library is empty". The imported setlist is listed, with the
  missing entry shown as missing and removable.
  <sub>r3-08</sub>

- [ ] **CORE-102** (P0) Reordering is written once, and never jumps
  1. Reorder a 10-song setlist by the drag handle, and by a long press anywhere on a row.
  2. Drag a row past the end of the setlist, over another setlist, and back.
  3. Drag to the bottom edge so the list auto-scrolls.
  4. Use Move up and Move down from the row menu.
  5. Swipe two songs out quickly.

  **Expected:** Each drag writes once, in the order you left it. Hovering over another setlist does nothing, and
  there is no freeze of about a second. Auto-scroll stops at the end of the setlist. Move up is not offered on the
  first row, Move down not on the last, and neither on a one-song setlist. Both swiped songs are gone from the file.
  <sub>r3-35, r4-30, r1-50</sub>

- [ ] **CORE-103** (P1) Transposing inside a setlist
  1. Open a song from a setlist and press + five times as fast as possible.

  **Expected:** The setlist file says `"transposition": 5` for that entry. The same song opened from the Songs screen
  is untransposed.
  <sub>r1-50</sub>

- [ ] **CORE-104** (P1) Renaming a setlist moves its file
  1. Edit `Summer set` to `Autumn set`.
  2. Change only the capitals.

  **Expected:** There is one file, `autumn_set.setlist.json`, with its entries and transpositions intact. The capitals
  change keeps the file and updates the title inside it.
  <sub>r2-33</sub>

- [ ] **CORE-105** (P0) Fields from a future version survive
  1. Import `import/library-archive.zip`, then `import/setlist-future-field.setlist.json`.
  2. Reorder, transpose, archive and rename that setlist.
  3. Export it and import it into an empty library (web is fine).

  **Expected:** The renamed file still carries `"venue": "Pécs"` at the top level and `"note": "capo 2"` on the
  entry, after every step and after the round trip.
  <sub>r4-05</sub>

- [ ] **CORE-106** (P1) Long setlist titles
  1. Give setlists titles of 10, 40, 60 and 500 characters.
  2. Archive one with "Show archived" on.

  **Expected:** Every ⋮ stays at the end of its pill. Long titles wrap to two lines, and only a third line is
  ellipsized. Renaming to a short title shrinks the pill.
  <sub>r3-07</sub>

- [ ] **CORE-107** (P1) Sheets never leave an invisible layer
  1. Open each sheet in turn (sort and filter, setlist picker, song picker, song display, language picker). Close
     each with ✕, a click on the scrim, and Escape.
  2. After each close, click something on the screen behind.

  **Expected:** Every click after a close lands. No tap is swallowed.
  <sub>r2-55, presentation</sub>

- [ ] **CORE-108** (P2) The same song twice in a hand-edited setlist
  1. Edit a setlist file to name one song twice, then open the setlist.

  **Expected:** No crash. The song appears once.
  <sub>r1-03</sub>

## 8. Import

- [ ] **CORE-120** (P0) 🆕 "Keep both" keeps the setlist pointing at the edited song
  1. Start from an empty library. Import `import/keep-both-1.zip`.
  2. Import `import/keep-both-2.zip`. The conflicts question appears: answer **Keep both**.
  3. Open both setlists, and the song from each.
  4. Repeat from an empty library, answering **Replace**. Then again, answering **Skip**.

  **Expected:** Keep both: `campfire_tests-keep_both_test.cho` ("original words") and `…_2.cho` ("EDITED words").
  The original setlist `keep_both_test.setlist.json` still opens the original, and a second, numbered setlist opens
  the `_2` song. Replace: one song with the edited words, one setlist, and no numbered copies. Skip: nothing changes.
  <sub>new, follow-up to r5</sub>

- [ ] **CORE-121** (P0) An import decides before it writes
  1. Export the library, then import the export straight back.
  2. Delete two songs and their setlist, and import the export again.
  3. Edit one exported song, then import. Answer each of Replace, Skip, Keep both and Cancel, on separate attempts.

  **Expected:** The first import reports everything "already there", with no dialog and nothing new on disk. The
  second brings back exactly what was deleted, under the same names. On the third, the dialog lists exactly one
  name. Replace restores it and leaves every other file byte-for-byte. Skip changes nothing. Keep both adds a `_2`
  (or `_3`). Cancel leaves the library untouched.
  <sub>r1-06, r2-02, r2-15, r4-17</sub>

- [ ] **CORE-122** (P1) 🆕 A byte order mark is never content
  1. Import `songs/bom-start.cho`, `songs/bom-double.cho` and `songs/bom-joined.cho`.
  2. Import `songs/bom-joined.cho` again.

  **Expected:** "Byte Order Mark". "Two marks" (as `two_marks.cho`). Two songs, **First** and **Second**, where the
  second one's title is its `{title}` and not a stray first line. The second import reports both as already there.
  <sub>r3-41, r5-13</sub>

- [ ] **CORE-123** (P1) Legacy encodings
  1. Import `songs/cp1250-hungarian.cho`, `songs/cp1252-french.cho`, `songs/utf16-with-bom.cho` and
     `songs/utf16-no-bom.cho`.
  2. Tag each one, then `head -c 3 file | xxd`.

  **Expected:** Titles `Árvíztűrő tükörfúrógép` and `Café à la crème` read correctly. The two UTF-16 files are one
  song, "Ősz" by "Teszt"; the other is "already there". After tagging, every file is UTF-8 with no byte order mark
  (`7b 74 69`).
  <sub>r1-32, r4-06, r2-28</sub>

- [ ] **CORE-124** (P0) 🆕 Composed and decomposed names are one song
  1. Import everything in `songs/nfc/`. Then import `songs/nfd/`.
  2. Put the NFD files into the library folder directly, and bring the app to the front.

  **Expected:** The second import reports all three as already there. Nothing is numbered, and no `_2` file appears.
  A library file with a decomposed name offers **Update file name** once, and never again after taking it. The file
  names are `кино-катюша_й.cho`, `teszt-cafe_elan.cho` and a Vietnamese name with its letters kept.
  <sub>r5-07, r2-26</sub>

- [ ] **CORE-125** (P1) Size limits
  1. Pick `size/oversized-9mib.cho`, `size/huge-20mb.cho` and two ordinary songs together.
  2. Import `import/finder-with-junk.zip`.
  3. Import `import/songbook.txt`.

  **Expected:** Two songs are imported. The two large files are skipped, and a second snackbar says they are too
  large. From the zip, Hey Jude, Who Are You and Con are imported, and the PDF, MP3 and `__MACOSX` entries are
  skipped. The songbook gives three songs, the title-less one as `untitled.cho`.
  <sub>r2-15, r2-02</sub>

- [ ] **CORE-126** (P1) Windows zip entry names
  1. Import `import/windows-oem-names.zip`.

  **Expected:** A song titled `tukorfurogep`, not garbled letters. The report names `Tükörfúrógép.cho`.
  <sub>r4-29</sub>

- [ ] **CORE-127** (P1) Hidden macOS files are not songs
  1. Copy `songs/hidden/._test.cho` and `.DS_Store` into `library/songs`, and bring the app to the front.
  2. Import `._test.cho` together with a real song.

  **Expected:** The song count is unchanged, and there is no `._test` song. The import reports 1 imported and
  1 skipped.
  <sub>r2-27</sub>

- [ ] **CORE-128** (P1) Counted sentences read right, in both languages
  1. Import one `.cho`. Then import a zip holding one changed file and one identical file. Then two identical files.
  2. Repeat in Hungarian.

  **Expected:** "Songs imported: 1 · Setlists: 0 · Already there: 0 · Skipped: 0". The dialog says "One file is
  already in your library…", or "2 files are…". Singular and plural are correct in Hungarian too.
  <sub>r4-31</sub>

- [ ] **CORE-129** (P1) Imports queue, and never replace a dialog you are typing in
  1. Open the New song dialog and type a title. Drop `import/keep-both-2.zip` (after keep-both-1) onto the window.
  2. Cancel the dialog.
  3. With the editor holding unsaved text, drop another conflicting file.

  **Expected:** The dialog stays. The conflicts question appears only after you cancel it, and the import completes.
  The editor's unsaved question also stays up first. Files dropped while another import runs are queued, not lost.
  <sub>r3-33, r1-53</sub>

- [ ] **CORE-130** (P1) One song handed over opens that song
  1. Drop a single `.cho` onto the window.

  **Expected:** It is imported and opened. If the editor is open, it is not opened over the editor.
  <sub>presentation</sub>

- [ ] **CORE-131** (P1) A large import stays responsive
  1. Import `large-library.zip`. Then import it again.

  **Expected:** The progress bar moves and the list scrolls during planning. The second import reports everything as
  already there, and the window stays responsive.
  <sub>r2-34, r3-48</sub>

## 9. Export

- [ ] **CORE-140** (P0) 🆕 An export never hands out half a library
  1. `chmod 000` one song, then Export library.
  2. `chmod 000 <library>/songs`, then Export library.
  3. Put `size/huge-20mb.cho` into `library/songs`, restore the permissions, then Export library.

  **Expected:** The first export saves every other song and a snackbar names the unreadable one. The second shows
  "Export failed", with no setlists-only zip. The third leaves the huge file out of the list, and the export names
  it as not in the archive. Repeat in Hungarian for the plural.
  <sub>r5-06</sub>

- [ ] **CORE-141** (P1) Export dates and names
  1. Export the library. Run `unzip -l campfire_library.zip`.
  2. Import it into an empty library.

  **Expected:** Every entry is dated today in local time, never 1979. The entries keep their library names. The round
  trip is complete.
  <sub>r2-31, CLAUDE</sub>

- [ ] **CORE-142** (P1) An export over the import limit
  1. With `--large 9000 50`, export the library.
  2. Import that archive into an empty library.

  **Expected:** The export saves, with a message that it is larger than Campfire can import at once. The import says
  the file is too large.
  <sub>r3-09</sub>

- [ ] **CORE-143** (P1) A double-click opens one dialog
  1. Double-click Export library. Double-click Export in a song menu and in a setlist menu.

  **Expected:** One save dialog each time. After cancelling, the next click works.
  <sub>r3-10, r3-11</sub>

## 10. File names and "Update file name"

- [ ] **CORE-150** (P0) New names follow the header
  1. Create `Gęsi za wodą` by `Kasia`, `Катюша` by `Кино`, a setlist `Летний сет`, and a second `Gęsi za wodą` by
     `Kasia`.
  2. Export one song and look at the proposed name.

  **Expected:** `kasia-gesi_za_woda.cho`, `кино-катюша.cho`, `летний_сет.setlist.json`, and `kasia-gesi_za_woda_2.cho`.
  The export proposes the same name.
  <sub>r2-26, r4-20, CLAUDE</sub>

- [ ] **CORE-151** (P0) 🆕 Renaming never names a song twice
  1. Put `hello.cho` (`{title: Hello}`, `{artist: Adele}`) into the library, plus a setlist `gig.setlist.json` that
     names `adele-hello.cho` (missing) and then `hello.cho`.
  2. On the Setlists screen, Gig shows one missing row and "Hello".
  3. From the Songs screen, choose Update file name on Hello.

  **Expected:** Gig holds a single "Hello" row and the missing row is gone. The file names `adele-hello.cho` once.
  Nothing crashes. Before this fix it crashed with "Key … was already used".
  <sub>r5-32</sub>

- [ ] **CORE-152** (P1) 🆕 Renaming from the song details screen keeps you on the song
  1. Put `old name.cho` (Adele / Hello) in 3 setlists and give it a library transposition.
  2. Open it from Songs, and choose Update file name from its menu.
  3. Open it from a setlist, and do the same on another copy.

  **Expected:** The screen stays on the song, the bar names it, and the transposition is kept. Opened from a
  setlist, the pager stays on the song. Every setlist follows the new name. Before this fix the screen closed itself.
  <sub>r5-33, r2-33</sub>

- [ ] **CORE-153** (P1) Older names offer Update file name
  1. Put `untitled.cho` with `{title: Катюша}` into the library.

  **Expected:** Its menu offers Update file name. Taking it renames the file, the setlists follow, and the entry
  disappears. A library of Latin-only, current names shows no such entries.
  <sub>r2-26, r4-20</sub>

- [ ] **CORE-154** (P1) A rename that could not be followed everywhere
  1. Make one setlist file read-only (`chmod a-w`, and on macOS also its folder). Update the file name of a song in
     that setlist.
  2. Make `library/songs` read-only and try again.

  **Expected:** The song keeps its new name. A snackbar says a setlist still points at the old name, and that setlist
  shows the entry as missing. With `songs` read-only, "Something went wrong" appears and nothing changes.
  <sub>r4-40, r1-39</sub>

- [ ] **CORE-155** (P1) A case-only rename renames in place
  1. Create `foo.cho`, then change its title so the only difference is capitals, and Update file name.

  **Expected:** The file is renamed in place, with no `_2` copy.
  <sub>r3-48, r1-37</sub>

- [ ] **CORE-156** (P2) Very long names, and leftover temporary files
  1. Put a file named with 240 `a`s + `.cho` into the library and tag it.
  2. Put `.campfire-1.tmp` into the songs folder, with an old date (`touch -t 202601010000`), and relaunch.

  **Expected:** Tagging saves. The old temporary file is removed after the list loads. Creating a song leaves no
  `.tmp` behind.
  <sub>r2-29</sub>

## 11. Settings, theme, language

- [ ] **CORE-170** (P1) The four tabs
  1. Open Settings for the first time, switch tabs, scroll each, and relaunch.

  **Expected:** Settings opens on General the first time. The tabs are General, Songs, Library and About. The tab you
  left and each tab's scroll position are remembered.
  <sub>presentation</sub>

- [ ] **CORE-171** (P1) Theme and color
  1. Try light, dark and system with each color. Relaunch in a dark, non-default color.

  **Expected:** Every change cross-fades. On relaunch, the window never flashes the wrong palette or language before
  settling.
  <sub>features, presentation</sub>

- [ ] **CORE-172** (P1) Language
  1. Switch between English and Hungarian on every screen and every Settings tab, then open every dialog and sheet.

  **Expected:** Everything switches at once. Nothing shows "???", the new About rows included. In Hungarian the
  bridge section is "Átkötés" everywhere. Each language is named in its own name in the list.
  <sub>features, r4-38, r4-39, r5-16</sub>

- [ ] **CORE-173** (P1) 🆕 The About tab
  1. Open Settings → About on the desktop build.
  2. Click every row.

  **Expected:** One untitled section: the author and version (linking the author's site), Campfire on GitHub, Report
  a problem, **Every version of Campfire** (opens the README's "Get Campfire" section), Privacy Policy, and Buy me a
  coffee. **Rate Campfire** is absent, because the Mac App Store listing does not exist yet. There are no per-store
  rows and no "Coming soon" rows. Every link opens. The platform documents cover the rating row on Android, and the
  missing coffee row on iOS and on a Mac App Store build.
  <sub>r5-15, r5-16</sub>

- [ ] **CORE-174** (P2) Library size
  1. Note the size in Settings → Library. Save a longer song, add a song to a setlist, and delete a song.
  2. Compare with `du -b` of `library/songs` and `library/setlists`.

  **Expected:** The figure goes up, up and down, and matches the folder (hidden files and files over 8 MiB aside).
  <sub>r4-45</sub>

## 12. Snackbars, dialogs, keyboard

- [ ] **CORE-180** (P2) Snackbars queue
  1. Import the same file twice quickly, so two identical results are due.

  **Expected:** Both are shown, one after the other, above the navigation rail and never hidden behind anything.
  <sub>r3-34, r4-36</sub>

- [ ] **CORE-181** (P1) Escape closes only the top thing
  1. Open a menu, a sheet, a dialog over a sheet, and a search, then press Escape repeatedly.

  **Expected:** Each press closes only the topmost thing, and the back stack comes last. Holding Escape closes one
  thing, not the whole app.
  <sub>presentation, r3-19</sub>

- [ ] **CORE-182** (P2) Keyboard-only use
  1. Using only Tab and Enter, reach a setlist row's ⋮, open it, arrow to Move down, and press Enter.

  **Expected:** The row moves down one place.
  <sub>r4-30</sub>

## 13. Touch-only checks (do these on the phone)

These run on 01 and 02 and are listed here so they are not forgotten:

- a long press on a song row opens the same menu as its ⋮ (touch only; the desktop does nothing on a long press);
- the predictive back gesture on an open search previews the close, and cancelling the gesture keeps the search open;
- the song details screen keeps the display on;
- Share appears next to Export, in the song and setlist menus;
- the editor toolbars, with the on-screen keyboard up;
- a Bluetooth page-turner pedal pages the setlist;
- TalkBack and VoiceOver read chorded lines with the chord names in place, and each section whole, in song order.
