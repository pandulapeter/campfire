plugins {
    id("kotlin")
    id("org.jetbrains.compose") version libs.versions.jetbrains.compose.get()
}

dependencies {
    implementation(project(":domain:api"))
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.mpp)
    implementation(libs.compose.reorderable)
    implementation(libs.koin.core)
}