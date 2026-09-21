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
            // What jdeps finds on top of the modules Compose Desktop includes by itself; the packaged runtime holds the
            // listed modules and nothing else, so one that is missing is a NoClassDefFoundError only an installed build
            // can throw. `./gradlew :app:desktop:suggestRuntimeModules` prints the list.
            modules("java.instrument", "java.management", "jdk.unsupported")
            macOS {
                iconFile.set(project.file("src/main/resources/appIcon.icns"))
                chordProFileAssociations()
            }
            windows {
                iconFile.set(project.file("src/main/resources/appIcon.ico"))
                chordProFileAssociations()
                // An installer asked for neither of these installs an application that is nowhere to be found.
                menu = true
                menuGroup = "Campfire"
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
 * The extensions a ChordPro song is found under, ".cho" first. They are all registered as plain text, which is what
 * they are; zip and .txt are deliberately not here, since an app that claims those would be answering for every
 * archive and note on the machine.
 *
 * Kept in step with `LibraryFiles.SONG_EXTENSIONS` in `:data:model`, which the build file cannot see.
 */
fun org.jetbrains.compose.desktop.application.dsl.AbstractPlatformSettings.chordProFileAssociations() =
    listOf("cho", "chopro", "chordpro", "crd", "chord", "pro").forEach { extension ->
        fileAssociation(mimeType = "text/plain", extension = extension, description = "ChordPro song")
    }
