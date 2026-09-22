<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->
# 17 — The packaged desktop runtime is missing `jdk.localedata` and `jdk.accessibility`

## What the user sees

**The language names are English in every installed desktop build.** Campfire ships no language names at all — the
root `CLAUDE.md` is explicit: "The **names are never shipped**: the app carries a list of codes and nothing else, and
asks the platform what each is called in the language the app is set to". On desktop the platform it asks is
`java.util.Locale`. A packaged runtime without `jdk.localedata` has only the root locale's data, so a Hungarian user
sees the Songs screen's language filter chips, the language picker and the song details header chips reading
*German*, *English*, *Romanian* where the app itself is otherwise entirely in Hungarian. `./gradlew :app:desktop:run`
is fine, because it runs on the whole JDK — the breakage exists only in the `.dmg`, `.msi` and `.deb` a user
installs, which is exactly the class of problem `app/desktop/CLAUDE.md` already warns about for this setting.

**Windows screen readers get nothing from the app.** Swing and Compose Desktop reach Narrator, NVDA and JAWS through
the Java Access Bridge, which lives in `jdk.accessibility`. Without that module in the runtime the bridge is not
there to be enabled, so the Windows build is silent to an assistive technology — not degraded, absent.

## Cause

`app/desktop/build.gradle.kts:48-52`, verified at HEAD `984861e4`:

```kotlin
            // What jdeps finds on top of the modules Compose Desktop includes by itself; the packaged runtime holds the
            // listed modules and nothing else, so one that is missing is a NoClassDefFoundError only an installed build
            // can throw. `./gradlew :app:desktop:suggestRuntimeModules` prints the list.
            modules("java.instrument", "java.management", "jdk.unsupported")
```

The comment names the trap and then falls into it: `suggestRuntimeModules` runs `jdeps` over the byte code, and
neither of these two modules is reachable from byte code. `jdk.localedata` is a **service provider** — `Locale`
finds it through `ServiceLoader` at run time, so no class file ever names it — and `jdk.accessibility` is loaded by
the JDK's own accessibility bootstrap. `jdeps` cannot see either, so the list it suggests is silently short.

The runtime that is actually built today, read out of the `release` file of the app image at
`app/desktop/build/compose/binaries/main-release/app/Campfire.app/Contents/runtime/Contents/Home/release`:

```
MODULES="java.base java.datatransfer java.xml java.prefs java.desktop java.instrument java.logging java.management jdk.crypto.ec jdk.unsupported"
```

Neither `jdk.localedata` nor `jdk.accessibility` is there.

The consumer of the first, `presentation/src/desktopMain/.../ui/platform/LanguageNames.desktop.kt:18-20`:

```kotlin
internal actual fun languageDisplayName(code: String, inLocaleCode: String): String? = Locale.forLanguageTag(code)
    .getDisplayLanguage(Locale.forLanguageTag(inLocaleCode))
    .takeIf { it.isNotEmpty() && !it.equals(code, ignoreCase = true) }
```

Reproduced directly against the packaged module list:

```
$ java -cp … L                                                              # the whole JDK
német
angol
$ java --limit-modules java.base,java.desktop,java.instrument,java.management,jdk.unsupported -cp … L
German
English
```

— where `L` is `Locale.forLanguageTag("de").getDisplayLanguage(Locale.forLanguageTag("hu"))` and the same for `en`.
Note that the fallback in `LanguageNames.desktop.kt` does not catch it: the answer is a real name, just the wrong
language's, so `takeIf` passes it through and the app has no way to notice.

## The change

One line, in `app/desktop/build.gradle.kts`, with the comment extended to say why two of the four are not things
`suggestRuntimeModules` will ever print:

```kotlin
            // What jdeps finds on top of the modules Compose Desktop includes by itself, plus the two it cannot:
            // `jdk.localedata` is found through ServiceLoader at run time, so no class file names it, and without it
            // Locale.getDisplayLanguage answers in English whatever language it is asked in - the language filter
            // chips and the language picker would read "German" in a Hungarian app. `jdk.accessibility` is loaded by
            // the JDK itself and is what the Java Access Bridge lives in, without which Narrator and NVDA get nothing
            // from the Windows build. The packaged runtime holds the listed modules and nothing else, so one that is
            // missing is a NoClassDefFoundError - or, for these two, a silent wrong answer - that only an installed
            // build can show. `./gradlew :app:desktop:suggestRuntimeModules` prints what jdeps can see.
            modules("java.instrument", "java.management", "jdk.accessibility", "jdk.localedata", "jdk.unsupported")
```

