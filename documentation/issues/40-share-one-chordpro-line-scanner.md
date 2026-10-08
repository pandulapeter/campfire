# Read ChordPro text line by line through one internal ChordProLineScanner instead of seven hand-written loops

**Challenged:** amended — step 4 names the two highlighter reads that need the state *after* the line (`isUnreadable` and `onceOnlyKind`); step 3 scans the unmodified split result rather than the list the loop writes into; step 6 records the trace showing `SectionBuilder.isDelegated` and the scanner agree (so the expected outcome is removal, not the fallback).

**Kind:** architecture  ·  **Severity:** medium  ·  **Effort:** L  ·  **Risk:** medium  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/.../chordpro/` — new `ChordProLineScanner.kt`; `ChordProParser.kt` (`parseAsWritten`, private `scan`; after the :chordpro split lane `SectionBuilder.isDelegated` lives in its own file); `ChordProTransposer.kt` (`rewriteChordNamesInText`); `ChordProHighlighter.kt` (`tokenize`'s loop, the `isInTab` / `isInGrid` / `isInDelegate` flags); `ChordProSplitter.kt` (`split`, `comparable`); `ChordProSummaryCache.kt` (`safeLineAt`); `ChordProPrettifier.kt` (`prettify`, `hoistedTimings`) — documented, see step 7; `ChordProSyntax.kt` (`splitLines`, `matchDirective`, `matchDelegatedDirective`, `startOfEnvironment`, `endOfEnvironment`, `delegateEnvironments`, `hasSelectorSuffix`; after the split lane these sit in `ChordProLines` / `ChordProDirectives` / `ChordProEnvironments`); new `chordpro/src/commonTest/.../ChordProLineScannerTest.kt`; `chordpro/CLAUDE.md`
**Depends on:** none (land after the :chordpro split lane; if 41 runs in the same lane, land 40 first — both touch the highlighter and the prettifier)

## Problem

Every reader of raw ChordPro text re-implements the same walk: split the text with `ChordProSyntax.splitLines`, trim
each line, skip a `#` source comment unless inside an environment handed to another program, pick
`matchDelegatedDirective` or `matchDirective` by that state, and track which environment is open. At 2940b0e0a the walk
is written seven times, each with its own representation of "which environment am I in":

- `ChordProParser.parseAsWritten` (≈line 54) — the state is `section.isDelegated` (`lineMode == LineMode.VERBATIM`
  inside `SectionBuilder`):
  ```kotlin
  if (trimmedLine.startsWith(SOURCE_COMMENT) && !section.isDelegated) return@forEach
  val directive = if (section.isDelegated) ChordProSyntax.matchDelegatedDirective(trimmedLine) else ChordProSyntax.matchDirective(trimmedLine)
  ```
- `ChordProParser.scan` (≈line 244) — `var environment: String?`, lowercased, plus a `startsWith(DIRECTIVE_START)` precheck
  and a `hasSelectorSuffix` guard.
- `ChordProTransposer.rewriteChordNamesInText` (≈line 346) — the same `var environment` walk, plus tab-run collection.
- `ChordProHighlighter.tokenize` (≈line 82) — three booleans `isInTab`, `isInGrid`, `isInDelegate`, every `end_of_…`
  clearing all three.
- `ChordProSplitter.split` and `ChordProSplitter.comparable` — a `var delegatedEnvironment: String?` that is only set for
  the delegated environments, written out twice in the same file, and a delegated start "moves it on":
  ```kotlin
  if (delegatedEnvironment != null) {
      ChordProSyntax.matchDelegatedDirective(trimmedLine)?.name?.let { name ->
          ChordProSyntax.startOfEnvironment(name)?.let { environment ->
              delegatedEnvironment = environment.takeIf { it in ChordProSyntax.delegateEnvironments }
          }
          ChordProSyntax.endOfEnvironment(name)?.let { delegatedEnvironment = null }
      }
  ```
- `ChordProSummaryCache.safeLineAt` (≈line 83) — walks raw character offsets instead of `splitLines`, with
  `trimmed.startsWith('#')` and a `startsWith('{')` precheck.
- `ChordProPrettifier.prettify` and `hoistedTimings` — a *stack* (`val environments = mutableListOf<String>()`) where an
  `{end_of_x}` closes only the innermost `x` (`indexOfLast { it == end }`), and `prettify` reads delegated lines with
  plain `matchDirective`.

The parser's rule is the reference ("any end of an environment ends the way the lines were being read", as the
highlighter's own comment says), yet nothing enforces that the others follow it, and a change to the rule (a new
delegated environment, a new short name) has to be made in seven places. Some differences are intentional (the
prettifier's stack, the summary cache's offset walk), some are accidental, and none are written down.

Note: `hasSelectorSuffix` returns false for every `start_of_…` / `end_of_…` name, so the guards some loops have and
others lack do not change environment tracking; they only matter for what each consumer does with other directives.

## Fix

