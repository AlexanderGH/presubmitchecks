package org.undermined.presubmitchecks.core

import java.nio.file.Path

/**
 * https://www.chromium.org/chromium-os/developer-library/guides/development/keep-files-in-sync/
 */
class IfChangeThenChangeChecker :
    ChangelistVisitor,
    ChangelistVisitor.FileAfterVisitor
{
    private val ifChangeThenChange = """[\s/#;<>\-]*LINT\.(?:(IfChange)(?:\(([a-z\-]+)\))?|(ThenChange)\(([^):]*(?::[a-z-]+)?)\))""".toRegex()

    private val needsChangedBlocks = mutableSetOf<String>()
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
                blockStack.removeLast()
                val targetLabelParts = targetLabel.split(':')
                val path = targetLabelParts[0]
                val file = if (path.startsWith("//")) {
                    path.substring(2)
                } else if (path.isEmpty()) {
                    currentFile.toString()
                } else {
                    currentFile!!.parent.resolve(path).toString()
                }
                val label = if (targetLabelParts.size == 2) ":${targetLabelParts[1]}" else ""
                needsChangedBlocks.add("//$file$label")
            }
        } else if (modified && !currentBlockTracked) {
            changedBlocks.add("//$currentFile")
            if (blockStack.isNotEmpty()) {
                changedBlocks.addAll(blockStack)
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
        needsChangedBlocks.removeAll(changedBlocks)
    }

    fun getMissingChanges(): List<String> {
        return needsChangedBlocks.toList()
    }
}