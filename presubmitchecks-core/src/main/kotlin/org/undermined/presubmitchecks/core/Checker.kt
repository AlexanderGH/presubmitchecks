package org.undermined.presubmitchecks.core

import kotlinx.serialization.Serializable
import org.undermined.presubmitchecks.fixes.FileFixFilter
import java.nio.file.Path
import java.util.Optional

interface Checker {
    val config: CheckerConfig
}

interface CheckerConfig {
    val severity: CheckerMode

    enum class CheckerMode {
        DISABLED,
        NOTE,
        WARNING,
        ERROR,
    }
}

@Serializable
data class CoreConfig(
    override val severity: CheckerConfig.CheckerMode = CheckerConfig.CheckerMode.DISABLED,
) : CheckerConfig

interface CheckerReporter {
    fun report(result: CheckResult)

    suspend fun flush() {}
}

interface CheckResult {
    fun toConsoleOutput(): String {
        return toString()
    }
}

data class CheckResultDebug(val message: String) : CheckResult {
    override fun toConsoleOutput(): String {
        return "[DEBUG] $message"
    }
}

data class CheckResultMessage(
    val checkGroupId: String,
    val title: String,
    val message: String,
    val severity: Severity = Severity.NOTE,
    val location: Location? = null,
    val fix: CheckResultFix? = null,
): CheckResult {
    enum class Severity {
        NOTE,
        WARNING,
        ERROR
    }

    data class Location(
        val file: String,
        val startLine: Int? = null,
        val startCol: Int? = null,
        val endLine: Int? = null,
        val endCol: Int? = null,
    )

    override fun toConsoleOutput(): String {
        return StringBuilder().append(checkGroupId).append(" (").append(severity).append(")\n")
            .append(title).append("\n")
            .append(message).apply {
                location?.let {
                    append("\n")
                    append(it.file)
                    append(" ")
                    append(arrayOf(it.startLine, ":", it.startCol, " ", it.endLine, ":", it.endCol).joinToString(""))

                    if (false && it.startLine != null) {
                        append(" ${it.startLine}")
                        if (it.endLine == null || it.endLine == it.startLine) {
                            if (it.startCol != null || it.endCol != null) {
                                append(" ${it.startCol}->${it.endCol}")
                            }
                        } else {
                            if (it.startCol != null) {
                                append(":${it.startCol}")
                            }
                            append("->${it.endLine}")
                            if (it.endCol != null) {
                                append(":${it.endCol}")
                            }
                        }
                    }
                }
            }
            .toString()
    }
}

data class CheckResultFix(
    val fixId: String,
    val file: String,
    val transform: FileFixFilter,
): CheckResult

fun CheckerConfig.CheckerMode.toResultSeverity(): CheckResultMessage.Severity = when (this) {
    CheckerConfig.CheckerMode.DISABLED -> error("Check is disabled")
    CheckerConfig.CheckerMode.NOTE -> CheckResultMessage.Severity.NOTE
    CheckerConfig.CheckerMode.WARNING -> CheckResultMessage.Severity.WARNING
    CheckerConfig.CheckerMode.ERROR -> CheckResultMessage.Severity.ERROR
}

interface CheckerChangelistVisitorFactory {
    fun newCheckVisitor(
        repository: Repository,
        changelist: Changelist,
        reporter: CheckerReporter,
    ): Optional<ChangelistVisitor>
}

suspend fun CheckerService.runChecks(repository: Repository, changelist: Changelist, reporter: CheckerReporter) {
    checkers.values.filterIsInstance<CheckerChangelistVisitorFactory>().map {
        it.newCheckVisitor(repository, changelist, reporter = reporter)
    }
        .filter { it.isPresent }
        .map { it.get() }
        .takeIf { it.isNotEmpty() }?.let {
            changelist.visit(repository, it)
        }
}
