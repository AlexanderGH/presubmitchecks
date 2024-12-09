plugins {
    id("buildlogic.kotlin-library-conventions")
}

dependencies {
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.strikt)
}
