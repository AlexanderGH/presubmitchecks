package org.undermined.presubmitchecks.git

import org.undermined.presubmitchecks.core.CheckResultMessage
import java.io.BufferedWriter
import java.io.File
import java.io.StringWriter
import java.security.SecureRandom
import kotlin.random.asKotlinRandom
import kotlin.streams.asSequence

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

    fun mask(value: String) {
        command("add-mask", message = value)
    }

    private val randomChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    private val secureRandom by lazy {
        SecureRandom()
    }

    fun stopCommands(block: () -> Unit) {
        val randomString = secureRandom.ints(64, 0, randomChars.length)
            .asSequence()
            .map(randomChars::get)
            .joinToString("")
        command("stop-commands", message = randomString)
        try {
            block()
        } finally {
            command(randomString)
        }
    }

    fun outputVar(key: String, value: String) {
        File(System.getenv("GITHUB_OUTPUT")).appendText("$key=$value\n")
    }

    fun addState(key: String, value: String) {
        File(System.getenv("GITHUB_STATE")).appendText("$key=$value\n")
    }

    fun setEnv(key: String, value: String) {
        File(System.getenv("GITHUB_ENV")).appendText("$key=$value\n")
    }

    fun appendStepSummary(markdown: String) {
        File(System.getenv("GITHUB_STEP_SUMMARY")).appendText("$markdown\n")
    }

    fun appendStepSummary(block: BufferedWriter.() -> Unit) {
        File(System.getenv("GITHUB_STEP_SUMMARY")).bufferedWriter().use {
            it.block()
        }
    }

    fun addPath(path: String) {
        File(System.getenv("GITHUB_PATH")).appendText("$path\n")
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
