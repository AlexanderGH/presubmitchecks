package org.undermined.presubmitchecks.checks

import com.google.re2j.Matcher
import com.google.re2j.Pattern
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.decodeFromStream
import org.undermined.presubmitchecks.core.Changelist
import org.undermined.presubmitchecks.core.ChangelistVisitor
import org.undermined.presubmitchecks.core.CheckResultMessage
import org.undermined.presubmitchecks.core.Checker
import org.undermined.presubmitchecks.core.CheckerChangelistVisitorFactory
import org.undermined.presubmitchecks.core.CheckerConfig
import org.undermined.presubmitchecks.core.CheckerFileCollectionVisitorFactory
import org.undermined.presubmitchecks.core.CheckerProvider
import org.undermined.presubmitchecks.core.CheckerRegistry
import org.undermined.presubmitchecks.core.CheckerReporter
import org.undermined.presubmitchecks.core.FileCollection
import org.undermined.presubmitchecks.core.FileVisitors
import org.undermined.presubmitchecks.core.Repository
import org.undermined.presubmitchecks.core.toResultSeverity
import java.util.Optional
import java.util.function.BiFunction
import java.util.function.Function
import java.util.function.Predicate

class ContentPatternChecker(
    override val config: PatternsConfig,
) :
    Checker,
    CheckerChangelistVisitorFactory,
    CheckerFileCollectionVisitorFactory {

    val defaultSeverity = config.severity.toResultSeverity()

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


        var commentsMatchingTarget: List<PatternsConfig.CommentPattern> = emptyList()

        var commentsMatchingFile: List<PatternsConfig.CommentPattern> = emptyList()

        var commentsMatchingLines: List<PatternsConfig.CommentPattern> = emptyList()

        override fun enterChangelist(changelist: Changelist): Boolean {
            commentsMatchingTarget = config.allPatterns.filter {
                it.matches.compiledChangeTargetChecks.test(changelist.target ?: "")
            }
            if (changelist.target != null && commentsMatchingTarget.isNotEmpty()) {
                val issues = mutableListOf<MatchedIssue>()
                commentsMatchingTarget.filter {
                    it.matches.context.any { context ->
                        context == CONTEXT_CL_TITLE
                    }
                }.forEach { comment ->
                    if (comment.matchesPattern(changelist.title)) {
                        issues.add(MatchedIssue(comment, CONTEXT_CL_TITLE))
                    }
                }
                commentsMatchingTarget.filter {
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
            return commentsMatchingTarget.any {
                it.matches.context.any { context ->
                    context.startsWith("file:")
                }
            }
        }

        override fun enterFile(file: Changelist.FileOperation): Boolean {
            commentsMatchingFile = commentsMatchingTarget.filter {
                it.matches.context.any { context ->
                    context.startsWith("file:")
                } && it.matches.compiledFileChecks.test(file.name)
            }
            val issues = mutableListOf<MatchedIssue>()
            commentsMatchingFile.filter {
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
                val lineAddedComments = commentsMatchingFile.filter {
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
                val lineRemovedComments = commentsMatchingFile.filter {
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

            commentsMatchingLines = commentsMatchingFile.filter {
                it.matches.context.any { context ->
                    context == CONTEXT_LINE_ANY
                }
            }

            return commentsMatchingLines.isNotEmpty()
        }

        override fun visitAfterLine(name: String, line: FileVisitors.FileLine): Boolean {
            val issues = mutableListOf<MatchedIssue>()
            commentsMatchingLines.forEach { comment ->
                comment.matchPattern(line.content)?.let {
                    issues.add(
                        MatchedIssue(
                            comment,
                            CONTEXT_LINE_ANY,
                            it.toLocation(name, line.line)
                        )
                    )
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
                startCol = 1 + (highlight?.let { start(it) } ?: start()),
                endLine = line,
                endCol = highlight?.let { end(it) } ?: end(),
            )
        }
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
            private val patterns: List<CommentPattern> = emptyList(),
            // val includePatterns: List<String> = emptyList(),
            private val canned: List<String> = emptyList(),
        ) : CheckerConfig {

            val allPatterns by lazy {
                patterns + canned.let { cannedId ->
                    if (cannedId.isEmpty()) {
                        emptyList()
                    } else {
                        val canned: Map<String, CommentPattern> = ContentPatternChecker::class.java
                            .getResourceAsStream("/contentpatternchecker.canned.json").use {
                                Json.decodeFromStream(it)
                            }
                        cannedId.map {
                            canned.getValue(it)
                        }
                    }
                }
            }

            @Serializable
            data class CommentPattern(
                val id: String,
                val message: String,
                val title: String? = null,
                val severity: CheckResultMessage.Severity? = null,
                val matches: MatchPattern,
            ) {

                fun matchesPattern(value: String): Boolean {
                    return matchPattern(value) != null
                }

                fun matchPattern(value: String): Matcher? {
                    val firstMatcher = matches.compiledPatterns.firstNotNullOfOrNull { pattern ->
                        val matcher = pattern.value.matcher(value)
                        if (matcher.find()) {
                            matcher
                        } else {
                            null
                        }
                    }
                    if (firstMatcher != null) {
                        if (matches.compiledExcludes.any { it.value.matcher(value).find() }) {
                            return null
                        }
                    }
                    return firstMatcher
                }

                @Serializable
                data class MatchPattern(
                    val changeTargets: List<String> = emptyList(),
                    val files: List<String> = emptyList(),
                    val patterns: List<String> = emptyList(),
                    val excludes: List<String> = emptyList(),
                    // TODO: Add simple file + line based fix infra.
                    //val fixes: List<String?> = emptyList(),
                    val context: List<String> = listOf(
                        CONTEXT_FILE_NAME,
                        CONTEXT_LINE_ADDED,
                        CONTEXT_CL_TITLE,
                        CONTEXT_CL_DESCRIPTION,
                    ),
                ) {
                    init {
                        require(!context.any {
                            it == CONTEXT_LINE_ANY
                        } || !context.any {
                            it == CONTEXT_LINE_ADDED || it == CONTEXT_LINE_REMOVED
                        })
                    }

                    val compiledChangeTargetChecks: Predicate<String> by lazy {
                        compileContextChecks(changeTargets)
                    }

                    val compiledFileChecks: Predicate<String> by lazy {
                        compileContextChecks(files)
                    }

                    val compiledPatterns: List<Lazy<Pattern>> by lazy {
                        patterns.map {
                            lazy {
                                Pattern.compile(it, Pattern.DOTALL)
                            }
                        }
                    }
                    val compiledExcludes: List<Lazy<Pattern>> by lazy {
                        excludes.map {
                            lazy {
                                Pattern.compile(it, Pattern.DOTALL)
                            }
                        }
                    }

                    private fun compileContextChecks(checks: List<String>): Predicate<String> {
                        val transforms: List<BiFunction<Boolean, String, Boolean>> = checks.map {
                            if (it.startsWith("!")) {
                                val pattern = Pattern.compile(it.substring(1))
                                BiFunction { accumulate, value ->
                                    accumulate || !pattern.matcher(value).find()
                                }
                            } else if (it.startsWith("+")) {
                                val pattern = Pattern.compile(it.substring(1))
                                BiFunction { accumulate, value ->
                                    accumulate || pattern.matcher(value).find()
                                }
                            } else if (it.startsWith("-")) {
                                val pattern = Pattern.compile(it.substring(1))
                                BiFunction { accumulate, value ->
                                    accumulate && !pattern.matcher(value).find()
                                }
                            } else {
                                error("All context patterns must start with +, -, or !.")
                            }
                        }
                        return Predicate { value ->
                            var start = transforms.isEmpty()
                            transforms.forEach {
                                start = it.apply(start, value)
                            }
                            start
                        }
                    }
                }
            }
        }
    }
}
