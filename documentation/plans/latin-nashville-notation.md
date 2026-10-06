<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Latin and Nashville notations — implementation plan

Written 2026-10-06 against `5550057cb`. German notation was built as the first of several: every file and the model
are in `ChordNotation.STANDARD`, and a notation is converted at two boundaries only (the page, and the editor's field).
This plan adds the two that were left room for. They are not the same kind of thing, and the plan treats them
differently on purpose:

- **Latin** (`Do Re Mi Fa Sol La Si`) is German's sibling: other names for the same seven notes. It is shown and typed.
- **Nashville** (`1 4 5 6m`) is not a naming of notes at all: a number means nothing without the song's key. It is
  shown, and never typed or stored.

## 1. What is being built

1. **Settings → Songs offers four notations** in place of the German switch: Standard, German, Latin, Nashville numbers.
2. **Latin is a full notation**: the page, the lists' keys, the stepper, the PDF, the editor's field and the Song
   defaults key field are all in it, and the file stays in the standard one.
3. **Latin chords are read wherever they arrive from**, whatever the reader's notation is: an imported ChordPro file, a
   chord-over-lyrics text, a PDF or Word document, a file a sync run brought. `[Lam]` is never anything but A minor.
4. **Nashville numbers the chords of a song from its key** on the page, in the editor's preview and in the PDF. Keys
   stay letters, a song with no key stays letters, and the editor's field is in letters.

### What is already there

- `ChordNotation` / `ChordProNotation` (`toNotation` for the model, `convertText` for raw text, `normalized` for
  reading), and `ChordProTransposer.rewriteChords`, which takes the rename as a function and already hands a
  **different rewrite to each stretch a `{transpose}` starts** (`rewriteAt(offset)`). Nashville needs exactly that.
- `ChordProChordNames.lowercaseMinorExpanded` is the precedent for "a spelling read as the chord it stands for":
  one function that returns the standard name or null, asked wherever a written name is read. Latin is one more.
- `ChordProTabTransposer.rewriteChordNames` already keeps a tab's columns when a name grows or shrinks.
- `UserPreferences.Notation` is stored by id with the old boolean as a fallback, so two more ids need no migration,
  and an older build that reads `latin` falls back to standard.
- The whole presentation path goes through `renderSong`, `renderKey`, `editorNotation` and three use cases; no screen
  knows what a notation is.

### Defaults this plan assumes — veto any of them before the work starts

1. **One choice of four**, not a note naming plus a "numbers" switch. Under Nashville every absolute name — the key in
   the app bar, on the cards, on the stepper, in the Song defaults sheet — is in standard letters.
2. **Under Nashville the editor's field is in letters**, and its preview in numbers. A field in numbers would make
   correcting a wrong `{key}` line move every chord of the song at Save (the numbers stay, so the letters change),
   which is the opposite of what the Song defaults sheet does with the same edit; and a key half typed would leave
   every number unresolvable. So `editorNotation` is `STANDARD` there and nothing ever converts text *out of* numbers.
3. **A song that declares no readable `{key}` is shown in letters** under Nashville. The first chord is a guess the
   transposer may make for choosing flats; as the meaning of every number on the page it is not good enough.
4. **A minor key is numbered from its relative major**: in A minor, `Am` is `6m` and `E7` is `37`. That is the
   Nashville system's own convention and the one that leaves the diatonic chords without accidentals.
5. **Numbers are plain text**: `57`, `6m7`, `1sus4`, `5/7`. The chromatic degrees are `b2 b3 #4 b6 b7`, whatever the
   Accidentals preference says (it still spells the key). No superscripts in this version: that would reach into the
   chord measuring of the viewer, the print layout and the PDF's text layer.
6. **Number charts are not read.** A `[1]` or `[2]` in a file is as often an ending or a verse marker as a chord, and
   `b7` is already B minor seven in the lowercase-minor spelling, so no file is taken for a number chart on its own.
   See §9 for the explicit action that could follow.
7. **Latin names are read in title case or capitals only** (`Do`, `DO`, never `do`), the bass note after a `/` in any
   case as today. Read, never written: the accented spellings (`Ré`, `Dó`, `Fá`, `Lá`). Written: `Do Re Mi Fa Sol La Si`
   with `#` and `b`, the quality untouched (`Sim`, `Sib`, `Dom7`, `Solsus4`, `La/Do#`).
8. **A lowercase minor that goes through a Latin editor comes back spelled out** (`a` → `Lam` → `Am`): there is no
   lowercase Latin spelling to keep it in.

## 2. `:chordpro` — Latin

### 2.1 Reading: `ChordProChordNames.latinExpanded`

```kotlin
/**
 * [word] as the chord a Latin name stands for — `Lam` is `Am`, `Sib7` is `Bb7`, `DO/MI` is `C/E`, `(Sol)` is `(G)` —
 * or null for anything else. No name is a chord in both readings, which is what lets this be asked of every text.
 */
fun latinExpanded(word: String): String?
```

- Unwrap the parentheses, match one of the accepted spellings at the root (a small table, longest first), replace it
  with its letter; do the same for the note after the last `/` where what follows it is a Latin note and an optional
  accidental and nothing else; accept the result only if `isChordName` does.
- A cheap reject first (first letter one of `DRMFSL`, second a vowel), since this is asked of every name on a parse.
- **The claim in the KDoc is a test**: over every root × accidental × the quality and alteration tables × an optional
  bass, no generated name is accepted by both `isChordName` and `latinExpanded`. Today it holds (`Do…` is never a
  standard chord since `o` is no quality; `Fadd9` and `Faug` are not Latin since `dd9` and `ug` are no suffix). The
  test is what stops somebody adding `o` as a diminished sign later.

### 2.2 One reader for a written name

`ChordProNotation` grows the one function every reading path calls, replacing the `lowercaseMinorExpanded(x) ?: x`
plus `fromGerman` plus `withAsciiAccidentals` chains that are spelled out in three places today:

```kotlin
/** The standard chord [name] stands for as a file writes it: Latin, a lowercase minor, German where [isGerman]. */
internal fun read(name: String, isGerman: Boolean): String
```

Latin first (a Latin name is never German or a lowercase minor), then today's order. Used by:

| Where | Change |
| --- | --- |
| `normalized` (every `parse`) | The early return also asks whether any name is Latin; the rename is `read`. |
| `convertText` | Reads with `read`; its fast path (`to == STANDARD`, no `♯`/`♭`, not German) also needs "no bracket, grid or tab line that could hold a Latin name", a prefilter in the shape of `mayHoldGermanName`. |
| `ChordProParser.scan` | Only the key is its business: `{key: Lam}` comes out `Am`. No pass over the body is added, since Latin needs no vote. |
| `ChordProTransposer.renameKey` / `spelledOutKeyNoteLength` | A key may start with a Latin note (`Sol major`). |
| `ChordProTabTransposer.chordWords` | A row of Latin names over a staff is a chord row, so it is renamed in its columns. |
| `ChordProHighlighter.isMovedChordName` | A Latin name in a comment's or a label's brackets is coloured as the chord it is. |
| `ChordSheetConverter.chord` and `isChordPro` | A line of Latin names over a line of lyrics is a chord line; `Key: Lam` in a header is a key. |

`ChordProTransposer.transposeText` is not on the list: the view model hands it the file's text, never the field's.
`ChordProSplitter.comparable` already runs `convertText(STANDARD, STANDARD)`, so a Latin file imported twice is the
same song the second time without a change there.

Optional, and small: the key words of the Romance languages (`mayor`, `menor`, `majeur`, `mineur`, `maggiore`,
`minore`) join `keyWords`. If they do, `ChordProTransposer.keyOf` has to stop deciding minor by "starts with an `m`
and not with `maj`" (`mayor` would be minor) and ask a set of minor words instead. §3 needs that set anyway.

### 2.3 Writing: `toLatin`

- `toLatin(name)`: `isChordName` or unchanged, then `rewriteNotes` with the letter swapped for its name. The existing
  bass-note folding gives `D/f#` → `Re/fa#` for free.
- `toNotation(song, LATIN)`: `rewriteChords` with `toLatin` and `rewriteChordNames` for the tabs, as German.
- `convertText(…, to = LATIN)`: `toLatin(read(name))`, **without** `keepingLowercaseMinors` (default 8); definitions
  are renamed as for German.
- `ChordProTabTransposer.replaceKeepingColumns` meets its hardest case here: every name grows, `G` by two. Its rule
  (eat a filler after, then before, else grow the line) is already general; the tests have to show a dense row
  (`C G Am F` a space apart) staying readable and the staff under it staying aligned where there was room.

### 2.4 After the conversion: `ChordProTabWrapper.isChordLine`

The one place that reads chord names **out of an already converted model**: it decides which lines of a tab run are
chord rows that travel with the columns. With German it kept working by accident (`H` is a letter `isChordName`
takes); a row of `Sol Lam` or of `1 4 5` would be taken for prose. It asks a new
`ChordProChordNames.isDisplayedChordName` — standard, Latin, or a number (§3.1). A row of beat counts (`1 2 3 4`)
passes too, and travelling with its columns is the right thing for it.

### Tests

`ChordProChordNamesTest` (the table, capitals, accents, bass, parentheses, the no-collision sweep),
`ChordProNotationTest` (model and text both ways; standard → Latin → standard is the identity over the generated
set; `Mib5`, `Sib`, `Sim`, `Dom7`, `Faadd9`; a pasted `[Am]` in a Latin text; a German `H` file shown in Latin; a
lowercase minor through the editor), `ChordProParserTest` (`parse` and `summarize` of a Latin file),
`ChordProTabTransposerTest`, `ChordProTabWrapperTest`, `ChordProHighlighterTest`, `ChordSheetConverterTest` (a Spanish
chord-over-lyrics sheet; `La la la la` stays a lyric line; `LA LA LA` is the known cost, like a line of `A` today),
`ChordProSplitterTest`.

## 3. `:chordpro` — Nashville

### 3.1 `ChordProNashville` (new, internal)

- `tonicOf(key: String): Int?` — the pitch class the numbers count from: the key's note, moved up three semitones
  for a minor key (default 4). Reads what `keyOf` reads (`Am`, `F#m`, `A minor`, `a-moll`, `Dm (capo 2)`), on the
  shared set of minor words; null where the key starts with no note.
- `number(name: String, tonic: Int): String` — `isChordName` or unchanged, then `rewriteNotes`, each note becoming
  `degrees[(pitch - tonic).mod(12)]` with whatever followed it kept: `1 b2 2 b3 3 4 #4 5 b6 6 b7 7`.
- `isNumber(word: String): Boolean` for §2.4: an optional `b`/`#`, a digit 1–7, and a remainder that is a chord's
  (checked by putting a `C` where the degree was).

### 3.2 `ChordProNotation.toNotation(song, NASHVILLE)`

```kotlin
val tonic = song.metadata.key?.let(ChordProNashville::tonicOf) ?: return song
return ChordProTransposer.rewriteChords(song) { offset -> numbering(tonic + offset) }
```

- **Keys are not chords here.** `rewriteChords` renames `metadata.key` and every `Transpose.key` with the stretch's
  rename; `ChordRewrite` gets a `renameKey` of its own, defaulting to `rename`, and Nashville's is the identity.
- **A modulation keeps its numbers**, which is the point of them: the transposition has already moved the chords after
  a `{transpose: 2}` and left the block's `semitones` in place, so the stretch is numbered from `tonic + 2` and a
  chorus recalled after it reads `1 4 5` again. The page's key-change line names the new key, in letters.
- The reader's transposition and the capo change no number, only the key named next to them.
- Grids, labels, comments and the chord rows of tabs are numbered like everything else `rewriteChords` reaches.
- `convertText` never writes numbers (default 2): `to = NASHVILLE` is treated as `STANDARD`, and says so.
- `parse`, `summarize` and `ChordProSummaryCache` given `NASHVILLE` read as `STANDARD` (default 6).

### Tests (`ChordProNashvilleTest`, `ChordProNotationTest`, `ChordProTransposerTest`)

Every degree in C and in a flat and a sharp key; `G/B` → `5/7`, `C6/9` → `16/9`, `D/f#`; a minor key, spelled `Am`,
`A minor` and `a-moll`; no key, an unreadable key; a modulation and a recall after it; a transposed song numbering
the same; the key and `Transpose.key` left in letters; a grid; a tab's chord row; `N.C.` and `[Chorus 2x]` untouched.

## 4. `:data:model`, `:data:source:local`, `:domain`

- `UserPreferences.Notation` gains `LATIN("latin")` and `NASHVILLE("nashville")`, and
  `val isTyped get() = this != NASHVILLE` (or the equivalent `forTyping: Notation`), so the rule of default 2 is
  stated once, next to the enum, rather than in the view model.
- `UserPreferencesMappers` needs nothing; `UserPreferencesMappersTest` gets both ids and an unknown one. The legacy
  `isGermanNotationEnabled` stays what it is, read only for a document with no `notation` yet.
- `NotationMappers.toChordNotation`: two branches. The three use cases are unchanged. `ChordSpelling`'s KDoc
  ("applied after the accidentals") gets a line saying that Nashville ignores them for the numbers.

## 5. `:presentation`

### 5.1 The view model

- `editorNotation` is the reader's notation, or `STANDARD` where that is Nashville. `editorTextOf`, `fileTextOf`,
  `transposeText`, `editorKeyOf`, `fileKeyOf` all follow it and need no change.
- `editorSummaryCache` maps through one function instead of its own `when` over the enum.
- `renderSong` and `renderKey` are unchanged: the preview parses the field in `editorNotation` and renders it with the
  reader's spelling, so under Nashville it shows numbers over a field in letters. `presentation/CLAUDE.md`'s "the
  field, the stepper and the preview name the same chords" gets that one exception written into it.

### 5.2 Settings → Songs

The switch becomes a `SettingsSubsection` ("Chord notation", with the sentence about files always being saved in the
standard one) holding four `RadioListItem`s, each with its example as the description — a list, as the language
choice is, since four names do not fit a row of segments at 360dp:

| Option | Description |
| --- | --- |
| Standard | `C D E F G A B` |
| German | `H` for B, and `B` for B flat |
| Latin | `Do Re Mi Fa Sol La Si` |
| Nashville numbers | `1 4 5 6m`, counted from the song's key. A song with no key keeps its letters, and the editor is always in letters. |

Disabled with the Chords feature off, as today. The Accidentals subsection stays enabled under Nashville, since it
still spells the key. Strings in both languages; `settings_german_notation` and its description are removed.

### 5.3 Width

Nothing on a screen measures a chord by its letter count, but a few places were sized by eye for the standard
notation and now meet `Sol#m`, two characters wider than `G#m`: the stepper's key, the app bar's and the cards' key,
the read only line, the Song defaults key field (`C#m7b5` is its stated worst case). Check each at 360dp rather than
changing any up front.

## 6. Corner cases, and what each one does

| Case | Behaviour |
| --- | --- |
| A Latin file arriving by sync or edited by hand | Read as the chords it names in every notation; rewritten in the standard one the next time the editor saves it. |
| A German reader's library holding a Latin file | The same: `[Sib]` is shown `B`. |
| `[Do]`, `[La]`, `[Si]` meant as a word | Read as a chord, as `[A]` is today. `[Solo]`, `[Refrain]`, `[Fade]`, `[La la la]` are not chords in either reading. |
| `{define: Lam …}` | Renamed with the chords by a change of notation, the fingering untouched. Under Nashville there is nothing to do: the model carries no definitions and no text is ever written in numbers. |
| Latin, Accidentals set to flats | `La#` is shown `Sib`: the accidentals are applied first, in the standard notation. |
| Nashville, a chord outside the key | Its number with its own quality: `E` in C is `3`, `Bb` is `b7`. |
| Nashville, `C7` in C | `17`. Plain text (default 5). |
| Nashville, `{key}` given a second time further down | Read past, as today; only `{transpose}` moves the tonic. |
| Nashville, Chords feature off | Nothing to number; the key leaves the page with the chords as today. |
| Nashville, performance mode | The read only line names the key in letters, the chords are numbers. |
| PDF export | Prints what the page shows. A Latin PDF comes back as chords on re-import; a Nashville one comes back as lines of numbers, which nothing reads (default 6). |
| Export, sync, setlists, search | Untouched: files are in the standard notation and nothing else carries a chord. |
| Editor draft restored after the notation changed | Already stored in the file's notation, so it means the same chords. |
| Demo songs | Unchanged. |

## 7. Order of work

Each step builds and tests green on its own, one commit each. Steps 1–3 are Latin and shippable without 4.

1. §2.1–2.2: `latinExpanded`, `read`, and every reading path. No setting yet: after this step a Latin file imports
   and reads correctly for every existing user.
2. §2.3–2.4: `ChordNotation.LATIN`, `toLatin`, `convertText`, the tab wrapper.
3. §4 and §5 for Latin: the enum, the mappers, the Settings list (three options).
4. §3: `ChordProNashville`, `ChordRewrite.renameKey`, `toNotation`.
5. §4 and §5 for Nashville: the fourth option, `editorNotation`.
6. Docs: the root `CLAUDE.md` ("Every file is in the standard chord notation": Latin is self-identifying, Nashville
   is shown only); `chordpro/CLAUDE.md` (`ChordNotation` / `ChordProNotation`, chord names, the tab wrapper, the
   converter); `domain/api` and `domain/implementation`; `presentation/CLAUDE.md` (the editor's notation, Settings);
   the What's new message at release time.

## 8. Checks owed by hand

- A Spanish or Italian chord sheet through each import: ChordPro, plain text, PDF, Word.
- Latin on a phone at 360 × 640dp: a dense chord line, a tab with a chord row, a grid, the widths of §5.3.
- Latin in the editor: open, type `[Lam]` and `[Sib]`, save, look at the file; transpose from the stepper; the Song
  defaults key field typed in Latin.
- Nashville: a song in a major and in a minor key, one with a `{transpose}` modulation and a recalled chorus, one
  with no key; stepping the transposition and the capo (numbers stay, key moves); the editor's split pane.
- Switching between all four with a song open behind Settings, on a setlist's pager.
- PDF export in both, and the Latin one imported back.

## 9. Not in this plan

- **Typing or importing number charts.** The safe form is an explicit action rather than a guess: an editor overflow
  entry that resolves the numbers of the text against its `{key}` once, and the same question asked by an import that
  finds a file full of them.
- Roman numerals (`I IV V vi`), which are Nashville with another table and a case rule.
- Superscript qualities for numbers, and `-` for minor.
- Nashville numbers with German or Latin key names.
- Cyrillic or Greek solfège, `Ut`, and the classical German `Cis` / `Es`.
- Examples in the Accidentals description that follow the notation.
