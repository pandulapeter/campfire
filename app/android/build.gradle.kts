@file:Suppress("UnstableApiUsage")

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

dependencies {
    implementation(project(":data:repository:implementation"))
    implementation(project(":data:source:local:implementation"))
    implementation(project(":data:source:remote:implementation"))
    implementation(project(":domain:implementation"))
    implementation(project(":presentation:android"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appCompat)
    implementation(libs.androidx.browser)
    implementation(libs.google.material)
    implementation(libs.koin.android)
}

android {
    namespace = "com.pandulapeter.campfire"
    val targetSdkVersion = libs.versions.android.compileSdk.get().toInt()
    compileSdk = targetSdkVersion
    defaultConfig {
        applicationId = "com.pandulapeter.campfire"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = targetSdkVersion
        versionCode = System.getProperty("VERSION_CODE").toInt()
        versionName = System.getProperty("VERSION_NAME")
    }
    buildFeatures.compose = true
    val internalSigningConfig = "internal"
    val releaseSigningConfig = "release"
    signingConfigs {
        create(internalSigningConfig) {
            keyAlias = "androiddebugkey"
            keyPassword = "android"
            storeFile = file("internal.keystore")
            storePassword = "android"
        }
        create(releaseSigningConfig) {
            keyAlias = System.getProperty("KEY_ALIAS")
            keyPassword = System.getProperty("KEY_PASSWORD")
            storeFile = file(System.getProperty("STORE_FILE"))
            storePassword = System.getProperty("STORE_PASSWORD")
        }
    }
    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
            versionNameSuffix = "-debug"
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName(internalSigningConfig)
        }
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(releaseSigningConfig)
        }
    }
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
}