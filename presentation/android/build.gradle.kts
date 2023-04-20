@file:Suppress("UnstableApiUsage")

plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-kapt")
}

dependencies {
    implementation(project(":domain:api"))
    implementation(project(":presentation:android-debug-menu"))
    api(project(":presentation:shared")) // TODO: Should be an implementation detail
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.ui)
    implementation(libs.compose.reorderable)
    implementation(libs.google.android.material)
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
    compileOptions {// TODO: Remove this block after upgrading to Gradle 8.1.0.
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}