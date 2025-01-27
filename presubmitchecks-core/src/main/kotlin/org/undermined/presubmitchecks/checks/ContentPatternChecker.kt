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

class ContentPatternChecker(
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

        /**
         * TODO Checks/Patterns
         *
         * Description/Title:
         * - DO NOT SUBMIT
         * - NO EMPTY DESCRIPTION
         * - conventional commits
         * - Gender Neutrality
         * - Inclusive language
         * - No tabs
         * - No trailing whitespace
         * - to do format
         * - long lines
         */

    }

    companion object {
        const val ID = "ContentPattern"

        val PROVIDER = object : CheckerProvider {
            override val id: String = ID

            override fun newChecker(
                config: JsonElement,
            ): ContentPatternChecker {
                return ContentPatternChecker(
                    config = Json.decodeFromJsonElement(config),
                )
            }
        }

        @Serializable
        data class PatternsConfig(
            override val severity: CheckerConfig.CheckerMode = CheckerConfig.CheckerMode.WARNING,
            val patterns: List<CommentPattern> = emptyList(),
        ) : CheckerConfig {
            @Serializable
            data class CommentPattern(
                val id: String,
                val message: String,
                val title: String? = null,
                val targetBranches: List<String> = emptyList(),
                val files: List<String> = emptyList(),
                val patterns: List<String> = emptyList(),
                val context: List<String> = listOf("file:name", "file:line:added", "cl:title", "cl:description"),
                val severity: CheckResultMessage.Severity? = null,
            )
        }
    }
}
