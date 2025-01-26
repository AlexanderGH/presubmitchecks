package org.undermined.presubmitchecks

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.optionalValue
import com.github.ajalt.clikt.parameters.options.varargValues
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.undermined.presubmitchecks.core.Changelist
import org.undermined.presubmitchecks.core.CheckResult
import org.undermined.presubmitchecks.core.CheckResultDebug
import org.undermined.presubmitchecks.core.CheckResultFix
import org.undermined.presubmitchecks.core.CheckResultMessage
import org.undermined.presubmitchecks.core.CheckerRegistry
import org.undermined.presubmitchecks.core.CheckerReporter
import org.undermined.presubmitchecks.core.CheckerService
import org.undermined.presubmitchecks.core.FileCollection
import org.undermined.presubmitchecks.core.LocalFilesystemRepository
import org.undermined.presubmitchecks.core.Repository
import org.undermined.presubmitchecks.core.runChecks
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.FileSystems
import java.nio.file.PathMatcher
import java.nio.file.Paths

class FilesCommand : SuspendingCliktCommand("files") {
    val config by option(help="Configuration file path").optionalValue("")

    val files by argument(help="Files to check. Reads from stdin if omitted.").multiple()

    val fix by option(help="Apply fixes the files.")
        .flag(default = false)

    override suspend fun run() {
        val basePath = File(".")
        val (filenames, repository) = if (files.isNotEmpty()) {
            val files = matchFiles(basePath, files)
            Pair(
                files.map { it.path },
                LocalFilesystemRepository(basePath)
            )
        } else {
            Pair(
                listOf(""),
                object : Repository, Repository.WritableRepository {
                    override suspend fun readFile(path: String, ref: String): InputStream {
                        check(path == "" && ref == "")
                        return System.`in`
                    }

                    override suspend fun writeFile(path: String, writer: (OutputStream) -> Unit) {
                        check(path == "")
                        writer(System.out)
                    }
                },
            )
        }

        val globalConfig: CheckerService.GlobalConfig = config?.let { filePath ->
            File(filePath).takeIf { it.exists() }?.inputStream()?.use {
                Json.decodeFromStream(it)
            }
        } ?: CheckerService.GlobalConfig()
        val checkerService = CheckerRegistry.newServiceFromConfig(globalConfig)

        val reporter = object : CheckerReporter {
            override fun report(result: CheckResult) {
                when (result) {
                    is CheckResultMessage -> {
                            echo(result.toConsoleOutput(), err = result.severity == CheckResultMessage.Severity.ERROR)
                            echo("", err = result.severity == CheckResultMessage.Severity.ERROR)
                    }
                    is CheckResultFix -> {
                        echo(result.toConsoleOutput())
                        echo("")
                    }
                    is CheckResultDebug -> {
                        echo(result.message)
                    }
                }
            }
        }

        checkerService.runChecks(repository, filenames, reporter)
        reporter.flush()
    }

    private fun matchFiles(base: File, items: List<String>): List<File> {
        val results = mutableListOf<File>()

        fun traverse(dir: File, matcher: PathMatcher) {
            if (!dir.exists() || !dir.isDirectory) {
                return
            }

            val files = dir.listFiles() ?: return

            for (file in files) {
                // Avoid creating a new Path object if not necessary
                if (file.isDirectory) {
                    if (matcher.matches(Paths.get(file.absolutePath))) {
                        results.add(file) // Add directory if it matches
                    }
                    traverse(file, matcher) // Recurse first, potentially avoiding path creation
                } else if (matcher.matches(Paths.get(file.absolutePath))) {
                    results.add(file) // Add file if it matches
                }
            }
        }
        items.forEach { glob ->
            val file = File(base, glob)
            if (file.exists()) {
                results.add(file)
            } else {
                val matcher = FileSystems.getDefault().getPathMatcher("glob:$glob")
                traverse(base, matcher)
            }
        }
        return results
    }
}