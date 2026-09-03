plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
}

dependencies {
    implementation(project(":domain:api"))
    api(project(":presentation:shared")) // TODO: Should be an implementation detail
    implementation(compose.desktop.currentOs)
    implementation(libs.koin.core)
    implementation(libs.kotlin.coroutines)
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
}
