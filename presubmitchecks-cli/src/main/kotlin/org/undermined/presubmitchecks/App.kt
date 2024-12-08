package org.undermined.presubmitchecks

import kotlinx.coroutines.runBlocking
import org.undermined.presubmitchecks.core.Changelist
import org.undermined.presubmitchecks.core.GithubChangelists
import org.undermined.presubmitchecks.core.IfChangeThenChangeChecker
import org.undermined.presubmitchecks.core.visit
import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val changelist = if (args.getOrNull(0) == "--stdin") {
        println("Reading Changelist From stdin")
        val fileDiffs = parseGitDiff(generateSequence(::readLine))
        Changelist(
            title = "",
            description = "",
            files = fileDiffs,
        )
    } else {
        println("Fetching Changelist From git")
        val process = Runtime.getRuntime().exec(arrayOf("git", "diff", "HEAD"))
        try {
            val fileDiffs = parseGitDiff(process.inputStream.bufferedReader().lineSequence())
            Changelist(
                title = "",
                description = "",
                files = fileDiffs,
            )
        } finally {
            process.destroy()
        }
    }

    runBlocking {
        val ifChangeThenChange = IfChangeThenChangeChecker()

        changelist.visit(listOf(ifChangeThenChange))

        val missingChanges = ifChangeThenChange.getMissingChanges()
        if (missingChanges.isNotEmpty()) {
            println("Missing changes to these blocks:\n ${missingChanges.joinToString("\n ")}")
            exitProcess(1)
        }
    }
    exitProcess(0)
}

private fun parseGitDiff(diffOutput: Sequence<String>): List<Changelist.FileOperation> {
    val fileDiffs = mutableListOf<Changelist.FileOperation>()

    var oldFilename: String? = null
    var newFilename: String? = null
    val currentPatchContent = StringBuilder()

    val nextFile = {
        val localOldFilename = oldFilename
        val localNewFilename = newFilename
        val localPatch = currentPatchContent.toString()
        if (localOldFilename != null && localNewFilename != null) {
            fileDiffs.add(Changelist.FileOperation.ModifiedFile(
                localNewFilename,
                beforeName = if (localNewFilename == localOldFilename) null else localOldFilename,
                patchLines = GithubChangelists.parseGitHubFilePatch(localPatch),
                afterRevision = {
                    sequence {
                        File(localNewFilename).useLines {
                            yieldAll(it)
                        }
                    }
                },
            ))
        } else if (localNewFilename != null) {
            fileDiffs.add(Changelist.FileOperation.AddedFile(localNewFilename, {
                localPatch.lineSequence().filter {
                    it.startsWith("+")
                }.map { it.substring(1) }
            }))
        } else if (localOldFilename != null) {
             fileDiffs.add(Changelist.FileOperation.RemovedFile(localOldFilename, {
                 localPatch.lineSequence().filter {
                     it.startsWith("-")
                 }.map { it.substring(1) }
             }))
        }
        oldFilename = null
        newFilename = null
        currentPatchContent.clear()
    }

    diffOutput.forEach { line ->
        if (line.startsWith("diff --git ")) {
            nextFile()
        } else if (line.startsWith("--- ")) {
            oldFilename = line.substring(4)
            if (oldFilename == "/dev/null") {
                oldFilename = null
            } else {
                oldFilename = oldFilename!!.substring(2)
            }
            currentPatchContent.clear()
        } else if (line.startsWith("+++ ")) {
            newFilename = line.substring(4)
            if (newFilename == "/dev/null") {
                newFilename = null
            } else {
                newFilename = newFilename!!.substring(2)
            }
            currentPatchContent.clear()
        } else if (line.startsWith("rename from ") || line.startsWith("new file ") || line.startsWith("deleted file ")) {

        } else if (line == "\\ No newline at end of file" || line.startsWith("index ")) {

        } else {
            currentPatchContent.appendLine(line)
        }
    }
    nextFile()

    return fileDiffs
}