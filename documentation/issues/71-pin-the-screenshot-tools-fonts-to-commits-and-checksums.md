# Pin the screenshot tool's downloaded fonts to commit SHAs and verify their SHA-256

**Challenged:** amended — step 1 now pins to the commits whose files match the fonts the last renders used (the `build/fonts` copies on the author's machine, which may predate today's `main`), rather than assuming today's `main` is what was rendered with; the action keeps to locals so it stays configuration-cache safe.

**Kind:** build  ·  **Severity:** low  ·  **Effort:** S  ·  **Risk:** low  ·  **Platforms:** tools (store screenshots)
**Files:** `tools/screenshots/build.gradle.kts` (`downloadFonts`); `tools/screenshots/CLAUDE.md` (the Fonts sentence:
"downloads the three into `build/fonts`")
**Depends on:** none (if plan 66 lands first, keep the task's action free of script-level references)

## Problem

```kotlin
val downloadFonts by tasks.registering {
    val fontDirectory = fonts
    outputs.dir(fontDirectory)
    doLast {
        val directory = fontDirectory.get().asFile.apply { mkdirs() }
        fun download(url: String) = URI(url).toURL().openStream().use { it.readBytes() }
        File(directory, "Roboto.ttf").writeBytes(download("https://github.com/google/fonts/raw/main/ofl/roboto/Roboto%5Bwdth,wght%5D.ttf"))
        File(directory, "DroidSansMono.ttf").writeBytes(
            download("https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/data/fonts/DroidSansMono.ttf"),
        )
        File(directory, "OpenSans.ttf").writeBytes(download("https://github.com/google/fonts/raw/main/ofl/opensans/OpenSans%5Bwdth,wght%5D.ttf"))
    }
}
```

All three URLs follow moving `main` branches. A font update upstream silently changes the metrics every Android and
Windows store screenshot is laid out with (text wraps differently between two retakes for no reason in the repo); a
renamed or deleted file breaks `:tools:screenshots:run` with an opaque `FileNotFoundException`; and nothing checks
that what arrived is the font rather than an HTML error page. Because the task declares an output and no inputs, a
machine that once downloaded the fonts keeps them forever, so two machines can render with different fonts.

## Fix

1. For each font, pick the current commit of its repository (`google/fonts` for Roboto and Open Sans,
   `aosp-mirror/platform_frameworks_base` for DroidSansMono — check with `git ls-remote https://github.com/google/fonts
   refs/heads/main` etc.) and replace `main` in the URL with that 40-char SHA (`raw.githubusercontent.com/<owner>/<repo>/<sha>/<path>`).
   Download each once and record its SHA-256. If `tools/screenshots/build/fonts` still holds the files the last
   renders were made with (the task never re-downloads, so they may be older than today's `main`), compare: where a
   SHA-256 differs, pin instead to the newest commit of that file whose blob matches the local copy
   (`git log --format=%H -- <path>` in a clone, `git hash-object` on the local file against `git rev-parse <sha>:<path>`),
   so the next retake lays text out as the published images do. Say in the commit which commits were chosen and why.
2. Declare them as data at the top of the task:
   ```kotlin
   val fontSources = mapOf(
       "Roboto.ttf" to ("https://raw.githubusercontent.com/google/fonts/<sha>/ofl/roboto/Roboto%5Bwdth,wght%5D.ttf" to "<sha256>"),
       …
   )
   ```
   register them as `inputs.property("fonts", fontSources.toString())` so a changed pin re-downloads (declare
   `fontSources` inside the `registering { }` block, or copy it into a local there, so the action captures a local
   rather than the script), and in the action
   compute the SHA-256 of each download (`java.security.MessageDigest`) and `throw GradleException("<name> from <url>
   has SHA-256 <actual>, expected <expected>")` on a mismatch, before writing the file. Keep the KDoc on the task
   (which fonts and why), adding a sentence on why they are pinned.
3. `tools/screenshots/CLAUDE.md`: say the fonts are pinned to commits and checked, and that updating one means a new
   SHA and checksum in `build.gradle.kts`.

## Tests

None (build script; the checksum check is itself the test). Run `./gradlew :tools:screenshots:downloadFonts
--rerun-tasks` and confirm the three files land in `tools/screenshots/build/fonts`; change one checksum digit and
confirm the task fails with the message; restore it.

## Manual check

Run `./gradlew :tools:screenshots:run` and compare one Android and one Windows render with the previous renders in
`tools/screenshots/renders` (or the images in `documentation/screenshots`): identical text layout, assuming the pinned
commits are the ones `main` pointed to before.
