package org.undermined.presubmitchecks.checks

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.undermined.presubmitchecks.core.Changelist
import org.undermined.presubmitchecks.core.CheckResult
import org.undermined.presubmitchecks.core.CheckerChangelistVisitorFactory
import org.undermined.presubmitchecks.core.CheckerConfig
import org.undermined.presubmitchecks.core.CheckerProvider
import org.undermined.presubmitchecks.core.CheckerReporter
import org.undermined.presubmitchecks.core.CoreConfig
import org.undermined.presubmitchecks.core.visit

internal object CheckerTests {
    suspend fun runChecker(
        changelist: Changelist,
        checkerProvider: CheckerProvider,
        checkerConfig: JsonElement = Json.encodeToJsonElement(
            CoreConfig(severity = CheckerConfig.CheckerMode.ERROR)
        ),
    ): TestCheckerResultReporter {
        val reporter = TestCheckerResultReporter()
        val checker = checkerProvider.newChecker(checkerConfig)
        val checkers = listOf(checker).filterIsInstance<CheckerChangelistVisitorFactory>().map {
            it.newCheckVisitor(changelist, reporter = reporter)
        }.filter { it.isPresent }.map { it.get() }
        changelist.visit(checkers)
        reporter.flush()
        return reporter
    }

    class TestCheckerResultReporter : CheckerReporter {
        val results = mutableListOf<CheckResult>()

        override fun report(result: CheckResult) {
            results.add(result)
        }

        override suspend fun flush() = Unit
    }
}