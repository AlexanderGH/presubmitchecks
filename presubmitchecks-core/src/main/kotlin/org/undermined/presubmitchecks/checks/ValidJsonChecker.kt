package org.undermined.presubmitchecks.checks

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.decodeFromStream
import org.undermined.presubmitchecks.core.Changelist
import org.undermined.presubmitchecks.core.ChangelistVisitor
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
import org.undermined.presubmitchecks.core.Repository
import org.undermined.presubmitchecks.core.toResultSeverity
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.file.Path
import java.util.Optional

class ValidJsonChecker(
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
            if (file is Changelist.FileOperation.RemovedFile || !file.name.endsWith(".json")) {
                return false
            }
            if (file.isBinary) {
                reporter.report(
                    CheckResultMessage(
                        checkGroupId = ID,
                        severity = config.severity.toResultSeverity(),
                        title = "Invalid JSON",
                        message = "Expected JSON but found a binary file.",
                        location = CheckResultMessage.Location(
                            file = file.name,
                        ),
                    )
                )
                return false
            }
            return true
        }

        override suspend fun visitAfterFile(name: String, inputStream: InputStream) {
            try {
                Json.decodeFromStream<JsonElement>(inputStream)
            } catch (e: SerializationException) {
                reporter.report(
                    CheckResultMessage(
                        checkGroupId = ID,
                        severity = config.severity.toResultSeverity(),
                        title = "Invalid JSON",
                        message = "${e.message}",
                        location = CheckResultMessage.Location(
                            file = name,
                        ),
                    )
                )
            }
        }
    }

    companion object {
        const val ID = "ValidJson"

        val PROVIDER = object : CheckerProvider {
            override val id: String = ID

            override fun newChecker(
                config: JsonElement,
            ): ValidJsonChecker {
                return ValidJsonChecker(
                    config = Json.decodeFromJsonElement(config),
                )
            }
        }
    }
}
