plugins {
    id("buildlogic.kotlin-application-conventions")
}

dependencies {
    implementation(project(":presubmitchecks-core"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.clikt)
}

application {
    mainClass = "org.undermined.presubmitchecks.AppKt"
}

tasks.register<Jar>("fatJar") {
    dependsOn.addAll(listOf("compileJava", "compileKotlin", "processResources"))
    archiveClassifier.set("standalone")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest { attributes(mapOf("Main-Class" to application.mainClass)) }
    val sourcesMain = sourceSets.main.get()
    val contents = configurations.runtimeClasspath.get()
        .map { if (it.isDirectory) it else zipTree(it) } +
            sourcesMain.output
    from(contents)
}