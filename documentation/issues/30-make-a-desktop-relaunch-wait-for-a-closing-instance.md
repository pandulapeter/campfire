# Make a desktop relaunch wait for a closing instance for as long as that instance can take to close

**Challenged:** amended — the short budget counts from when the endpoint file appeared (the plan's version could give up on a fresh holder after a long wait, starting next to it); a holder whose listener failed now leaves a placeholder endpoint file, so only a *closing* holder gets the 30 s budget (otherwise every launch and every file opened next to such a holder would hang silently for 30 s instead of 5 s); the waiting UX, repeated clicks and a hung holder are spelled out; manual check extended.

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** desktop (Windows and Linux mainly; macOS from a Gradle/IDE run or a second `open -n`)
**Files:** `app/desktop/src/main/java/com/pandulapeter/campfire/SingleInstance.kt`,
`app/desktop/src/main/java/com/pandulapeter/campfire/CampfireDesktopApplication.kt` (comment only),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt` (comment on
`EXIT_SYNC_GRACE` only), `app/desktop/CLAUDE.md`

## Problem

Quitting the desktop app does not end the process at once. `leave` in `CampfireDesktopApplication.kt` hides the window,
stops listening for other instances and only then lets the sync run finish:

```kotlin
val leave = { end: () -> Unit ->
    if (!isLeaving) {
        isLeaving = true
        stopListeningForOtherInstances()
        scope.launch {
            viewModel.value?.settleSynchronizationBeforeExit()
            end()
        }
    }
}
```

`settleSynchronizationBeforeExit` (`CampfireViewModel.kt`) waits up to `EXIT_SYNC_GRACE = 15.seconds` for the run (and
any run chained behind it), then cancels it and waits up to `EXIT_SYNC_STOP_GRACE = 2.seconds` more. So a quitting
process with a sync run going can live about 17 s after its window disappears, writing library files and
`sync-index.json` the whole time. `stopListeningForOtherInstances()` closes the socket and deletes `instance.endpoint`,
but keeps the `instance.lock` file lock until the process is gone (its KDoc says so, on purpose).

A process started in that time (the user clicks the launcher again, or opens a `.cho` from Explorer / the file manager)
runs `claimSingleInstance`:

```kotlin
repeat(HAND_OVER_ATTEMPTS) {
    val lock = ... channel.tryLock() ...       // null: the closing process still holds it
    if (lock != null) { ...; return true }
    if (sendPaths(dataDirectory, paths)) return false   // false at once: instance.endpoint was deleted, readLines throws
    Thread.sleep(HAND_OVER_RETRY_MILLIS)
}
println("The running instance did not answer, starting next to it.")
return true
```

with `HAND_OVER_ATTEMPTS = 20` and `HAND_OVER_RETRY_MILLIS = 250L`. With no endpoint file each `sendPaths` fails
immediately, so the whole loop is about 5 s. A relaunch made 0–12 s after quitting during a sync run therefore gives
up while the old process still holds the lock and is still writing, and starts Koin and reads the library next to it —
exactly the two-process situation the file's KDoc says makes the processes "take back each other's changes" (each
repository writes its files whole from what it cached; the new process also starts its own launch sync run while the
old one's run is still writing the same files and the same index).

It is worse afterwards: the process that started next to the closing one holds no lock and never listens. Once the
old process exits, the lock is free, so the *next* launch (another file opened from Explorer, say) takes the lock and
starts a third window instead of handing over — two live processes on one library for the rest of the session.

The KDoc of `claimSingleInstance` and `app/desktop/CLAUDE.md` both already promise the opposite ("a process started
while the previous one is still closing takes it over once it is free rather than starting without it"); the promise
only holds for a close that takes less than about 5 s.

## Fix

Two options:

- **A (recommended): give the "lock held, no endpoint" case its own, longer budget.** That state means either a
  holder that is closing (no endpoint because `stopListeningForOtherInstances` deleted it), a holder that started a
  moment ago and has not written its port yet (milliseconds), or the rare holder whose `startListening` failed (the
  `catch` there). Waiting longer in the first case is the point; it costs nothing in the second; in the third it only
  delays the "start next to it" fallback that already exists. When the endpoint file *is* there but does not answer,
  keep today's short budget (about 5 s), since that is a live instance that is not answering and waiting longer helps
  nobody.
- B: keep the listener open while closing and answer a new `CLOSING` reply, so the newcomer knows to wait. This
  changes the protocol between versions (an older running instance never says it) and still needs a cap, so it is more
  code for the same result. Not recommended.

Implementing A in `SingleInstance.kt`: replace the attempt count with elapsed time and choose the budget each round
from whether `instance.endpoint` exists at that moment.

```kotlin
internal fun claimSingleInstance(...): Boolean {
    val start = System.nanoTime()
    var endpointSeenAt: Long? = null
    while (true) {
        val lock = ... // unchanged
        if (lock != null) { heldLock = lock; startListening(dataDirectory, onActivated); return true }
        if (sendPaths(dataDirectory, paths)) return false
        val now = System.nanoTime()
        val hasEndpoint = File(dataDirectory, ENDPOINT_FILE_NAME).exists()
        endpointSeenAt = if (hasEndpoint) endpointSeenAt ?: now else null
        // The short budget counts from when the endpoint appeared, not from this process's start: a newcomer that
        // waited 20 s for a closing holder must not give up on the next holder because it read its endpoint file
        // half written once.
        val isOverBudget = if (endpointSeenAt != null) {
            now - endpointSeenAt >= UNANSWERED_HAND_OVER_MILLIS * 1_000_000
        } else {
            now - start >= CLOSING_INSTANCE_WAIT_MILLIS * 1_000_000
        }
        if (isOverBudget) break
        Thread.sleep(HAND_OVER_RETRY_MILLIS)
    }
    println("The running instance did not answer, starting next to it.")
    return true
}

private const val HAND_OVER_RETRY_MILLIS = 250L
private const val UNANSWERED_HAND_OVER_MILLIS = 5_000L
private const val CLOSING_INSTANCE_WAIT_MILLIS = 30_000L
```

`CLOSING_INSTANCE_WAIT_MILLIS` must exceed `EXIT_SYNC_GRACE + EXIT_SYNC_STOP_GRACE` (17 s) plus the JVM's own shutdown
(shutdown hooks, `exitApplication`) with a margin; 30 s does. Give it a KDoc saying exactly that and naming
`CampfireViewModel.EXIT_SYNC_GRACE`, and add to the comment on `EXIT_SYNC_GRACE` in `CampfireViewModel.kt` that the
desktop's `SingleInstance.kt` waits `CLOSING_INSTANCE_WAIT_MILLIS` for a closing process, so raising the grace means
raising that too (the two modules cannot share the constant: `SingleInstance.kt` runs before Koin and `:presentation`'s
companion value is private). Print one line when the wait starts in the no-endpoint state ("Waiting for the closing
instance to exit.") so a log shows why a launch took long; `DesktopLog` is not installed yet at that point, so this
goes to the console only, which is fine.

**Tell the failed-listener holder apart from a closing one.** As written, "lock held, no endpoint" also describes a
live holder whose `startListening` failed (the `catch` deletes the endpoint file), and that state lasts for the whole
session: every launch, and every file opened from Explorer, would then hang silently for 30 s before starting next to
it, where today it takes 5 s. Make that holder leave an endpoint file that exists but cannot be answered: in
`startListening`'s `catch`, instead of `endpointFile.delete()`, delete it and then write (best effort, in its own
`try`; if even that fails, the long wait is the fallback) an empty (or `"unavailable"`) file and set
`listeningEndpointFile = endpointFile`, so `stopListeningForOtherInstances` still deletes it as that process starts
closing (its close is then waited for in full, like any other). A newcomer's `sendPaths` already fails on a file it
cannot parse, and `File.exists()` puts it on the short budget. Leftovers are harmless: the next lock holder's
`startListening` deletes whatever is there first, and the file never decides anything while the lock is free. An
older running version (which deletes the file in that `catch`) only gets the long wait in that already rare case.

A holder that hangs while closing (lock held, endpoint gone, never exits) is waited for the full 30 s and then started
next to, the same fallback as today, only later: there is no way to tell a slow close from a hung one, and the old
process is not writing anything a hung process could lose.

Behavior for the user: a relaunch during a long exit shows nothing for up to ~17 s and then opens normally, with the
lock and a listener of its own (so later launches hand over to it). That is the intended trade: no window for a few
seconds, rather than two processes writing one library. Nothing is shown while waiting on purpose: this runs before
Koin, so there is no language preference and no theme to draw a localized window with, and starting AWT just for a
"please wait" would cost the common, instant case. A user who clicks the launcher again in the meantime starts another
waiting process; once the old one exits one of them wins the lock and writes its endpoint, and the others (still inside
their budget, since the endpoint now exists and answers) hand over to it and come forward — check that in the manual
check below. On macOS from Finder/the Dock, LaunchServices activates the closing process rather than starting a second
one, so this case is reached there only through `open -n` or a Gradle/IDE run, as the header says.

Update the docs to match:
- `claimSingleInstance`'s KDoc: replace "the attempts are more than one" wording with the two budgets — a closing
  holder (lock held, no endpoint) is waited for as long as a closing process can take; a holder whose endpoint does not
  answer, about five seconds; both end in "starting next to it".
- `startListening`'s `catch` comment: it now leaves an unanswerable endpoint file so that newcomers take the short
  budget, rather than deleting it.
- `stopListeningForOtherInstances`' KDoc already says a newcomer waits for the lock; add "for up to
  `CLOSING_INSTANCE_WAIT_MILLIS`".
- `app/desktop/CLAUDE.md`, the single-instance paragraph ("The lock is asked for again before every hand-over attempt,
  so a process started while the previous one is still closing takes it over once it is free…"): say the newcomer waits
  up to 30 s while the lock is held and the endpoint is gone — longer than the 15 + 2 s a quit gives a sync run in
  `settleSynchronizationBeforeExit` — and gives up after about 5 s only when an endpoint is there and does not answer.
  The paragraph about `leave` ("The listener for other instances is closed first, so a launch in the meantime waits for
  the lock.") can stay.

## Tests

None. `:app:desktop` is a plain JVM module with no test source set and CI runs no test there; the change is a loop
over a real file lock and a real socket, and the only pure part (picking a budget from "endpoint exists") is one `if`.
Adding a test source set and a `tests.yml` entry for it would be out of proportion.

## Manual check

On Windows or Linux (or macOS with `./gradlew :app:desktop:run`, run twice from two terminals):
1. Connect Dropbox and make the exit slow: e.g. import a few hundred songs so that a run is long, or throttle the
   network (macOS Network Link Conditioner, Linux `tc`), then edit a song so a run is scheduled.
2. Quit the app; while its window is gone but `ps`/Task Manager still shows the process (it can be checked with
   `lsof <data dir>/instance.lock` on macOS/Linux), launch Campfire again 6–15 s after quitting.
3. Expected: the new window appears only after the old process has exited; the console of the new one says it waited;
   there is never a moment with two Campfire processes past the old one's exit. Then open a `.cho` from the file
   manager: it must open in that window (hand-over works, so the new process holds the lock).
4. Regression: with no sync connected, quit and relaunch at once — the new window appears as quickly as before.
5. During the wait of step 2, launch two or three more times: once the old process is gone exactly one window opens,
   and the extra processes exit after handing over (no second window, none left running).
6. Failed listener: temporarily make `startListening` throw after taking the lock (a debug build only), start it, then
   launch again — the newcomer gives up after about 5 s, not 30 s.
