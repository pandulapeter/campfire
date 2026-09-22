/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
}

dependencies {
    implementation(project(":app:di"))
    implementation(project(":data:model"))
    implementation(project(":presentation"))
    implementation(libs.compose.components.resources)
    implementation(compose.desktop.currentOs)
    implementation(libs.koin.compose.viewmodel)
    runtimeOnly(libs.kotlin.coroutines.swing) // Provides Dispatchers.Main for viewModelScope.
}

val versionName = project.property("campfire.versionName").toString()
group = "com.pandulapeter.campfire"
version = versionName

compose.desktop {
    application {
        mainClass = "com.pandulapeter.campfire.CampfireDesktopApplicationKt"
        buildTypes.release.proguard {
            configurationFiles.from(project.file("proguard-rules.pro"))
        }
        // Package with the toolchain JDK rather than the JVM running Gradle, which may lack jpackage.
        javaHome = javaToolchains.launcherFor {
            languageVersion = JavaLanguageVersion.of(libs.versions.jvmTarget.get().toInt())
        }.get().metadata.installationPath.asFile.absolutePath
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Exe, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Campfire"
            packageVersion = versionName
            description = "Your songbook, on every screen you own"
            vendor = "Pandula Péter"
            copyright = "Copyright (c) Pandula Péter 2017-2026"
            // What jdeps finds on top of the modules Compose Desktop includes by itself, plus the two it cannot:
            // `jdk.localedata` is found through ServiceLoader at run time, so no class file names it, and without it
            // Locale.getDisplayLanguage answers in English whatever language it is asked in - the language filter
            // chips and the language picker would read "German" in a Hungarian app. `jdk.accessibility` is loaded by
            // the JDK itself and is what the Java Access Bridge lives in, without which Narrator and NVDA get nothing
            // from the Windows build. The packaged runtime holds the listed modules and nothing else, so one that is
            // missing is a NoClassDefFoundError - or, for these two, a silent wrong answer - that only an installed
            // build can show. `./gradlew :app:desktop:suggestRuntimeModules` prints what jdeps can see.
            modules("java.instrument", "java.management", "jdk.accessibility", "jdk.localedata", "jdk.unsupported")
            macOS {
                iconFile.set(project.file("src/main/resources/appIcon.icns"))
                // Not fileAssociation(): the plugin writes its own document type with the "****" OS type, which claims
                // every kind of file, and with no rank or content type. These are the iOS app's document types.
                infoPlist {
                    extraKeysRawXml = macChordProDocumentTypes()
                }
            }
            windows {
                iconFile.set(project.file("src/main/resources/appIcon.ico"))
                chordProFileAssociations()
                // jpackage creates no shortcut it is not asked for, and an installer asked for none installs an application
                // that is nowhere to be found: these are the Start menu entry and the one on the desktop.
                menu = true
                menuGroup = "Campfire"
                shortcut = true
                // Installs into the user's own profile, so the installer never asks for an administrator.
                perUserInstall = true
                // What makes a newer installer replace the installed version rather than land next to it, so it has to
                // stay what it is for as long as the app exists.
                upgradeUuid = "243e51a3-b5a0-49a7-9a61-2c276db59db9"
            }
            linux {
                iconFile.set(project.file("src/main/composeResources/drawable/app_icon.png"))
                // jpackage writes the .desktop entry — and with it the icon — only for a package that asks for a
                // shortcut or declares file associations, and this one declares none: a MIME type is what a Linux
                // association is keyed by, and ChordPro files have none of their own to claim.
                shortcut = true
                menuGroup = "AudioVideo;Audio;Music"
                appCategory = "sound"
                debMaintainer = "pandulapeter@gmail.com"
            }
        }
    }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "com.pandulapeter.campfire.resources"
    generateResClass = always
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
}

/**
 * The ChordPro extensions the Windows installer claims: the ones no other application uses. An MSI can only make an
 * application an extension's handler outright - there is no Alternate rank to register the shared ones with, the
 * way iOS and macOS do - so ".crd", ".chord" and ".pro" (Qt's project files among others) are left to whoever has
 * them, and are imported from inside the app. They are all registered as plain text, which is what they are; zip and
 * .txt are deliberately not here, since an app that claims those would be answering for every archive and note on
 * the machine.
 *
 * A subset of `LibraryFiles.SONG_EXTENSIONS` in `:data:model`, which the build file cannot see.
 */
fun org.jetbrains.compose.desktop.application.dsl.AbstractPlatformSettings.chordProFileAssociations() =
    listOf("cho", "chopro", "chordpro").forEach { extension ->
        fileAssociation(mimeType = "text/plain", extension = extension, description = "ChordPro song")
    }

/**
 * The macOS document types, the same as the iOS app's (`app/ios/iosApp/iosApp/Info.plist`): ".cho" claimed as the
 * default, the rest of the family as an alternate, since those extensions are shared with other kinds of file, all
 * of them imported as types that conform to plain text. Kept in step with `LibraryFiles.SONG_EXTENSIONS` in
 * `:data:model`, which the build file cannot see.
 */
fun macChordProDocumentTypes() = """
    <key>UTImportedTypeDeclarations</key>
    <array>
        <dict>
            <key>UTTypeIdentifier</key>
            <string>org.chordpro.cho</string>
            <key>UTTypeDescription</key>
            <string>ChordPro song</string>
            <key>UTTypeConformsTo</key>
            <array>
                <string>public.plain-text</string>
            </array>
            <key>UTTypeTagSpecification</key>
            <dict>
                <key>public.filename-extension</key>
                <array>
                    <string>cho</string>
                </array>
            </dict>
        </dict>
        <dict>
            <key>UTTypeIdentifier</key>
            <string>org.chordpro.chordpro</string>
            <key>UTTypeDescription</key>
            <string>ChordPro song</string>
            <key>UTTypeConformsTo</key>
            <array>
                <string>public.plain-text</string>
            </array>
            <key>UTTypeTagSpecification</key>
            <dict>
                <key>public.filename-extension</key>
                <array>
                    <string>chopro</string>
                    <string>chordpro</string>
                    <string>crd</string>
                    <string>chord</string>
                    <string>pro</string>
                </array>
            </dict>
        </dict>
    </array>
    <key>CFBundleDocumentTypes</key>
    <array>
        <dict>
            <key>CFBundleTypeName</key>
            <string>ChordPro song</string>
            <key>CFBundleTypeRole</key>
            <string>Editor</string>
            <key>CFBundleTypeIconFile</key>
            <string>Campfire.icns</string>
            <key>LSHandlerRank</key>
            <string>Default</string>
            <key>LSItemContentTypes</key>
            <array>
                <string>org.chordpro.cho</string>
            </array>
        </dict>
        <dict>
            <key>CFBundleTypeName</key>
            <string>ChordPro song</string>
            <key>CFBundleTypeRole</key>
            <string>Editor</string>
            <key>CFBundleTypeIconFile</key>
            <string>Campfire.icns</string>
            <key>LSHandlerRank</key>
            <string>Alternate</string>
            <key>LSItemContentTypes</key>
            <array>
                <string>org.chordpro.chordpro</string>
            </array>
        </dict>
    </array>
""".trimIndent()
