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
    implementation(project(":presentation:desktop"))
    implementation(compose.desktop.currentOs)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.koin.core)
    runtimeOnly(libs.kotlin.coroutines.swing) // Provides Dispatchers.Main for viewModelScope.
}

val versionName = System.getProperty("VERSION_NAME").orEmpty()
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
            }
            windows {
                iconFile.set(project.file("src/main/resources/appIcon.ico"))
            }
            linux {
                iconFile.set(project.file("src/main/resources/appIcon.png"))
            }
        }
    }
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
}
