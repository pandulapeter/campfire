/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs.about

import com.pandulapeter.campfire.presentation.ui.platform.AppBuild

/**
 * What Campfire is made of that somebody else wrote, as the Open-source licenses sheet lists it: every library the
 * builds ship, the transitive ones included, the fonts the app bundles, the runtime the desktop builds bundle,
 * and the chord shapes the tables were filled from, each with the builds it is part of, since a build lists only what
 * it ships. The names, the holders and the licenses' names are proper names, so they are not translated.
 *
 * Kept by hand rather than read off the build: a new dependency that ships in a build gets an entry here in the same
 * change, its license text, and its NOTICE where it has one, under `composeResources/files/licenses` of the source set
 * of the builds that show it (`commonMain` where several do), and the README's list too. What decided this list was
 * every artifact of the Android, desktop, and web runtime classpaths, the NOTICE files of their projects, the
 * third-party code compiled into the native libraries Skiko ships for the desktop, iOS, and the web, and the libraries
 * the AppImage runtime is statically linked with.
 *
 * @property licenseFile The license's text among the app's files, which Google's Play libraries ship as their own list
 *   of what they bundle.
 * @property noticeFile The project's NOTICE, which the Apache License asks to be passed on with the work, where it
 *   has one.
 * @property builds The builds that ship it.
 */
internal data class OpenSourceComponent(
    val name: String,
    val holder: String,
    val url: String,
    val licenseName: String,
    val licenseFile: String,
    val licenseUrl: String,
    val noticeFile: String? = null,
    val builds: Set<AppBuild> = AppBuild.entries.toSet(),
)

private val ANDROID = setOf(AppBuild.ANDROID)
private val DESKTOP = setOf(AppBuild.MACOS, AppBuild.WINDOWS, AppBuild.LINUX)
private val JVM = ANDROID + DESKTOP

/** Every build that draws with Skia itself rather than through Android's own. */
private val SKIA = setOf(AppBuild.IOS, AppBuild.WEB) + DESKTOP

/** The builds whose Skia reads raw camera images, which Skiko's Windows and web libraries are built without. */
private val SKIA_RAW = setOf(AppBuild.IOS, AppBuild.MACOS, AppBuild.LINUX)

/** The builds whose Skia rasterizes fonts with its own FreeType rather than with the system's font engine. */
private val SKIA_FREETYPE = setOf(AppBuild.WEB, AppBuild.LINUX)

/** What the AppImage runtime is made of, which runs first whenever the Linux build is started as an AppImage. */
private val APPIMAGE = setOf(AppBuild.LINUX)

private const val LICENSES = "files/licenses"
private const val APACHE_2 = "Apache License 2.0"
private const val APACHE_2_FILE = "$LICENSES/apache_2.0.txt"
private const val APACHE_2_URL = "https://www.apache.org/licenses/LICENSE-2.0"
private const val MPL_2 = "Mozilla Public License 2.0"
private const val MPL_2_FILE = "$LICENSES/mpl_2.0.txt"
private const val MPL_2_URL = "https://mozilla.org/MPL/2.0/"
private const val MIT = "MIT License"
private const val MIT_URL = "https://opensource.org/license/mit"
private const val OFL = "SIL Open Font License 1.1"
private const val OFL_URL = "https://openfontlicense.org"
private const val BSD_3 = "BSD 3-Clause License"
private const val BSD_3_URL = "https://opensource.org/license/bsd-3-clause"

private fun apache(name: String, holder: String, url: String, builds: Set<AppBuild> = AppBuild.entries.toSet(), noticeFile: String? = null) = OpenSourceComponent(
    name = name,
    holder = holder,
    url = url,
    licenseName = APACHE_2,
    licenseFile = APACHE_2_FILE,
    licenseUrl = APACHE_2_URL,
    noticeFile = noticeFile,
    builds = builds,
)

