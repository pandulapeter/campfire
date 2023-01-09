plugins {
    id("kotlin")
    id("com.google.devtools.ksp") version libs.versions.kotlin.ksp.get()
}

dependencies {
    api(project(":data:model"))
    implementation(libs.koin.core)
    implementation(libs.kotlin.coroutines)
    implementation(libs.square.moshi)
    implementation(libs.square.okhttp)
    implementation(libs.square.retrofit)
    implementation(libs.square.retrofit.converter)
    implementation(libs.theapache64.retrosheet)
    ksp(libs.square.moshi.codegen)
}