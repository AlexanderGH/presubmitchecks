package org.undermined.presubmitchecks.core

import org.undermined.presubmitchecks.core.Changelist.PatchLine

internal fun FileContents.reversePatch(patchLines: Collection<PatchLine>): FileContents {
    check(this is FileContents.Text)
    return FileContents.Text(
        suspend {
            val lines = this@reversePatch.lines()

            sequence {
                var currentLineNumber = 1
                var operationIndex = 0
                var skip = 0

                val patchLinesList = patchLines.toList()
                for (newLine in lines) {
                    while (
                        operationIndex < patchLinesList.size
                        && patchLinesList[operationIndex].line <= currentLineNumber
                    ) {
                        val operation = patchLinesList[operationIndex]
                        when (operation.operation) {
                            Changelist.ChangeOperation.ADDED -> {
                                skip++
                            }

                            Changelist.ChangeOperation.REMOVED -> {
                                yield(operation.content)
                                currentLineNumber++
                            }
                        }
                        operationIndex++
                    }
                    if (skip > 0) {
                        skip--
                    } else {
                        yield(newLine)
                        currentLineNumber++
                    }
                }

                // Process remaining additions after the last new line
                while (operationIndex < patchLinesList.size) {
                    val operation = patchLinesList[operationIndex]
                    if (operation.operation == Changelist.ChangeOperation.ADDED) {
                        yield(operation.content)
                    }
                    operationIndex++
                }
            }
        }
    )
}