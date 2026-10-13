# Credit js-joda, which kotlinx-datetime bundles into the web build

**Kind:** licensing  ·  **Severity:** medium  ·  **Platforms:** web
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/OpenSourceComponents.kt`,
`presentation/src/wasmJsMain/composeResources/files/licenses/js_joda_bsd_3.txt` (new),
`presentation/src/desktopTest/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/OpenSourceComponentsTest.kt`,
`README.md` (Third-party software table only)

## Problem

`kotlinx-datetime` on Kotlin/Wasm depends on the npm package `@js-joda/core`, and webpack bundles it into the web build's
`campfire.js`. The built site at 72d48deb7 says so itself: `app/web/build/dist/wasmJs/productionExecutable/campfire.js.LICENSE.txt`
holds only js-joda's banners,

```
@copyright (c) 2016, Philipp Thürwächter & Pattrick Hüper
@copyright (c) 2007-present, Stephen Colebourne & Michael Nascimento Santos
@license BSD-3-Clause (see LICENSE in the root directory of this source tree)
…
//! @version @js-joda/core - 3.2.0
```

and the bundle contains its code (`"Pattern using (localized) text not implemented, use js-joda-locale plugin!"`). The
generated `build/wasm/packages/campfire/package.json` lists `"@js-joda/core": "3.2.0"` (BSD-3-Clause per its
`package.json`). Neither `OpenSourceComponents.kt` nor the README's Third-party software table mentions it, against the root
`CLAUDE.md`'s rule that a transitive dependency that ships is credited. The BSD 3-Clause License asks that a binary
redistribution reproduce the copyright notice and the license text in its documentation.

The same `package.json` lists `"ws": "8.20.1"` (MIT), which Ktor's JS engine asks for only under Node.js. It is **not** in
the bundle: `campfire.js` has no `require`/`import` of it and none of its code (no `maxPayload`, no
`perMessageDeflate`); Ktor's browser engine uses the browser's `WebSocket`. Nothing to credit for it.

## Fix

1. Copy js-joda's license as it is into `presentation/src/wasmJsMain/composeResources/files/licenses/js_joda_bsd_3.txt`.
   The text is in the local npm install at `build/wasm/node_modules/@js-joda/core/LICENSE` (BSD License, "Copyright (c)
   2016, Philipp Thürwächter & Pattrick Hüper"), and upstream at https://github.com/js-joda/js-joda/blob/main/LICENSE.
   js-joda is a port of ThreeTen (the banners name Stephen Colebourne & Michael Nascimento Santos); append ThreeTen-bp's
   BSD 3-Clause text (https://github.com/ThreeTen/threetenbp/blob/main/LICENSE.txt) after it, under a line naming it, so
   both copyright notices the bundle carries are reproduced.
2. Add, in `OpenSourceComponents.kt` between `JetBrains Runtime API` and `JSpecify` (the list is sorted by lowercase name, which the test checks):

   ```kotlin
   OpenSourceComponent(
       name = "js-joda",
       holder = "Philipp Thürwächter, Pattrick Hüper, and the js-joda contributors; Stephen Colebourne and Michael Nascimento Santos (ThreeTen)",
       url = "https://js-joda.github.io/js-joda/",
       licenseName = BSD_3,
       licenseFile = "$LICENSES/js_joda_bsd_3.txt",
       licenseUrl = BSD_3_URL,
       builds = setOf(AppBuild.WEB),
   ),
   ```

3. README Third-party software table, in the same order:
   `| [js-joda](https://js-joda.github.io/js-joda/), inside kotlinx-datetime | Philipp Thürwächter, Pattrick Hüper, and the js-joda contributors; Stephen Colebourne and Michael Nascimento Santos | [BSD 3-Clause](presentation/src/wasmJsMain/composeResources/files/licenses/js_joda_bsd_3.txt) | Web |`

## Tests

`OpenSourceComponentsTest` checks the text is in `wasmJsMain` and the order. Extend `a build lists what it ships and nothing
else` with `assertTrue("js-joda" in namesOf(AppBuild.WEB) && "js-joda" !in namesOf(AppBuild.ANDROID))`.

## Manual check

In the web build (`./gradlew :app:web:wasmJsBrowserDevelopmentRun`), Settings → About → Open-source licenses lists js-joda
between JetBrains Runtime API and JSpecify, and it opens on both BSD texts. The Android,
desktop, and iOS builds do not list it.
