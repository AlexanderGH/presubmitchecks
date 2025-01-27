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
import org.undermined.presubmitchecks.core.Changelist.FileOperation
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
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
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
        val (fileCollection, repository) = if (files.isNotEmpty()) {
            val files = matchFiles(basePath, files)
            Pair(
                FileCollection(
                    files = files.map { file ->
                        FileOperation.AddedFile(
                            file.path,
                            patchLines = emptyList(),
                            afterRef = "",
                            isBinary = file.inputStream().buffered().use {
                                !LocalFilesystemRepository.isText(it)
                            }
                        )
                    }
                ),
                LocalFilesystemRepository(basePath)
            )
        } else {
            val input = ByteArrayInputStream(System.`in`.readAllBytes())
            Pair(
                FileCollection(
                    files = listOf(
                        FileOperation.AddedFile(
                            "",
                            patchLines = emptyList(),
                            afterRef = "",
                            isBinary = !LocalFilesystemRepository.isText(input, input.available())
                        )
                    )
                ),
                object : Repository, Repository.WritableRepository {
                    override suspend fun readFile(path: String, ref: String): InputStream {
                        check(path == "" && ref == "")
                        input.reset()
                        return input
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

        checkerService.runChecks(repository, fileCollection, reporter)
        reporter.flush()
    }

    private fun matchFiles(
        base: File,
        items: List<String>,
        includeDirectories: Boolean = false,
    ): List<File> {
        val results = mutableListOf<File>()

        fun traverse(dir: File, matcher: PathMatcher) {
            if (!dir.exists() || !dir.isDirectory) {
                return
            }

            val files = dir.listFiles() ?: return

            for (file in files) {
                if (file.isDirectory) {
                    if (includeDirectories && matcher.matches(Paths.get(file.absolutePath))) {
                        results.add(file)
                    }
                    traverse(file, matcher) // Recurse first, potentially avoiding path creation
                } else if (matcher.matches(Paths.get(file.absolutePath))) {
                    results.add(file)
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
