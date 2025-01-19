plugins {
    id("buildlogic.kotlin-library-conventions")
    alias(libs.plugins.kotlinx.serialization.json)
    alias(libs.plugins.maven.publish)
}

version = "0.0.1-SNAPSHOT"

dependencies {
    // keep-sorted start template=gradle-dependencies
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.re2j)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.strikt)
    // keep-sorted end
}

mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()

    coordinates(artifactId = "core")
    pom {
        name.set("Presubmit Checks Core")
        description.set("Core libraries for presubmit checks.")
    }
}
