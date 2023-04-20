plugins {
    id("com.android.library")
    id("kotlin-android")
}

dependencies {
    implementation(project(":data:model"))
    implementation(libs.androidx.appcompat)
    debugImplementation(libs.beagle)
    debugImplementation(libs.beagle.crashLogger)
}

android {
    val targetSdkVersion = System.getProperty("TARGET_SDK_VERSION").toInt()
    compileSdk = targetSdkVersion
    defaultConfig.minSdk = System.getProperty("MIN_SDK_VERSION").toInt()
    namespace = "com.pandulapeter.campfire.presentation.androidDebugMenu"
    compileOptions { // TODO: Remove this block after upgrading to Gradle 8.1.0.
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}