1. **Add the scanner** (no consumer changes). In `chordpro` (package `…chordpro`, or `…chordpro.syntax` if plan 43
   phase 1 has landed), an `internal object ChordProLineScanner` with

   ```kotlin
   internal class ScannedLine(
       val index: Int,
       val raw: String,
       val trimmed: String,
       /** Lowercase environment open *before* this line ("tab", "grid", "abc", …) or null. */
       val environment: String?,
       val isDelegated: Boolean,
       /** A `#` line outside a delegated environment. */
       val isSourceComment: Boolean,
       /** matchDelegatedDirective inside a delegated environment, matchDirective elsewhere, null for a source comment. */
       val directive: ChordProSyntax.Directive?,
   )
   internal fun scan(lines: List<String>): Sequence<ScannedLine>   // or forEach(lines, visit)
   ```

   The environment advances *after* each line with the parser's rule: `startOfEnvironment(name)` opens it (lowercased),
   any `endOfEnvironment(name)` closes it whatever it names, and inside a delegated environment only what
   `matchDelegatedDirective` returns counts. Write `ChordProLineScannerTest` first, from the parser's behaviour: a `#`
   inside `{start_of_abc}` is not a comment, `{ c d e }` inside it is not a directive, `{end_of_verse}` closes a tab,
   an unclosed delegated environment takes the rest of the text, CRLF / CR / LF all split alike.

2. **Migrate `ChordProParser.scan`** onto it. Keep the `startsWith(DIRECTIVE_START)` precheck semantics (it only skips
   `matchDirective` work, `matchDirective` already returns null for such lines). Guarded by `ChordProParserTest`,
   `ChordProSummaryCacheTest`, `ChordProMetadataTest`.

3. **Migrate `ChordProTransposer.rewriteChordNamesInText`**. Its tab-run flushing stays where it is; only the
   `environment` / `isDelegated` / `isSourceComment` / `directive` locals come from the `ScannedLine`. Scan the list
   `splitLines` returned *before* it is copied into the `lines` the loop writes rewritten text into (keep both: the
   scanner reads the original, the loop writes `lines[index]`), so a lazy scanner never reads a line this loop has
   already rewritten. Guarded by
   `ChordProTransposerTest`, `ChordProTabTransposerTest`, `ChordProNotationTest`.

4. **Migrate `ChordProHighlighter.tokenize`**: `isInTab = line.environment == TAB_ENVIRONMENT` etc. are now read
   from the *next* scanned line's state, so compute them from `ScannedLine.environment` of the line being tokenized
   and check against `ChordProHighlighterTest` that tokens are unchanged. Today's code updates the three flags *before*
   it builds a directive line's tokens, so on a `{start_of_…}` / `{end_of_…}` line two reads see the state *after* the
   line: `isUnreadable = !isInDelegate && …` and `onceOnlyKind = if (isInDelegate || isUnreadable) null else …`. Add a
   scanner property `isDelegatedAfter` (or `environmentAfter`) and use it for exactly those two; the directive match,
   the `#` check and the grid / tab / delegate branches for non-directive lines keep using the state before the line
   (which, on a non-directive line, equals the state after the previous one, as today).

5. **Migrate `ChordProSplitter.split` and `comparable`**. Their rule differs from the parser's in one way: they track only
   delegated environments, so a non-delegated environment never changes how a later line is matched — which is the
   same result as the parser's rule (a non-delegated environment never makes a line delegated). Confirm with
   `ChordProSplitterTest` and the `:domain:implementation` import tests (`ImportPlanner`), then delete both copies.

6. **Migrate `ChordProParser.parseAsWritten`** last, since its state lives in `SectionBuilder`: replace the
   `section.isDelegated` reads in the loop with `ScannedLine.isDelegated`, and add an assertion test that the two
   agree on every fixture of `ChordProParserTest` before removing the read (they can differ only if `SectionBuilder`
   opens `LineMode.VERBATIM` in a way the scanner does not; if a fixture shows that, keep `section.isDelegated` and
   document why instead). Traced at 2940b0e0a they agree: `VERBATIM` is only opened by a delegated `{start_of_…}`, only
   left through `close()` / `closeLineMode()` (a start or an end of an environment, which the scanner tracks too),
   `addBlock` restores the line mode it saves, and a legacy `{comment}` heading never closes it (`handleComment` checks
   `isInLineMode`, which `VERBATIM` is). The fallback is for a fixture that proves this trace wrong.
   `handleDirective`'s own `section.isDelegated` read (definitions inside a delegated environment) is outside the loop
   and stays.

7. **Do not migrate** `ChordProSummaryCache.safeLineAt` (walks offsets of a text being typed, before a cut point, and
   must not allocate the whole line list) or `ChordProPrettifier` (its environment *stack* is intentional: it decides
   where a gap goes and keeps nested environments verbatim). Instead add a one-line comment at each saying which
   rule it follows and how it differs from `ChordProLineScanner`, and list the differences in `chordpro/CLAUDE.md`
   under `ChordProSyntax` (or the split lane's `ChordProLines` entry). If the user later wants the prettifier on the
   parser's rule, that is a behaviour change and a plan of its own.

Each step is one commit and compiles on its own.

## Tests

- New `ChordProLineScannerTest` (step 1), table-driven over environment/comment/directive cases.
- Existing guards: `ChordProParserTest`, `ChordProMetadataTest`, `ChordProSummaryCacheTest`, `ChordProTransposerTest`,
  `ChordProTabTransposerTest`, `ChordProNotationTest`, `ChordProHighlighterTest`, `ChordProSplitterTest`,
  `ChordProPrettifierTest`, plus `:domain:implementation:desktopTest` (import comparisons use `ChordProSplitter.comparable`).
- Run `./gradlew :chordpro:desktopTest :domain:implementation:desktopTest :presentation:desktopTest` after every step.

## Manual check

none — covered by tests (the editor's highlighting is the one visible consumer; `ChordProHighlighterTest` pins its tokens).
