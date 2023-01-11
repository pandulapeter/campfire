plugins {
    id("kotlin")
}

dependencies {
    api(project(":data:model"))
    implementation(libs.kotlin.coroutines)
}