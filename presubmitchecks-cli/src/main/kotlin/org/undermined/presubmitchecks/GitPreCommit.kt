package org.undermined.presubmitchecks

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.undermined.presubmitchecks.checks.IfChangeThenChangeChecker
import org.undermined.presubmitchecks.core.Changelist
import org.undermined.presubmitchecks.core.visit
import org.undermined.presubmitchecks.git.GitChangelists
import java.io.File

internal class GitPreCommit : SuspendingCliktCommand() {
    val diff by option(help="Git diff output file. - for stdin. @exec to execute git")
        .default("@exec")

    override fun help(context: Context) = "Run presubmit checks as a git pre-commit."

    override suspend fun run() {
        val changelist = Changelist(
            title = "",
            description = "",
            files = when (diff) {
                "-" -> {
                    GitChangelists.parseDiff(generateSequence(::readLine))
                }

                "@exec" -> {
                    withContext(Dispatchers.IO) {
                        val process = Runtime.getRuntime().exec(arrayOf("git", "diff", "HEAD"))
                        try {
                            GitChangelists.parseDiff(
                                process.inputStream.bufferedReader().lineSequence()
                            )
                        } finally {
                            process.destroy()
                        }
                    }
                }

                else -> {
                    withContext(Dispatchers.IO) {
                        GitChangelists.parseDiff(File(diff).bufferedReader().use {
                            it.lineSequence()
                        })
                    }
                }
            },
        )

        val ifChangeThenChange = IfChangeThenChangeChecker()

        changelist.visit(listOf(ifChangeThenChange))

        val results = ifChangeThenChange.getResults()
        results.forEach {
            println(it.toConsoleOutput())
            println("\n")
        }
        if (results.isNotEmpty()) {
            throw CliktError(statusCode = 1)
        }
    }
}