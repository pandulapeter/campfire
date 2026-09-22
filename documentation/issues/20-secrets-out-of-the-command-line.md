<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->
# 20 — Signing secrets are interpolated into a shell command

## What goes wrong at release time

`${{ secrets.X }}` in a `run:` block is **textual substitution into the script before the shell parses it**. Inside
double quotes the shell then re-reads the substituted characters:

- a password containing `$` — `Tr0ub4dor$3` — has `$3` expanded to an empty positional parameter, so Gradle is handed
  `Tr0ub4dor` and the APK is signed with the wrong password, or the build fails with "keystore password was
  incorrect" and the log says nothing about why;
- a backtick opens a command substitution, so the password's contents are **executed** on the runner;
- a `"` ends the quoting and the rest of the value becomes further arguments to `gradlew`;
- a `\` is eaten as an escape.

The failure mode that matters is the first one: it does not fail, it signs with a different password than the one in
the secret store, and the first thing that notices is Play rejecting an APK whose signature does not match the
uploaded key — or, worse, a release that Play accepts under a key nobody has.

Separately, every one of these values is passed as a **command-line argument**, so it appears in the runner's process
list for the whole build, readable by anything else running on the machine. The root `CLAUDE.md` says outright that
this is the thing to avoid: "CI has no `local.properties`, so it passes the same names with
`-Pcampfire.android.keyAlias=…` or writes the file from its own secret store; the latter keeps the values out of the
process list."

## Cause

`.github/workflows/android-publish.yml:123-132`, verified at HEAD `984861e4`:

```yaml
      - name: Decode release keystore
        run: echo "${{ secrets.ANDROID_KEYSTORE_BASE64 }}" | base64 --decode > app/android/release.keystore
      - name: Build release APK
        run: |
          ./gradlew :app:android:assembleRelease \
            -Pcampfire.dropbox.appKey="${{ secrets.DROPBOX_APP_KEY }}" \
            -Pcampfire.android.keyAlias="${{ secrets.ANDROID_KEY_ALIAS }}" \
            -Pcampfire.android.keyPassword="${{ secrets.ANDROID_KEY_PASSWORD }}" \
            -Pcampfire.android.keystoreFile=release.keystore \
            -Pcampfire.android.keystorePassword="${{ secrets.ANDROID_KEYSTORE_PASSWORD }}"
```

Four secrets, all inside double quotes, all on the command line. The same pattern for the Dropbox key in
`desktop-publish.yml:109` and `web-publish.yml:52`.

`ios-publish.yml:51-54` already does it right, and is the model to follow:

```yaml
      - name: Write local.properties
        env:
          DROPBOX_APP_KEY: ${{ secrets.DROPBOX_APP_KEY }}
        run: echo "campfire.dropbox.appKey=$DROPBOX_APP_KEY" > local.properties
```

## The change

Every secret reaches the script through `env:` and the script writes `local.properties`, which
`settings.gradle.kts:73-80` already loads and lays over every project — so the build files keep reading an ordinary
property and never learn where the value came from.

**One thing has to be got right:** `local.properties` is read by `java.util.Properties.load`, where a backslash in a
**value** is an escape character. A password containing `\` would be mangled by the very file that fixes the shell
problem, so every value is written with its backslashes doubled. `:`, `=` and `#` are literal inside a value and need
nothing; leading whitespace is stripped, which no password should have.

### `android-publish.yml`

Replace the two steps (lines 123-132) with:

```yaml
      - name: Decode release keystore
        env:
          ANDROID_KEYSTORE_BASE64: ${{ secrets.ANDROID_KEYSTORE_BASE64 }}
        run: printf '%s' "$ANDROID_KEYSTORE_BASE64" | base64 --decode > app/android/release.keystore
      # Through local.properties rather than -P: a value on the command line is in the runner's process list for the
      # whole build, and a secret interpolated into the script is re-read by the shell, so a password holding a $, a
      # backtick or a quote would sign the APK with something other than what is in the secret store. Backslashes are
      # doubled because java.util.Properties treats one as an escape inside a value.
      - name: Build release APK
        env:
          DROPBOX_APP_KEY: ${{ secrets.DROPBOX_APP_KEY }}
          ANDROID_KEY_ALIAS: ${{ secrets.ANDROID_KEY_ALIAS }}
          ANDROID_KEY_PASSWORD: ${{ secrets.ANDROID_KEY_PASSWORD }}
          ANDROID_KEYSTORE_PASSWORD: ${{ secrets.ANDROID_KEYSTORE_PASSWORD }}
        run: |
          escape() { printf '%s' "${1//\\/\\\\}"; }
          {
            printf 'campfire.dropbox.appKey=%s\n' "$(escape "$DROPBOX_APP_KEY")"
            printf 'campfire.android.keyAlias=%s\n' "$(escape "$ANDROID_KEY_ALIAS")"
            printf 'campfire.android.keyPassword=%s\n' "$(escape "$ANDROID_KEY_PASSWORD")"
            printf 'campfire.android.keystoreFile=release.keystore\n'
            printf 'campfire.android.keystorePassword=%s\n' "$(escape "$ANDROID_KEYSTORE_PASSWORD")"
          } > local.properties
          ./gradlew :app:android:assembleRelease
```

`${1//\\/\\\\}` is a bash parameter expansion; `ubuntu-latest` runs `bash` by default, so no `shell:` key is needed
here.

### `web-publish.yml`

Replace lines 49-52 with:

