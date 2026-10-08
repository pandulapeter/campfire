# Move the pure rendering and normalization helpers out of the view model into an injected `SongRenderer`, with the editor notation passed explicitly

**Kind:** architecture  ·  **Severity:** medium  ·  **Effort:** M  ·  **Risk:** low  ·  **Platforms:** all
**Challenged:** amended — `SongRenderer` cannot be `internal` while it is a constructor parameter of the public
`CampfireViewModel` (an exposed internal parameter type does not compile); it is a public class with a public
constructor instead. Package placement checked against the package-move pass; plan 01's #18 overlap named.
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`
(`renderSong`, `transposedSong`, `notatedSong`, both `renderKey`, `prettifyText`, `transposeText`, `editorNotation`,
`editorTextOf`, `lastEditorText`, `EditorText`, `editorSummaryCache`, `editorKeyOf`, `fileTextOf`, `normalize`,
`normalizeForSearch`, `languageCode`, `preparePrintSource`, `fileKeyOf`, `hasUnsavedEditorText`, `hasUnsavedEditorChanges`,
constructor parameters `transposeChordPro`, `transposeChordProText`, `convertChordProNotation`,
`convertChordProTextNotation`, `prettifyChordPro`, `normalizeText`, `normalizeLanguageCode`); new
`ui/rendering/SongRenderer.kt`; callers `screens/songs/SongsScreen.kt` (`renderKey`), `screens/setlists/SetlistsScreen.kt`
(`renderKey`, `normalizeForSearch`), `screens/songDetails/SongDetailsScreen.kt` (`transposedSong`, `notatedSong`,
`renderKey`), `screens/songDetails/SongMetadataActions.kt` (`renderKey`), `screens/songEditor/SongEditorScreen.kt`
(`editorTextOf` ×7, `prettifyText`, `editorSummaryCache`, `editorKeyOf`, `transposeText`, `renderSong`,
`editorNotation`), `screens/importReport/ImportReportScreen.kt` (`normalizeForSearch`), `dialogs/ChordShapesSheet.kt`
(`transposedSong`), `dialogs/Dialogs.kt` (`normalizeForSearch` ×6, `normalize` ×2, `languageCode`, `editorKeyOf`);
new `commonTest/.../rendering/SongRendererTest.kt`; `presentation/CLAUDE.md` (paragraphs naming
`CampfireViewModel.renderKey`, `editorTextOf`, `transposedSong`, `editorNotation`)
**Depends on:** none. Plan 01 lists `SongRenderer` as one of its holders; landing this first removes that step and
shrinks plan 01's `EditorSession` (#18) to `editorNotation` and the calls that pass it.

## Problem

Seventeen members of `CampfireViewModel` are pure functions of their arguments plus stateless use cases, yet every
screen and dialog that needs to render a key or fold a search string has to be handed the whole view model:

```kotlin
fun renderKey(key: String?, transpose: Int, transposition: Int, capo: Int, spelling: UserPreferences.ChordSpelling) = key?.let {
    val keyOnly = ChordProSong(metadata = ChordProMetadata(key = it), blocks = emptyList())
    convertChordProNotation(transposeChordPro(keyOnly, transpose + transposition + capo, spelling.accidentals), spelling).metadata.key
}
fun normalizeForSearch(text: String) = normalizeSearchText(text)
fun languageCode(value: String) = normalizeLanguageCode(value)
```

Worse, the editor helpers read the user's notation **implicitly** from a `StateFlow`:

```kotlin
val editorNotation get() = (userPreferences.value?.chordSpelling?.notation ?: UserPreferences.Notation.STANDARD).forTyping
fun editorTextOf(fileText: String): String { val notation = editorNotation; lastEditorText?.takeIf { … } … }
fun transposeText(text: String, semitones: Int, accidentals: …) = convertChordProTextNotation(
    text = transposeChordProText(fileTextOf(text), semitones, accidentals), from = STANDARD, to = editorNotation)
