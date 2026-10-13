# Credit the libraries Skia bundles into the desktop, iOS, and web builds, with their license texts

**Kind:** licensing  ·  **Severity:** medium  ·  **Platforms:** desktop (macOS, Windows, Linux), iOS, web; not Android
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/OpenSourceComponents.kt`,
`presentation/src/commonMain/composeResources/files/licenses/` (new texts, see below),
`presentation/src/wasmJsMain/composeResources/files/licenses/` (FreeType, if it turns out to be the web's alone),
`presentation/src/desktopTest/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/OpenSourceComponentsTest.kt`,
`README.md` (Third-party software table only),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/CLAUDE.md` (one sentence)
**Challenged:** amended — several of step 1's markers are exported symbols that a stripped library does not carry: on this Mac `strings -a libskiko-macos-arm64.dylib` finds no `XML_ParserCreate`, `inflateInit`, `png_create_read_struct`, `WebPDecode`, or `jpeg_CreateDecompress`, although Expat, zlib, libpng, libwebp, and libjpeg-turbo are all in it (no `libz` in `otool -L`; their own strings are there). A zero from those markers on Windows or Linux would have dropped a library that ships, so step 1 now greps for strings each library keeps. Everything else (the texts, the `builds`, the `commonMain` placement beside `skia_bsd_3.txt`, the README rows) held.

## Problem

The root `CLAUDE.md` asks that "A dependency that ships in a build is credited in the same change, a transitive one
included". Skia is credited once (`OpenSourceComponents.kt`, at 72d48deb7):

```kotlin
OpenSourceComponent(name = "Skia", holder = "Google Inc.", url = "https://skia.org", licenseName = BSD_3,
    licenseFile = "$LICENSES/skia_bsd_3.txt", licenseUrl = BSD_3_URL, builds = SKIA),
apache("Skiko", "JetBrains s.r.o.", "https://github.com/JetBrains/skiko", SKIA, noticeFile = "$LICENSES/skiko_notice.txt"),
```

but the Skia that Skiko 0.150.1 ships is built with third-party libraries linked in, each under its own license, and none
of them is listed in the app or the README. Checked by reading the built artifacts on this machine with `strings`:

| Library (version where the binary says) | macOS `libskiko-macos-arm64.dylib` | iOS `ComposeApp.framework` (release, arm64) | web `skiko.wasm` | License |
|---|---|---|---|---|
| HarfBuzz | yes (`hb_shape`, `hb_font_create`) | yes | yes (`harfbuzz.cpp`) | "Old MIT" |
| ICU, data `icudt74l` | yes | yes | yes | Unicode License V3 (ICU 74), with the third-party sections of ICU's LICENSE |
| libjpeg-turbo 3.1.0 | yes | yes | yes | IJG License, Modified (3-clause) BSD, zlib License |
| libpng (1.6.56 on iOS) | yes | yes | yes | PNG Reference Library License version 2 |
| libwebp | yes (`WebPDecode`, `VP8L`) | yes | yes | BSD 3-Clause (Google), with its PATENTS grant |
| Wuffs | yes (`wuffs_base`, `wuffs_gif`) | yes | yes | Apache 2.0 (or MIT) |
| Expat (2.7.4 on iOS) | yes (`XML_ParserCreate`) | yes | yes (`EXPAT_*` strings) | MIT |
| zlib (1.3.0.1 on iOS) | yes | yes | yes | zlib License |
| piex | yes | yes | no | Apache 2.0 |
| Adobe DNG SDK | yes (`dng_host`, `dng_negative`, `SkRawCodec`) | yes | no | DNG SDK License (BSD-style) |
| FreeType | no | no | yes (`FREETYPE_PROPERTIES`, `truetype-engine`) | FreeType License (FTL) |

The iOS klib (`skiko-iosArm64Main-0.150.1.klib`, `default/targets/ios_arm64/included/`) carries them as static libraries:
`libdng_sdk.a libexpat.a libharfbuzz.a libicu.a libjpeg.a libpiex.a libpng.a libwebp.a libzlib.a` beside Skia's own
(`libskia.a libskparagraph.a libskshaper.a libskunicode_*.a libsvg.a libskottie.a libsksg.a libskresources.a
libjsonreader.a`, all Skia's BSD-3). The Windows and Linux runtimes (`org.jetbrains.skiko:skiko-awt-runtime-windows-x64`
and `-linux-x64`, 0.150.1) are not in this Mac's Gradle cache and were not checked; Skiko builds them from the same Skia
configuration, and Linux is the one most likely to add FreeType. Android draws with the platform's own Skia and ships none
of this (`SKIA` excludes it already).

