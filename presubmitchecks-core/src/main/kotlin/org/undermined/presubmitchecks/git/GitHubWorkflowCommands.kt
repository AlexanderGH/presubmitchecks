package org.undermined.presubmitchecks.git

import org.undermined.presubmitchecks.core.CheckResultMessage

object GitHubWorkflowCommands {
    fun debug(message: String) {
        command("debug", message = message)
    }

    fun message(
        severity: CheckResultMessage.Severity,
        message: String,
        title: String? = null,
        file: String? = null,
        line: Int? = null,
        endLine: Int? = null,
        col: Int? = null,
        endColumn: Int? = null,
    ) {
        val level = when (severity) {
            CheckResultMessage.Severity.SUCCESS -> "notice"
            CheckResultMessage.Severity.NOTE -> "notice"
            CheckResultMessage.Severity.WARNING -> "warning"
            CheckResultMessage.Severity.ERROR -> "error"
        }
        val args = mapOf(
            "title" to title,
            "file" to file,
            "col" to col,
            "endColumn" to endColumn,
            "line" to line,
            "endLine" to endLine,
        )
        command(level, args, message)
    }

    fun group(title: String, block: () -> Unit) {
        command("group", message = title)
        try {
            block()
        } finally {
            command("endgroup")
        }
    }

    private fun command(
        command: String,
        properties: Map<String, Any?> = emptyMap(),
        message: String = "",
    ) {
        val args = properties
            .filterValues { it != null }
            .ifEmpty { null }
            ?.entries
            ?.joinToString(",") {
                "${it.key}=${it.value.toString().escapeProperty()}"
            }?.prependIndent(" ") ?: ""
        val messageString = message.escapeData()
        println("::$command$args::$messageString")
    }

    private fun String.escapeData(): String {
        return replace("%", "%25")
            .replace("\r", "%0D")
            .replace("\n", "%0A")
    }

    private fun String.escapeProperty(): String {
        return replace("%", "%25")
            .replace("\r", "%0D")
            .replace("\n", "%0A")
            .replace(":", "%3A")
            .replace(",", "%2C")
    }
}