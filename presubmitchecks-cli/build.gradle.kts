plugins {
    id("buildlogic.kotlin-application-conventions")
    alias(libs.plugins.kotlinx.serialization.json)
}

dependencies {
    // keep-sorted start template=gradle-dependencies
    implementation(project(":presubmitchecks-core"))
    // keep-sorted end

    // keep-sorted start template=gradle-dependencies
    implementation(libs.clikt)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    // keep-sorted end
}

application {
    mainClass = "org.undermined.presubmitchecks.AppKt"
}

tasks {
    register<Jar>("fatJar") {
        dependsOn.addAll(listOf("compileJava", "compileKotlin", "processResources"))
        archiveClassifier.set("standalone")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        manifest {
            attributes(
                mapOf(
                    "Main-Class" to application.mainClass,
                )
            )
        }
        val sourcesMain = sourceSets.main
        dependsOn(configurations.runtimeClasspath)
        from(configurations.runtimeClasspath.map {
            it.map { file ->
                if (file.isDirectory) {
                    file
                } else {
                    zipTree(file)
                }
            } + sourcesMain.get().output
        })
    }
}
