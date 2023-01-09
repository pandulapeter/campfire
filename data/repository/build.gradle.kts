plugins {
    id("kotlin")
}

dependencies {
    api(project(":data:model"))
    implementation(project(":data:source:local"))
    implementation(project(":data:source:remote"))
    implementation(libs.koin.core)
    implementation(libs.kotlin.coroutines)
}