```yaml
      # Through local.properties rather than -P, for the reason android-publish.yml gives: a -P value is in the
      # runner's process list, and a secret interpolated into the script is re-read by the shell.
      - name: Build web distribution
        env:
          DROPBOX_APP_KEY: ${{ secrets.DROPBOX_APP_KEY }}
        run: |
          printf 'campfire.dropbox.appKey=%s\n' "${DROPBOX_APP_KEY//\\/\\\\}" > local.properties
          ./gradlew :app:web:wasmJsBrowserDistribution
```

### `desktop-publish.yml`

Replace lines 106-110 with (keeping the long comment above the step unchanged):

```yaml
      - name: Build the installer
        env:
          DROPBOX_APP_KEY: ${{ secrets.DROPBOX_APP_KEY }}
        run: |
          printf 'campfire.dropbox.appKey=%s\n' "${DROPBOX_APP_KEY//\\/\\\\}" > local.properties
          ./gradlew :app:desktop:createReleaseDistributable :app:desktop:${{ matrix.task }} \
            -Pcampfire.desktop.distribution=${{ matrix.distribution }}
          ./gradlew --stop
```

The job's `defaults.run.shell: bash` (lines 76-79) means this is Git Bash on the Windows runner, where the same
parameter expansion works and `local.properties` is written into the checkout root — which is `settingsDir`, where
`settings.gradle.kts` looks for it. `campfire.desktop.distribution` (plan 15) stays a `-P`: it is not a secret, and
keeping it on the command line makes each run's log say which distribution it built.

### `ios-publish.yml`

Already correct in shape; add the backslash doubling and switch `echo` for `printf`, since `echo` interprets escapes
in some shells:

```yaml
        run: printf 'campfire.dropbox.appKey=%s\n' "${DROPBOX_APP_KEY//\\/\\\\}" > local.properties
```

The `macos-26` runner's default shell is bash.

### Not done

`local.properties` is not deleted afterwards. The runners are ephemeral, and the Gradle cache that
`actions/setup-java` saves holds `~/.gradle`, not the checkout.

## Tests

None; this is workflow configuration. Run the standard suite to confirm nothing else moved:

```bash
./gradlew :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest
```

## Verification

The escaping can be proven locally, with no workflow at all — this is the part worth being sure of:

```bash
# A password with every character that breaks the old form.
export P='Tr0ub4dor$3`whoami`"\x'
printf 'campfire.android.keyPassword=%s\n' "${P//\\/\\\\}" > /tmp/local.properties
python3 - <<'PY'
import javaproperties  # or: read it with Gradle, below
PY
# Simplest check without extra packages: let Gradle read it back.
cd /path/to/Campfire && cp /tmp/local.properties . && ./gradlew -q :app:desktop:properties | grep keyPassword
```

The value Gradle prints must be character-for-character what `$P` holds. Repeat with the old `-P"${{ … }}"` form to
see it differ.

The rest **can only be proven by dispatching the workflow**:

1. Dispatch `Publish Android` by hand with no `release_tag`, so it builds the branch and uploads to Play. The
   `Build release APK` step must succeed and the APK must be signed with the release key:

   ```bash
   apksigner verify --print-certs campfire-<version>-android.apk
   ```

   The SHA-256 of the certificate must equal the one Play shows under App integrity → App signing key.
2. Check the step's log: the `-P…` arguments must be gone from the Gradle invocation, and GitHub must not have had to
   mask anything in that line (a masked secret prints as `***`; a value that now never reaches the log prints
   nothing at all).
3. Dispatch `Publish Web` and `Publish Desktop` once each and confirm sync is offered in the built app — a
   `local.properties` written wrong would show up as "this build has no sync credentials" in Settings → Library,
   which is the same symptom plan 19's check exists for.

Needs write access to dispatch workflows; the Play certificate check needs the Play Console. No Mac or Windows PC is
needed beyond what the runners provide.

## Docs

Root `CLAUDE.md`, the Build section. This sentence offers both forms and should now say which one the workflows use:

> CI has no `local.properties`, so it passes the same names with `-Pcampfire.android.keyAlias=…` or writes the file
> from its own secret store; the latter keeps the values out of the process list.

becomes

> CI has no `local.properties`, so every workflow writes one from its own secret store, with each value reaching the
> script through `env:` rather than being interpolated into it: a `-P` puts the value in the runner's process list,
> and a secret substituted into a `run:` block is re-read by the shell, so a password holding a `$`, a backtick or a
> quote would sign with something other than what is stored. Backslashes are doubled on the way in, since
> `java.util.Properties` reads one as an escape. What stays on the command line is the things that are not secret —
> `campfire.desktop.distribution`, which is worth having in the log.

## Files touched

- `.github/workflows/android-publish.yml`
- `.github/workflows/desktop-publish.yml`
- `.github/workflows/web-publish.yml`
- `.github/workflows/ios-publish.yml`
- `CLAUDE.md`

## Depends on

Nothing. Overlaps plan 19 (the same steps gain the empty-key check) and plan 15 (the desktop build step gains
`-Pcampfire.desktop.distribution`); the quoted replacements above already show all three together, so whichever lands
last only has to keep the other two's lines.

## Rules

- Load the `code-style` skill before the first edit.
- No Kotlin, no strings, no `commonMain` changes here.
- Everything configurable stays a `campfire.*` Gradle property read with `project.property`; `local.properties`
  overriding it is the mechanism that already exists, and no build file learns that CI is writing the file.
