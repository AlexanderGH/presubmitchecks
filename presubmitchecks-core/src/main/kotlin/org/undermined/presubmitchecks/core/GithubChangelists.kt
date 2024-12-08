package org.undermined.presubmitchecks.core

object GithubChangelists {
    fun parseGitHubFilePatch(patchString: String): List<Changelist.PatchLine> {
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
        }

        return modifiedLines
    }
}