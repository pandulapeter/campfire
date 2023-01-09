plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-kapt")
}

dependencies {
    implementation(project(":data:repository"))
    implementation(project(":data:source:local"))
    implementation(project(":data:source:localImpl"))
    implementation(project(":data:source:remote"))
    implementation(project(":domain"))
    implementation(project(":presentation:debugMenu"))
    implementation(project(":presentation:utilities"))
    implementation(project(":presentation:shared"))
    implementation(project(":presentation:collections"))
    implementation(libs.androidx.appcompat)
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
    kotlinOptions.jvmTarget = libs.versions.jvm.target.get()
    buildFeatures.dataBinding = true
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
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