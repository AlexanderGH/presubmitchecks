package org.undermined.presubmitchecks.core

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import org.undermined.presubmitchecks.fixes.Fixes
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

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
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
