# Render the Windows release start check in software, as the Linux one does, so that Skiko's renderer fallback is not taken for a crash

**Kind:** ci · **Severity:** medium · **Platforms:** Windows (Microsoft Store)
**Files:** `.github/workflows/publish-windows.yml`, `app/desktop/CLAUDE.md` (only if it describes the start check's environment)

## Problem

`92a26fb1c` (2026-10-06, after 4.6.1 was published) added a log check to the "Start the release build once" step of
`.github/workflows/publish-windows.yml`:

```bash
          export APPDATA="$(cygpath -w "$HOME_DIRECTORY/AppData/Roaming")"
          "$APP" &
...
          grep -q "Exception" "$LOG" && fail "The release build logged an exception."
```

The check has never run on a real runner (the last `publish-windows.yml` run is from 2026-09-29, and 4.6.1's
`publish-all` predates the commit). `windows-latest` is a VM with no GPU. Skiko tries Direct3D / OpenGL first and, when
that fails, logs the failed attempt with its exception ("Fallback to next API", `RenderException`) before falling back
to software. `DesktopLog` copies stdout/stderr into `campfire.log`, the file the step greps. The Linux leg already met
exactly this and avoids it (`.github/workflows/publish-linux.yml`):

```bash
          # Xvfb has no OpenGL. Skiko would fall back to software rendering on its own, but it logs the failed attempt
          # as a RenderException first, which the check below would take for a crash.
          export SKIKO_RENDER_API=SOFTWARE_FAST
```

If the runner's adapter fails the same way, the 4.7.0 release's Windows leg fails after the build and before
`microsoft_store_submission.py`: nothing reaches the Microsoft Store (the other stores are unaffected).

## Fix

In the Windows step, next to the other `export`s and before `"$APP" &`, add the same variable with a comment in the
Linux one's voice:

```bash
          # The runner has no GPU. Skiko would fall back to software rendering on its own, but it logs the failed
          # attempt as a RenderException first, which the check below would take for a crash.
          export SKIKO_RENDER_API=SOFTWARE_FAST
```

Skiko reads the variable from the environment of the process (`SKIKO_RENDER_API`), which the launcher passes on to the
JVM, as on Linux. Nothing else changes; the software renderer is what a GPU-less runner ends up with anyway.

## Tests

None: a workflow step. Check the YAML still parses (`python3 -c "import yaml,sys; yaml.safe_load(open(sys.argv[1]))" .github/workflows/publish-windows.yml`).

## Manual check

Before publishing 4.7.0: dispatch `publish-windows.yml` by hand with `submit` off (still owed from the sixteenth
review, plan 62) and confirm the start step passes and the printed `campfire.log` holds no exception. Delete the draft
submission it leaves in Partner Center, or let the release's run reuse it.
