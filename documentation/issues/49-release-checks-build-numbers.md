# 49 — A release that forgot to raise the build numbers fails only at the Play upload, after the APK is attached

**Severity:** release safety (CI; no user-facing build is wrong, but a release half happens) · **Area:**
`.github/workflows/release.yml`; also `settings.gradle.kts` (non-ASCII in `local.properties`)

**Read, not run.** Found by reading the workflows at HEAD; no workflow was run. The "Verification" section rehearses
both parts on a fork, the way `documentation/testing/08-release-and-stores.md` §2 already does for REL-011.

## What the user sees

The user here is the person publishing a release.

1. They bump `campfire.versionName` but forget `campfire.android.versionCode` (or `campfire.ios.buildNumber`), tag and
   publish. `release.yml`'s `prepare` job passes — it checks only the version name — and all four builds start. The
   web build deploys; the desktop installers and the `.ipa` are attached; the Android job builds, signs and **attaches
   the APK to the release** and only then fails, at the Play upload, because Play refuses a version code it has seen.
   The release now carries an APK whose version code is the previous release's, the website is already on the new
   version, and the fix is a new commit and a new tag. An unraised iOS build number fails nowhere today at all — the
   `.ipa` is unsigned — and would surface only as an App Store Connect rejection once the TestFlight upload exists.
