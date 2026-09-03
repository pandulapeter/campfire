plugins {
    id("kotlin")
}

dependencies {
    api(project(":data:model"))
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
}