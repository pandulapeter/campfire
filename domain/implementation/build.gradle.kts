plugins {
    id("kotlin")
}

dependencies {
    api(project(":domain:api"))
    implementation(project(":data:repository:api"))
    implementation(libs.koin.core)
    implementation(libs.kotlin.coroutines)
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
}