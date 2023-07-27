plugins {
    id("com.android.library")
    id("kotlin-android")
}

dependencies {
    implementation(project(":data:model"))
    implementation(libs.androidx.appCompat)
    debugImplementation(libs.beagle)
    debugImplementation(libs.beagle.crashLogger)
}

android {
    val targetSdkVersion = System.getProperty("TARGET_SDK_VERSION").toInt()
    compileSdk = targetSdkVersion
    defaultConfig.minSdk = System.getProperty("MIN_SDK_VERSION").toInt()
    namespace = "com.pandulapeter.campfire.presentation.androidDebugMenu"
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
}