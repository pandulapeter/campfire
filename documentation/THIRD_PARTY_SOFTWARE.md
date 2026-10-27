<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Third-party software

Campfire is built with the work of others, which keeps its own license. Each build lists what it ships, with the full
license texts and the projects' NOTICE files, under Settings → About → Open-source licenses; the table below is every
build's, from the Android, desktop, and web runtime dependencies. The texts are under `composeResources/files/licenses`
of the source set of the builds that ship them.

| Component | Copyright | License | Builds |
|---|---|---|---|
| [abc2svg](https://chiselapp.com/user/moinejf/repository/abc2svg) v1.23.6 | Jean-François Moine | [LGPL 3.0 or later, with the GPL 3.0 it incorporates](../presentation/src/commonMain/composeResources/files/licenses/lgpl_3.0.txt), and its author's permission, granted by email on 2026-10-27: "I allow abc2svg to be distributed as part of Campfire through Apple's app stores." ([notice](../presentation/src/commonMain/composeResources/files/licenses/abc2svg_notice.txt)) | Android, desktop |
| [abc2svg music font](https://chiselapp.com/user/moinejf/repository/abc2svg), its glyph outlines | Jean-François Moine | [SIL Open Font License 1.1](../presentation/src/commonMain/composeResources/files/licenses/abc2svg_font_ofl.txt) | All |
| [Accompanist](https://github.com/google/accompanist) Drawable Painter | Google | [Apache 2.0](../presentation/src/commonMain/composeResources/files/licenses/apache_2.0.txt) | Android |
| [AndroidX](https://developer.android.com/jetpack/androidx) libraries (Activity, AppCompat, Browser, Core, Fragment, ProfileInstaller, and what they need) | The Android Open Source Project | [Apache 2.0](../presentation/src/commonMain/composeResources/files/licenses/apache_2.0.txt) | Android |
| AndroidX [multiplatform](https://github.com/JetBrains/compose-multiplatform-core) libraries (Annotation, Collection, Graphics Shapes, Lifecycle, Navigation 3, NavigationEvent, SavedState) | The Android Open Source Project, JetBrains | [Apache 2.0](../presentation/src/commonMain/composeResources/files/licenses/apache_2.0.txt) | All |
| [ANGLE](https://chromium.googlesource.com/angle/angle) | The ANGLE Project Authors | [BSD 3-Clause](../presentation/src/desktopMain/composeResources/files/licenses/angle_bsd_3.txt) | Windows |
| [ChordPro](https://www.chordpro.org) chord shapes | The ChordPro Team | [Artistic 2.0](../presentation/src/commonMain/composeResources/files/licenses/chordpro_artistic_2.0.txt) | All |
| [chords-db](https://github.com/tombatossals/chords-db) and [its fork by Tabsy](https://github.com/tabsy-gr/chords-db) chord shapes | David Rubert and the chords-db contributors, Tabsy contributors | [MIT](../presentation/src/commonMain/composeResources/files/licenses/chords_db_mit.txt) | All |
| [Coil](https://github.com/coil-kt/coil) | Coil Contributors | [Apache 2.0](../presentation/src/commonMain/composeResources/files/licenses/apache_2.0.txt) | All |
| [Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform) and Material 3 | JetBrains, The Android Open Source Project | [Apache 2.0](../presentation/src/commonMain/composeResources/files/licenses/apache_2.0.txt) | All |
| [DNG SDK](https://helpx.adobe.com/camera-raw/digital-negative.html), inside Skia | Adobe Systems Incorporated | [DNG SDK License](../presentation/src/commonMain/composeResources/files/licenses/dng_sdk.txt) | iOS, macOS, Linux |
| [Expat](https://libexpat.github.io), inside Skia | Thai Open Source Software Center Ltd, Clark Cooper, and the Expat maintainers | [MIT](../presentation/src/commonMain/composeResources/files/licenses/expat_mit.txt) | Desktop, iOS, web |
| [FreeType](https://freetype.org), inside Skia | David Turner, Robert Wilhelm, and Werner Lemberg (The FreeType Project) | [FreeType License](../presentation/src/commonMain/composeResources/files/licenses/freetype_ftl.txt) | Linux, web |
| [Google Play In-App Updates](https://developer.android.com/guide/playcore/in-app-updates) and Play services | Google | [Play Core SDK Terms of Service](https://developer.android.com/guide/playcore/license), and [the licenses of what it bundles](../presentation/src/androidMain/composeResources/files/licenses/google_play_third_party_licenses.txt) | Android |
| [Google Play Install Referrer Library](https://developer.android.com/google/play/installreferrer/library) | Google | [Android Software Development Kit License](../presentation/src/androidMain/composeResources/files/licenses/android_sdk_license.txt) | Android |
| [Guava](https://github.com/google/guava) ListenableFuture | The Guava Authors | [Apache 2.0](../presentation/src/commonMain/composeResources/files/licenses/apache_2.0.txt) | Android |
| [HarfBuzz](https://harfbuzz.github.io), inside Skia | Google, Behdad Esfahbod, and the other HarfBuzz authors | [Old MIT](../presentation/src/commonMain/composeResources/files/licenses/harfbuzz_mit.txt) | Desktop, iOS, web |
| [ICU](https://icu.unicode.org), inside Skia | Unicode, Inc. | [Unicode License V3](../presentation/src/commonMain/composeResources/files/licenses/icu_unicode_3.txt) | Desktop, iOS, web |
| [Inter](https://github.com/rsms/inter) | The Inter Project Authors | [SIL Open Font License 1.1](../presentation/src/commonMain/composeResources/files/licenses/inter_ofl.txt) | All |
| [JetBrains Annotations](https://github.com/JetBrains/java-annotations) | JetBrains | [Apache 2.0](../presentation/src/commonMain/composeResources/files/licenses/apache_2.0.txt) | Android, desktop |
| [JetBrains Mono](https://github.com/JetBrains/JetBrainsMono) | The JetBrains Mono Project Authors | [SIL Open Font License 1.1](../presentation/src/commonMain/composeResources/files/licenses/jetbrains_mono_ofl.txt) | All |
| [JetBrains Runtime](https://github.com/JetBrains/JetBrainsRuntime) | Oracle and/or its affiliates, JetBrains | [GPL 2.0 with the Classpath Exception](../presentation/src/desktopMain/composeResources/files/licenses/openjdk_gpl_2.0_classpath_exception.txt) ([additional information](../presentation/src/desktopMain/composeResources/files/licenses/jetbrains_runtime_additional_license_info.txt)) | Desktop |
| [JetBrains Runtime API](https://github.com/JetBrains/JetBrainsRuntimeApi) | JetBrains | [Apache 2.0](../presentation/src/commonMain/composeResources/files/licenses/apache_2.0.txt) | Desktop |
| [js-joda](https://js-joda.github.io/js-joda/), inside kotlinx-datetime | Philipp Thürwächter, Pattrick Hüper, and the js-joda contributors; Stephen Colebourne and Michael Nascimento Santos | [BSD 3-Clause](../presentation/src/wasmJsMain/composeResources/files/licenses/js_joda_bsd_3.txt) | Web |
| [JSpecify](https://jspecify.dev) | The JSpecify Authors | [Apache 2.0](../presentation/src/commonMain/composeResources/files/licenses/apache_2.0.txt) | Android, desktop |
| [Koin](https://github.com/InsertKoinIO/koin) | Kotzilla, Koin contributors | [Apache 2.0](../presentation/src/commonMain/composeResources/files/licenses/apache_2.0.txt) | All |
| [Kotlin](https://kotlinlang.org) and kotlinx (coroutines, serialization, datetime, io, atomicfu, browser) | JetBrains, Kotlin contributors | [Apache 2.0](../presentation/src/commonMain/composeResources/files/licenses/apache_2.0.txt) ([NOTICE](../presentation/src/commonMain/composeResources/files/licenses/kotlin_notice.txt)) | All |
| [Ktor](https://github.com/ktorio/ktor) | JetBrains, Ktor contributors | [Apache 2.0](../presentation/src/commonMain/composeResources/files/licenses/apache_2.0.txt) | All |
| [libjpeg-turbo](https://libjpeg-turbo.org), inside Skia | The libjpeg-turbo Project, D. R. Commander, the Independent JPEG Group | [IJG, BSD 3-Clause, and zlib](../presentation/src/commonMain/composeResources/files/licenses/libjpeg_turbo.txt) | Desktop, iOS, web |
| [libpng](http://www.libpng.org/pub/png/libpng.html), inside Skia | The PNG Reference Library Authors, Cosmin Truta, Glenn Randers-Pehrson, and the other libpng authors | [PNG Reference Library License version 2](../presentation/src/commonMain/composeResources/files/licenses/libpng_2.txt) | Desktop, iOS, web |
| [libwebp](https://chromium.googlesource.com/webm/libwebp), inside Skia | Google | [BSD 3-Clause](../presentation/src/commonMain/composeResources/files/licenses/libwebp_bsd_3.txt) ([PATENTS](../presentation/src/commonMain/composeResources/files/licenses/libwebp_patents.txt)) | Desktop, iOS, web |
| [OkHttp](https://github.com/square/okhttp) | Square | [Apache 2.0](../presentation/src/commonMain/composeResources/files/licenses/apache_2.0.txt) | Android |
| [Okio](https://github.com/square/okio) | Square | [Apache 2.0](../presentation/src/commonMain/composeResources/files/licenses/apache_2.0.txt) | All |
| [piex](https://github.com/google/piex), inside Skia | Google | [Apache 2.0](../presentation/src/commonMain/composeResources/files/licenses/apache_2.0.txt) | iOS, macOS, Linux |
| [Public Suffix List](https://publicsuffix.org), inside OkHttp | Mozilla Foundation | [MPL 2.0](../presentation/src/commonMain/composeResources/files/licenses/mpl_2.0.txt) | Android |
| [QuickJS](https://bellard.org/quickjs), with [c-vector](https://github.com/eteran/c-vector), inside quickjs-kt | Fabrice Bellard, Charlie Gordon; Evan Teran | [MIT](../presentation/src/commonMain/composeResources/files/licenses/quickjs_mit.txt) | Android, desktop |
| [quickjs-kt](https://github.com/dokar3/quickjs-kt) | Dokar | [Apache 2.0](../presentation/src/commonMain/composeResources/files/licenses/apache_2.0.txt) | Android, desktop |
| [Reorderable](https://github.com/Calvin-LL/Reorderable) | Calvin Liang | [Apache 2.0](../presentation/src/commonMain/composeResources/files/licenses/apache_2.0.txt) | All |
| [Skia](https://skia.org) | Google | [BSD 3-Clause](../presentation/src/commonMain/composeResources/files/licenses/skia_bsd_3.txt) | Desktop, iOS, web |
| [Skiko](https://github.com/JetBrains/skiko) | JetBrains | [Apache 2.0](../presentation/src/commonMain/composeResources/files/licenses/apache_2.0.txt) ([NOTICE](../presentation/src/commonMain/composeResources/files/licenses/skiko_notice.txt)) | Desktop, iOS, web |
| [SLF4J](https://www.slf4j.org) | QOS.ch | [MIT](../presentation/src/commonMain/composeResources/files/licenses/slf4j_mit.txt) | Android, desktop |
| [Stately](https://github.com/touchlab/Stately) | Touchlab | [Apache 2.0](../presentation/src/commonMain/composeResources/files/licenses/apache_2.0.txt) | All |
| [Wuffs](https://github.com/google/wuffs), inside Skia | The Wuffs Authors | [Apache 2.0](../presentation/src/commonMain/composeResources/files/licenses/apache_2.0.txt) | Desktop, iOS, web |
| [zlib](https://zlib.net), inside Skia | Jean-loup Gailly and Mark Adler | [zlib License](../presentation/src/commonMain/composeResources/files/licenses/zlib.txt) | Desktop, iOS, web |

The chord shapes from chords-db and ChordPro were checked and reordered for Campfire; each table's source file names
where its shapes came from.
