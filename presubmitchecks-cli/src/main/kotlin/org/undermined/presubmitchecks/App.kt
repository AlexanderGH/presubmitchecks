package org.undermined.presubmitchecks

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.core.subcommands
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) = runBlocking {
    PresubmitChecks()
        .subcommands(
            FilesCommand(),
            GitHubAction(),
            GitPreCommit(),
        )
        .main(args)
}

private class PresubmitChecks : SuspendingCliktCommand(name = "presubmit") {
    override suspend fun run() = Unit
}
