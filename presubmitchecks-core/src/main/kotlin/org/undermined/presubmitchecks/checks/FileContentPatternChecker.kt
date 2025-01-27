package org.undermined.presubmitchecks.checks

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import org.undermined.presubmitchecks.core.Changelist
import org.undermined.presubmitchecks.core.ChangelistVisitor
import org.undermined.presubmitchecks.core.CheckResultMessage
import org.undermined.presubmitchecks.core.Checker
import org.undermined.presubmitchecks.core.CheckerChangelistVisitorFactory
import org.undermined.presubmitchecks.core.CheckerConfig
import org.undermined.presubmitchecks.core.CheckerFileCollectionVisitorFactory
import org.undermined.presubmitchecks.core.CheckerProvider
import org.undermined.presubmitchecks.core.CheckerReporter
import org.undermined.presubmitchecks.core.FileCollection
import org.undermined.presubmitchecks.core.Repository
import java.util.Optional

class FileContentPatternChecker(
    override val config: PatternsConfig,
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

    class Checker(private val reporter: CheckerReporter) :
        ChangelistVisitor,
        ChangelistVisitor.FileVisitor {

    }

    companion object {
        const val ID = "PatternComment"

        val PROVIDER = object : CheckerProvider {
            override val id: String = ID

            override fun newChecker(
                config: JsonElement,
            ): FileContentPatternChecker {
                return FileContentPatternChecker(
                    config = Json.decodeFromJsonElement(config),
                )
            }
        }

        @Serializable
        data class PatternsConfig(
            override val severity: CheckerConfig.CheckerMode = CheckerConfig.CheckerMode.WARNING,
            val patterns: List<CommentPattern>,
        ) : CheckerConfig {
            @Serializable
            data class CommentPattern(
                val id: String,
                val message: String,
                val title: String? = null,
                val targetBranches: List<String> = emptyList(),
                val files: List<String> = emptyList(),
                val lines: List<String> = emptyList(),
                val modifications: List<String> = listOf("added"),
                val severity: CheckResultMessage.Severity? = null,
            )
        }
    }
}