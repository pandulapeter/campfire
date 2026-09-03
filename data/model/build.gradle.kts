plugins {
    id("kotlin")
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
}