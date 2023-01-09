plugins {
    id("kotlin")
}

dependencies {
    api(project(":data:model"))
    implementation(project(":data:repository"))
    implementation(libs.koin.core)
    implementation(libs.kotlin.coroutines)
}