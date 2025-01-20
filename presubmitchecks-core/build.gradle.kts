plugins {
    id("buildlogic.kotlin-library-conventions")
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.re2j)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.strikt)
}
