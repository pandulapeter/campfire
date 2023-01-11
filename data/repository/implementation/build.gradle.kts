plugins {
    id("kotlin")
}

dependencies {
    implementation(project(":data:repository:api"))
    implementation(project(":data:source:local:api"))
    implementation(project(":data:source:remote:api"))
    implementation(libs.koin.core)
    implementation(libs.kotlin.coroutines)
}