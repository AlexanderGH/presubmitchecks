package org.undermined.presubmitchecks.core

import org.undermined.presubmitchecks.core.Changelist.FileOperation
import org.undermined.presubmitchecks.core.ChangelistVisitor.FileVisitor.FileAfterLineVisitor
import org.undermined.presubmitchecks.core.ChangelistVisitor.FileVisitor.FileAfterRandomVisitor
import org.undermined.presubmitchecks.core.ChangelistVisitor.FileVisitor.FileAfterSequentialVisitor
import java.util.Optional

data class FileCollection(
    val files: Collection<FileOperation.AddedFile>,
)

suspend fun FileCollection.visit(
    repository: Repository, visitors:
    Collection<ChangelistVisitor.FileVisitor>,
) {
    files.forEach { file ->
        val fileVisitors = visitors
            .filter{
                it.enterFile(file)
            }

        FileVisitors.visitFile(
            {
                repository.readFile(file.name, file.afterRef)
            },
            lineVisitors = fileVisitors
                .filterIsInstance<FileAfterLineVisitor>().map { v ->
                    { v.visitAfterLine(file.name, it) }
                },
            rawFileVisitorsSequential = fileVisitors
                .filterIsInstance<FileAfterSequentialVisitor>().map { v ->
                    { v.visitAfterFile(file.name, it) }
                },
            rawFileVisitorsRandom = fileVisitors
                .filterIsInstance<FileAfterRandomVisitor>().map { v ->
                    { v.visitAfterFile(file.name, it) }
                },
            isLineModified = fun (_): Boolean { return true },
        )

        fileVisitors.forEach { it.leaveFile(file) }
    }
}

interface CheckerFileCollectionVisitorFactory {
    fun newCheckVisitor(
        repository: Repository,
        fileCollection: FileCollection,
        reporter: CheckerReporter,
    ): Optional<ChangelistVisitor.FileVisitor>
}

suspend fun CheckerService.runChecks(
    repository: Repository,
    files: Iterable<String>,
    reporter: CheckerReporter
) {
    val fc = FileCollection(files.map { file ->
        FileOperation.AddedFile(
            file,
            patchLines = emptyList(),
            afterRef = "",
            isBinary = false
        )
    })
    checkers.values.filterIsInstance<CheckerFileCollectionVisitorFactory>().map {
        it.newCheckVisitor(repository, fc, reporter = reporter)
    }
        .filter { it.isPresent }
        .map { it.get() }
        .takeIf { it.isNotEmpty() }?.let {
            fc.visit(repository, it)
        }
}