Both modules are alphabetically placed in the existing list. Nothing else changes: the size cost is `jdk.localedata`,
around 15 MB of CLDR data before jlink's compression, which is the price of the app speaking a second language at
all.

Consider while here whether the Access Bridge also needs to be switched on. On Windows the bridge is enabled per user
by `jabswitch /enable`, which writes `assistive_technologies=com.sun.java.accessibility.AccessBridge` into
`.accessibility.properties` — Campfire must not do that behind the user's back, and a user who has enabled it for
any other Java application already has it on. Shipping the module is what makes the app answerable at all; leave the
switch to the user. Note it in the comment only if review wants it recorded.

## Tests

None — this is packaging configuration, and the root `CLAUDE.md` restricts tests to pure logic. Run the standard
suite to confirm nothing else moved:

```bash
./gradlew :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest
```

## Verification

**The module list actually in the runtime** — this is the check that proves the change, and it needs no store, no
signing and no other machine:

```bash
./gradlew :app:desktop:createReleaseDistributable
# macOS:
grep -o 'MODULES="[^"]*"' app/desktop/build/compose/binaries/main-release/app/Campfire.app/Contents/runtime/Contents/Home/release
# Linux / Windows:
grep -o 'MODULES="[^"]*"' app/desktop/build/compose/binaries/main-release/app/Campfire/lib/runtime/release
```

Both `jdk.accessibility` and `jdk.localedata` must appear.

**The language names, in the packaged build** (the only build that can show the bug):

```bash
app/desktop/build/compose/binaries/main-release/app/Campfire.app/Contents/MacOS/Campfire
```

Set the app's language to Hungarian in Settings → General, then open the Songs screen's filter. With at least two
languages in the library the language chips appear; they must read *angol* / *magyar*, not *English* / *Hungarian*.
Song details → the language chip in the header must agree. Before the change the same build shows the English names,
which is the comparison worth making once.

**The regression guard** on any machine, without packaging anything:

```bash
java --limit-modules java.base,java.desktop,java.instrument,java.management,jdk.unsupported …   # English names
java --limit-modules java.base,java.desktop,java.instrument,java.management,jdk.localedata,jdk.unsupported …   # localized names
```

**The accessibility half needs a Windows PC.** Build the `.msi`
(`./gradlew :app:desktop:packageReleaseMsi` on Windows), install it, run `jabswitch /enable` once, sign out and
back in, then start Narrator (Win+Ctrl+Enter) and tab through the Songs screen: the rows and the app bar actions must
be announced. There is no way to prove this from macOS or Linux, and no way to prove it from an unpackaged `run`,
since a full JDK has the module either way.

The release workflow's smoke test (`desktop-publish.yml`, "Start the release build once") passes either way — it
looks for the demo library and for exceptions, and neither of these failures throws — so this cannot be caught by CI
as it stands.

## Docs

`app/desktop/CLAUDE.md`, the Packaging paragraph. This sentence stays true but is now incomplete and misleading,
since it points at the one tool that cannot find the two modules being added:

> `modules(...)` lists what `suggestRuntimeModules` finds beyond Compose Desktop's defaults: the packaged runtime
> holds nothing else, so a missing module is a `NoClassDefFoundError` only an installed build throws — run that task
> again after adding a JVM dependency.

becomes

> `modules(...)` lists what `suggestRuntimeModules` finds beyond Compose Desktop's defaults, plus the two it cannot
> find: `jdk.localedata`, which `Locale` reaches through `ServiceLoader` so that no class file names it and without
> which every language name in an installed build is English, and `jdk.accessibility`, which holds the Java Access
> Bridge that Windows screen readers read the app through. The packaged runtime holds nothing else, so a missing
> module is a `NoClassDefFoundError` — or, for a module found by service loading, a silent wrong answer — that only
> an installed build shows. Run that task again after adding a JVM dependency, and remember that it answers about
> byte code only.

Root `CLAUDE.md` needs no edit: its language bullet already says the names come from `java.util.Locale` and says
nothing about the packaged runtime. The change is what makes that sentence true of an installed build.

## Files touched

- `app/desktop/build.gradle.kts`
- `app/desktop/CLAUDE.md`

## Depends on

Nothing.

## Rules

- Load the `code-style` skill before the first edit (it governs the build file's comment voice too: why, not what,
  in full sentences, wrapped at the surrounding width).
- No string, no `commonMain` and no `expect`/`actual` changes here.
- The per-module `CLAUDE.md` is part of the change, not a follow-up.
