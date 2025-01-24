package org.undermined.presubmitchecks.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.undermined.presubmitchecks.checks.IfChangeThenChangeChecker

class CheckerRegistry {
    companion object {
        private val allCheckerProviders = listOf(
            IfChangeThenChangeChecker.PROVIDER
        ).associateBy { it.id }

        val defaultCheckerConfigs = Json.encodeToJsonElement(
            mapOf(
                IfChangeThenChangeChecker.PROVIDER.id to
                        CoreConfig(severity = CheckerConfig.CheckerMode.WARNING)
            )
        ).jsonObject

        fun newServiceFromConfig(
            globalConfig: CheckerService.GlobalConfig,
        ): CheckerService {
            val mergedConfigs = defaultCheckerConfigs + globalConfig.checkerConfigs
            return CheckerService(globalConfig, mergedConfigs.mapValues {
                allCheckerProviders.getValue(it.key).newChecker(it.value)
            })
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

fun main() {
    CheckerRegistry.newServiceFromConfig(
        CheckerService.GlobalConfig(
            checkerConfigs = CheckerRegistry.defaultCheckerConfigs
        )
    )
}