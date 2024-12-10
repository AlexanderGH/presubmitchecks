plugins {
    id("buildlogic.kotlin-application-conventions")
    alias(libs.plugins.kotlinx.serialization.json)
}

dependencies {
    implementation(project(":presubmitchecks-core"))

    implementation(libs.clikt)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
}

application {
    mainClass = "org.undermined.presubmitchecks.AppKt"
}