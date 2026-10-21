# Check the nesting depth and token count of the cloud folder's `preferences.json` and `shared-links.json` before parsing them, so a hand-edited document reads as unreadable instead of overflowing the stack or the heap

**Kind:** bug (crash) · **Severity:** high · **Platforms:** all (a crash on Android and desktop when Sharing is opened; on iOS, where Kotlin/Native cannot catch a stack overflow, during every sync run)
**Challenged:** amended — plan 01 now writes `JsonBounds` in `:data:model` itself, so this plan only calls it (no move, no second copy, no tests to relocate); the folder documents use `JsonBounds.MAX_TOKENS` (600,000), not 200,000, because a refused `preferences.json` is replaced with this device's values, so a budget a large real library can reach would silently overwrite other devices' settings on every run; the mirror is checked with a small margin over the registry's budget; a mirror test is added.
**Files:**
- `data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/JsonBounds.kt` (from plan 01; unchanged here)
- `data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/preferences/SyncedPreferencesDocument.kt`
- `data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/shared/SharedLinksFormat.kt`
- `data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/shared/SharedLinksMirror.kt`
- `data/sync/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/sync/implementation/preferences/SyncedPreferencesTest.kt`
- `data/sync/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/sync/implementation/shared/SharedLinksFormatTest.kt`
- `data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/preferences/CLAUDE.md`, `.../shared/CLAUDE.md`

## Problem

Plan 01 bounds depth and tokens for the import's readers and `SetlistDocumentFormat.decode`. That covers synced
setlists, both at launch and in `SetlistComparisonImpl` during sync. The two documents the sync engine reads straight
from the user's folder go through a bare `parseToJsonElement` with no bound, and their `catch` blocks stop only
exceptions:

- `SyncedPreferencesDocument.kt:83–87`:

  ```kotlin
  fun decode(bytes: ByteArray): JsonObject? = try {
      json.parseToJsonElement(bytes.decodeToString()) as? JsonObject
  } catch (exception: IllegalArgumentException) {
      null
  }
  ```

  It is called by every run (`SyncedPreferencesSync`) and by the watcher (`SyncWatchPoint.isOtherPreferences`,
  `SyncRemoteWatcher.kt:66`).
- `SharedLinksFormat.kt:46–50` (`catch (_: SerializationException)`). It is called by `SharedLinksRegistry.read`, from
  every run's shared-copy step, from Settings → Sharing (`SharingSections.kt:96`, a `LaunchedEffect` calling
  `controller.refresh()`), and from every sharing action. The registry's unknown members are then pretty-printed into
  the local mirror (`SharedLinksMirror.saveLocked`) and parsed again on its first load (`SharedLinksMirror.kt:137`),
  which `ShareLinkController.shares` starts eagerly.

The hand edit that triggers it: anything that nests brackets a few thousand deep, for example `"x":[[[[…]]]]` added
to either file. 40 KB is enough. Measured with kotlinx-serialization-json 1.11.0 on the JVM (throwaway probe):
`parseToJsonElement` overflows the stack at a nesting of 1,994 on a 1 MB thread and 864 on a 512 KB thread.
Re-encoding pretty-printed overflows sooner, at 732 and 312. At 20,000 it overflows on every stack tried.

- **Android and desktop**: `StackOverflowError` is an `Error`. In a run it reaches `SyncRunner`'s
  `catch (throwable: Throwable)`, so every run ends as `Failure(UNKNOWN)`, even though the library itself synced. The
  watcher catches only `Exception`, so the scope's handler logs it and the feed stops being watched. Settings →
  Sharing is worse: `failureOf` catches only `Exception`, so the error leaves the `LaunchedEffect` and **the app
  crashes each time the Sharing tab is shown** while connected.
- **iOS**: Kotlin/Native turns stack exhaustion into a `SIGSEGV`, not a catchable throwable. This is the platform's
  documented behaviour; it was not run here. Worker threads have 512 KB stacks. The run that starts at every launch
  and every return to the front crashes the app. Disconnecting is only possible in the seconds before the run
  reaches the preferences step.
