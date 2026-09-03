plugins {
    id("kotlin")
    id("com.google.devtools.ksp") version libs.versions.kotlin.ksp.get()
}

dependencies {
    api(project(":data:source:local:api"))
    implementation(libs.koin.core)
    implementation(libs.kotlin.coroutines)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.sqlite.bundled)
    ksp(libs.androidx.room.codegen)
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
}
