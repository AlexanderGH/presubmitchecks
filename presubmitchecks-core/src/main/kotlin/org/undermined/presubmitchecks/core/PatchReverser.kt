package org.undermined.presubmitchecks.core

import org.undermined.presubmitchecks.core.Changelist.PatchLine

internal fun Sequence<String>.reversePatch(patchLines: Collection<PatchLine>): Sequence<String> {
    return sequence {
        var currentLineNumber = 1
        var operationIndex = 0
        var skip = 0

        val patchLinesList = patchLines.toList()
        for (newLine in this@reversePatch) {

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