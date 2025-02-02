package org.undermined.presubmitchecks.core.reporters

import org.undermined.presubmitchecks.core.CheckResult
import org.undermined.presubmitchecks.core.CheckResultFix
import org.undermined.presubmitchecks.core.CheckResultMessage
import org.undermined.presubmitchecks.core.CheckerReporter

class AggregationReporter : CheckerReporter {
    private val _results = mutableListOf<CheckResult>()

    val results: List<CheckResult> = _results

    val fixes: List<CheckResultFix>
        get() {
            return results.mapNotNull {
                when (it) {
                    is CheckResultMessage -> {
                        it.fix
                    }

                    is CheckResultFix -> {
                        it
                    }

                    else -> null
                }
            }
        }

    override fun report(result: CheckResult) {
        _results.add(result)
    }
}
