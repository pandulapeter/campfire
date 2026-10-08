# Fail the release start check when the log or the class-load log it checks is missing

**Kind:** bug (CI)  ·  **Severity:** medium  ·  **Platforms:** Linux, Windows, macOS (store publishing)
**Files:** `.github/scripts/start_release_build.sh`, `.github/CLAUDE.md` (only if it describes what the script checks)
**Challenged:** amended — the log guard's placement stated once and unambiguously (between the post-wait `is_running` check and the exception grep, i.e. before `stop`, not after it); simulated under /bin/bash 3.2 with fake launchers; `.github/CLAUDE.md` needs no change.

## Problem

The desktop publish workflows (`publish-linux.yml:110`, `publish-windows.yml:169`, `publish-macos.yml:234`) start the
release build once before submitting it, through `.github/scripts/start_release_build.sh`. Before the refactor these
checks were inline `run:` blocks, which GitHub runs with `bash --noprofile --norc -eo pipefail`. The extracted script
is run with plain `bash … start_release_build.sh` and has no `set -e`, and its last command is the success `echo`, so
two checks now pass when there is nothing to check:

```sh
grep -q "Exception" "$LOG" && fail "The release build logged an exception."
stop
cat "$LOG"
if [ -n "$CLASS_LOAD" ]; then
  LOADED=$(wc -l < "$CLASS_LOAD")
  SHARED=$(grep -c 'source: shared objects file' "$CLASS_LOAD" || true)
  ...
```

- **Windows:** `$LOG` is `$DATA/campfire.log`, written by the app itself, not by the script. If the app stops writing it,
  `grep -q` exits 2, the `&&` is skipped, and `cat "$LOG"` fails without stopping anything. The old step failed at
  `cat`. The new one reports success, so a build that throws on start-up would still be submitted to the Microsoft Store.
- **Linux and Windows `--class-load`:** if the JVM writes no class-load file (an `-Xlog` path problem), `LOADED` and
  `SHARED` are empty and the "most classes came from the class data sharing archive" check does not fail the run. That
  defeats the twentieth review's archive check.

The reviewer reproduced it: a copy of these lines exits 0 under `bash` and 1 under `bash -e`.

## Fix

Add explicit guards rather than `set -e`. The script deliberately runs commands that fail (`is_running`, `ls`, `grep`)
inside conditions, and an explicit message says what went wrong:

- `[ -f "$LOG" ] || fail "The release build wrote no log."` as its own line between
  `is_running || fail "The release build exited on its own after it had written the library."` and
  `grep -q "Exception" "$LOG" && fail …` (that is, after the 15-second wait and *before* `stop`, so `fail` still stops
  the running app). On Linux and macOS the shell creates `$LOG` itself (`"$@" > "$LOG"`), so the guard always holds
  there and only bites on Windows, where the app writes `$DATA/campfire.log`; by this point the demo library has been
  written and 15 seconds have passed, so a healthy app has long since opened its log (`DesktopLog.install`).
- inside `if [ -n "$CLASS_LOAD" ]`, as its first line: `[ -s "$CLASS_LOAD" ] || fail "The release build logged no class loading."`.
  `fail` is still safe to call there, because `stop` ignores a process that is already gone (it prints the log a
  second time, which is acceptable). macOS passes no `--class-load`, so this guard never runs there.

Keep it compatible with bash 3.2 and Git Bash, as the header says.

## Tests

None. CI scripts are not unit-tested. (The challenger ran exactly these two inserted lines under /bin/bash 3.2 with
a fake launcher and `tasklist`/`taskkill` shims: good Linux, good Windows and macOS-style runs exit 0; a Windows run
with no app log, and runs with an empty or a missing class-load file, exit 1 with the new messages; HEAD exits 0 on all
three.) Check by hand in the scratchpad: a fake run with a missing `$LOG`, and one with
an empty class-load file, each exits 1 with the new message. A run with both files present and good still exits 0.

## Manual check

The next dispatch of `publish-linux.yml` and `publish-windows.yml` with `submit` off passes the start step and prints
the "N of M classes" line.
