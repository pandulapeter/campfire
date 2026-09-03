plugins {
    id("kotlin")
    id("com.google.devtools.ksp") version libs.versions.kotlin.ksp.get()
}

dependencies {
    api(project(":data:source:remote:api"))
    implementation(libs.koin.core)
    implementation(libs.kotlin.coroutines)
    // Keeps kotlin-reflect in step with the Kotlin version; Moshi/Retrosheet still request 1.8.21.
    implementation(libs.kotlin.reflect)
    implementation(libs.square.moshi)
    implementation(libs.square.okhttp)
    implementation(libs.square.retrofit)
    implementation(libs.square.retrofit.converter)
    implementation(libs.theapache64.retrosheet)
    ksp(libs.square.moshi.codegen)
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
}