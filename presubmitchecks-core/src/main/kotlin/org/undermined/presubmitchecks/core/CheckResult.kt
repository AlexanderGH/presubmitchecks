package org.undermined.presubmitchecks.core

import java.nio.file.Path

interface Checker {
    fun getResults(): List<CheckResult>
}

interface CheckResult {

    fun toConsoleOutput(): String {
        return toString()
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
            .append(message).append("\n")
            .append(location?.let { "${it.file} ${arrayOf(it.startLine, ":", it.startCol, " ", it.endLine, ":", it.endCol).joinToString("")}" } ?: "")
            .toString()
    }
}