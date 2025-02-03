package org.undermined.presubmitchecks.core

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromStream
import org.undermined.presubmitchecks.checks.ContentPatternChecker
import org.undermined.presubmitchecks.checks.DoNotSubmitIfChecker
import org.undermined.presubmitchecks.checks.NewLineChecker
import org.undermined.presubmitchecks.checks.IfChangeThenChangeChecker
import org.undermined.presubmitchecks.checks.KeepSortedChecker
import org.undermined.presubmitchecks.checks.ValidJsonChecker
import java.io.Closeable

class CheckerRegistry {
    companion object {
        private val allCheckerProviders = listOf(
            // keep-sorted start
            ContentPatternChecker.PROVIDER,
            DoNotSubmitIfChecker.PROVIDER,
            IfChangeThenChangeChecker.PROVIDER,
            KeepSortedChecker.PROVIDER,
            NewLineChecker.PROVIDER,
            ValidJsonChecker.PROVIDER,
            // keep-sorted end
        ).associateBy { it.id }

        @OptIn(ExperimentalSerializationApi::class)
        val defaultGlobalConfig: CheckerService.GlobalConfig by lazy {
            CheckerRegistry::class.java
                .getResourceAsStream("/presubmitchecks.defaults.json")?.use {
                    Json.decodeFromStream(it)
                } ?: CheckerService.GlobalConfig()
        }

        fun newServiceFromConfig(
            globalConfig: CheckerService.GlobalConfig,
        ): CheckerService {
            val mergedCheckerConfigs =
                defaultGlobalConfig.checkerConfigs + globalConfig.checkerConfigs
            return CheckerService(
                globalConfig,
                checkers = mergedCheckerConfigs.mapValues {
                    allCheckerProviders.getValue(it.key).newChecker(it.value)
                }.filterValues { it.config.severity != CheckerConfig.CheckerMode.DISABLED },
            )
        }
    }
}

class CheckerService(
    val globalConfig: GlobalConfig,
    internal val checkers: Map<String, Checker>,
): Closeable {
    @Serializable
    data class GlobalConfig(
        val checkerConfigs: Map<String, JsonElement> = emptyMap(),
    )

    override fun close() {
        checkers.values.filterIsInstance<Closeable>().forEach {
            it.close()
        }
    }
}

interface CheckerProvider {
    val id: String
    fun newChecker(config: JsonElement): Checker
}
