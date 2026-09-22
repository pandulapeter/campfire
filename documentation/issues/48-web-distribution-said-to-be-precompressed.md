# 48 · The root CLAUDE.md says the web distribution is precompressed; precompression is off

**Severity:** docs (web) · **Area:** root `CLAUDE.md`, `app/web/build.gradle.kts` (KDoc and task description)

## Symptom
A reader takes it that the deployed site carries `.gz` / `.br` copies of its files. It does not.

## Cause
`CLAUDE.md:380-381`: "`finishWebDistribution` … writes that total into `index.html`, and precompresses everything
worth compressing." The task only writes the copies when `campfire.web.precompress` is true
(`app/web/build.gradle.kts:67`, `:100`), and `gradle.properties:33-37` sets it to false because GitHub Pages ignores the
copies and gzips on the fly. `app/web/CLAUDE.md:78-81` says so correctly. The task's own KDoc (`build.gradle.kts:69-73`,
"and then writes a precompressed copy of everything worth compressing next to it") and its `description` (`:78`,
"…and precompresses it.") read as unconditional too.

## Fix
1. `CLAUDE.md:380-381`, replace "…writes that total into `index.html`, and precompresses everything worth compressing."
   with "…writes that total into `index.html`, and precompresses the files when `campfire.web.precompress` is on —
   which it is not, since GitHub Pages ignores the copies (see `app/web`)."
2. `app/web/build.gradle.kts:70-72`, in the KDoc of `finishWebDistribution`, replace "and then writes a precompressed
   copy of / everything worth compressing next to it, for hosts that serve those." with "and then, with
   `campfire.web.precompress` on, writes a precompressed copy of everything worth compressing next to it, for hosts
   that serve those." (rewrap at the file's width).
3. `:78`, `description = "Fills in the build manifest of the web distribution and precompresses it if asked to."`

## Tests
None (docs, build script wording).

## Verify
`./gradlew :app:web:help` in a worktree is enough to show the script still configures (optional; only a string and a
comment change).

## Docs
This is the doc change.

## Touches
- `CLAUDE.md`
- `app/web/build.gradle.kts`

## Depends on
None. 47 edits another sentence of `CLAUDE.md`.
