plugins {
    id("kotlin")
    id("org.jetbrains.compose") version libs.versions.jetbrains.compose.get()
}

dependencies {
    implementation(project(":domain:api"))
    api(project(":presentation:shared")) // TODO: Should be an implementation detail
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.reorderable)
    implementation(libs.koin.core)
    implementation(libs.kotlin.coroutines)
}