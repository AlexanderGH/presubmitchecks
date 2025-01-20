package org.undermined.presubmitchecks.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.undermined.presubmitchecks.checks.FileEndsInNewLineChecker
import org.undermined.presubmitchecks.checks.IfChangeThenChangeChecker

class CheckerRegistry {
    companion object {
        private val allCheckerProviders = listOf(
            // keep-sorted start
            FileEndsInNewLineChecker.PROVIDER,
            IfChangeThenChangeChecker.PROVIDER,
            // keep-sorted end
        ).associateBy { it.id }

        val defaultGlobalConfig: CheckerService.GlobalConfig by lazy {
            CheckerRegistry::class.java
                .getResourceAsStream("/presubmitchecks.defaults.json").use {
                    Json.decodeFromStream(it)
                }
        }

        fun newServiceFromConfig(
            globalConfig: CheckerService.GlobalConfig,
        ): CheckerService {
            val mergedConfigs = defaultGlobalConfig.checkerConfigs + globalConfig.checkerConfigs
            return CheckerService(globalConfig, mergedConfigs.mapValues {
                allCheckerProviders.getValue(it.key).newChecker(it.value)
            }.filterValues { it.config.severity != CheckerConfig.CheckerMode.DISABLED })
        }
    }
}

class CheckerService(
    val globalConfig: GlobalConfig,
    internal val checkers: Map<String, Checker>,
) {

    @Serializable
    data class GlobalConfig(
        val checkerConfigs: Map<String, JsonElement> = emptyMap()
    )
}

interface CheckerProvider {
    val id: String
    fun newChecker(config: JsonElement): Checker
}
