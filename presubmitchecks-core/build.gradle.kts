plugins {
    id("buildlogic.kotlin-library-conventions")
    alias(libs.plugins.kotlinx.serialization.json)
}

dependencies {
    // keep-sorted start template=gradle-dependencies
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.re2j)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.strikt)
    // keep-sorted end
}