## Fix

1. **Confirm Windows and Linux** before writing the `builds`: download
   `https://repo1.maven.org/maven2/org/jetbrains/skiko/skiko-awt-runtime-windows-x64/0.150.1/skiko-awt-runtime-windows-x64-0.150.1.jar`
   and the `linux-x64` one into a new empty folder of the scratchpad, `unzip` them, and run
   `strings -a <lib> | grep -c "<marker>"` with markers the libraries keep as text even in a stripped binary (checked
   against `libskiko-macos-arm64.dylib`, 0.150.1): `hb_shape` or `harfbuzz` (HarfBuzz), `icudt74l` (ICU), `libjpeg-turbo`
   or `Not a JPEG` (libjpeg-turbo), `libpng` (libpng), `VP8L` (libwebp), `wuffs_base` (Wuffs), `expat` (Expat),
   `invalid distance too far back` (zlib; not `unsupported zlib version`, which is libpng's), `piex` (piex), `dng_host` (DNG SDK), and `FREETYPE_PROPERTIES` or `freetype`
   (FreeType). Exported symbols such as `XML_ParserCreate`, `inflateInit`, `png_create_read_struct`, or `WebPDecode` are
   no evidence either way: the macOS library has none of them and all of those libraries. A library linked dynamically
   instead (a `.dll` / `.so` it depends on, `objdump -p` / `ldd`) is the system's, not shipped, and is not credited.
   Desktop builds are one `AppBuild` set (`DESKTOP`) unless one of them differs.
2. **Get the license texts.** The exact Skia that Skiko 0.150.1 is built from is named by `dependencies.skia` in Skiko's
   `skiko/gradle.properties` at tag `v0.150.1` (a `JetBrains/skia-pack` release); Skia's `DEPS` at that commit pins every
   library under `third_party/externals/`. Take each text from that pinned revision where possible, else from the
   library's upstream at the version the binary names:
   - HarfBuzz: `COPYING` — https://github.com/harfbuzz/harfbuzz/blob/main/COPYING
   - ICU 74: `LICENSE` (the whole file: Unicode License V3 and the third-party data and code it lists) —
     https://github.com/unicode-org/icu/blob/release-74-2/LICENSE
   - libjpeg-turbo 3.1.0: `LICENSE.md` and the IJG text in `README.ijg` —
     https://github.com/libjpeg-turbo/libjpeg-turbo/blob/3.1.0/LICENSE.md
   - libpng: `LICENSE` — https://github.com/pnggroup/libpng/blob/libpng16/LICENSE
   - libwebp: `COPYING` and `PATENTS` — https://chromium.googlesource.com/webm/libwebp/+/refs/heads/main/COPYING
   - Wuffs: `LICENSE` — https://github.com/google/wuffs (Apache 2.0, the app's `apache_2.0.txt` serves)
   - Expat 2.7.4: `expat/COPYING` — https://github.com/libexpat/libexpat/blob/R_2_7_4/expat/COPYING
   - zlib (Chromium's copy): `LICENSE` — https://chromium.googlesource.com/chromium/src/third_party/zlib/+/refs/heads/main/LICENSE
   - piex: Apache 2.0 — https://github.com/google/piex (the app's `apache_2.0.txt` serves)
   - DNG SDK: `LICENSE` of Skia's copy — https://skia.googlesource.com/third_party/dng_sdk/+/refs/heads/main/LICENSE
   - FreeType: `docs/FTL.TXT` — https://gitlab.freedesktop.org/freetype/freetype/-/blob/master/docs/FTL.TXT (choose the
     FTL of its dual license; it asks for the credit "Portions of this software are copyright © The FreeType Project
     (www.freetype.org). All rights reserved." in the documentation, which the entry's text gives)
3. **Add the files** under `presentation/src/commonMain/composeResources/files/licenses/` (several builds ship each):
   `harfbuzz_mit.txt`, `icu_unicode_3.txt`, `libjpeg_turbo.txt` (LICENSE.md followed by the IJG text), `libpng_2.txt`,
   `libwebp_bsd_3.txt`, `libwebp_patents.txt`, `expat_mit.txt`, `zlib.txt`, `dng_sdk.txt`, and `freetype_ftl.txt`
   (under `wasmJsMain` instead if step 1 shows the web is the only build with FreeType). Each file is the upstream text
   as it is; the repository's license-header check covers source files, scripts, workflows, XML resources, and
   `CLAUDE.md`, not these `.txt` texts (the existing ones have no header).
4. **Add one `OpenSourceComponent` per library** to `openSourceComponents` (the list is sorted by lowercase name after
   Campfire, which the test checks): `DNG SDK` (Adobe Systems Incorporated; https://helpx.adobe.com/camera-raw/digital-negative.html;
   "DNG SDK License"; builds `setOf(AppBuild.IOS) + DESKTOP`), `Expat` (Thai Open Source Software Center Ltd, Clark
   Cooper, and the Expat maintainers; https://libexpat.github.io; `MIT`; `SKIA`), `FreeType` (The FreeType Project;
   https://freetype.org; "FreeType License"; the web, plus Linux if found), `HarfBuzz` (the HarfBuzz authors, as `COPYING`
   lists them; https://harfbuzz.github.io; "Old MIT License"; `SKIA`), `ICU` (Unicode, Inc.; https://icu.unicode.org;
   "Unicode License V3"; `SKIA`), `libjpeg-turbo` (the libjpeg-turbo Project, Independent JPEG Group; https://libjpeg-turbo.org;
   "IJG License, BSD 3-Clause License, and zlib License"; `SKIA`), `libpng` (Cosmin Truta, Glenn Randers-Pehrson, and the
   libpng authors; http://www.libpng.org/pub/png/libpng.html; "PNG Reference Library License version 2"; `SKIA`),
   `libwebp` (Google Inc.; https://chromium.googlesource.com/webm/libwebp; `BSD_3`, `noticeFile` the PATENTS file;
   `SKIA`), `piex` (`apache("piex", "Google Inc.", "https://github.com/google/piex", setOf(AppBuild.IOS) + DESKTOP)`),
   `Wuffs` (`apache("Wuffs", "The Wuffs Authors", "https://github.com/google/wuffs", SKIA)`), and `zlib` (Jean-loup Gailly
   and Mark Adler; https://zlib.net; "zlib License"; `SKIA`). Take each holder from the first lines of its license text
   rather than from this list where they differ. Adjust any `builds` step 1 contradicts.
5. **README**: one row each in the Third-party software table, in the same order, written like the Public Suffix List's
   ("[HarfBuzz](https://harfbuzz.github.io), inside Skia | … | [Old MIT](presentation/src/commonMain/composeResources/files/licenses/harfbuzz_mit.txt) | Desktop, iOS, web").
   piex and DNG SDK read "Desktop, iOS"; FreeType "Web" (or "Linux, web").
6. **Dialogs `CLAUDE.md`**: after "The list was taken from every artifact on the Android, desktop, and web runtime
   classpaths, …" add "and from the native libraries Skiko ships for the desktop, iOS, and the web, whose bundled
   third-party code (Skia's `third_party`) was found with `strings`".

## Tests

`OpenSourceComponentsTest` already checks that every listed text is among the files of every build that lists it, and the
order. Extend `a build lists what it ships and nothing else` with:
`assertTrue("HarfBuzz" in namesOf(AppBuild.IOS) && "HarfBuzz" in namesOf(AppBuild.WEB) && "HarfBuzz" in namesOf(AppBuild.MACOS) && "HarfBuzz" !in namesOf(AppBuild.ANDROID))`,
`assertTrue("piex" in namesOf(AppBuild.MACOS) && "piex" !in namesOf(AppBuild.WEB))`, and
`assertTrue("FreeType" in namesOf(AppBuild.WEB) && "FreeType" !in namesOf(AppBuild.IOS))`.

## Manual check

On the desktop, iOS, and the web: Settings → About → Open-source licenses lists the new entries in alphabetical order, each
opening on its full text (ICU's is long and scrolls in chunks). On Android none of them is listed.
