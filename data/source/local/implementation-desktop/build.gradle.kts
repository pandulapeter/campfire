plugins {
    id("kotlin")
    id("io.realm.kotlin")
}

dependencies {
    api(project(":data:source:local:api"))
    implementation(libs.koin.core)
    implementation(libs.kotlin.coroutines)
    implementation(libs.realm)
}