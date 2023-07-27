@file:Suppress("UnstableApiUsage")

plugins {
    id("com.android.library")
    id("kotlin-android")
    id("org.jetbrains.compose") version libs.versions.jetbrains.compose.get()
}

dependencies {
    implementation(project(":domain:api"))
    implementation(project(":presentation:android-debug-menu"))
    api(project(":presentation:shared")) // TODO: Should be an implementation detail
    implementation(compose.desktop.currentOs)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.reorderable)
    implementation(libs.google.material)
    implementation(libs.koin.android)
    implementation(libs.kotlin.coroutines)
}

android {
    val targetSdkVersion = System.getProperty("TARGET_SDK_VERSION").toInt()
    compileSdk = targetSdkVersion
    defaultConfig.minSdk = System.getProperty("MIN_SDK_VERSION").toInt()
    buildFeatures.compose = true
    composeOptions.kotlinCompilerExtensionVersion = libs.versions.androidx.compose.compiler.get()
    namespace = "com.pandulapeter.campfire.presentation.android"
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
}