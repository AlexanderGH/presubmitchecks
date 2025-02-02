package org.undermined.presubmitchecks.checks

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import org.undermined.presubmitchecks.checks.ContentPatternChecker.Companion.PatternsConfig.CommentPattern
import org.undermined.presubmitchecks.core.Changelist
import org.undermined.presubmitchecks.core.ChangelistVisitor
import org.undermined.presubmitchecks.core.CheckResultFileFix
import org.undermined.presubmitchecks.core.CheckResultFix
import org.undermined.presubmitchecks.core.CheckResultMessage
import org.undermined.presubmitchecks.core.Checker
import org.undermined.presubmitchecks.core.CheckerChangelistVisitorFactory
import org.undermined.presubmitchecks.core.CheckerConfig
import org.undermined.presubmitchecks.core.CheckerFileCollectionVisitorFactory
import org.undermined.presubmitchecks.core.CheckerProvider
import org.undermined.presubmitchecks.core.CheckerReporter
import org.undermined.presubmitchecks.core.CoreConfig
import org.undermined.presubmitchecks.core.FileCollection
import org.undermined.presubmitchecks.core.FileVisitors
import org.undermined.presubmitchecks.core.Repository
import org.undermined.presubmitchecks.core.toResultSeverity
import org.undermined.presubmitchecks.fixes.Fixes
import org.undermined.presubmitchecks.fixes.Fixes.transformLines
import org.undermined.presubmitchecks.fixes.KeepSorted
import org.undermined.presubmitchecks.fixes.KeepSortedConfig
import org.undermined.presubmitchecks.fixes.KeepSortedSectionConfig
import java.io.InputStream
import java.io.OutputStream
import java.util.Optional
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class KeepSortedChecker(
    override val config: KeepSortedCheckerConfig,
) :
    Checker,
    CheckerChangelistVisitorFactory,
    CheckerFileCollectionVisitorFactory {

    private val keepSortedConfig = KeepSortedConfig(
        matchRegexp = KeepSortedConfig.pattern("kt"),
        templates = config.let {
            val templates = mutableMapOf<String, KeepSortedSectionConfig>()
            config.templates.forEach {
                templates[it.key] =
                    KeepSortedSectionConfig.parse(it.value, templates)
            }
            templates
        }
    )

    override fun newCheckVisitor(
        repository: Repository,
        changelist: Changelist,
        reporter: CheckerReporter
    ): Optional<ChangelistVisitor> {
        return Optional.of(Checker((reporter)))
    }

    override fun newCheckVisitor(
        repository: Repository,
        fileCollection: FileCollection,
        reporter: CheckerReporter
    ): Optional<ChangelistVisitor.FileVisitor> {
        return Optional.of(Checker(reporter))
    }

    inner class Checker(private val reporter: CheckerReporter) :
        ChangelistVisitor,
        ChangelistVisitor.FileVisitor,
        //ChangelistVisitor.FileVisitor.FileAfterLineVisitor,
        ChangelistVisitor.FileVisitor.FileAfterSequentialVisitor {

        override fun enterFile(file: Changelist.FileOperation): Boolean {
            return file.isText && file !is Changelist.FileOperation.RemovedFile
        }

        fun visitAfterLine(name: String, line: FileVisitors.FileLine): Boolean {
            if (line.content.contains("keep-sorted ")) {
                reporter.report(
                    CheckResultFileFix(
                        fixId = ID,
                        file = name,
                        transform = ::autoFix,
                    )
                )
            }
            return true
        }

        override suspend fun visitAfterFile(name: String, inputStream: InputStream) {
            val changed = AtomicInteger()
            val keepSorted = KeepSorted()
            Fixes.didLinesChange(inputStream.bufferedReader().lineSequence(), changed) {
                yieldAll(keepSorted.sort(
                    config = keepSortedConfig,
                    lines = it,
                ))
            }.count()
            if (changed.get() != -1) {
                reporter.report(
                    CheckResultMessage(
                        checkGroupId = ID,
                        title = "Not Sorted",
                        message = "This section should be kept sorted.",
                        severity = config.severity.toResultSeverity(),
                        location = CheckResultMessage.Location(
                            file = name,
                            startLine = changed.get(),
                        ),
                        fix = CheckResultFileFix(
                            fixId = ID,
                            file = name,
                            transform = ::autoFix,
                        )
                    )
                )
            }
        }
    }

    fun autoFix(inputStream: InputStream, outputStream: OutputStream): Boolean {
       return inputStream.transformLines(outputStream) {
           val keepSorted = KeepSorted()
           val sorted = keepSorted.sort(
               config = keepSortedConfig,
               lines = it
           )
           yieldAll(sorted)
       }
    }

    companion object {
        const val ID = "KeepSorted"

        val PROVIDER = object : CheckerProvider {
            override val id: String = ID

            override fun newChecker(
                config: JsonElement,
            ): KeepSortedChecker {
                return KeepSortedChecker(
                    config = Json.decodeFromJsonElement(config),
                )
            }
        }

        @Serializable
        data class KeepSortedCheckerConfig(
            override val severity: CheckerConfig.CheckerMode = CheckerConfig.CheckerMode.WARNING,
            val templates: Map<String, String> = emptyMap(),
        ) : CheckerConfig
    }
}