- Size without depth does the same to the heap. An 8 MiB `{"songs":{},"x":[0,0,…]}` builds a tree of about 312 MB
  (plan 01's table), which is an `OutOfMemoryError` on Android, inside a run.

## Fix

Use one bounded pre-check for every JSON document from outside the app.

1. **Call the check plan 01 writes.** Plan 01 (same lane, landed first) adds the single pass as
   `JsonBounds.isWithin(text, maxTokens, maxNesting = 64)` in `:data:model`, which `:data:sync:implementation` sees
   through the API modules. Call it; do not add another loop.
2. `SyncedPreferencesDocument.decode`: return null when `!JsonBounds.isWithin(text, maxTokens = JsonBounds.MAX_TOKENS)`, before
   parsing. Null already means "not a JSON object, replaced keeping this device's values", which is the existing
   unreadable path (`preferences/CLAUDE.md`). Recovery is therefore automatic: the next completed run uploads this
   device's document over the bad one with the revision it read, and the watcher sees a document unlike its own and
   schedules that run.
3. `SharedLinksFormat.decode`: return `Decoded.Unreadable` on the same check. An unreadable registry already counts as
   empty, and only a sharing action writes over it (`shared/CLAUDE.md`). Settings then shows no shares instead of
   crashing.
4. `SharedLinksMirror.decode`: apply the same check to the mirror's text (return null, which is "no mirror"), with
   `maxTokens = JsonBounds.MAX_TOKENS + 16`: the mirror wraps the registry in an object with its revision, a few
   tokens more, so a registry just inside the budget must not make its own mirror unreadable. The embedded registry
   is then checked again by `SharedLinksFormat.decode`. The mirror is written only from a bounded registry once 3 is
   in; this step protects devices that already hold a mirror saved before the fix, at the one launch-time parse this
   module does.
5. Budget: `JsonBounds.MAX_TOKENS` (600,000, plan 01's) for both documents; no constant of their own. Not lower: an
   unreadable `preferences.json` is replaced with this device's values, so a real document refused by its size would
   overwrite every other device's settings on each run, and the device's own upload would then be refused by them in
   turn. A song's entry with transposition, tempo, capo, and beat levels of one 16-beat signature is about 30 tokens,
   so 200,000 would be reached by about 6,500 configured songs, which a large library can have; 600,000 is about
   20,000. A registry entry is about 22 tokens. The worst tree stays at about 65 MB (plan 01's figure), and only for a
   document built to reach it. The depth of 64 is far beyond the 4 levels either document uses (`songs` → song →
   `beatLevels` → list). Say this in a comment at each call.

Nothing new in the UI. The sync index's `syncedPreferences` and the local `preferences.json` hold only what passed
these readers, so they need no change.

Docs:
- `preferences/CLAUDE.md`: "A folder document that is missing or unreadable…" gains "(one nested past 64 levels or
  over the token budget is unreadable, checked before parsing)".
- `shared/CLAUDE.md` Registry bullet: "One that does not decode, or is nested or sized past the bound checked before
  parsing, counts as empty".

Name only constants that exist (`check_doc_names.py`).

## Tests

`./gradlew :data:sync:implementation:desktopTest`. Each test below fails at 5514fcf76 (and at ea4b86a1e).

- `SyncedPreferencesTest`:
  - ``a folder document nested past the bound reads as unreadable``:
    `SyncedPreferencesDocument.decode(("{\"songs\":{},\"x\":" + "[".repeat(100_000) + "]".repeat(100_000) + "}").encodeToByteArray())`
    is null. Today it throws `StackOverflowError`, so the test errors.
  - ``a folder document past the token budget reads as unreadable``: `{"songs":{},"x":[0,0,…]}` with 600,001 zeros
    (about 1.2 MB) decodes to null. Today it returns the object.
  - Keep a positive case: the existing round trips of real documents still decode.
- `SharedLinksFormatTest`:
  ``a registry nested past the bound is unreadable rather than overflowing the stack``: the same `[` × 100,000 under
  an unknown member gives `Decoded.Unreadable`. Today it throws `StackOverflowError`. Add a token-budget twin.
- The mirror: in `SharedLinksRepositoryImplTest` (or wherever the mirror's load is driven with
  `FakeSharedLinksLocalSource`), a stored mirror whose registry nests `[` × 100,000 under an unknown member loads as
  no mirror (empty `shares`) instead of throwing `StackOverflowError`, which it does today.
- Optional, in `SharedLinksRepositoryImplTest`: with a fake folder whose `shared-links.json` is the deep document,
  `refresh()` answers without throwing and `shares` is empty. Today the `StackOverflowError` escapes `refresh`.
