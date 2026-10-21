# Count a JSON document's tokens before parsing it, and refuse one too large to hold as a tree, for every reader on the import path, setlists included

**Kind:** bug (crash, remotely triggerable) · **Severity:** high · **Platforms:** all
**Challenged:** amended — the single bounded pass is written as `JsonBounds` in `:data:model` from the start (plan 09 needs it from `:data:sync:implementation` and would otherwise move it and its tests again); `SetlistDocumentFormat` calls `JsonBounds` rather than reaching into `backup`; tests of the pass itself live in `:data:model`; the budget constant is shared with plan 09; a note on SongbookPro members nobody has measured.
**Files:**
- `data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/JsonBounds.kt` (new)
- `data/model/src/commonTest/kotlin/com/pandulapeter/campfire/data/model/JsonBoundsTest.kt` (new)
- `data/model/CLAUDE.md`
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/backup/ShallowJson.kt`
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/model/SetlistDocumentFormat.kt`
- `data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/backup/SongbookProBackupTest.kt`
- `data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/model/SetlistDocumentFormatTest.kt`
- `data/source/local/implementation/CLAUDE.md`

## Problem

Every JSON reader on the import path builds the whole `JsonElement` tree before any limit on its size applies, and the
tree costs 40 to 150 bytes per value. A file small enough to pass every size limit can therefore exhaust the heap.
`OutOfMemoryError` is an `Error`, so the `catch (exception: Exception)` blocks on the import path do not stop it, and
the app dies while preparing the import, before the review appears. Android gives an app a heap of about 192 MB
(`ImportLimits`' own KDoc).

The readers, at 5514fcf76:

- `backup/ShallowJson.kt`, used by `JsonSongs` and `SongbookProBackup` (and recursively for JSON inside strings):

  ```kotlin
  fun parse(text: String): JsonElement? = if (nestsTooDeep(text)) null else try {
      Json.parseToJsonElement(text)
  ```

  Its pre-pass `nestsTooDeep` counts only bracket depth (`MAX_NESTING = 64`). `JsonSongs.Walk.visit` stops after
  `MAX_NODES = 200_000`, but only while walking the tree that has already been built.
- `SongbookProBackup.read` parses `dataFile.txt`, which `ArchiveLocalSourceImpl` reads up to
  `ImportLimits.MAX_IMPORT_SIZE` (24 MiB). `JsonSongs.read` refuses only text over `MAX_TEXT_FILE_SIZE` (8 MiB).
- `model/SetlistDocumentFormat.kt` has no pre-check at all:

  ```kotlin
  fun decode(text: String): SetlistDocument {
      val element = json.parseToJsonElement(text)
      val document = json.decodeFromJsonElement<SetlistDocument>(element)
  ```

  It serves an imported `*.setlist.json` (`SetlistLocalSourceImpl.parseSetlist`) up to 8 MiB. It also serves every
  setlist the library holds: `loadSetlists` and `loadSetlist` at launch and on refresh, `loadSetlistDocument` on share,
  and `SetlistComparisonImpl` during sync. Because the depth guard of 1ea24739e lives only in `ShallowJson`, a few
  hundred kilobytes of `[[[[` inside a setlist still overflow the parser's stack. A probe confirmed it:
  `{"title":"x","songs":[{"file":"a","z":` + 100,000 `[`… throws `StackOverflowError`.

Measured (kotlinx-serialization-json 1.11.0, JVM, `parseToJsonElement` only, retained heap):

| document | text | tree |
|---|---|---|
| `{"songs":[0,0,…]}` | 23 MiB (12 M values) | 882 MB |
| `{"songs":[0,0,…]}` | 8 MiB (4.2 M values) | 312 MB (OOM at `-Xmx192m`) |
| `{"songs":[{"file":"a"},…]}` | 8 MiB (645 k entries) | 201 MB |
| 500,000 values of `0` / `{}` / `[]` / `{"file":"a"}` / `{"a":{}}` | 1–3 MB | 39 / 42 / 23 / 78 / 81 MB |

How it is reached:
- A 23 KB zip whose `dataFile.txt` is `1.0\n{"songs":[0,0,…]}` (the reviewer's
  `scratchpad/recipient/bomb_sbp.zip`). This needs one tap on a Campfire shared link, since the link's download is a
  zip read by the same import. Opening, sharing, or dropping the zip does the same.
- An 8 KB zip holding an 8 MiB `[0,0,…]`-shaped `*.setlist.json` (`bomb_setlist.zip`), or a loose `.json` read by
  `JsonSongs`.
- A setlist that does get into the library by another route (sync from the user's own folder, a hand-placed file) is
  decoded by the same `decode` at every launch. `SetlistLocalSourceImpl.loadSetlists` skips only files over 8 MiB.

The other JSON readers fall outside this finding. `UserPreferencesDocumentFormat`, the sync index, the shared links
mirror and journal, and the editor draft are local files the app writes. `SyncedPreferencesDocument` is read from the
user's own cloud folder. None of them is imported. `mapper/SetlistMappers.kt`'s `toFields` parses only text the mapper
wrote.

## Fix

Bound the tree before it is built: count structural tokens outside strings in the same single pass that already
counts depth, and refuse a document past a fixed budget. Use a fixed budget, not one scaled to the text's size. Memory
is what has to be bounded, and a budget proportional to 24 MiB would still allow a tree of hundreds of MB.

1. Add `data/model/.../JsonBounds.kt`, a public, dependency-free object beside `Logger.kt` (`:data:model` already
   hosts such helpers, `ImportLimits`, `ShareLinks`), so that plan 09 can call the same pass from
   `:data:sync:implementation`, which cannot see `:data:source:local:implementation`. Write it here once; plan 09
   only calls it. Do not keep a second copy of the loop anywhere.

   ```kotlin
   object JsonBounds {
       /** Whether [text] nests no deeper than [maxNesting] and holds at most [maxTokens] of `[ { , :` outside strings. */
       fun isWithin(text: String, maxTokens: Int, maxNesting: Int = MAX_NESTING): Boolean
       const val MAX_NESTING = 64
       const val MAX_TOKENS = 600_000
   }
   ```

   The loop is today's `ShallowJson.nestsTooDeep` (string and escape tracking moved as is), with a counter that
   increments on `[`, `{`, `,`, and `:` outside strings and returns false as soon as either bound is passed. Every
   value except the root follows one of those four characters, and so does every object key. The count therefore
   bounds the number of tree nodes from above, so no shape can get more values past the check than the budget allows.

   `MAX_TOKENS = 600_000`. By the measurements above, the worst shape at that budget is about 65 MB of tree
   (`{"file":"a"}` entries, 3 tokens per 2 values). The 24 MiB budget's library of about 6,000 SongbookPro songs writes
   roughly 25 members a song, about 50 tokens. That totals about 300,000, so legitimate backups pass with room to
   spare. State this reasoning in the constant's KDoc, and the stack reasoning (today's `MAX_NESTING` KDoc) on
   `MAX_NESTING`. Note in the KDoc that a refusal costs the whole document: SongbookPro's format is unpublished, and
   members never measured (its drawings, if a version writes them as arrays rather than as text) count too.

   `ShallowJson` keeps its name and role; `parse` becomes `if (!JsonBounds.isWithin(text, JsonBounds.MAX_TOKENS)) null
   else try { … }`, its private `nestsTooDeep` and `MAX_NESTING` are deleted, and its KDoc names both bounds: depth,
   because of the stack, and tokens, because of the heap (an `OutOfMemoryError` is an `Error` too).

2. In `SetlistDocumentFormat.decode`, check the text before parsing it:

   ```kotlin
   require(JsonBounds.isWithin(text, maxTokens = MAX_SETLIST_TOKENS)) { "The setlist is too large or too deeply nested to read." }
   ```

   `IllegalArgumentException` is what `decode` already throws in practice, since `SerializationException` extends
   it, and every caller already handles it: `loadSetlists` logs and skips, `parseSetlist` logs and returns null, and
   `loadSetlist` throws to the repository as it does for invalid JSON. `MAX_SETLIST_TOKENS = 100_000`. A song entry
   the app writes (`file` plus at most `transposition`, `tempo`, and `capo`) is at most about 9 tokens, so the budget
   holds over 10,000 songs, more than any library the import budget admits, and its tree stays under about 15 MB.
   This one check covers depth and size for the import and for the launch-time loaders, which share `decode`.
   `model` calls `JsonBounds` directly, not `backup.ShallowJson`.

   A separate cap on setlist entries is not needed: the token budget already bounds them.

3. Keep `JsonSongs`' `MAX_NODES` / `MAX_DEPTH` walk bounds. They bound the work done on a tree that passed, and
   they are documented. Nested JSON inside strings goes through `ShallowJson.parse` and is bounded the same way, one
   string at a time.

4. In `data/source/local/implementation/CLAUDE.md`, change the sentence "JSON is bounded to the text limit, 64 levels
   of brackets before parsing (`ShallowJson`; deeper overflows the parser's stack), and 32 levels and 200,000 nodes
   when walked" so that it also names the token budget counted before parsing, which keeps the tree within the heap.
   Under "Documents", add to the Setlists bullet that `decode` refuses a document past the same depth and a
   100,000-token budget before parsing. In `data/model/CLAUDE.md` "Other", add one line: `JsonBounds` is the one
   check of a JSON text from outside the app (depth, and tokens outside strings) made before it is parsed. Name no
   constant the code lacks, because the pre-commit hook (`check_doc_names.py`) refuses backticked names that are not
   in the code.

Every new or changed source file keeps the MPL-2.0 header. Load the `code-style` skill before editing.

## Tests

`./gradlew :data:model:desktopTest :data:source:local:implementation:desktopTest`. Each test below fails at 5514fcf76 (and at ea4b86a1e) for the reason given.

- New `JsonBoundsTest` in `:data:model` (`./gradlew :data:model:desktopTest`; fails to compile without the fix):
  - ``fun `a document past the token budget is refused` `` — `JsonBounds.isWithin("[" + List(600_001) { "0" }.joinToString(",") + "]", JsonBounds.MAX_TOKENS)` is false, and the same with 599,999 zeros is true.
  - ``fun `tokens inside strings do not count` `` — `"[\"" + ",:".repeat(600_001) + "\"]"` is within; escaped quotes
    (`"\\\""`) do not end the string.
  - ``fun `depth 64 passes and 65 does not` ``.
- `SongbookProBackupTest`, ``fun `a data file past the token budget is unreadable rather than parsed` ``: run
  `SongbookProBackup.read(listOf(dataFile("1.0\n{\"songs\": [" + List(600_001) { "0" }.joinToString(",") + "]}")))`
  and assert `Result.Unreadable` (the version line marks the document as SongbookPro's). Today the tree is built,
  `songs` is an array of non-objects, and the result is `Result.Library(emptyList())`, so the test fails.
- `SetlistDocumentFormatTest`:
  - ``fun `a setlist past the token budget is refused before parsing` `` asserts `assertFailsWith<IllegalArgumentException>`
    on `decode("""{"title":"x","songs":[""" + List(40_000) { """{"file":"a.cho"}""" }.joinToString(",") + "]}")`.
    That is 120,000 tokens, over 100,000. Today it decodes successfully, so the test fails.
  - ``fun `a setlist nested past the parser's stack is refused rather than overflowing it` `` asserts the same exception for
    `"""{"title":"x","songs":[{"file":"a.cho","z":""" + "[".repeat(100_000) + "]".repeat(100_000) + "}]}"`. Today it
    throws `StackOverflowError`, which `assertFailsWith<IllegalArgumentException>` does not catch, so the test errors.
  - Keep a positive case: an ordinary setlist of a few hundred songs still decodes. The existing tests already cover
    one of a handful.

The existing `JsonSongsTest` cases (`the depth and node caps stop cleanly` with 250,000 values, and the bracket cases)
must still pass unchanged. Heap use in the tests stays small: the largest document is about 1.2 MB of text.
