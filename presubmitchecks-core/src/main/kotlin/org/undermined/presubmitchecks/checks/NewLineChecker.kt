package org.undermined.presubmitchecks.checks

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import org.undermined.presubmitchecks.core.Changelist
import org.undermined.presubmitchecks.core.ChangelistVisitor
import org.undermined.presubmitchecks.core.CheckResultFix
import org.undermined.presubmitchecks.core.CheckResultMessage
import org.undermined.presubmitchecks.core.Checker
import org.undermined.presubmitchecks.core.CheckerChangelistVisitorFactory
import org.undermined.presubmitchecks.core.CheckerFileCollectionVisitorFactory
import org.undermined.presubmitchecks.core.CheckerProvider
import org.undermined.presubmitchecks.core.CheckerReporter
import org.undermined.presubmitchecks.core.CoreConfig
import org.undermined.presubmitchecks.core.FileCollection
import org.undermined.presubmitchecks.core.Repository
import org.undermined.presubmitchecks.core.toResultSeverity
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.Optional

class NewLineChecker(
    override val config: CoreConfig,
) :
    Checker,
    CheckerChangelistVisitorFactory,
    CheckerFileCollectionVisitorFactory {

    override fun newCheckVisitor(
        repository: Repository,
        changelist: Changelist,
        reporter: CheckerReporter
    ): Optional<ChangelistVisitor> {
        return Optional.of(Checker(reporter))
    }

    override fun newCheckVisitor(
        repository: Repository,
        fileCollection: FileCollection,
        reporter: CheckerReporter
    ): Optional<ChangelistVisitor.FileVisitor> {
        return Optional.of(Checker(reporter))
    }

    internal inner class Checker(
        private val reporter: CheckerReporter
    ) : ChangelistVisitor,
        ChangelistVisitor.FileVisitor,
        ChangelistVisitor.FileVisitor.FileAfterSequentialVisitor {
        override fun enterFile(file: Changelist.FileOperation): Boolean {
            if (file is Changelist.FileOperation.RemovedFile || file.isBinary) {
                return false
            }
            if (newLineFiles.any {
                file.name.endsWith(it)
            }) {
                /*
                val lastLine = when (file) {
                    is Changelist.FileOperation.AddedFile -> file.patchLines.lastOrNull()
                    is Changelist.FileOperation.ModifiedFile -> file.patchLines.lastOrNull {
                        it.operation == Changelist.ChangeOperation.ADDED
                    }
                    else -> null
                }
                if (lastLine != null) {
                    if (!lastLine.hasNewLine) {
                        reporter.report(
                            CheckResultMessage(
                                checkGroupId = ID,
                                severity = config.severity.toResultSeverity(),
                                title = "File Ending",
                                message = "File must end in a single new line.",
                                location = CheckResultMessage.Location(
                                    file = file.name,
                                    startLine = lastLine.line,
                                ),
                                fix = CheckResultFix(
                                    fixId = ID,
                                    file = file.name,
                                    transform = ::autoFix
                                ),
                            )
                        )
                        return false
                    }
                }
                */
                return true
            }
            return false
        }

        override suspend fun visitAfterFile(name: String, inputStream: InputStream) {
            val buffer = ByteBuffer.allocate(4096)
            var totalBytes = 0
            var bytesRead: Int = 0
            var currentLine = 1
            var totalCr = 0
            var lastWasCr = false
            var trailingNl = 0

            while (inputStream.read(buffer.array()).also { bytesRead = it } != -1) {
                if (bytesRead == 0) break // Handle empty streams
                for (i in 0 until bytesRead) {
                    val byte = buffer.get(i)
                    when (byte) {
                        LF_BYTE -> {
                            if (!lastWasCr) {
                                trailingNl++
                                currentLine++
                            }
                            lastWasCr = false
                        }
                        CR_BYTE -> {
                            lastWasCr = true
                            trailingNl++
                            currentLine++
                            totalCr++
                        }
                        else -> {
                            lastWasCr = false
                            trailingNl = 0
                        }
                    }
                }
                totalBytes += bytesRead
            }

            val issues = mutableListOf<String>()
            if (totalCr > 0) {
                issues.add("File must use only \\n. Found $totalCr instances of \\r.")
            }
            if (totalBytes > 0 && trailingNl != 1) {
                issues.add("File must end in a single new line. Found $trailingNl new lines.")
            }

            if (issues.isNotEmpty()) {
                reporter.report(
                    CheckResultMessage(
                        checkGroupId = ID,
                        severity = config.severity.toResultSeverity(),
                        title = "New Lines",
                        message = issues.joinToString(" "),
                        location = CheckResultMessage.Location(
                            file = name,
                        ),
                        fix = CheckResultFix(
                            fixId = ID,
                            file = name,
                            transform = ::autoFix
                        ),
                    )
                )
            }

            /*
            val last4 = ArrayDeque<Byte>(4)
            while (inputStream.available() > 4) {
                inputStream.skip(inputStream.available() - 4L)
            }

            while (inputStream.read(buffer.array()).also { bytesRead = it } != -1) {
                if (bytesRead == 0) break // Handle empty streams
                if (bytesRead > 4) {
                    last4.clear()
                }
                for (i in (bytesRead - 4).coerceAtLeast(0) until bytesRead) {
                    if (last4.size == 4) {
                        last4.removeFirst()
                    }
                    last4.addLast(buffer.get(i))
                }
            }
            val nlCount = last4.reversed()
                .takeWhile { it == LF_BYTE || it == CR_BYTE }
                .count { it == LF_BYTE }
            if (last4.isNotEmpty() && nlCount != 1) {
                reporter.report(
                    CheckResultMessage(
                        checkGroupId = ID,
                        severity = config.severity.toResultSeverity(),
                        title = "File Ending",
                        message = "File must end in a single new line. Found $nlCount new lines.",
                        location = CheckResultMessage.Location(
                            file = name,
                        ),
                        fix = CheckResultFix(
                            fixId = ID,
                            file = name,
                            transform = ::autoFix
                        ),
                    )
                )
            }
            */
        }
    }

    fun autoFix(inputStream: InputStream, outputStream: OutputStream): Boolean {
        val buffer = ByteArray(8192)
        var bytesRead: Int = 0
        var changes = 0
        var lastByte = LF_BYTE
        var lastNonNewLine = 0
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            for (i in 0 until bytesRead) {
                val byte = buffer[i]
                when (byte) {
                    LF_BYTE -> {
                        if (lastNonNewLine > 0) {
                            outputStream.write(buffer, i - lastNonNewLine, lastNonNewLine)
                            lastNonNewLine = 0
                        }
                        if (lastByte != CR_BYTE) {
                            outputStream.write(LF_CODE)
                        }
                    }
                    CR_BYTE -> {
                        if (lastNonNewLine > 0) {
                            outputStream.write(buffer, i - lastNonNewLine, lastNonNewLine)
                            lastNonNewLine = 0
                        }
                        outputStream.write(LF_CODE)
                        changes++
                    }
                    else -> {
                        lastNonNewLine++
                    }
                }
                lastByte = byte
            }
            if (lastNonNewLine > 0) {
                outputStream.write(buffer, bytesRead - lastNonNewLine, lastNonNewLine)
            }
        }

        if (lastByte != LF_BYTE && lastByte != CR_BYTE) {
            outputStream.write(LF_CODE)
            changes++
        }
        return changes != 1
    }

    /*
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
    */

    companion object {
        const val ID = "NewLine"

        const val LF = '\n'
        const val LF_CODE = LF.code
        const val LF_BYTE = LF_CODE.toByte()
        const val CR = '\r'
        const val CR_CODE = CR.code
        const val CR_BYTE = CR_CODE.toByte()

        private val newLineFiles = setOf(
            // keep-sorted start
            ".gradle",
            ".java",
            ".json",
            ".kt",
            ".kts",
            ".md",
            ".py",
            ".txt",
            ".yaml",
            ".yml",
            // keep-sorted end
        )

        val PROVIDER = object : CheckerProvider {
            override val id: String = ID

            override fun newChecker(
                config: JsonElement,
            ): NewLineChecker {
                return NewLineChecker(
                    config = Json.decodeFromJsonElement(config),
                )
            }
        }
    }
}
