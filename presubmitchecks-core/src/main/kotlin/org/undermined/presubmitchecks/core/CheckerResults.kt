package org.undermined.presubmitchecks.core

import org.undermined.presubmitchecks.fixes.Fixes

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
                    append(
                        arrayOf(it.startLine, ":", it.startCol, " ", it.endLine, ":", it.endCol)
                            .joinToString("")
                    )

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

interface CheckResultFix : CheckResult {
    val fixId: String
}

data class CheckResultLineFix(
    override val fixId: String,
    val message: String,
    val location: CheckResultMessage.Location,
    val transform: Fixes.LineFixFilter,
): CheckResultFix

data class CheckResultFileFix(
    override val fixId: String,
    val file: String,
    val phase: Phase = Phase.WHOLE_FILE,
    val transform: Fixes.FileFixFilter,
) : CheckResultFix {
    enum class Phase {
        LINE_STABLE,
        WHOLE_FILE,
        FORMAT
    }
}

fun CheckerConfig.CheckerMode.toResultSeverity(): CheckResultMessage.Severity = when (this) {
    CheckerConfig.CheckerMode.DISABLED -> error("Check is disabled")
    CheckerConfig.CheckerMode.NOTE -> CheckResultMessage.Severity.NOTE
    CheckerConfig.CheckerMode.WARNING -> CheckResultMessage.Severity.WARNING
    CheckerConfig.CheckerMode.ERROR -> CheckResultMessage.Severity.ERROR
}
