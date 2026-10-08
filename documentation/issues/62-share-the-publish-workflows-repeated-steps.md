# Give the publish workflows' repeated steps one implementation each: a JDK action, a secrets check, a local.properties writer and a desktop start check

**Challenged:** amended — steps 1 and 2 must be one commit (a `git checkout FETCH_HEAD -- .github/actions` against a workflow commit that has no `.github/actions` fails with "pathspec did not match" and would break every publish run in between, and reverting step 2 alone would do the same); each later step's fetch now names only paths that exist; the writer pins UTF-8 and LF on the Windows runner; the start script stays bash-3.2-compatible for macOS.

**Kind:** ci  ·  **Severity:** medium  ·  **Effort:** M  ·  **Risk:** high  ·  **Platforms:** all (release pipeline)
**Files:** `.github/workflows/publish-android.yml`, `publish-ios.yml`, `publish-linux.yml`, `publish-macos.yml`,
`publish-web.yml`, `publish-windows.yml`, `publish-all.yml`, `tests.yml`; new `.github/actions/setup-jdk/action.yml`;
new `.github/scripts/write_local_properties.py` + `test_write_local_properties.py`; new
`.github/scripts/require_secrets.py` (+ test); new `.github/scripts/start_release_build.sh`; root `CLAUDE.md` (Build →
"CI has no `local.properties`…" and the `publish-*` bullets) or `.github/CLAUDE.md` if plan 60 landed
**Depends on:** none

## Problem

The same steps are written out in each workflow, sometimes in two idioms, in the one part of the repo that can only be
exercised by publishing:

- **JDK setup ×7** (`tests.yml` and the six `publish-*.yml`): the identical `actions/setup-java@v5` step with
  `distribution: 'jetbrains'`, `java-version: 21`, `cache: 'gradle'`, the `GITHUB_TOKEN: ${{ github.token }}` env and
  the same three-line comment about the JetBrains runtime lookup.
- **`DROPBOX_APP_KEY` check ×7 in two styles**: `publish-all/android/linux/web.yml`
  ```bash
  if [ -z "$DROPBOX_APP_KEY" ]; then
    echo "::error::DROPBOX_APP_KEY is empty or not visible to this workflow. The published app would have no sync provider at all." >&2
  ```
  versus `publish-ios/macos/windows.yml`, which check several secrets at once with a `problem NAME "reason"` helper:
  `[ -n "$DROPBOX_APP_KEY" ] || problem DROPBOX_APP_KEY "empty or not visible to this workflow"`.
- **`local.properties` written ×6 in two idioms**: `publish-android.yml` defines
  `escape() { printf '%s' "${1//\\/\\\\}"; }` and pipes a `{ … } > local.properties` block; `publish-ios/linux/macos/
  web/windows.yml` inline `"${DROPBOX_APP_KEY//\\/\\\\}"` per line, iOS and macOS appending `campfire.buildNumber`
  with `>>`. The escaping rule (backslashes doubled, UTF-8) is documented once in the root `CLAUDE.md` but implemented
  six times, untested.
- **"Take the store scripts from the workflow's commit" ×3** (`publish-ios/macos/windows.yml`):
  `git fetch --depth 1 origin "$WORKFLOW_SHA"` + `git checkout FETCH_HEAD -- .github/scripts`.
- **The desktop start check ×3** (`publish-linux.yml` "Start the release build once", ~50 lines; `publish-windows.yml`
  ~50 lines; `publish-macos.yml` ~45 lines plus the ad-hoc re-signing): the same 120 × 1 s wait for
  `preferences/preferences.json` and `library/songs/*.cho`, the 15 s survival wait, the `grep -q "Exception"` check,
  and on Linux/Windows the identical class-data-sharing threshold
  `[ $((SHARED * 100)) -ge $((LOADED * 80)) ]`. A change of policy (the timeout, the 80 %) must be made three times.

## Fix

**Pitfall that shapes every step:** a local action (`uses: ./.github/actions/x`) and a script under `.github/scripts`
are resolved from the *workspace*, which every publish workflow checks out at `inputs.release_tag`. A hand-dispatched
run against a tag older than this change would not find them. So step 1 must land first, and every later step relies
on it. The reverse holds too: `git checkout FETCH_HEAD -- <path>` fails ("pathspec did not match") when `<path>` does
not exist in the workflow's commit, so the fetch may only name `.github/actions` from the commit that creates it —
**steps 1 and 2 land as one commit**, and neither is reverted without the other. (On `workflow_call` from
`publish-all.yml`, `github.workflow_sha` is the caller's commit, the release tag's, as the existing iOS/macOS/Windows
step already relies on.)

1. **Fetch the shared files from the workflow's commit in all six publish workflows.** Extend the existing "Take the
   store scripts from the workflow's commit" step to `git checkout FETCH_HEAD -- .github/scripts .github/actions` and
   add the same step (right after `actions/checkout`) to `publish-android.yml`, `publish-linux.yml` and
   `publish-web.yml`. `publish-web.yml` checks out the branch, not a tag — the step is harmless there. This step is the
   one piece that stays written out in each file (a step cannot fetch the action it is defined in).
