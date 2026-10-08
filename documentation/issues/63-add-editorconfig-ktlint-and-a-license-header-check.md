# Enforce the code style with .editorconfig, a ktlint check and an MPL-header check in CI instead of only the Claude hook

**Challenged:** amended — ordered after the build-cleanup pass (the `src/main/java` → `src/main/kotlin` move of `:app:android` / `:app:desktop`, `kotlin("test")` into the convention plugin, the `android-library` alias removed), which edits the same `LibraryPlugin.kt`, root `build.gradle.kts` and `libs.versions.toml`; Spotless is applied from one `campfire-style` plugin rather than from both the convention plugins and a root `subprojects {}` block, so it stays configuration-cache and isolated-project friendly (plan 66).

**Kind:** build  ·  **Severity:** medium  ·  **Effort:** M  ·  **Risk:** low  ·  **Platforms:** all
**Files:** new `.editorconfig`; `gradle/libs.versions.toml` (the chosen plugin); `gradle/build-logic/build.gradle.kts` and
`gradle/build-logic/src/main/kotlin/com/pandulapeter/campfire/buildLogic/plugins/LibraryPlugin.kt` (or a new
`StylePlugin`) / root `build.gradle.kts`; new `.github/scripts/check_license_headers.py` +
`test_check_license_headers.py`; `.github/workflows/tests.yml`; `.claude/skills/code-style/SKILL.md`; root `CLAUDE.md`
(Conventions, the tests bullet); `gradle/build-logic/CLAUDE.md`; optionally `.git-blame-ignore-revs`
**Depends on:** none (land after the concurrent split/move lanes so a reformat does not conflict with them, and after the build-cleanup pass that moves `:app:android` / `:app:desktop` sources to `src/main/kotlin`, moves `commonTest`'s `kotlin("test")` into the convention plugin and removes the unused `android-library` alias — it edits the same `LibraryPlugin.kt`, root `build.gradle.kts` and `libs.versions.toml`)

## Problem

There is no `.editorconfig`, no ktlint/detekt/Spotless anywhere (`grep -rln "ktlint\|detekt\|spotless" gradle
build.gradle.kts settings.gradle.kts` finds nothing). The style lives in `.claude/skills/code-style/SKILL.md` and a
`PreToolUse` hook in `.claude/settings.json` that reminds Claude of it before an edit to `*.kt`, `*.kts` or
`strings.xml`; a human edit, an IDE reformat with different settings or a model that skips the reminder is caught by
nothing. The skill's mechanical rules are checkable: the MPL-2.0 header on every new file, trailing commas
("Always use trailing commas on the last element of any multi-line comma-separated list … a `)` or `]` that starts a
line of its own ends a list that wants a trailing comma"), the ~130-column width ("same width as the surrounding file
(~130 columns)"), no wildcard imports. Today all 745 `.kt` files carry the header and only 2 use a wildcard import, so
the code is close to the rules — but 4,027 lines exceed 120 columns and 514 exceed 150, so a stock ktlint
configuration would reformat a great deal. `kotlin.code.style=official` in `gradle.properties` is the only shared
setting, and it reaches the IDE only.

## Fix

1. **`.editorconfig`** at the root: `root = true`; `[*]` UTF-8, LF, final newline, trim trailing whitespace (except
   `*.md`); `[*.{kt,kts}]` `indent_size = 4`, `max_line_length = 150` (see Decision), `ij_kotlin_allow_trailing_comma =
   true`, `ij_kotlin_allow_trailing_comma_on_call_site = true`, `ij_kotlin_name_count_to_use_star_import = 999`,
   `ij_kotlin_name_count_to_use_star_import_for_members = 999`; `[*.{yml,yaml,json}]` `indent_size = 2`;
   `[*.py]` `indent_size = 4`. Check first that IntelliJ's reformat of a few representative files
   (`SongLyrics.kt`, a `build.gradle.kts`, a test) produces no diff with it; adjust until it does.
2. **License header check**: `.github/scripts/check_license_headers.py` lists tracked files with `git ls-files` and
   requires the MPL block (the text in `.claude/skills/code-style/SKILL.md`) in each `*.kt`, `*.kts`, `*.py`, `*.yml`,
   `*.js`, `*.cjs`, `*.sh`, `*.swift`, `*.xml` under `src/`, and `CLAUDE.md` (HTML-comment form), with an explicit
   allow-list for files that cannot carry one (generated baseline profiles, `*.json`, test fixtures under
   `src/desktopTest/resources/document/`, `gradle/wrapper/*`, `.idea/xcode.xml`, `.run/*.xml` — the root `CLAUDE.md`
   says the IDE rewrites those). First run it and fix or allow-list every miss in its own commit. Add a step to
   `tests.yml` before Gradle: `python3 .github/scripts/check_license_headers.py`.
3. **ktlint**, applied to every module through one small `campfire-style` plugin in `gradle/build-logic` (registered
   like the other two; `campfire-library` and `campfire-compose-library` apply it, and the plain `:app:*` and
   `:tools:screenshots` scripts add `id("campfire-style")` — not a root `subprojects {}`/`allprojects {}` block, which
   is cross-project configuration that plan 66's configuration cache tolerates but isolated projects do not, and would
   configure the library modules twice): recommended via **Spotless** (`com.diffplug.spotless`, its Gradle plugin
   artifact added to `gradle/build-logic/build.gradle.kts` like `libs.gradle` / `libs.kotlin`) with
   `kotlin { target("src/**/*.kt"); ktlint(<version>) }` and `kotlinGradle { ktlint() }`, so `./gradlew spotlessCheck` /
   `spotlessApply` cover everything and the KMP source sets are found by path rather than by source-set API — which
   also covers `:app:android` / `:app:desktop` whether their sources sit in `src/main/java` or, after the cleanup pass,
   `src/main/kotlin`. Use a Spotless version that supports the configuration cache (plan 66). Start with the rule set the code already satisfies: in
   `.editorconfig` set `ktlint_code_style = intellij_idea` and disable (`ktlint_standard_<rule> = disabled`) each rule
   whose first `spotlessCheck` run reports more than a handful of violations — expect `max-line-length`,
   `multiline-expression-wrapping`, `function-signature`, `chain-method-continuation`, `class-signature`,
   `string-template-indent`, `function-expression-body`, `backing-property-naming`, `property-naming` — and keep
   `trailing-comma-on-call-site`, `trailing-comma-on-declaration-site`, `no-wildcard-imports`, `no-unused-imports`,
   `no-trailing-spaces`, `final-newline`, `indent`. Fix the remaining violations with `spotlessApply` in one
   mechanical commit and list it in `.git-blame-ignore-revs`. Run `./gradlew desktopTest` and the compile tasks of
   plan 61 after it.
4. **CI**: add `./gradlew spotlessCheck` to `tests.yml` (in the `test` job, before `desktopTest`, so a style failure is
   reported with the tests via `--continue`).
5. **Docs**: in `.claude/skills/code-style/SKILL.md` say that `./gradlew spotlessApply` fixes the mechanical rules and
   CI checks them, keeping the prose rules (comment voice, KDoc vs `//`, `modifier` first) as the skill's job; one line
   in the root `CLAUDE.md` Conventions and in the tests bullet; `gradle/build-logic/CLAUDE.md` for the plugin.
   Leave the `.claude/settings.json` hook as it is.

Each further rule is enabled later in a commit of its own (enable + `spotlessApply`), so no single diff is large.

## Tests

`test_check_license_headers.py`: a file with the header passes, one without fails, one in the allow-list passes, the
`CLAUDE.md` HTML-comment form passes. ktlint itself needs no tests. Guarding behaviour: the full `desktopTest` run and
plan 61's compile jobs, since a formatter commit must not change code meaning.

## Manual check

Open the project in Android Studio / IntelliJ, reformat one Kotlin file and confirm `git diff` is empty (the IDE reads
`.editorconfig`). Run `./gradlew spotlessCheck` on a clean checkout: green.

## Decision

1. Tool:
   - **Recommended — Spotless + ktlint**: one Gradle task pair for check/apply, KMP-friendly by path, can also host
     the header check later (`licenseHeaderFile`) if wanted.
   - ktlint-gradle (`org.jlleitschuh.gradle.ktlint`): ktlint only, per-source-set tasks, more KMP wiring.
   - detekt with `detekt-formatting`: also finds code smells; much larger initial report and its own config.
   - Only `.editorconfig` + the header check, no formatter (cheapest; trailing commas stay unchecked).
2. Existing code:
   - **Recommended — curated rule set, no bulk reformat**: disable the rules the code does not follow today; one small
     mechanical commit for the rest; tighten rule by rule later.
   - One full `spotlessApply` with ktlint's defaults (largest diff: thousands of lines re-wrapped; blame noise
     mitigated by `.git-blame-ignore-revs`; conflicts with every open lane).
   - A ktlint baseline file freezing today's violations (no code change, but a large generated file to maintain).
3. Line length: **150** (recommended; 514 lines exceed it today and stay exempt by rule-disable until fixed), 130 (the
   skill's figure; 4,000+ lines over), or unchecked.
