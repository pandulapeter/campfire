plugins {
    id("kotlin")
}

dependencies {
    api(project(":data:model"))
    implementation(libs.kotlin.coroutines)
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
}