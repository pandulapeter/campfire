@file:Suppress("UnstableApiUsage")

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-kapt")
}

dependencies {
    implementation(project(":data:repository:implementation"))
    implementation(project(":data:source:local:implementation-android"))
    implementation(project(":data:source:remote:implementation-jvm"))
    implementation(project(":domain:implementation"))
    implementation(project(":presentation:android"))
    implementation(project(":presentation:android-debug-menu"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.browser)
    implementation(libs.google.android.material)
    implementation(libs.koin.android)
}

android {
    namespace = "com.pandulapeter.campfire"
    val targetSdkVersion = System.getProperty("TARGET_SDK_VERSION").toInt()
    compileSdk = targetSdkVersion
    defaultConfig {
        applicationId = "com.pandulapeter.campfire"
        minSdk = System.getProperty("MIN_SDK_VERSION").toInt()
        targetSdk = targetSdkVersion
        versionCode = System.getProperty("VERSION_CODE").toInt()
        versionName = System.getProperty("VERSION_NAME")
    }
    buildFeatures.compose = true
    composeOptions.kotlinCompilerExtensionVersion = libs.versions.androidx.compose.compiler.get()
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
    compileOptions {// TODO: Remove this block after upgrading to Gradle 8.1.0.
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}