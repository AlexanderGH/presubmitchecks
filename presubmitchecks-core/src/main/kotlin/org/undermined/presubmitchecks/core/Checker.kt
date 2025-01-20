package org.undermined.presubmitchecks.core

import kotlinx.serialization.Serializable
import java.nio.file.Path
import java.util.Optional

interface Checker

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

    suspend fun flush()
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
): CheckResult {
    enum class Severity {
        SUCCESS,
        NOTE,
        WARNING,
        ERROR
    }

    data class Location(
        val file: Path,
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
                    append("${it.file} ${arrayOf(it.startLine, ":", it.startCol, " ", it.endLine, ":", it.endCol).joinToString("")}")
                }
            }
            .toString()
    }
}

fun CheckerConfig.CheckerMode.toResultSeverity(): CheckResultMessage.Severity = when (this) {
    CheckerConfig.CheckerMode.DISABLED -> CheckResultMessage.Severity.SUCCESS
    CheckerConfig.CheckerMode.NOTE -> CheckResultMessage.Severity.NOTE
    CheckerConfig.CheckerMode.WARNING -> CheckResultMessage.Severity.WARNING
    CheckerConfig.CheckerMode.ERROR -> CheckResultMessage.Severity.ERROR
}

interface CheckerChangelistVisitorFactory {
    fun newCheckVisitor(changelist: Changelist, reporter: CheckerReporter): Optional<ChangelistVisitor>
}

suspend fun CheckerService.runChecks(changelist: Changelist, reporter: CheckerReporter) {
    checkers.values.filterIsInstance<CheckerChangelistVisitorFactory>().map {
        it.newCheckVisitor(changelist, reporter = reporter)
    }.filter { it.isPresent }.map { it.get() }.takeIf { it.isNotEmpty() }?.let {
        changelist.visit(it)
    }
}