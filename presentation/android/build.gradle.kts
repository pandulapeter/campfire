@file:Suppress("UnstableApiUsage")

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
}

dependencies {
    implementation(project(":domain:api"))
    implementation(project(":presentation:android-debug-menu"))
    api(project(":presentation:shared")) // TODO: Should be an implementation detail
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.animation)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material)
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.google.material)
    implementation(libs.koin.android)
    implementation(libs.kotlin.coroutines)
}

android {
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig.minSdk = libs.versions.android.minSdk.get().toInt()
    buildFeatures.compose = true
    namespace = "com.pandulapeter.campfire.presentation.android"
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
}
