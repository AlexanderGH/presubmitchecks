package org.undermined.presubmitchecks.checks

import com.google.re2j.Matcher
import com.google.re2j.Pattern
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
import org.undermined.presubmitchecks.core.FileVisitors
import org.undermined.presubmitchecks.core.Repository
import org.undermined.presubmitchecks.core.toResultSeverity
import java.util.Optional

class ContentPatternChecker(
    override val config: PatternsConfig,
) :
    Checker,
    CheckerChangelistVisitorFactory,
    CheckerFileCollectionVisitorFactory {

    val defaultSeverity = config.severity.toResultSeverity()
    val comments: List<PatternsConfig.CommentPattern> by lazy {
        config.patterns
    }

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

    data class MatchedIssue(
        val comment: PatternsConfig.CommentPattern,
        val context: String,
        val location: CheckResultMessage.Location? = null,
    )

    inner class Checker(
        private val reporter: CheckerReporter,
    ) :
        ChangelistVisitor,
        ChangelistVisitor.FileVisitor,
        ChangelistVisitor.FileVisitor.FileAfterLineVisitor {

        val allLineCheckers = comments.filter {
            it.matches.context.any { context ->
                context == CONTEXT_LINE_ANY
            }
        }

        override fun enterChangelist(changelist: Changelist): Boolean {
            if (changelist.target != null) {
                val issues = mutableListOf<MatchedIssue>()
                comments.filter {
                    it.matches.context.any { context ->
                        context == CONTEXT_CL_TITLE
                    }
                }.forEach { comment ->
                    if (comment.matchesPattern(changelist.title)) {
                        issues.add(MatchedIssue(comment, CONTEXT_CL_TITLE)) != comment.matches.expected
                    }
                }
                comments.filter {
                    it.matches.context.any { context ->
                        context == CONTEXT_CL_DESCRIPTION
                    }
                }.forEach { comment ->
                    if (comment.matchesPattern(changelist.description)) {
                        issues.add(MatchedIssue(comment, CONTEXT_CL_DESCRIPTION))
                    }
                }
                if (issues.isNotEmpty()) {
                    reporter.report(issueListToMessage(issues))
                }
            }
            return comments.any {
                it.matches.context.any { context ->
                    context.startsWith("file:")
                }
            }
        }

        override fun enterFile(file: Changelist.FileOperation): Boolean {
            val issues = mutableListOf<MatchedIssue>()
            comments.filter {
                it.matches.context.any { context ->
                    context == CONTEXT_FILE_NAME
                }
            }.forEach { comment ->
                if (comment.matchesPattern(file.name)) {
                    issues.add(MatchedIssue(comment, CONTEXT_FILE_NAME))
                }
            }
            if (issues.isNotEmpty()) {
                reporter.report(
                    issueListToMessage(
                        issues,
                        location = CheckResultMessage.Location(file = file.name),
                    )
                )
            }

            val changedLines = when (file) {
                is Changelist.FileOperation.AddedFile -> file.patchLines
                is Changelist.FileOperation.ModifiedFile -> file.patchLines
                is Changelist.FileOperation.RemovedFile -> file.patchLines
            }

            if (changedLines.isNotEmpty()) {
                val lineAddedComments = comments.filter {
                    it.matches.context.any { context ->
                        context == CONTEXT_LINE_ADDED
                    }
                }
                changedLines.filter {
                    it.operation == Changelist.ChangeOperation.ADDED
                }.forEach { line ->
                    issues.clear()
                    lineAddedComments.forEach { comment ->
                        comment.matchPattern(line.content)?.let {
                            issues.add(
                                MatchedIssue(
                                    comment,
                                    CONTEXT_LINE_ADDED,
                                    it.toLocation(file.name, line.line)
                                )
                            )
                        }
                    }
                    if (issues.isNotEmpty()) {
                        reporter.report(
                            issueListToMessage(
                                issues,
                                location = CheckResultMessage.Location(
                                    file = file.name,
                                    startLine = line.line,
                                ),
                            )
                        )
                    }
                }
                val lineRemovedComments = comments.filter {
                    it.matches.context.any { context ->
                        context == CONTEXT_LINE_REMOVED
                    }
                }
                changedLines.filter {
                    it.operation == Changelist.ChangeOperation.ADDED
                }.forEach { line ->
                    issues.clear()
                    lineRemovedComments.forEach { comment ->
                        comment.matchPattern(line.content)?.let {
                            issues.add(
                                MatchedIssue(
                                    comment,
                                    CONTEXT_LINE_REMOVED,
                                    it.toLocation(file.name, line.line)
                                )
                            )
                        }
                    }
                    if (issues.isNotEmpty()) {
                        reporter.report(
                            issueListToMessage(
                                issues,
                                location = CheckResultMessage.Location(
                                    file = file.name,
                                    startLine = line.line,
                                ),
                            )
                        )
                    }
                }
            }

            return allLineCheckers.isNotEmpty()
        }

        override fun visitAfterLine(name: String, line: FileVisitors.FileLine): Boolean {
            val issues = mutableListOf<MatchedIssue>()
            allLineCheckers.forEach { comment ->
                comment.matchPattern(line.content)?.let {
                    issues.add(MatchedIssue(comment, CONTEXT_LINE_ANY, it.toLocation(name, line.line)))
                }
            }
            if (issues.isNotEmpty()) {
                reporter.report(
                    issueListToMessage(
                        issues,
                        location = CheckResultMessage.Location(
                            file = name,
                            startLine = line.line,
                        ),
                    )
                )
            }
            return true
        }

        private fun issueListToMessage(
            issues: MutableList<MatchedIssue>,
            location: CheckResultMessage.Location? = null,
        ): CheckResultMessage {
            issues.sortByDescending {
                it.comment.severity?.ordinal ?: defaultSeverity.ordinal
            }
            val topIssue = issues.first()
            return CheckResultMessage(
                checkGroupId = ID,
                severity = topIssue.comment.severity ?: defaultSeverity,
                title = topIssue.comment.title ?: topIssue.comment.id,
                message = topIssue.comment.message + if (issues.size > 1) {
                    """
                    |
                    |
                    |Additional Issues:
                    ${issues.drop(1).joinToString("\n") { "|- ${it.comment.message}" }}
                    """.trimMargin()
                } else {
                    ""
                },
                location = topIssue.location?.takeIf { issues.size == 1 } ?: location,
            )
        }

        private fun Matcher.toLocation(file: String, line: Int): CheckResultMessage.Location {
            val highlight = pattern().namedGroups()["hl"]
            return CheckResultMessage.Location(
                file = file,
                startLine = line,
                startCol = highlight?.let { start(it) } ?: start(),
                endLine = line,
                endCol = highlight?.let { end(it) } ?: end(),
            )
        }

        /**
         * TODO Checks/Patterns
         *
         * Description/Title:
         * - Gender Neutrality
         * - Inclusive language
         * - to do format
         * - long lines
         */
    }

    companion object {
        const val ID = "ContentPattern"

        const val CONTEXT_FILE_NAME = "file:name"
        const val CONTEXT_LINE_ANY = "file:line:any"
        const val CONTEXT_LINE_ADDED = "file:line:added"
        const val CONTEXT_LINE_REMOVED = "file:line:removed"
        const val CONTEXT_CL_TITLE = "cl:title"
        const val CONTEXT_CL_DESCRIPTION = "cl:description"

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
            // val includePatterns: List<String> = emptyList(),
        ) : CheckerConfig {
            @Serializable
            data class CommentPattern(
                val id: String,
                val message: String,
                val title: String? = null,
                val severity: CheckResultMessage.Severity? = null,
                val matches: MatchPattern,
            ) {

                fun matchesPattern(value: String): Boolean {
                    return matches.compiledPatterns.any { pattern ->
                        pattern.value.matcher(value).find() != matches.expected
                    }
                }

                fun matchPattern(value: String): Matcher? {
                    return matches.compiledPatterns.firstNotNullOfOrNull { pattern ->
                        val matcher = pattern.value.matcher(value)
                        if (matcher.find() != matches.expected) {
                            matcher
                        } else {
                            null
                        }
                    }
                }

                @Serializable
                data class MatchPattern(
                    //val changeTargets: List<String> = emptyList(),
                    //val files: List<String> = emptyList(),
                    val patterns: List<String> = emptyList(),
                    val expected: Boolean = false,
                    //val fixes: List<String?> = emptyList(),
                    val context: List<String> = listOf(
                        CONTEXT_FILE_NAME,
                        CONTEXT_LINE_ADDED,
                        CONTEXT_CL_TITLE,
                        CONTEXT_CL_DESCRIPTION,
                    ),
                ) {
                    init {
                        //require(!expected || fixes.isEmpty())
                        require(!expected || patterns.size <= 1)
                        require(!expected || context.all {
                            it == CONTEXT_CL_TITLE || it == CONTEXT_CL_DESCRIPTION
                        })
                    }

                    val compiledPatterns: List<Lazy<Pattern>> by lazy {
                        patterns.map {
                            lazy {
                                Pattern.compile(it, Pattern.DOTALL)
                            }
                        }
                    }
                }
            }
        }
    }
}
