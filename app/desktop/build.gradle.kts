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
group = "com.pandulapeter.accountant"
version = versionName

compose.desktop {
    application {
        // TODO: https://github.com/JetBrains/compose-jb/tree/master/tutorials/Native_distributions_and_local_execution#app-icon
        mainClass = "com.pandulapeter.campfire.CampfireDesktopApplicationKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Campfire"
            packageVersion = versionName
        }
    }
}