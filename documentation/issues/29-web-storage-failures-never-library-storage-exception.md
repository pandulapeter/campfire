# 29 · Web: a sync run the browser's storage refuses says "The sync did not finish." instead of "The library could not be read or written."

**Severity:** minor (web only; wrong message, nothing lost; whenever OPFS refuses an operation — quota exceeded, a file
locked by a writable, storage disabled) · **Area:** `:data:source:local:implementation` (`OpfsFileStorage`)

**Verifier:** `delete` is no longer put through `failingAsStorage` (the contract promises `LibraryStorageException`
for reads and writes only, and the JVM and iOS deletions throw `IllegalStateException`), and an
`IllegalArgumentException` from `requireValidFileName` is passed on as it is, as the storage contract requires.

## Symptom
On the web, fill the origin's quota (or have OPFS refuse a write in any other way) and run a sync: the run fails, and
Settings says "The sync did not finish." — the message for an unknown failure — rather than "The library could not be
read or written.", which is the one that tells the user the problem is on this device. The same holds for every
failure of an OPFS read or write: none of them is ever the `LibraryStorageException` the storage contract promises
(`FileStorage.kt:57-70`, `:data:source:local:implementation` CLAUDE.md: "throws `LibraryStorageException` … on every
platform").

## Cause
`data/source/local/implementation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/FileStorage.wasmJs.kt:107-116`:

```kotlin
/**
 * A rejected promise surfaces from `await` as a `JsException`, …
 */
private suspend fun <T> failingAsStorage(name: String, operation: suspend () -> T): T = try {
    operation()
} catch (exception: JsException) {
    throw LibraryStorageException("Could not access \"$name\".", exception)
}
```

The premise is wrong for the `await` this file imports (`kotlinx.coroutines.await`, 1.11.0,
`webMain/Promise.kt:79-83`): a rejection is resumed with `JsPromiseError.toThrowable()`, whose wasm actual
(`wasmJsMain/Promise.wasm.kt:8-12`) is

```kotlin
internal actual fun JsPromiseError.toThrowable(): Throwable = try {
    unsafeCast<JsReference<Throwable>>().get()
} catch (_: Throwable) {
    Exception("Non-Kotlin exception $this of type '${this::class}'")
}
```

A `DOMException` is not a wrapped Kotlin object, so `get()` throws `ClassCastException` and the rejection arrives as a
plain `kotlin.Exception`. `JsException` is only what a *synchronous* `js(...)` call throws, and it is not an `Exception`
at all (`kotlin-stdlib-wasm-js` 2.4.20, `JsException : Throwable`). So the `catch` never matches anything the
operations inside it can throw, and every OPFS failure leaves the storage as `Exception("Non-Kotlin exception …")`.

Where that is visible: `SyncRepositoryImpl.toFailureReason()`
(`data/repository/implementation/.../SyncRepositoryImpl.kt:589-595`) maps only `LibraryStorageException` to
`SyncFailureReason.STORAGE`, and the writes of the index that open and complete a run are the ones that end a run with
it. Everything else in the app catches `Exception` and is unaffected, which is why this is only a message.

## Fix
In `FileStorage.wasmJs.kt`:

1. `failingAsStorage` catches every failure except a cancellation and a refused name:

   ```kotlin
   private suspend fun <T> failingAsStorage(name: String, operation: suspend () -> T): T = try {
       operation()
   } catch (exception: CancellationException) {
       throw exception
   } catch (exception: IllegalArgumentException) {
       // requireValidFileName: a name the caller should never have asked for, which the contract reports as it is.
       throw exception
   } catch (exception: LibraryStorageException) {
       throw exception
   } catch (exception: Throwable) {
       throw LibraryStorageException("Could not access \"$name\".", exception)
   }
   ```

   `Throwable` rather than `Exception`: a rejected promise arrives as a plain `kotlin.Exception` (above), but a
   synchronous `js(...)` call that throws surfaces as `JsException`, which is not an `Exception` at all
   (`FilePicker.wasmJs.kt:148-149` says the same). Import `kotlinx.coroutines.CancellationException`; drop the
   `kotlin.js.JsException` import, which nothing else in the file uses.
2. Rewrite its KDoc: "A rejected promise surfaces from `await` as a plain `Exception` whose message names the
   JavaScript error (the coroutines library can only unwrap a Kotlin one), and a synchronous `js(...)` call throws a
   `JsException`, which is not an `Exception` at all; neither says anything a caller could tell apart from any other
   failure. Only a file that is not there is folded into null (see `getFileHandle`); everything else the browser
   refuses — a `NotAllowedError`, a `QuotaExceededError`, a file locked by a writable — is a file that exists and cannot
   be used."
3. Leave `delete`, `list`, `listNames`, `info` and `exists` as they are. The contract (`FileStorage.kt:57-70`) promises
   `LibraryStorageException` for reads and writes only, the JVM and iOS storages do the same, and a failure of any of
   the others is still an `Exception` (a rejected promise, or the `IllegalStateException` of `directoryHandle`) that
   fails the scan or the sync run as a whole, which is what it does on the other platforms.

Do **not** catch `Throwable` without rethrowing `CancellationException` first — on the web a cancelled scan would
otherwise become a storage failure.

## Tests
None (the OPFS storage runs only in a browser).

## Verify
1. `./gradlew :app:web:wasmJsBrowserDevelopmentRun`, connect a Dropbox account (a build with `campfire.dropbox.appKey`).
2. Once connected, Chrome DevTools → Application → Storage → "Simulate custom storage quota", set below what the
   origin already uses, then press Sync. Before: "The sync did not finish." After: "The library could not be read or
   written."
3. In the console, the log line of the failure now reads "Could not access "sync-index.json"." with the
   `QuotaExceededError` as its cause.

## Docs
None: `data/source/local/implementation/CLAUDE.md` already says what this makes true ("A file that is there and cannot
be read or written throws `LibraryStorageException` … on every platform").

## Touches
- `data/source/local/implementation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/FileStorage.wasmJs.kt`

## Depends on
01 (same file: `writeText`/`writeBytes`, `writeFile`); do 01 first, then this. 05 is independent of it (different
modules) and in either order: after this plan an OPFS read or write failure is `SyncFailureReason.STORAGE`, and 05's
last-resort `Throwable` branch in `runSynchronization` is only for what is not an `Exception`.
