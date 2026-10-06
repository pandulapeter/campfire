# Read `campfire.log` in the Windows start check, as the Linux and macOS legs read theirs

**Kind:** bug / docs  ·  **Severity:** low  ·  **Platforms:** CI (Windows)
**Files:** `.github/workflows/publish-windows.yml`, root `CLAUDE.md` (one parenthesis), `app/desktop/CLAUDE.md` (one
parenthesis)

## Problem

The Windows leg starts the ProGuard-built app image once before submitting it, but only checks that the process is
still running (`publish-windows.yml:147-186`, 8ee010b36):

```sh
# ... The
# Windows launcher writes no log, so an exception that leaves the process running goes unseen here; the Linux
# workflow starts the same ProGuard output and reads its log.
...
sleep 15
is_running || fail "The release build exited on its own after it had written the library."
stop
```

The premise is wrong. `CampfireDesktopApplication.kt:92` calls `DesktopLog.install(desktopDataDirectory())` on every
platform, which mirrors stdout, stderr and every uncaught exception (the default handler, which AWT's event thread
uses too) into `campfire.log` in the data directory, flushed after every write (`DesktopLog.kt`, `RollingLog.append`).
The step starts the plain app image (no package family name), so the data directory is `%APPDATA%\Campfire` — the
`$DATA` the step already polls for `preferences/` and `library/`. The log is at `$DATA/campfire.log`.

The Linux leg is not a substitute: what only a Windows release build breaks — the JBR custom title bar (bound by
interface name, the reason for the `com.jetbrains.**` keep rule), the Windows branches of `setUndrawnAreaColor`, the
Windows audio and file dialog paths — throws on the AWT thread, is logged, leaves the process running, and the
package is submitted to the Store.

The same false sentence is in the root `CLAUDE.md` (`publish-windows.yml` bullet: "(the Windows launcher writes no
log, so there only an exit counts)") and in `app/desktop/CLAUDE.md` ("the Windows launcher writes no log, so there
only an exit counts").

## Fix

In `publish-windows.yml`'s "Start the release build once":
- add `LOG="$DATA/campfire.log"` next to `DATA`;
- make `fail()` print the log before stopping: `cat "$LOG" 2> /dev/null || true`;
- after `is_running || fail "… after it had written the library."`, add
  `grep -q "Exception" "$LOG" && fail "The release build logged an exception."`, and `cat "$LOG"` before the final
  `echo`, as `publish-linux.yml:131-133` does. The step runs under Git Bash (as the rest of it does), so `grep` and
  `cat` are there.
- replace the comment's last sentence with: "The app writes everything it prints to campfire.log in the data
  directory, which is read for an exception the way the Linux and macOS legs read theirs."

In the root `CLAUDE.md` replace "(the Windows launcher writes no log, so there only an exit counts)" with "(and
reads the `campfire.log` the app writes into its data directory for an exception)"; in `app/desktop/CLAUDE.md`
replace "; the Windows launcher writes no log, so there only an exit counts" with "; on Windows that log is the
`campfire.log` in the data directory". Edit only those words: other lanes edit the root `CLAUDE.md` too.

If the first run shows a benign line containing "Exception" that only Windows prints (the Linux leg had to set
`SKIKO_RENDER_API` for its software renderer's `RenderException`), deal with its cause the same way rather than
loosening the grep.

## Tests

None: a workflow step, which only a Windows runner executes.

## Manual check

Dispatch `publish-windows.yml` by hand with `submit` off on the current tag: the step passes and prints the log;
the log holds the startup lines (timestamped) and no exception.
