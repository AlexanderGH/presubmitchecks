package org.undermined.presubmitchecks

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.optionalValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.undermined.presubmitchecks.core.Changelist
import org.undermined.presubmitchecks.core.CheckResultDebug
import org.undermined.presubmitchecks.core.CheckResultMessage
import org.undermined.presubmitchecks.core.CheckerRegistry
import org.undermined.presubmitchecks.core.CheckerService
import org.undermined.presubmitchecks.core.applyFixes
import org.undermined.presubmitchecks.core.reporters.AggregationReporter
import org.undermined.presubmitchecks.core.reporters.CompositeReporter
import org.undermined.presubmitchecks.core.reporters.ConsolePromptReporter
import org.undermined.presubmitchecks.core.reporters.ConsoleReporter
import org.undermined.presubmitchecks.core.reporters.FixFilteringReporter
import org.undermined.presubmitchecks.core.runChecks
import org.undermined.presubmitchecks.git.GitChangelists
import org.undermined.presubmitchecks.git.GitLocalRepository
import java.io.File
import java.io.IOException
import java.util.function.Predicate
import kotlin.system.measureTimeMillis

internal class GitPreCommit : SuspendingCliktCommand() {
    val config by option(help="Configuration file path").optionalValue("")

    val diff by option(help="Git diff output file. - for stdin. @exec to execute git")
        .default("@exec")

    val prompt by option(help="Whether to prompt whether to continue, fail or auto-fix each issue.")
        .flag(default = false)

    val fix by option(help="Apply fixes to any changed files.")
        .flag(default = false)

    override fun help(context: Context) = "Run presubmit checks as a git pre-commit."

    @OptIn(ExperimentalSerializationApi::class)
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
                            process.inputStream.bufferedReader().use {
                                GitChangelists.parseDiff(it.lineSequence())
                            }
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
        ).let { changelist ->
            val previousRev = GitLocalRepository.getGitRevision(File("."))
                ?: error("Could not get current repo revision")
            changelist.copy(
                files = changelist.files.map { file ->
                    when (file) {
                        is Changelist.FileOperation.AddedFile -> file.copy(
                            afterRef = "",
                        )
                        is Changelist.FileOperation.ModifiedFile -> file.copy(
                            beforeRef = previousRev,
                            afterRef = ""
                        )
                        is Changelist.FileOperation.RemovedFile -> file.copy(
                            beforeRef = previousRev
                        )
                    }
                }
            )
        }

        val globalConfig: CheckerService.GlobalConfig = config?.let { filePath ->
            File(filePath).takeIf { it.exists() }?.inputStream()?.use {
                Json.decodeFromStream(it)
            }
        } ?: CheckerService.GlobalConfig()
        val checkerService = CheckerRegistry.newServiceFromConfig(globalConfig)

        val repository = GitLocalRepository(
            File("."),
            currentRef = lazy { "" }
        )

        val aggregationReporter = AggregationReporter()
        val reporter = CompositeReporter(
            listOf(aggregationReporter)
        ).let {
            val consoleReporter = ConsoleReporter(
                out = { echo(it) },
                err = { echo(it, err = true) },
                filter = Predicate {
                    it is CheckResultMessage || it is CheckResultDebug
                },
            )
            if (prompt) {
                ConsolePromptReporter(
                    it,
                    consoleReporter,
                    prompt = { message ->
                        echo(message, trailingNewline = false)
                        // TODO: Windows support :/
                        File("/dev/tty").bufferedReader().use { tty ->
                            tty.readLine().trim()
                        }
                    }
                )
            } else {
                CompositeReporter(listOf(it, consoleReporter))
            }
        }.let {
            if (fix) {
                it
            } else {
                FixFilteringReporter(it)
            }
        }

        checkerService.runChecks(repository, changelist, reporter)
        reporter.flush()

        if (fix) {
            checkerService.applyFixes(
                repository,
                files = changelist.files,
                fixes = aggregationReporter.fixes,
                ref = "",
                wrapper = { fixes, f ->
                    echo("Fixing: ${fixes.key} (${fixes.value.map { it.fixId }})")
                    try {
                        val time = measureTimeMillis {
                            f()
                        }
                        echo("Fixed: ${fixes.key} (${time}ms)")
                    } catch (e: IOException) {
                        echo("Could not fix: ${fixes.key}", err = true)
                    }
                }
            )
        }

        checkerService.close()

        if (aggregationReporter.results.filterIsInstance<CheckResultMessage>().any {
            it.severity == CheckResultMessage.Severity.ERROR
                    && (!fix || it.fix == null)
        }) {
            throw CliktError(statusCode = 1)
        }
    }
}
