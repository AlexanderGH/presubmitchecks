package org.undermined.presubmitchecks.core.reporters

import org.undermined.presubmitchecks.core.CheckResult
import org.undermined.presubmitchecks.core.CheckerReporter

class CompositeReporter(
    private val reporters: Iterable<CheckerReporter>,
): CheckerReporter {
    override fun report(result: CheckResult) {
        reporters.forEach {
            it.report(result)
        }
    }

    override suspend fun flush() {
        super.flush()
        reporters.forEach {
            it.flush()
        }
    }
}
