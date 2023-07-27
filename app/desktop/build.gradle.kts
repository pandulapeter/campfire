import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("kotlin")
    id("org.jetbrains.compose") version libs.versions.jetbrains.compose.get()
}

dependencies {
    implementation(project(":data:repository:implementation"))
    implementation(project(":data:source:local:implementation-desktop"))
    implementation(project(":data:source:remote:implementation-jvm"))
    implementation(project(":domain:implementation"))
    implementation(project(":presentation:desktop"))
    implementation(compose.desktop.currentOs)
    implementation(libs.koin.core)
}

val versionName = System.getProperty("VERSION_NAME").orEmpty()
group = "com.pandulapeter.campfire"
version = versionName

compose.desktop {
    application {
        mainClass = "com.pandulapeter.campfire.CampfireDesktopApplicationKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Exe, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Campfire"
            packageVersion = versionName
            macOS {
                iconFile.set(project.file("appIcon.icns"))
            }
            windows {
                iconFile.set(project.file("appIcon.ico"))
            }
            linux {
                iconFile.set(project.file("appIcon.png"))
            }
        }
    }
}