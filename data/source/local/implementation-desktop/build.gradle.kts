plugins {
    id("kotlin")
}

dependencies {
    api(project(":data:source:local:api"))
    implementation(libs.koin.core)
    implementation(libs.kotlin.coroutines)
}