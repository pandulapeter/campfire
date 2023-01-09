plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-kapt")
    id("kotlin-parcelize")
}

dependencies {
    implementation(project(":data:model"))
    implementation(project(":domain"))
    implementation(project(":presentation:debugMenu"))
    implementation(project(":presentation:utilities"))
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.recyclerview)
    api(libs.androidx.swiperefreshlayout)
    implementation(libs.coil)
    implementation(libs.google.android.material)
    implementation(libs.kotlin.coroutines)
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
    buildFeatures {
        dataBinding = true
        viewBinding = true
    }
}