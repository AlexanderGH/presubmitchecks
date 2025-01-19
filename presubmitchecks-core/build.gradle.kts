plugins {
    id("buildlogic.kotlin-library-conventions")
    alias(libs.plugins.maven.publish)
}

version = "0.0.1-SNAPSHOT"

dependencies {
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.strikt)
}

mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()
}