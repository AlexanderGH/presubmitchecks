package org.undermined.presubmitchecks.checks

import org.undermined.presubmitchecks.core.Changelist
import org.undermined.presubmitchecks.core.ChangelistVisitor
import org.undermined.presubmitchecks.core.CheckResult
import org.undermined.presubmitchecks.core.CheckResultMessage
import org.undermined.presubmitchecks.core.Checker
import java.nio.file.Path

/**
 * https://www.chromium.org/chromium-os/developer-library/guides/development/keep-files-in-sync/
 */
class IfChangeThenChangeChecker :
    Checker,
    ChangelistVisitor,
    ChangelistVisitor.FileAfterVisitor
{
    private val ifChangeThenChange =
        """[\s/#;<>\-]*LINT\.(?:(IfChange)(?:\(([a-z\-]+)\))?|(ThenChange)\(([^):]*(?::[a-z-]+)?)\))[\s/#;<>\-]*""".toRegex()

    /**
     * Key: The block id that is requested to have changes.
     * Value: The set of block ids that requested the changes.
     */
    private val needsChangedBlocks = mutableMapOf<String, MutableSet<String>>()

    /**
     * Key: Block id
     * Value: The file location of that block id.
     */
    private val blockToLocation = mutableMapOf<String, CheckResultMessage.Location>()

    /**
     * The block ids that have been changed.
     */
    private val changedBlocks = mutableSetOf<String>()

    private var currentFile: Path? = null
    private var currentBlockTracked = false
    private var blockStack = ArrayDeque<String>()

    override fun enterFile(file: Changelist.FileOperation) {
        super.enterFile(file)
        currentFile = when (file) {
            is Changelist.FileOperation.AddedFile -> Path.of(file.name)
            is Changelist.FileOperation.ModifiedFile -> Path.of(file.name)
            is Changelist.FileOperation.RemovedFile -> Path.of(file.name)
        }
        currentBlockTracked = false
    }

    override fun visitAfterLine(line: Int, content: String, modified: Boolean) {
        val match = ifChangeThenChange.matchEntire(content)
        if (match != null) {
            val (ifChange, blockLabel, thenChange, targetLabel) = match.destructured
            if (ifChange == "IfChange") {
                val label = if (blockLabel.isEmpty()) "" else ":$blockLabel"
                val file = currentFile!!
                val canonical = "//$file$label"
                blockStack.addLast(canonical)
                currentBlockTracked = false
            } else if (thenChange == "ThenChange") {
                val block = blockStack.removeLast()
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
                needsChangedBlocks.getOrPut("//$file$label") {
                    mutableSetOf()
                }.apply {
                    add(block)
                }
            }
        } else if (modified && !currentBlockTracked) {
            changedBlocks.add("//$currentFile")
            if (blockStack.isNotEmpty()) {
                changedBlocks.addAll(blockStack)
                blockStack.forEach {
                    blockToLocation.computeIfAbsent(it) {
                        CheckResultMessage.Location(
                            file = currentFile!!,
                            startLine = line,
                        )
                    }
                }
            }
            currentBlockTracked = true
        }
    }

    override fun leaveFile(file: Changelist.FileOperation) {
        super.leaveFile(file)
        check(blockStack.isEmpty())
        currentFile = null
    }

    override fun leaveChangelist(changelist: Changelist) {
        super.leaveChangelist(changelist)
        changedBlocks.forEach {
            needsChangedBlocks.remove(it)
        }
    }

    fun getMissingChanges(): List<String> {
        return needsChangedBlocks.keys.toList()
    }

    override fun getResults(): List<CheckResult> {
        val requesterToMissing = mutableMapOf<String, MutableSet<String>>()
        needsChangedBlocks.forEach { entry ->
            entry.value.forEach {
                requesterToMissing.computeIfAbsent(it) {
                    mutableSetOf()
                }.add(entry.key)
            }
        }
        return requesterToMissing.map { entry ->
            CheckResultMessage(
                checkGroupId = "IfChangeThenChange",
                title = "Missing Changes",
                message = "The following locations should also be changed:\n  ${
                    entry.value.joinToString("  \n")
                }",
                severity = CheckResultMessage.Severity.WARNING,
                location = blockToLocation[entry.key]
            )
        }
    }
}