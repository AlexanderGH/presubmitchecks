package org.undermined.presubmitchecks.checks

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import org.undermined.presubmitchecks.core.Changelist
import org.undermined.presubmitchecks.core.ChangelistVisitor
import org.undermined.presubmitchecks.core.CheckResult
import org.undermined.presubmitchecks.core.CheckResultDebug
import org.undermined.presubmitchecks.core.CheckResultMessage
import org.undermined.presubmitchecks.core.Checker
import org.undermined.presubmitchecks.core.CheckerChangelistVisitorFactory
import org.undermined.presubmitchecks.core.CheckerProvider
import org.undermined.presubmitchecks.core.CheckerReporter
import org.undermined.presubmitchecks.core.CoreConfig
import org.undermined.presubmitchecks.core.FileVisitors
import org.undermined.presubmitchecks.core.Repository
import org.undermined.presubmitchecks.core.toResultSeverity
import java.nio.file.Path
import java.util.Optional
import kotlin.io.path.name
import kotlin.io.path.pathString

/**
 * https://www.chromium.org/chromium-os/developer-library/guides/development/keep-files-in-sync/
 */
class IfChangeThenChangeChecker(
    override val config: CoreConfig,
) :
    Checker,
    CheckerChangelistVisitorFactory,
    ChangelistVisitor,
    ChangelistVisitor.FileVisitor,
    ChangelistVisitor.FileVisitor.FileAfterLineVisitor
{

    private val ifChangeThenChange =
        """[\s/#;<>\-]*LINT\.(?:(IfChange|Ignore)(?:\(([a-z\-]+|@ignore)\))?|(ThenChange)\(([^):]*(?::[a-z-]+)?)\))[\s/#;<>\-]*""".toRegex()

    /**
     * Key: The block id that is requested to have changes.
     * Value: The set of block ids that requested the changes.
     */
    private val needsChangedBlocks = mutableMapOf<String, MutableSet<String>>()

    /**
     * Key: Block id
     * Value: The file location of that block id.
     */
    private val changedBlockLocations = mutableMapOf<String, CheckResultMessage.Location>()

    private var currentFile: Path? = null
    private var currentFileBlockCount = 0
    private var currentBlockTracked = false
    private var blockStack = ArrayDeque<String>()

    private var reporter: CheckerReporter? = null

    override fun newCheckVisitor(
        repository: Repository,
        changelist: Changelist,
        reporter: CheckerReporter
    ): Optional<ChangelistVisitor> {
        this.reporter = reporter
        return Optional.of(this)
    }

    override fun enterChangelist(changelist: Changelist): Boolean {
        return if (changelist.tags.contains(TAG_NO_IFTT)) {
            reporter?.report(
                CheckResultDebug(
                    "Skipping $ID because $TAG_NO_IFTT was found: ${changelist.tags[TAG_NO_IFTT]}"
                )
            )
            false
        } else {
            true
        }
    }

    override fun enterFile(file: Changelist.FileOperation): Boolean {
        currentFile = Path.of(file.name)
        currentBlockTracked = false
        currentFileBlockCount = 0
        return file.isText
    }

    override fun visitAfterLine(name: String, line: FileVisitors.FileLine): Boolean {
        if (currentFile == null) {
            return false
        }
        val match = ifChangeThenChange.matchEntire(line.content)
        if (match != null) {
            val (ifChange, blockLabel, thenChange, targetLabel) = match.destructured
            if (ifChange == "IfChange") {
                if (blockLabel == "@ignore") {
                    currentFile = null
                    return false
                }
                val label = if (blockLabel.isEmpty()) ":@${currentFileBlockCount}" else ":$blockLabel"
                val file = currentFile!!
                val canonical = "//$file$label"
                blockStack.addLast(canonical)
                currentBlockTracked = false
                currentFileBlockCount++
            } else if (thenChange == "ThenChange") {
                val block = blockStack.removeLastOrNull()
                if (block == null) {
                    reporter?.report(
                        CheckResultMessage(
                            checkGroupId = ID,
                            title = "Invalid Block",
                            message = "No open block",
                            severity = CheckResultMessage.Severity.ERROR,
                            location = CheckResultMessage.Location(
                                file = currentFile!!.pathString,
                                startLine = line.line,
                            ),
                        )
                    )
                    return true
                }
                val targetLabelParts = targetLabel.split(':')
                val path = targetLabelParts[0]
                val file = if (path.startsWith("//")) {
                    path.substring(2)
                } else if (path.isEmpty()) {
                    currentFile.toString()
                } else {
                    currentFile!!.parent?.resolve(path)?.toString() ?: path
                }
                val label = if (targetLabelParts.size == 2) ":${targetLabelParts[1]}" else ""
                if (changedBlockLocations.contains(block)) {
                    needsChangedBlocks.getOrPut("//$file$label") {
                        mutableSetOf()
                    }.apply {
                        add(block)
                    }
                }
            }
        } else if (line.isModified && !currentBlockTracked) {
            changedBlockLocations.computeIfAbsent("//$currentFile") {
                CheckResultMessage.Location(
                    file = currentFile!!.pathString,
                    startLine = line.line,
                )
            }
            if (blockStack.isNotEmpty()) {
                blockStack.forEach {
                    changedBlockLocations.computeIfAbsent(it) {
                        CheckResultMessage.Location(
                            file = currentFile!!.pathString,
                            startLine = line.line,
                        )
                    }
                }
            }
            currentBlockTracked = true
        }
        return true
    }

    override fun leaveFile(file: Changelist.FileOperation) {
        // check(blockStack.isEmpty()) { "Unclosed blocks: $blockStack" }
        if (blockStack.isNotEmpty()) {
            reporter?.report(
                CheckResultMessage(
                    checkGroupId = ID,
                    title = "Invalid Block",
                    message = "Unclosed blocks: $blockStack",
                    severity = CheckResultMessage.Severity.ERROR,
                    location = CheckResultMessage.Location(
                        file = currentFile!!.pathString,
                    ),
                )
            )
        }
        currentFile = null
    }

    override fun leaveChangelist(changelist: Changelist) {
        super.leaveChangelist(changelist)

        val requesterToMissing = mutableMapOf<String, MutableSet<String>>()
        needsChangedBlocks.filter { !changedBlockLocations.contains(it.key) }.forEach { entry ->
            entry.value.forEach {
                requesterToMissing.computeIfAbsent(it) {
                    mutableSetOf()
                }.add(entry.key)
            }
        }
        requesterToMissing.forEach { entry ->
            reporter?.report(
                CheckResultMessage(
                    checkGroupId = ID,
                    title = "Missing Changes",
                    message = "The following locations should also be changed:\n  ${
                        entry.value.joinToString("  \n")
                    }",
                    severity = config.severity.toResultSeverity(),
                    location = changedBlockLocations[entry.key]
                )
            )
        }
    }

    companion object {
        const val ID = "IfChangeThenChange"
        private const val TAG_NO_IFTT = "NO_IFTT"

        val PROVIDER = object : CheckerProvider {
            override val id: String = ID

            override fun newChecker(
                config: JsonElement,
            ): IfChangeThenChangeChecker {
                return IfChangeThenChangeChecker(
                    config = Json.decodeFromJsonElement(config),
                )
            }
        }
    }
}
