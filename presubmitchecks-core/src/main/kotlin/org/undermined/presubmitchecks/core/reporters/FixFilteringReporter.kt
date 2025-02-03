package org.undermined.presubmitchecks.core.reporters

import org.undermined.presubmitchecks.core.CheckResult
import org.undermined.presubmitchecks.core.CheckResultFix
import org.undermined.presubmitchecks.core.CheckResultMessage
import org.undermined.presubmitchecks.core.CheckerReporter

class FixFilteringReporter(
    private val delegate: CheckerReporter,
) : CheckerReporter {
    override fun report(result: CheckResult) {
        when (result) {
            is CheckResultMessage -> {
                delegate.report(result.copy(fix = null))
            }
            is CheckResultFix -> {}
            else -> {
                delegate.report(result)
            }
        }
    }

    override suspend fun flush() {
        super.flush()
        delegate.flush()
    }
}
