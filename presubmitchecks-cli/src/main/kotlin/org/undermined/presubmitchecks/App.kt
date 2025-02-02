package org.undermined.presubmitchecks

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.core.subcommands
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.net.URL
import java.util.Enumeration
import java.util.jar.Attributes
import java.util.jar.Manifest


fun main(args: Array<String>) = runBlocking {
    PresubmitChecks()
        .subcommands(
            FilesCommand(),
            GitHubAction(),
            GitPreCommit(),
            VersionCommand(),
        )
        .main(args)
}

private class PresubmitChecks : SuspendingCliktCommand(name = "presubmit") {
    override suspend fun run() = Unit
}

private class VersionCommand : SuspendingCliktCommand(name = "version") {
    override suspend fun run()  {
        val resources: Enumeration<URL> = VersionCommand::class.java.classLoader
            .getResources("META-INF/MANIFEST.MF")
        while (resources.hasMoreElements()) {
            resources.nextElement().openStream()?.use {
                try {
                    val manifest = Manifest(it);
                    val attr: Attributes = manifest.mainAttributes
                    val value: String? = attr.getValue("Implementation-Version")
                    if (value != null) {
                        //echo(value)
                        //return
                    }
                } catch (e: IOException) {}
            }
        }
        echo("unspecified")
    }
}
