import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
}

dependencies {
    implementation(project(":data:repository:implementation"))
    implementation(project(":data:source:local:implementation"))
    implementation(project(":data:source:remote:implementation"))
    implementation(project(":domain:implementation"))
    implementation(project(":presentation"))
    implementation(libs.compose.components.resources)
    implementation(compose.desktop.currentOs)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.koin.core)
    runtimeOnly(libs.kotlin.coroutines.swing) // Provides Dispatchers.Main for viewModelScope.
}

val versionName = project.property("campfire.versionName").toString()
group = "com.pandulapeter.campfire"
version = versionName

compose.desktop {
    application {
        mainClass = "com.pandulapeter.campfire.CampfireDesktopApplicationKt"
        // Package with the toolchain JDK rather than the JVM running Gradle, which may lack jpackage.
        javaHome = javaToolchains.launcherFor {
            languageVersion = JavaLanguageVersion.of(libs.versions.jvmTarget.get().toInt())
        }.get().metadata.installationPath.asFile.absolutePath
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Exe, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Campfire"
            packageVersion = versionName
            macOS {
                iconFile.set(project.file("src/main/resources/appIcon.icns"))
                chordProFileAssociations()
            }
            windows {
                iconFile.set(project.file("src/main/resources/appIcon.ico"))
                chordProFileAssociations()
            }
            linux {
                iconFile.set(project.file("src/main/composeResources/drawable/app_icon.png"))
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