2. (Secondary, confirmed.) A signing secret or a `local.properties` value with a character outside ASCII — a keystore
   password with an `é`, or `campfire.android.keystoreFile` pointing into a home folder like `/Users/Péter/…` — is
   read back garbled, and the build fails to open the keystore ("Keystore was tampered with, or password was
   incorrect"). It fails loudly, not silently, but it fails on a value that is right.

## Cause

**1.** `.github/workflows/release.yml:45-56`:

```yaml
      # A tag on a commit that still carries the previous version would build, sign and submit that version again
      # under a new name, and the stores would be the first to notice.
      - name: Check the tag against the version
        env:
          RELEASE_TAG: ${{ github.event.release.tag_name }}
        run: |
          VERSION_NAME=$(sed -n 's/^campfire\.versionName=//p' gradle.properties)
          if [ "${RELEASE_TAG#v}" != "$VERSION_NAME" ]; then
            echo "The release is tagged $RELEASE_TAG, but gradle.properties at that tag says campfire.versionName=$VERSION_NAME" >&2
            exit 1
          fi
          echo "Releasing version $VERSION_NAME"
```

Nothing else in `prepare` looks at the counters. `gradle.properties:17-20` keeps them per store ("the build counters
below are per-store and advance independently"), and the history shows them moving by hand with each release
(`4.2.1` → 31/31, `4.2.2` → 32/32, `4.2.3` → 33/33, HEAD `4.3.0` → 34/34). `android-publish.yml:165-174` attaches the
APK **before** the Play upload on purpose ("so that a release Play turns down still has its APK"), which is right for
a rejection on review grounds and wrong for this one. `documentation/publishing/ios-app-store.md:42-43` carries it as a
manual checkbox: "Check that `campfire.ios.buildNumber` in `gradle.properties` is raised with every release."

**2.** `settings.gradle.kts:74-77`:

```kotlin
val localProperties = java.util.Properties()
java.io.File(settingsDir, "local.properties").let { file ->
    if (file.exists()) file.inputStream().use(localProperties::load)
}
```

`Properties.load(InputStream)` decodes ISO-8859-1. The workflows write `local.properties` with `printf '%s'` of the
secret's UTF-8 bytes (`android-publish.yml:156-161`), so `é` (`C3 A9`) is read back as `Ã©`. Locally the same
happens to anything typed into the file in a UTF-8 editor.

## The change

Invoke the **`code-style`** skill before the first edit.

### 1. Compare the counters with the previous release in `prepare`

Add a step to `release.yml`'s `prepare` job, after "Check the tag against the version":

```yaml
      # Play refuses a version code it has seen and App Store Connect a build number, but Play says so only after the
      # APK has been built, signed and attached to the release and the other three builds have gone out. The previous
      # release is the last one published, whatever its version: Play wants the code higher than every one it has had,
      # so a fix to an older line is compared with the newest release too. Pre-releases reach no store and are left out.
      - name: Check the build numbers against the previous release
        env:
          GH_TOKEN: ${{ github.token }}
          RELEASE_TAG: ${{ github.event.release.tag_name }}
        run: |
          PREVIOUS_TAG=$(gh api "repos/$GITHUB_REPOSITORY/releases?per_page=100" \
            | jq -r --arg tag "$RELEASE_TAG" '[.[] | select((.draft | not) and (.prerelease | not) and .tag_name != $tag)] | sort_by(.published_at) | last | .tag_name // empty')
          if [ -z "$PREVIOUS_TAG" ]; then
            echo "There is no earlier release to compare the build numbers with."
            exit 0
          fi
          git fetch --depth=1 origin "refs/tags/$PREVIOUS_TAG:refs/tags/$PREVIOUS_TAG"
          git show "refs/tags/$PREVIOUS_TAG:gradle.properties" > "$RUNNER_TEMP/previous.properties"
          FAILED=0
          for KEY in campfire.android.versionCode campfire.ios.buildNumber; do
            PATTERN="s/^${KEY//./\\.}=//p"
            CURRENT=$(sed -n "$PATTERN" gradle.properties | tr -d '\r')
            PREVIOUS=$(sed -n "$PATTERN" "$RUNNER_TEMP/previous.properties" | tr -d '\r')
            if ! [[ "$CURRENT" =~ ^[0-9]+$ && "$PREVIOUS" =~ ^[0-9]+$ ]]; then
              echo "::error::Could not read $KEY as a number here ($CURRENT) or at $PREVIOUS_TAG ($PREVIOUS)." >&2
              FAILED=1
            elif [ "$CURRENT" -le "$PREVIOUS" ]; then
              echo "::error::$KEY is $CURRENT, but the previous release ($PREVIOUS_TAG) already had $PREVIOUS. Raise it in gradle.properties and tag again." >&2
              FAILED=1
            else
              echo "$KEY: $PREVIOUS at $PREVIOUS_TAG, $CURRENT now"
            fi
          done
          exit $FAILED
```

Notes for whoever implements it:

- `prepare` already has `contents: write` (`release.yml:32-33`), which covers reading releases and fetching a tag.
  `gh` and `jq` are on `ubuntu-latest`. The REST listing is used rather than `gh release list --json`, whose flags
  have changed between `gh` versions; the draft and pre-release fields have been in the REST answer all along.
- The checkout is shallow (`actions/checkout@v5`'s default), so the previous tag is fetched on its own, one commit
  deep — `git show` needs nothing more.
- A fork with no releases yet (the REL-010/REL-011 rehearsal) has no previous release and passes, as it should.
- It is deliberately conservative: a previous release whose Android job failed before Play still counts. Raising a
  counter that no store saw costs nothing; guessing which store saw what would need each store's API.
- Only `release.yml` gets it. The hand-dispatched workflows are for repeating half a release, where the numbers are
  meant to be the same as the release's; checking them there would get in the way of exactly that.
- HEAD passes it today: `4.3.0` has 34/34 against `4.2.3`'s 33/33.

### 2. Read `local.properties` as UTF-8

`settings.gradle.kts:76`:

```kotlin
    // UTF-8 rather than the ISO-8859-1 Properties.load(InputStream) assumes: the workflows write the file with printf
    // and people write it in their editor, both in UTF-8, and a password or a path with an accent would otherwise be
    // read as something else. A file Properties.store wrote (Android Studio's sdk.dir) escapes non-ASCII as \uXXXX,
    // so it reads the same either way.
    if (file.exists()) file.reader(Charsets.UTF_8).use(localProperties::load)
```

The one thing that would read differently is a file somebody saved in Latin-1 with raw accented bytes in it, which no
tool in this project writes.

## Tests

- **No unit test is possible** for either: a workflow step and a settings script.
- Local, for part 2: REL-001 extended as below.
- For part 1, the shell can be dry-run locally against the real repository by setting `RELEASE_TAG=4.3.0`,
  `GITHUB_REPOSITORY=pandulapeter/campfire`, `RUNNER_TEMP=$(mktemp -d)`, `GH_TOKEN=$(gh auth token)` and pasting the
  `run:` block into `bash` (it only reads, apart from fetching one tag): it must print
  `campfire.android.versionCode: 33 at 4.2.3, 34 now` and the same for iOS. Then edit `gradle.properties` to 33 in a
  scratch copy and run it again: it must fail naming the key.

## Verification

1. **Fork rehearsal** (`08-release-and-stores.md` §2, with the dummy key): publish a release `X` in the fork; then
   bump only `campfire.versionName`, tag, publish release `Y`. **Expected:** `Y` fails in "Check the build numbers
   against the previous release" naming both keys and `X`; no other job starts. Raise both counters, tag `Y2`: it
   passes that step.
2. In the fork, publish a pre-release with a higher counter, then a release with the counter between the two:
   passes (the pre-release is not a previous release).
3. **Part 2:** REL-001 with a password containing `é`, and `campfire.android.keystoreFile` set to an absolute path
   through a folder named `Péter` holding a copy of the debug keystore: `./gradlew :app:android:assembleRelease`
   signs; before the change it fails to open the keystore.

## Docs

- Root `CLAUDE.md`, Build section, lines 272-274, currently "…by checking that the tag is the `campfire.versionName`
  of the commit it is on — a tag on a commit that still carries the last version would submit that version again
  under a new name — and then calling the four workflows below side by side." → "…by checking that the tag is the
  `campfire.versionName` of the commit it is on — a tag on a commit that still carries the last version would submit
  that version again under a new name — and that `campfire.android.versionCode` and `campfire.ios.buildNumber` are
  higher than the last published release's, since Play would refuse a used one only after the APK had been attached
  to the release, and then calling the four workflows below side by side." Line 259, after "Backslashes are
  doubled on the way in, since `java.util.Properties` reads one as an escape.", add: "The file is read as UTF-8, so a
  value outside ASCII survives as well."
- `documentation/publishing/ios-app-store.md:42-43`: the build-number item → tick it:
  `- [x] \`campfire.ios.buildNumber\` has to be raised with every release; \`release.yml\` refuses a release whose number is not higher than the previous release's, since App Store Connect refuses a build number it has seen.`
- `documentation/testing/08-release-and-stores.md`:
  - REL-001 (lines 41-49): add `é` to the example password, and a second step with the keystore path through a
    folder named `Péter`. Mark 🆕.
  - After REL-011 (lines 102-107), add **REL-017** 🆕 (P0) "A release that did not raise the build numbers is
    rejected" with verification steps 1 and 2 above.
- `.claude/skills/release-notes/SKILL.md:21` says the counters "move with it, and should match" — still true; no
  change.

## Files touched

- `.github/workflows/release.yml`
- `settings.gradle.kts`
- `CLAUDE.md`
- `documentation/publishing/ios-app-store.md`
- `documentation/testing/08-release-and-stores.md`

## Depends on

Nothing. Plan 48 also edits `documentation/publishing/ios-app-store.md` (lines 13-14, 34-39, 94) — different lines,
either order.
