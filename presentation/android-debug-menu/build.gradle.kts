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
    defaultConfig {
        minSdk = System.getProperty("MIN_SDK_VERSION").toInt()
        targetSdk = targetSdkVersion
    }
    kotlinOptions.jvmTarget = libs.versions.jvm.target.get()
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}