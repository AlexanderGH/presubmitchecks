package org.undermined.presubmitchecks.git

import org.undermined.presubmitchecks.core.Changelist
import java.io.File

object GitChangelists {

    fun parseDiff(diffOutput: Sequence<String>): List<Changelist.FileOperation> {
        val fileDiffs = mutableListOf<Changelist.FileOperation>()

        var oldFilename: String? = null
        var newFilename: String? = null
        var oldRef: String? = null
        var newRef: String? = null
        var isBinary = false
        val currentPatchContent = StringBuilder()

        val nextFile = {
            val localOldFilename = oldFilename
            val localNewFilename = newFilename
            val localPatch = currentPatchContent.toString()
            if (localOldFilename != null && localNewFilename != null) {
                fileDiffs.add(Changelist.FileOperation.ModifiedFile(
                    localNewFilename,
                    beforeName = localOldFilename,
                    patchLines = parseFilePatch(localPatch),
                    beforeRef = oldRef!!,
                    afterRef = newRef!!,
                    isBinary = isBinary,
                ))
            } else if (localNewFilename != null) {
                fileDiffs.add(Changelist.FileOperation.AddedFile(
                    localNewFilename,
                    patchLines = parseFilePatch(localPatch),
                    afterRef = newRef ?: "",
                    isBinary = isBinary,
                ))
            } else if (localOldFilename != null) {
                fileDiffs.add(Changelist.FileOperation.RemovedFile(
                    localOldFilename,
                    patchLines = parseFilePatch(localPatch),
                    beforeRef = oldRef ?: "",
                    isBinary = isBinary,
                ))
            }
            isBinary = false
            oldRef = null
            newRef = null
            oldFilename = null
            newFilename = null
            currentPatchContent.clear()
        }

        diffOutput.forEach { line ->
            if (line.startsWith("diff --git ")) {
                nextFile()
            } else if (line.startsWith("--- ")) {
                oldFilename = line.substring(4).let {
                    if (it == "/dev/null") {
                        null
                    } else {
                        it.substring(2)
                    }
                }
                currentPatchContent.clear()
            } else if (line.startsWith("+++ ")) {
                newFilename = line.substring(4).let {
                    if (it == "/dev/null") {
                        null
                    } else {
                        it.substring(2)
                    }
                }
                currentPatchContent.clear()
            } else if (line.startsWith("rename from ") || line.startsWith("new file ") || line.startsWith("deleted file ")) {

            } else if (line == "\\ No newline at end of file") {
                currentPatchContent.appendLine(line)
            } else if (line.startsWith("index ")) {
                line.split(" ")[1].split("..").let {
                    oldRef = it[0]
                    newRef = it[1]
                }
            } else if (line.startsWith("Binary files ")) {
                isBinary = true
            } else {
                currentPatchContent.appendLine(line)
            }
        }
        nextFile()

        return fileDiffs
    }

    fun parseFilePatch(patchString: String): List<Changelist.PatchLine> {
        val modifiedLines = mutableListOf<Changelist.PatchLine>()

        val diffRegex = """@@ -(\d+),(\d+) \+(\d+),(\d+) @@.*""".toRegex()

        var beforeLineNumber = 0
        var afterLineNumber = 0
        patchString.lineSequence().forEach { line ->
            if (line.startsWith("@@ ")) {
                val diffMatchResult = diffRegex.matchEntire(line)
                if (diffMatchResult != null) {
                    val (oldStartLine, _, newStartLine, _) = diffMatchResult.destructured
                    beforeLineNumber = oldStartLine.toInt()
                    afterLineNumber = newStartLine.toInt()
                }
                return@forEach
            }
            if (line.startsWith(" ")) {
                // Context
                /*
                modifiedLines.add(
                    Changelist.PatchLine(
                        Changelist.ChangeOperation.CONTEXT,
                        beforeLineNumber,
                        content = line.substring(1),
                    )
                )
                */
                beforeLineNumber++
                afterLineNumber++
                return@forEach
            }
            if (line.startsWith("+")) {
                modifiedLines.add(
                    Changelist.PatchLine(
                        Changelist.ChangeOperation.ADDED,
                        afterLineNumber,
                        content = line.substring(1),
                    )
                )
                afterLineNumber++
                return@forEach
            }
            if (line.startsWith("-")) {
                modifiedLines.add(
                    Changelist.PatchLine(
                        Changelist.ChangeOperation.REMOVED,
                        beforeLineNumber,
                        content = line.substring(1),
                    )
                )
                beforeLineNumber++
                return@forEach
            }
            if (line == "\\ No newline at end of file") {
                if (modifiedLines.isEmpty()) {
                    modifiedLines.add(Changelist.PatchLine(
                        Changelist.ChangeOperation.ADDED,
                        0,
                        "",
                        false
                    ))
                } else {
                    modifiedLines[modifiedLines.size - 1] =
                        modifiedLines.last().copy(hasNewLine = false)
                }
                return@forEach
            }
        }

        return modifiedLines
    }
}