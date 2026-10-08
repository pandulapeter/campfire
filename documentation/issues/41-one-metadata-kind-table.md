# Describe every metadata directive once, in an internal MetadataKind table, and derive the parser, highlighter, prettifier and field editor from it

**Challenged:** amended — the INVALID ⇔ dropped table test is restricted to the spellings the parser actually reads (standalone `{cover}` / `{link}` and `{meta: lang …}`-style variants differ), step 4 handles the `[ChordProHeader.repeatableMetadata]` KDoc link in `:presentation`'s `EditorToolbar.kt`, and step 1 notes `capo`'s once-only rule.

**Kind:** architecture  ·  **Severity:** medium  ·  **Effort:** M  ·  **Risk:** medium  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/.../chordpro/` — new `MetadataKind.kt` (internal); `ChordProSyntax.kt` (`standardMetaNames`, `standardMeta`, `metadataOrder`, `metadataKind`, `metadataAliases`, `longNames`, `metadataInsertionIndex`; after the :chordpro split lane in `ChordProMetaItems` / `ChordProHeaderLayout` / `ChordProDirectives`); `ChordProParser.kt` (`MetadataBuilder.consume`, `OnceValue` / `ChangeableValue` readability lambdas; after the split lane `MetadataBuilder.kt`); `ChordProMetadataFields.kt` (`Field`, private `Field.canRead`, `Field.isChangedInTheBody`); `ChordProHeader.kt` (`repeatableMetadata`, `changeableMetadata`); `ChordProHighlighter.kt` (`Directive.isUnreadable`, `Directive.onceOnlyKind`, `LANGUAGE_NAMES`); `ChordProPrettifier.kt` (`hoistedTimings`' `isReadable`, the `metadataOrder` ranks); tests `ChordProMetadataFieldsTest`, `ChordProHighlighterTest`, new `MetadataKindTest`; `chordpro/CLAUDE.md`
**Depends on:** none (land after the :chordpro split lane, and after 40 if both run in one lane)

## Problem

What a metadata directive *is* — its long name, its short aliases, whether `{meta: x …}` stands for it, where it sorts in
a header, whether it may repeat, whether a later one is a change, and which values the parser can read — is spread
over six places that must agree:

- `ChordProSyntax.standardMetaNames` (12 names), `metadataOrder` (16 names), `metadataAliases` (`t`, `st`, `lang`) and
  `longNames`.
- `MetadataBuilder.consume` re-lists the names and their aliases in a `when`:
  ```kotlin
  "title", "t" -> title.consume(value)
  "subtitle", "st" -> subtitle.consume(value)
  …
  "capo" -> if (capo == null) capo = value.toIntOrNull()?.takeIf { it >= 0 }
  "tempo" -> tempo.consume(value, isInBody)   // ChangeableValue { ChordProTempo.parse(it) != null }
  "language", "lang" -> …
  ```
- Public `ChordProMetadataFields.Field(directiveName)` and its private `canRead`:
  ```kotlin
  Field.TEMPO -> ChordProTempo.parse(value) != null
  Field.TIME -> ChordProTime.parse(value) != null
  Field.CAPO -> value.toIntOrNull()?.let { it >= 0 } == true
  Field.DURATION -> ChordProDuration.parse(value) != null
  ```
  and `isChangedInTheBody = KEY || TEMPO || TIME`.
- `ChordProHeader.repeatableMetadata = setOf(TAG_NAME, LANGUAGE_NAME, LINK_NAME)` and
  `changeableMetadata = setOf("tempo", "time")` — which does **not** include `key`, while `Field.isChangedInTheBody`
  does and the highlighter special-cases `KEY` by hand (`onceOnlyKind == KEY && index >= bodyStart && hasHeaderKey`).
- `ChordProHighlighter.isUnreadable` — the same readability rules a third time (`time`, `tempo`, `capo`, `duration`,
  plus transpose/cover/link/language), with its own `LANGUAGE_NAMES = setOf(LANGUAGE_NAME, "lang")`.
- `ChordProPrettifier.hoistedTimings` — a fourth copy for tempo and time.

The highlighter's whole promise is that "a line is marked exactly when the song comes out without what it says", but
that holds only as long as four copies of the readability rule stay in step by hand, and nothing tests the promise
across the two classes.

## Fix

1. **Quick win: one readability function.** Add `internal fun isReadableValue(kind: String, value: String): Boolean`
   (in the new `MetadataKind.kt` or on the split lane's `ChordProMetaItems`) covering `tempo`, `time`, `capo`,
   `duration` (true for every other kind), and call it from `MetadataBuilder` (the `ChangeableValue` / `OnceValue`
   lambdas and the `capo` branch — which keeps its own `if (capo == null)` first-readable-wins guard; only the
   `toIntOrNull()?.takeIf { it >= 0 }` test is shared), `ChordProMetadataFields.canRead`, `ChordProHighlighter.isUnreadable` (the four
   matching branches only; transpose/cover/link/language/definitions stay where they are) and
   `ChordProPrettifier.hoistedTimings`. Pure refactor; one commit.

2. **Add the table test before the bigger move** (see Tests): the highlighter flags `INVALID` exactly for the lines the
   parser drops. It must pass on step 1 unchanged.

3. **Introduce the table.** An internal enum (or list of data objects):
   ```kotlin
   internal enum class MetadataKind(
       val longName: String,
       val aliases: Set<String> = emptySet(),       // "t", "st", "lang"
       val isStandardMeta: Boolean,                 // {meta: title …} stands for {title: …}
       val isRepeatable: Boolean = false,           // tag, language, link
       val changesInBody: Boolean = false,          // key, tempo, time — see the note below
       val isReadable: (String) -> Boolean = { true },
   ) { TITLE(…), SUBTITLE(…), ARTIST, COMPOSER, LYRICIST, ALBUM, COVER, YEAR, KEY, CAPO, TEMPO, TIME, DURATION, TAG, LANGUAGE, LINK }
   ```
   in today's `metadataOrder` order, so `ordinal` is the header rank. Derive `metadataOrder`, `metadataAliases`,
   `standardMetaNames` and `isReadableValue` from it, keeping the old names as internal vals for one commit.

   Note on `changesInBody`: today two different sets answer "may it be said again further down".
   `ChordProHeader.changeableMetadata` (tempo, time) drives the editor's toolbar re-offering, `metadataInsertionIndex`
   and the prettifier's grouping of timing changes; `Field.isChangedInTheBody` (key, tempo, time) drives which body
   lines `ChordProMetadataFields.set` keeps. They are *not* the same concept (a later `{key}` is read past by the
   parser but kept by the field editor). Model them as two flags — `isTimingChange` (tempo, time) and
   `isKeptInBody` (key, tempo, time) — rather than merging them; behaviour must not change.

4. **Re-point the consumers** one per commit: `ChordProHeader.repeatableMetadata` / `changeableMetadata` become
   `MetadataKind.entries.filter { … }.map { it.longName }.toSet()` (they are public API on a public object but have no
   code use outside `:chordpro` — make them `internal` in the same commit unless the split/narrowing lane already did;
   `presentation/.../songEditor/EditorToolbar.kt`'s KDoc links `[ChordProHeader.repeatableMetadata]` and
   `[ChordProHeader.changeableMetadata]`, which no longer resolve from another module — reword that KDoc to name the
   kinds in plain text, and `ChordProHeaderTest`'s assertion on `repeatableMetadata` keeps compiling since tests see
   `internal`);
   `ChordProHighlighter.onceOnlyKind` reads the flags; `MetadataBuilder.consume` resolves the name through
   `MetadataKind` before its `when` so the alias spellings are listed once (the `when` itself stays — each field has
   its own accumulation rule).

5. **Keep `ChordProMetadataFields.Field` public** as the view `:presentation` and `:domain` use (9 imports outside the
   module); give it an internal `val kind: MetadataKind` and replace `canRead` / `isChangedInTheBody` with reads of it.

6. Update `chordpro/CLAUDE.md`: one entry for `MetadataKind`, and remove the restatements of the sets from the
   `ChordProHeader` / `ChordProHighlighter` / `ChordProMetadataFields` entries in favour of a pointer.

## Tests

- New `MetadataKindTest` — the derived `metadataOrder`, aliases and standard-meta names equal today's literal lists
  (copy them into the test at 2940b0e0a as the expected values).
- New table-driven test (in `ChordProHighlighterTest` or `MetadataKindTest`): for every kind and a list of values
  (empty, valid, invalid: `"fast"`, `"-1"`, `"3/5"`, `"1:2:3"`, `"abc"`), write each spelling **the parser reads for
  that kind** as the only metadata line of a one-line header, and assert
  `highlighter flags the line INVALID` ⇔ `ChordProParser.parse(text).metadata` has no value for that kind while the
  value is non-empty. The spellings at 2940b0e0a: `{name: value}` and `{meta: name value}` for the twelve
  `standardMetaNames` (plus the `t` / `st` aliases standalone); `{tag: …}` and `{meta: tag …}`; `{language: …}`,
  `{lang: …}`, `{meta: language …}`, `{meta: lang …}`; and only `{meta: cover …}` / `{meta: link …}` — a standalone
  `{cover: …}` or `{link: …}` is read by neither the parser nor the highlighter (no value, no INVALID) and would fail
  the ⇔, so it is listed as an explicit "ignored by both" case instead. Derive the list from `MetadataKind` once
  step 3 exists, so a new kind is covered automatically. This is the check that the highlighter flags exactly what
  the parser drops.
- Guards: `ChordProMetadataFieldsTest`, `ChordProHighlighterTest`, `ChordProHeaderTest`, `ChordProPrettifierTest`,
  `ChordProParserTest`, `ChordProMetadataTest`, `ChordProSyntaxTest`; `./gradlew :chordpro:desktopTest :presentation:desktopTest`.

## Manual check

none — covered by tests.