/** Campfire first, under its own license, then everything it is built with, by name. */
internal val openSourceComponents = listOf(
    OpenSourceComponent(
        name = "Campfire",
        holder = "Péter Pandula",
        url = "https://github.com/pandulapeter/campfire",
        licenseName = MPL_2,
        licenseFile = MPL_2_FILE,
        licenseUrl = MPL_2_URL,
    ),
    OpenSourceComponent(
        name = "abc2svg",
        holder = "Jean-François Moine",
        url = "https://chiselapp.com/user/moinejf/repository/abc2svg",
        licenseName = "GNU Lesser General Public License 3.0 or later",
        licenseFile = "$LICENSES/lgpl_3.0.txt",
        licenseUrl = "https://www.gnu.org/licenses/lgpl-3.0.html",
        // Its author's permission to ship it through Apple's stores, which the LGPL alone leaves in doubt there.
        noticeFile = "$LICENSES/abc2svg_notice.txt",
    ),
    OpenSourceComponent(
        name = "abc2svg music font",
        holder = "Jean-François Moine",
        url = "https://chiselapp.com/user/moinejf/repository/abc2svg",
        licenseName = OFL,
        licenseFile = "$LICENSES/abc2svg_font_ofl.txt",
        licenseUrl = OFL_URL,
    ),
    apache("Accompanist Drawable Painter", "Google LLC", "https://github.com/google/accompanist", ANDROID),
    apache(
        name = "AndroidX libraries (Activity, AppCompat, Browser, Core, Fragment, ProfileInstaller, and what they need)",
        holder = "The Android Open Source Project",
        url = "https://developer.android.com/jetpack/androidx",
        builds = ANDROID,
    ),
    apache(
        name = "AndroidX multiplatform libraries (Annotation, Collection, Graphics Shapes, Lifecycle, Navigation 3, NavigationEvent, SavedState)",
        holder = "The Android Open Source Project, JetBrains s.r.o.",
        url = "https://github.com/JetBrains/compose-multiplatform-core",
    ),
    OpenSourceComponent(
        name = "ANGLE",
        holder = "The ANGLE Project Authors",
        url = "https://chromium.googlesource.com/angle/angle",
        licenseName = BSD_3,
        licenseFile = "$LICENSES/angle_bsd_3.txt",
        licenseUrl = BSD_3_URL,
        builds = setOf(AppBuild.WINDOWS),
    ),
    OpenSourceComponent(
        name = "AppImage runtime",
        holder = "probonopd and the AppImage contributors",
        url = "https://github.com/AppImage/type2-runtime",
        licenseName = MIT,
        licenseFile = "$LICENSES/appimage_runtime_mit.txt",
        licenseUrl = MIT_URL,
        builds = APPIMAGE,
    ),
    OpenSourceComponent(
        name = "ChordPro",
        holder = "The ChordPro Team",
        url = "https://www.chordpro.org",
        licenseName = "Artistic License 2.0",
        licenseFile = "$LICENSES/chordpro_artistic_2.0.txt",
        licenseUrl = "https://opensource.org/license/artistic-2-0",
    ),
    OpenSourceComponent(
        name = "chords-db",
        holder = "David Rubert and the chords-db contributors, Tabsy contributors",
        url = "https://github.com/tombatossals/chords-db",
        licenseName = MIT,
        licenseFile = "$LICENSES/chords_db_mit.txt",
        licenseUrl = MIT_URL,
    ),
    apache("Coil", "Coil Contributors", "https://github.com/coil-kt/coil"),
    apache("Compose Multiplatform and Material 3", "JetBrains s.r.o., The Android Open Source Project", "https://github.com/JetBrains/compose-multiplatform"),
    OpenSourceComponent(
        name = "DNG SDK",
        holder = "Adobe Systems Incorporated",
        url = "https://helpx.adobe.com/camera-raw/digital-negative.html",
        licenseName = "DNG SDK License",
        licenseFile = "$LICENSES/dng_sdk.txt",
        licenseUrl = "https://android.googlesource.com/platform/external/dng_sdk/+/refs/heads/main/LICENSE",
        builds = SKIA_RAW,
    ),
    OpenSourceComponent(
        name = "Expat",
        holder = "Thai Open Source Software Center Ltd, Clark Cooper, and the Expat maintainers",
        url = "https://libexpat.github.io",
        licenseName = MIT,
        licenseFile = "$LICENSES/expat_mit.txt",
        licenseUrl = MIT_URL,
        builds = SKIA,
    ),
    OpenSourceComponent(
        name = "FreeType",
        holder = "David Turner, Robert Wilhelm, and Werner Lemberg (The FreeType Project)",
        url = "https://freetype.org",
        licenseName = "FreeType License",
        licenseFile = "$LICENSES/freetype_ftl.txt",
        licenseUrl = "https://freetype.org/license.html",
        builds = SKIA_FREETYPE,
    ),
    OpenSourceComponent(
        name = "Google Play In-App Updates and Play services",
        holder = "Google LLC",
        url = "https://developer.android.com/guide/playcore/in-app-updates",
        licenseName = "Play Core Software Development Kit Terms of Service, and the licenses of what it bundles",
        licenseFile = "$LICENSES/google_play_third_party_licenses.txt",
        licenseUrl = "https://developer.android.com/guide/playcore/license",
        builds = ANDROID,
    ),
    OpenSourceComponent(
        name = "Google Play Install Referrer Library",
        holder = "Google LLC",
        url = "https://developer.android.com/google/play/installreferrer/library",
        licenseName = "Android Software Development Kit License",
        licenseFile = "$LICENSES/android_sdk_license.txt",
        licenseUrl = "https://developer.android.com/studio/terms",
        builds = ANDROID,
    ),
    apache("Guava ListenableFuture", "The Guava Authors", "https://github.com/google/guava", ANDROID),
    OpenSourceComponent(
        name = "HarfBuzz",
        holder = "Google, Inc., Behdad Esfahbod, and the other HarfBuzz authors",
        url = "https://harfbuzz.github.io",
        licenseName = "Old MIT License",
        licenseFile = "$LICENSES/harfbuzz_mit.txt",
        licenseUrl = "https://github.com/harfbuzz/harfbuzz/blob/main/COPYING",
        builds = SKIA,
    ),
    OpenSourceComponent(
        name = "ICU",
        holder = "Unicode, Inc.",
        url = "https://icu.unicode.org",
        licenseName = "Unicode License V3",
        licenseFile = "$LICENSES/icu_unicode_3.txt",
        licenseUrl = "https://www.unicode.org/license.txt",
        builds = SKIA,
    ),
    OpenSourceComponent(
        name = "Inter",
        holder = "The Inter Project Authors",
        url = "https://github.com/rsms/inter",
        licenseName = OFL,
        licenseFile = "$LICENSES/inter_ofl.txt",
        licenseUrl = OFL_URL,
    ),
    apache("JetBrains Annotations", "JetBrains s.r.o.", "https://github.com/JetBrains/java-annotations", JVM),
    OpenSourceComponent(
        name = "JetBrains Mono",
        holder = "The JetBrains Mono Project Authors",
        url = "https://github.com/JetBrains/JetBrainsMono",
        licenseName = OFL,
        licenseFile = "$LICENSES/jetbrains_mono_ofl.txt",
        licenseUrl = OFL_URL,
    ),
    OpenSourceComponent(
        name = "JetBrains Runtime",
        holder = "Oracle and/or its affiliates, JetBrains s.r.o.",
        url = "https://github.com/JetBrains/JetBrainsRuntime",
        licenseName = "GNU General Public License 2.0 with the Classpath Exception",
        licenseFile = "$LICENSES/openjdk_gpl_2.0_classpath_exception.txt",
        licenseUrl = "https://openjdk.org/legal/gplv2+ce.html",
        noticeFile = "$LICENSES/jetbrains_runtime_additional_license_info.txt",
        builds = DESKTOP,
    ),
    apache("JetBrains Runtime API", "JetBrains s.r.o.", "https://github.com/JetBrains/JetBrainsRuntimeApi", DESKTOP),
    OpenSourceComponent(
        name = "js-joda",
        holder = "Philipp Thürwächter, Pattrick Hüper, and the js-joda contributors; Stephen Colebourne and Michael Nascimento Santos (ThreeTen)",
        url = "https://js-joda.github.io/js-joda/",
        licenseName = BSD_3,
        licenseFile = "$LICENSES/js_joda_bsd_3.txt",
        licenseUrl = BSD_3_URL,
        builds = setOf(AppBuild.WEB),
    ),
    apache("JSpecify", "The JSpecify Authors", "https://jspecify.dev", JVM),
    apache("Koin", "Kotzilla, Koin contributors", "https://github.com/InsertKoinIO/koin"),
    apache(
        name = "Kotlin and kotlinx (coroutines, serialization, datetime, io, atomicfu, browser)",
        holder = "JetBrains s.r.o., Kotlin Programming Language contributors",
        url = "https://kotlinlang.org",
        noticeFile = "$LICENSES/kotlin_notice.txt",
    ),
    apache("Ktor", "JetBrains s.r.o., Ktor contributors", "https://github.com/ktorio/ktor"),
    OpenSourceComponent(
        name = "libfuse",
        holder = "Miklos Szeredi and the libfuse contributors",
        url = "https://github.com/libfuse/libfuse",
        licenseName = "GNU Lesser General Public License 2.1",
        licenseFile = "$LICENSES/libfuse_lgpl_2.1.txt",
        licenseUrl = "https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html",
        builds = APPIMAGE,
    ),
    OpenSourceComponent(
        name = "libjpeg-turbo",
        holder = "The libjpeg-turbo Project, D. R. Commander, the Independent JPEG Group",
        url = "https://libjpeg-turbo.org",
        licenseName = "IJG License, BSD 3-Clause License, and zlib License",
        licenseFile = "$LICENSES/libjpeg_turbo.txt",
        licenseUrl = "https://github.com/libjpeg-turbo/libjpeg-turbo/blob/main/LICENSE.md",
        builds = SKIA,
    ),
    OpenSourceComponent(
        name = "libpng",
        holder = "The PNG Reference Library Authors, Cosmin Truta, Glenn Randers-Pehrson, and the other libpng authors",
        url = "http://www.libpng.org/pub/png/libpng.html",
        licenseName = "PNG Reference Library License version 2",
        licenseFile = "$LICENSES/libpng_2.txt",
        licenseUrl = "http://www.libpng.org/pub/png/src/libpng-LICENSE.txt",
        builds = SKIA,
    ),
    OpenSourceComponent(
        name = "libwebp",
        holder = "Google Inc.",
        url = "https://chromium.googlesource.com/webm/libwebp",
        licenseName = BSD_3,
        licenseFile = "$LICENSES/libwebp_bsd_3.txt",
        licenseUrl = BSD_3_URL,
        noticeFile = "$LICENSES/libwebp_patents.txt",
        builds = SKIA,
    ),
    OpenSourceComponent(
        name = "musl",
        holder = "Rich Felker, et al.",
        url = "https://musl.libc.org",
        licenseName = MIT,
        licenseFile = "$LICENSES/musl_mit.txt",
        licenseUrl = MIT_URL,
        builds = APPIMAGE,
    ),
    apache("OkHttp", "Square, Inc.", "https://github.com/square/okhttp", ANDROID),
    apache("Okio", "Square, Inc.", "https://github.com/square/okio"),
    apache("piex", "Google Inc.", "https://github.com/google/piex", SKIA_RAW),
    OpenSourceComponent(
        name = "Public Suffix List",
        holder = "Mozilla Foundation",
        url = "https://publicsuffix.org",
        licenseName = MPL_2,
        licenseFile = MPL_2_FILE,
        licenseUrl = MPL_2_URL,
        builds = ANDROID,
    ),
    OpenSourceComponent(
        name = "QuickJS",
        holder = "Fabrice Bellard, Charlie Gordon; c-vector: Evan Teran",
        url = "https://bellard.org/quickjs",
        licenseName = MIT,
        licenseFile = "$LICENSES/quickjs_mit.txt",
        licenseUrl = MIT_URL,
        builds = JVM,
    ),
    apache("quickjs-kt", "Dokar", "https://github.com/dokar3/quickjs-kt", JVM),
    apache("Reorderable", "Calvin Liang", "https://github.com/Calvin-LL/Reorderable"),
    OpenSourceComponent(
        name = "Skia",
        holder = "Google Inc.",
        url = "https://skia.org",
        licenseName = BSD_3,
        licenseFile = "$LICENSES/skia_bsd_3.txt",
        licenseUrl = BSD_3_URL,
        builds = SKIA,
    ),
    apache("Skiko", "JetBrains s.r.o.", "https://github.com/JetBrains/skiko", SKIA, noticeFile = "$LICENSES/skiko_notice.txt"),
    OpenSourceComponent(
        name = "SLF4J",
        holder = "QOS.ch",
        url = "https://www.slf4j.org",
        licenseName = MIT,
        licenseFile = "$LICENSES/slf4j_mit.txt",
        licenseUrl = MIT_URL,
        builds = JVM,
    ),
    OpenSourceComponent(
        name = "squashfuse",
        holder = "Dave Vasilevsky, Phillip Lougher",
        url = "https://github.com/vasi/squashfuse",
        licenseName = "BSD 2-Clause License",
        licenseFile = "$LICENSES/squashfuse_bsd_2.txt",
        licenseUrl = "https://opensource.org/license/bsd-2-clause",
        builds = APPIMAGE,
    ),
    apache("Stately", "Touchlab", "https://github.com/touchlab/Stately"),
    apache("Wuffs", "The Wuffs Authors", "https://github.com/google/wuffs", SKIA),
    OpenSourceComponent(
        name = "zlib",
        holder = "Jean-loup Gailly and Mark Adler",
        url = "https://zlib.net",
        licenseName = "zlib License",
        licenseFile = "$LICENSES/zlib.txt",
        licenseUrl = "https://zlib.net/zlib_license.html",
        builds = SKIA,
    ),
    OpenSourceComponent(
        name = "Zstandard",
        holder = "Meta Platforms, Inc. and affiliates",
        url = "https://facebook.github.io/zstd/",
        licenseName = BSD_3,
        licenseFile = "$LICENSES/zstd_bsd_3.txt",
        licenseUrl = BSD_3_URL,
        builds = APPIMAGE,
    ),
)
