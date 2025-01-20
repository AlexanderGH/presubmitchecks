package org.undermined.presubmitchecks

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.optionalValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.undermined.presubmitchecks.core.Changelist
import org.undermined.presubmitchecks.core.CheckResult
import org.undermined.presubmitchecks.core.CheckResultDebug
import org.undermined.presubmitchecks.core.CheckResultMessage
import org.undermined.presubmitchecks.core.CheckerRegistry
import org.undermined.presubmitchecks.core.CheckerReporter
import org.undermined.presubmitchecks.core.CheckerService
import org.undermined.presubmitchecks.core.runChecks
import org.undermined.presubmitchecks.fixes.FileFixFilter
import org.undermined.presubmitchecks.fixes.Fixes
import org.undermined.presubmitchecks.git.GitChangelists
import org.undermined.presubmitchecks.git.GitLocalRepository
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlin.io.path.pathString

internal class GitPreCommit : SuspendingCliktCommand() {
    val config by option(help="Configuration file path").optionalValue("")

    val diff by option(help="Git diff output file. - for stdin. @exec to execute git")
        .default("@exec")

    val fix by option(help="Apply fixes to any changed files.")
        .flag(default = false)

    override fun help(context: Context) = "Run presubmit checks as a git pre-commit."

    override suspend fun run() {
        val changelist = Changelist(
            title = "",
            description = "",
            patchOnly = false,
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
        )

        val globalConfig: CheckerService.GlobalConfig = config?.let { filePath ->
            File(filePath).takeIf { it.exists() }?.inputStream()?.use {
                Json.decodeFromStream(it)
            }
        } ?: CheckerService.GlobalConfig()
        val checkerService = CheckerRegistry.newServiceFromConfig(globalConfig)

        var hasFailure = false
        val repository = GitLocalRepository(File(""))
        val fixes = mutableListOf<Pair<String, FileFixFilter>>()
        val reporter = object : CheckerReporter {
            override fun report(result: CheckResult) {
                when (result) {
                    is CheckResultMessage -> {
                        val fixFilter = result.fix
                        val fixPath = result.location?.file
                        if (fix && fixFilter != null && fixPath != null) {
                            fixes.add(fixPath.pathString to fixFilter)
                        } else {
                            hasFailure = true
                            echo(result.toConsoleOutput(), err = result.severity == CheckResultMessage.Severity.ERROR)
                            echo("", err = result.severity == CheckResultMessage.Severity.ERROR)
                        }
                    }
                    is CheckResultDebug -> {
                        echo(result.message)
                    }
                }
            }

            override suspend fun flush() = Unit
        }

        checkerService.runChecks(repository, changelist, reporter)
        reporter.flush()

        if (fixes.isNotEmpty()) {
            val fixGroups = fixes.groupBy({ it.first }, { it.second })
            ByteArrayOutputStream(4096 * 8).use { tmpBuffer ->
                fixGroups.forEach { fixesForFile ->
                    val transform = Fixes.chainStreamModifiers(fixesForFile.value)
                    val hasChanges = repository.readFile(fixesForFile.key, "").use {
                        transform(it, tmpBuffer)
                    }
                    if (hasChanges) {
                        echo("Fixing: ${fixesForFile.key}")
                        try {
                            repository.writeFile(fixesForFile.key) {
                                tmpBuffer.writeTo(it)
                            }
                        } catch (e: IOException) {
                            echo("Could not fix: ${fixesForFile.key}", err = true)
                        }
                    }
                    tmpBuffer.reset()
                }
                tmpBuffer.close()
            }

        }

        if (hasFailure) {
            throw CliktError(statusCode = 1)
        }
    }
}