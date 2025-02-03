package org.undermined.presubmitchecks.core.reporters

import org.undermined.presubmitchecks.core.CheckResult
import org.undermined.presubmitchecks.core.CheckResultFix
import org.undermined.presubmitchecks.core.CheckResultMessage
import org.undermined.presubmitchecks.core.CheckerReporter
import java.util.function.Predicate
import kotlin.coroutines.cancellation.CancellationException

class ConsoleReporter(
    private val out: (String) -> Unit,
    private val err: (String) -> Unit,
    private val filter: Predicate<CheckResult> = Predicate { true },
) : CheckerReporter {
    override fun report(result: CheckResult) {
        if (!filter.test(result)) {
            return
        }
        when (result) {
            is CheckResultMessage -> {
                val isError = result.severity == CheckResultMessage.Severity.ERROR
                out("")
                if (isError) {
                    err(result.toConsoleOutput())
                } else {
                    out(result.toConsoleOutput())
                }
            }
            else -> {
                out(result.toConsoleOutput())
            }
        }
    }
}

class ConsolePromptReporter(
    private val delegate: CheckerReporter,
    private val consoleReporter: ConsoleReporter,
    private val prompt: (String) -> String,
) : CheckerReporter {
    override fun report(result: CheckResult) {
        consoleReporter.report(result)
        when (result) {
            is CheckResultMessage -> {
                when (
                    if (result.fix != null) {
                        internalPrompt(
                            "[F]ix/[C]ontinue/[I]gnore/[A]bort (Default: Fix): ",
                            listOf('f', 'c', 'i', 'a')
                        )
                    } else {
                        internalPrompt(
                            "[C]ontinue/[I]gnore/[A]bort (Default: Continue): ",
                            listOf('c', 'i', 'a')
                        )
                    }
                ) {
                    'f' -> delegate.report(result)
                    'c' -> delegate.report(result.copy(fix = null))
                    'i' -> {}
                    'a' -> throw CancellationException("Aborted by user")
                    else -> delegate.report(result)
                }
            }
            is CheckResultFix -> {
                when (
                    internalPrompt(
                        "[F]ix/[C]ontinue (Default: Fix): ",
                        listOf('f', 'c')
                    )
                ) {
                    'f' -> delegate.report(result)
                    'c' -> {}
                    else -> delegate.report(result)
                }
            }
            else -> {
                delegate.report(result)
            }
        }
    }

    private fun internalPrompt(message: String, options: List<Char>): Char? {
        val response = prompt(message).lowercase()
        if (response.length != 1 || !options.contains(response[0])) {
            return null
        }
        return response[0]
    }

    override suspend fun flush() {
        super.flush()
        delegate.flush()
    }
}
