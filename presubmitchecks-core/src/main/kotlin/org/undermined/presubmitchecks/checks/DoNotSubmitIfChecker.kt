package org.undermined.presubmitchecks.checks

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import org.undermined.presubmitchecks.core.Changelist
import org.undermined.presubmitchecks.core.ChangelistVisitor
import org.undermined.presubmitchecks.core.CheckResultMessage
import org.undermined.presubmitchecks.core.Checker
import org.undermined.presubmitchecks.core.CheckerChangelistVisitorFactory
import org.undermined.presubmitchecks.core.CheckerProvider
import org.undermined.presubmitchecks.core.CheckerReporter
import org.undermined.presubmitchecks.core.CoreConfig
import org.undermined.presubmitchecks.core.FileVisitors
import org.undermined.presubmitchecks.core.Repository
import org.undermined.presubmitchecks.core.toResultSeverity
import java.util.Optional

class DoNotSubmitIfChecker(
    override val config: CoreConfig,
) :
    Checker,
    CheckerChangelistVisitorFactory
{
    private val doNotSubmitIf =
        """[\s/#;<>\-]*LINT\.DoNotSubmitIf\((.+?)\)[\s/#;<>\-]*""".toRegex()

    override fun newCheckVisitor(
        repository: Repository,
        changelist: Changelist,
        reporter: CheckerReporter
    ): Optional<ChangelistVisitor> {
        return Optional.of(Checker(reporter))
    }


    internal inner class Checker(
        private val reporter: CheckerReporter
    ) : ChangelistVisitor,
        ChangelistVisitor.FileVisitor,
        ChangelistVisitor.FileVisitor.FileAfterLineVisitor
    {

        private val blockValues = mutableSetOf<String>()

        override fun enterFile(file: Changelist.FileOperation): Boolean {
            blockValues.clear()
            return super.enterFile(file)
        }

        override fun visitAfterLine(name: String, line: FileVisitors.FileLine): Boolean {
            if (line.content.isBlank()) {
                return true
            }
            val match = doNotSubmitIf.find(line.content)
            if (match != null) {
                blockValues.add(match.groups[1]!!.value)
            } else if (blockValues.isNotEmpty()) {
                val first = blockValues.firstOrNull {
                    line.content.contains(it, ignoreCase = true)
                }
                if (first != null) {
                    val startCol = line.content.indexOf(first, ignoreCase = true)
                    reporter.report(
                        CheckResultMessage(
                            checkGroupId = ID,
                            severity = config.severity.toResultSeverity(),
                            title = "Do Not" + " Submit",
                            message = "The value '$first' should not be submitted.",
                            location = CheckResultMessage.Location(
                                file = name,
                                startLine = line.line,
                                startCol = startCol + 1,
                                endCol = startCol + first.length,
                                endLine = line.line
                            ),
                        )
                    )
                }
                blockValues.clear()
            }
            return true
        }

        override fun leaveFile(file: Changelist.FileOperation) {
            super.leaveFile(file)
            check(blockValues.isEmpty())
        }
    }

    companion object {
        const val ID = "DoNotSubmitIf"

        val PROVIDER = object : CheckerProvider {
            override val id: String = ID

            override fun newChecker(
                config: JsonElement,
            ): DoNotSubmitIfChecker {
                return DoNotSubmitIfChecker(
                    config = Json.decodeFromJsonElement(config),
                )
            }
        }
    }
}
