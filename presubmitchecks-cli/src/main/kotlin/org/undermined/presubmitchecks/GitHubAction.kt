package org.undermined.presubmitchecks

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.CliktError

class GitHubAction : SuspendingCliktCommand("github-action") {
    override suspend fun run() {
        throw CliktError(
            message = System.getenv().entries.joinToString(";") {
                "${it.key}=${it.value}"
            },
            statusCode = 2,
        )
    }
}