```

so the result of `viewModel::editorTextOf` (passed as a function reference to `FollowFileWhileUntouched` and friends in
`SongEditorScreen`) depends on hidden state, `renderSong(…, writtenIn = viewModel.editorNotation)` is evaluated on
`Dispatchers.Default` inside `rememberSongLyricsModel`'s `prepare`, and none of it can be unit-tested without the view
model. Seven constructor parameters of the view model exist only for these functions
(`transposeChordPro`, `transposeChordProText`, `convertChordProNotation`, `convertChordProTextNotation`,
`prettifyChordPro`, `normalizeText`, `normalizeLanguageCode`; `parseChordPro` and `normalizeSearchText` are also used
elsewhere in it).

## Fix

1. **Create `SongRenderer`** in `ui/rendering/SongRenderer.kt`:
   ```kotlin
   @Single
   class SongRenderer(
       private val parseChordPro: ParseChordProUseCase,
       private val transposeChordPro: TransposeChordProUseCase,
       private val transposeChordProText: TransposeChordProTextUseCase,
       private val convertChordProNotation: ConvertChordProNotationUseCase,
       private val convertChordProTextNotation: ConvertChordProTextNotationUseCase,
       private val prettifyChordPro: PrettifyChordProUseCase,
       private val normalizeText: NormalizeTextUseCase,
       private val normalizeSearchText: NormalizeSearchTextUseCase,
       private val normalizeLanguageCode: NormalizeLanguageCodeUseCase,
   )
   ```
   It is annotated `@Single` and found by `PresentationModule`'s `@ComponentScan` (the Koin compiler plugin checks it
   at compile time in `:app:di`; it is a concrete class, not an interface, so it is not a `List<T>` concern and needs
   no `Impl`). It is **public**, unlike `DesktopSystemBrowser`/`AndroidFilePicker`: `CampfireViewModel` is public (the
   desktop app and `tools/screenshots` name it) and its constructor is public, so an `internal` parameter type is a
   compile error ("exposes its internal parameter type"). Every type in its signatures is already public (use cases
   from `:domain:api`, `:chordpro` models, `UserPreferences`). Put it in `ui/rendering/` unless the package-move pass's
   `ui.songLayout` turned out to be the home of song rendering; check first. Move into it, bodies unchanged: `renderSong`, `transposedSong`, `notatedSong`, both `renderKey`,
   `prettifyText`, `normalize`, `normalizeForSearch`, `languageCode`.
   Move the notation-dependent ones **with the notation as a parameter**:
   `editorTextOf(fileText, notation)`, `fileTextOf(editorText, notation)`, `editorKeyOf(key, notation)`,
   `transposeText(text, semitones, accidentals, notation)`, `editorSummaryCache(notation)`. `editorTextOf`'s
   one-entry cache (`lastEditorText`/`EditorText`) moves with it and stays keyed on `(fileText, notation)` — it is
   only ever called on the main thread today (from composition and from `hasUnsavedEditorChanges`' combine on the main
   dispatcher); keep that and say so in its KDoc, or make the cache a `kotlinx.atomicfu`-free `@Volatile`-less single
   reference swapped whole (it is an immutable `EditorText`, so a racy read sees an old or a new whole entry, never a
   torn one), which is what it already is.
2. **Inject it into the view model** in place of the seven parameters listed above (keep `parseChordPro` and
   `normalizeSearchText` where the view model still uses them). Keep `val editorNotation` on the view model (it is
   *the* source of the notation) and route the view model's own uses through the renderer:
   `hasUnsavedEditorChanges`/`hasUnsavedEditorText` → `renderer.editorTextOf(text, editorNotation)`,
   `fileKeyOf`/`writeEditorText` → `renderer.fileTextOf(…, editorNotation)`, `preparePrintSource` →
   `renderer.transposedSong`/`notatedSong`. Expose `internal val songRenderer: SongRenderer` on the view model for the
   screens. One commit with steps 1–2.
3. **Callers.** Replace `viewModel.renderKey(...)` with `songRenderer.renderKey(...)` etc. In the editor, read the
   notation once per composition (`val notation = viewModel.editorNotation`) and pass it; function references
   (`viewModel::editorTextOf`) become `remember(notation) { { text: String -> renderer.editorTextOf(text, notation) } }`
   so the reference stays stable across recompositions as the old bound reference was. In `SongEditorScreen.prepare`,
   capture `notation` outside the lambda rather than reading `viewModel.editorNotation` on `Dispatchers.Default`.
   Prefer passing `songRenderer` down explicitly to the few dialogs that need it over a `CompositionLocal` — it is
   needed in about ten call sites, all of which already receive the view model; a local would hide the dependency.
   One commit per screen or one for all, as convenient.

Behaviour is unchanged: the notation never changes under an open editor (selecting Settings clears the stack), which
is what makes passing it explicitly equivalent to reading it implicitly.

## Tests

New `SongRendererTest` with the real `:chordpro`-backed use cases replaced by small fakes, or — simpler and closer to
the truth — with the real implementations if `:domain:implementation` is reachable from `:presentation`'s tests (it is
not: `:presentation` depends on `:domain:api` only, so use fakes that delegate to `:chordpro`'s objects
`ChordProParser`, `ChordProTransposer`, `ChordProNotation`, which `:presentation` does depend on):
- `renderKey` applies `transpose + transposition + capo` and the spelling (`"G"`, transpose 0, transposition 2,
  capo 1 → `"A#"`/`"Bb"` per accidentals);
- `renderKey(null, …)` is null;
- `fileTextOf(editorTextOf(x, GERMAN), GERMAN) == x` for a standard-notation text with a `Bb` and a `B`;
- `editorTextOf` returns the cached instance for the same `(fileText, notation)` and recomputes for another notation;
- `transposeText` in German notation transposes `[H]` up 1 to `[C]`.
Existing: `SongModelBuildTest`, `RenderSectionsTest`, `chords/SongChordsTest`, `PrintLayoutTest`.

## Manual check

Set the notation to German in Settings; open a song in `Bb` in the editor (field shows `B`), transpose it up and save
(file holds `B`, i.e. standard), reopen in details (header key reads `H`); set Nashville numbers and confirm the editor
field is in letters and the preview in numbers; on the Songs and Setlists screens confirm every card's key is unchanged
with a capo override set; search a setlist by an accented title.
