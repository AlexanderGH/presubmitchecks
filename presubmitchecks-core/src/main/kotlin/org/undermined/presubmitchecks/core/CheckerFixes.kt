package org.undermined.presubmitchecks.core

import org.undermined.presubmitchecks.fixes.Fixes
import org.undermined.presubmitchecks.fixes.Fixes.transformLines
import java.io.ByteArrayOutputStream

suspend fun CheckerService.applyFixes(
    repository: Repository.WritableRepository,
    fixes: List<CheckResultFix>,
    files: Iterable<Changelist.FileOperation>,
    ref: String,
    wrapper: suspend (
        Map.Entry<String, List<CheckResultFix>>,
        suspend () -> Unit
    ) -> Unit = { _, f -> f() },
): Boolean {
    val fixesInternal = fixes.toMutableList()
    fixesInternal.filterIsInstance<CheckResultLineFix>().groupBy { it.location.file }.mapValues {
        it.value.groupBy {
            it.location.startLine!!
        }
    }.forEach {
        fixesInternal.add(
            CheckResultFileFix(
                fixId = "LineFixes:${it.key}",
                file = it.key,
                phase = CheckResultFileFix.Phase.LINE_STABLE,
                transform = Fixes.FileFixFilter { inputStream, outputStream ->
                    val transforms = it.value.mapValues {
                        it.value.map {
                            it.transform
                        }.toSet()
                    }
                    inputStream.transformLines(outputStream, transforms)
                },
            )
        )
    }

    val fileFixes = fixesInternal.filterIsInstance<CheckResultFileFix>()
    if (fileFixes.isNotEmpty()) {
        val fixFiles = fileFixes.groupBy { it.file }
        ByteArrayOutputStream(4096 * 8).use { tmpBuffer ->
            fixFiles.forEach { fixesForFile ->
                try {
                    wrapper(fixesForFile) {
                        val transform = Fixes.chainStreamModifiers(
                            fixesForFile.value
                                .sortedBy { it.phase.ordinal }
                                .map { it.transform }
                        )
                        val hasChanges = repository.readFile(fixesForFile.key, ref).use {
                            transform.transform(it, tmpBuffer)
                        }
                        if (hasChanges) {
                            repository.writeFile(fixesForFile.key) {
                                tmpBuffer.writeTo(it)
                            }
                        }
                    }
                } finally {
                    tmpBuffer.reset()
                }
            }
            tmpBuffer.close()
        }
    }

    return fileFixes.isNotEmpty()
}
