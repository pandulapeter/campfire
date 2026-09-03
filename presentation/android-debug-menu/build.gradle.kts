plugins {
    alias(libs.plugins.android.library)
}

dependencies {
    implementation(project(":data:model"))
    implementation(libs.androidx.appCompat)
    debugImplementation(libs.beagle)
    debugImplementation(libs.beagle.crashLogger)
}

android {
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig.minSdk = libs.versions.android.minSdk.get().toInt()
    namespace = "com.pandulapeter.campfire.presentation.androidDebugMenu"
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
}