2. **`.github/actions/setup-jdk/action.yml`** — a composite action holding the setup-java step (with
   `env: GITHUB_TOKEN: ${{ github.token }}`, valid in composite actions) and its comment. Replace the seven copies with
   `uses: ./.github/actions/setup-jdk`. In `tests.yml` the workspace is the workflow's own commit, so no fetch is needed.
   (If plan 65 lands first, the action also runs `gradle/actions/setup-gradle` and drops `cache: 'gradle'`.)
3. **`.github/scripts/require_secrets.py NAME [NAME…]`** — reads each named environment variable and prints one
   `::error::NAME is empty or not visible to this workflow.` per empty one, exiting 1 if any; an optional
   `--why "…"` adds the consequence sentence. Use it for the seven Dropbox checks (`publish-all.yml` included) and fold
   the other "empty" checks of `publish-ios/macos/windows.yml` into the same call; their *format* checks (the
   `BEGIN PRIVATE KEY` greps on `ASC_PRIVATE_KEY` and `APPLE_SIGNING_KEY`) stay as they are, after it. Keep the
   step's position (before the build) in every workflow. Python because it runs as `python3` on Linux/macOS and
   `python` on the Windows runner (as `microsoft_store_submission.py` already does there).
4. **`.github/scripts/write_local_properties.py [--append] key=ENV_NAME …`** — writes `key=<value of $ENV_NAME>` lines
   into `local.properties` in UTF-8, doubling backslashes (the only escape the root `CLAUDE.md` asks for), `--append`
   for the iOS/macOS build-number line; a literal value is passed as `key=:literal` (for
   `campfire.android.keystoreFile=release.keystore`). Secrets still reach it only through `env:`. Open the file with
   `encoding="utf-8", newline="\n"`, so the Windows runner writes the same bytes the `printf` lines wrote rather than
   CRLF in the platform encoding. Replace the six
   writers. Keep `publish-windows.yml`'s final `rm -f local.properties` and `publish-macos.yml`'s cleanup.
5. **`.github/scripts/start_release_build.sh`** — the shared part of the start check, called with
   `--launcher PATH --data DIR --log FILE [--class-load FILE --min-shared-percent 80] [--windows]`: starts the
   launcher (stdout/stderr to the log, except on Windows where the app writes `campfire.log` into its data directory and
   the launcher is stopped with `taskkill` / probed with `tasklist`, as today), waits up to 120 s for
   `preferences/preferences.json` and a `library/songs/*.cho`, waits 15 s more, fails on a dead process or `Exception`
   in the log, stops it, prints the log and, when `--class-load` is given, applies the shared-class threshold. Each
   workflow keeps its own environment preparation before the call (Linux: `xvfb-run`, `JAVA_TOOL_OPTIONS`,
   `SKIKO_RENDER_API`; Windows: `cygpath`, `APPDATA`; macOS: the copy re-signed ad hoc and the sandbox container path) —
   on Linux pass the launcher as `xvfb-run --auto-servernum <app>` via a `--` argument list. macOS does not pass
   `--class-load` (it records no archive, see `app/desktop/CLAUDE.md`). Error messages stay word for word. Call it as
   `bash .github/scripts/start_release_build.sh …` and keep it to what bash 3.2 (macOS' `/bin/bash`) and Git Bash on
   Windows both run — no associative arrays, `mapfile` or `${var,,}` — since the steps it replaces were only ever run
   by each runner's own `shell: bash`.
6. Update the root `CLAUDE.md` (or `.github/CLAUDE.md`): "CI has no `local.properties`, so every workflow writes one from
   its own secret store" — name the script; the `publish-linux.yml`/`-windows.yml`/`-macos.yml` sentences about
   starting the image — name the script.

Land steps 1+2 as one commit and 3, 4, 5 as one commit each; 3–5 are independently revertible (`.github/scripts`
already exists, so the fetch never fails on their account).

## Tests

- `test_write_local_properties.py` (unittest, run by the existing `python3 -m unittest discover -s .github/scripts`
  step in `tests.yml`): a value with `\`, `$`, a backtick, `"`, `'`, `é` and a space round-trips through
  `java.util.Properties`-style reading (assert the written line is `key=a\\\\b` for `a\b`, UTF-8 bytes for `é`);
  `--append` keeps existing lines; `key=:literal` writes the literal; an unset env var is an error.
- `test_require_secrets.py`: one empty and one set variable → exit 1 and exactly one `::error::` line naming the empty one.
- The start script has no unit test; it is exercised by the runs below.

## Manual check

The publish workflows cannot be run without publishing, so verify in this order:

1. `publish-linux.yml`, dispatched by hand with `release_tag` = the latest release tag: it rebuilds and re-attaches the
   same `.deb`s with `--clobber`, which is harmless. Confirms steps 1–5 on Linux, including the start check.
2. At the **next real release**, put `<!-- play-store submit: false -->`, `<!-- app-store submit: false -->`,
   `<!-- mac-app-store submit: false -->` and `<!-- microsoft-store submit: false -->` in its description: every store
   build, the Windows and macOS start checks and the new `local.properties` writer run for real, nothing is sent for
   review, and each store is submitted by hand from its console once the runs are green (the documented flow for a
   release with new screenshots).
3. `publish-web.yml` deploys the branch head and has no dry run: do not dispatch it to test; its first run is that
   release.

Do not dispatch Android/iOS/macOS/Windows against an already-published tag to test: Play refuses a used version code,
and the Apple/Windows runs would create drafts against a live version.
