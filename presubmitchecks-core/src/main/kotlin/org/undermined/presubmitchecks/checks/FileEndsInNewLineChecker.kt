package org.undermined.presubmitchecks.checks

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import org.undermined.presubmitchecks.core.Changelist
import org.undermined.presubmitchecks.core.ChangelistVisitor
import org.undermined.presubmitchecks.core.CheckResultMessage
import org.undermined.presubmitchecks.core.Checker
import org.undermined.presubmitchecks.core.CheckerChangelistVisitorFactory
import org.undermined.presubmitchecks.core.CheckerConfig
import org.undermined.presubmitchecks.core.CheckerProvider
import org.undermined.presubmitchecks.core.CheckerReporter
import org.undermined.presubmitchecks.core.CoreConfig
import org.undermined.presubmitchecks.core.Repository
import org.undermined.presubmitchecks.core.toResultSeverity
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path
import java.util.Optional

class FileEndsInNewLineChecker(
    override val config: CoreConfig,
) :
    Checker,
    CheckerChangelistVisitorFactory {
    override fun newCheckVisitor(
        repository: Repository,
        changelist: Changelist,
        reporter: CheckerReporter
    ): Optional<ChangelistVisitor> {
        return Optional.of(object : ChangelistVisitor {
            override fun enterFile(file: Changelist.FileOperation): Boolean {
                val filename = when (file) {
                    is Changelist.FileOperation.AddedFile -> file.name
                    is Changelist.FileOperation.ModifiedFile -> file.name
                    is Changelist.FileOperation.RemovedFile -> null
                } ?: return false
                val shouldCheck = filename.split(".").let { parts ->
                    (1..<parts.size).map { parts.subList(it, parts.size).joinToString(".") }
                }.any { newLineFiles.contains(it) }
                if (shouldCheck) {
                    val lastLine = when (file) {
                        is Changelist.FileOperation.AddedFile -> file.patchLines.lastOrNull()
                        is Changelist.FileOperation.ModifiedFile -> file.patchLines.lastOrNull()
                        else -> null
                    }
                    if (lastLine != null) {
                        if (!lastLine.hasNewLine || lastLine.content == "") {
                            reporter.report(
                                CheckResultMessage(
                                    checkGroupId = ID,
                                    severity = config.severity.toResultSeverity(),
                                    title = "File Ending",
                                    message = "${file.name} must end in a single new line",
                                    location = CheckResultMessage.Location(
                                        file = Path.of(file.name),
                                        startLine = lastLine.line,
                                    ),
                                    fix = ::autoFix
                                )
                            )
                        }
                    }
                }
                return false
            }
        })
    }

    fun autoFix(inputStream: InputStream, outputStream: OutputStream): Boolean {
        val buffer = ByteArray(8192)
        var fileSize = 0
        var bytesRead: Int
        var newlineCount = 0
        val cri = '\r'.code
        val crb = cri.toByte()
        val lfi = '\n'.code
        val lfb = lfi.toByte()
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            var currentBufferNewLineCount = 0
            var previousBufferNewLineCount = newlineCount
            for (i in 0 until bytesRead) {
                val byte = buffer[i]
                if (byte == crb) {
                    currentBufferNewLineCount++
                } else if (byte == lfb) {
                    newlineCount++
                    currentBufferNewLineCount++
                } else {
                    // Write the ones from previous buffers.
                    while (previousBufferNewLineCount > 0) {
                        previousBufferNewLineCount--
                        outputStream.write(lfi)
                    }
                    currentBufferNewLineCount = 0
                    newlineCount = 0
                }
            }
            if (bytesRead > 0) {
                outputStream.write(buffer, 0, bytesRead - currentBufferNewLineCount)
                fileSize += bytesRead
            }
        }

        outputStream.write(lfi)
        return newlineCount != 1
    }

    companion object {
        const val ID = "FileEndsInNewLine"
        private val newLineFiles = setOf(
            // keep-sorted start
            "java",
            "kt",
            "kts",
            "md",
            "yaml",
            // keep-sorted end
        )

        val PROVIDER = object : CheckerProvider {
            override val id: String = ID

            override fun newChecker(
                config: JsonElement,
            ): FileEndsInNewLineChecker {
                return FileEndsInNewLineChecker(
                    config = Json.decodeFromJsonElement(config),
                )
            }
        }
    }
}
