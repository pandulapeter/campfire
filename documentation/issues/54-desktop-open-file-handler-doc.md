# 54 · `app/desktop/CLAUDE.md` says the macOS open-file handler is registered "as the first thing `main` does"

**Severity:** docs (desktop. No behaviour changes: the JDK queues the `odoc` events that arrive before the first
handler, so a file the app was started for is still imported. A maintainer who relies on the sentence could put
something between the two that depends on it) · **Area:** `app/desktop/CLAUDE.md`

## Symptom
The module doc says `OpenedFiles` registers its handler with `Desktop.setOpenFileHandler` "as the first thing `main`
does". It is not the first thing: the single-instance check runs before it and can take seconds.

## Cause
`app/desktop/src/main/java/com/pandulapeter/campfire/CampfireDesktopApplication.kt:49-64`:

```kotlin
fun main(args: Array<String>) {
    val activations = Channel<Unit>(Channel.CONFLATED)
    val isFirstInstance = claimSingleInstance(
        dataDirectory = desktopDataDirectory(),
        paths = args.map { File(it).absolutePath },
        onActivated = { ... },
    )
    if (!isFirstInstance) exitProcess(0)
    OpenedFiles.listenForSystemRequests()
    OpenedFiles.open(args.toList())
    startCampfireDependencyGraph()
```

`claimSingleInstance` (`SingleInstance.kt`) runs first and, against a holder that is closing, retries for a while (the
reviewer counts 20 × 250 ms plus connect and read timeouts per attempt) before `OpenedFiles.listenForSystemRequests()`
is reached. It has to stay in that order: a process that is not the first instance exits before Koin and before
anything asks `Desktop`, and asking `Desktop` starts AWT. The doc is what is wrong, not the code. (A second process on
macOS is rare in any case: Launch Services hands a document to the running app rather than starting another.)

## Fix
`app/desktop/CLAUDE.md`, the paragraph beginning "`main` takes `args`...": replace "which the JDK hands to the
handler `OpenedFiles` registers with `Desktop.setOpenFileHandler` as the first thing `main` does, on macOS only" with
"which the JDK hands to the handler `OpenedFiles` registers with `Desktop.setOpenFileHandler` right after the
single-instance check and before Koin starts, on macOS only". The parenthesis that follows already says that the JDK
queues the events that precede the first handler, which is what makes the delay harmless; leave it as it is.

## Tests
None (docs).

## Verify
Read the paragraph against `main` in `CampfireDesktopApplication.kt`: the order it describes (single-instance check,
then the handler, then Koin) is the order of the calls.

## Docs
This plan is the doc change.

## Touches
- `app/desktop/CLAUDE.md`

## Depends on
Nothing. 16 edits the Packaging paragraph of the same file.
