package org.undermined.presubmitchecks.core

import org.undermined.presubmitchecks.core.ChangelistVisitor.FileVisitor.FileAfterLineVisitor
import org.undermined.presubmitchecks.core.ChangelistVisitor.FileVisitor.FileAfterRandomVisitor
import org.undermined.presubmitchecks.core.ChangelistVisitor.FileVisitor.FileAfterSequentialVisitor
import org.undermined.presubmitchecks.core.ChangelistVisitor.FileVisitor.FileBeforeLineVisitor
import org.undermined.presubmitchecks.core.ChangelistVisitor.FileVisitor.FileBeforeRandomVisitor
import org.undermined.presubmitchecks.core.ChangelistVisitor.FileVisitor.FileBeforeSequentialVisitor
import java.io.InputStream
import java.nio.ByteBuffer

interface ChangelistVisitor {
    fun enterChangelist(changelist: Changelist): Boolean { return true }

    fun leaveChangelist(changelist: Changelist) {}

    interface FileVisitor {

        fun enterFile(file: Changelist.FileOperation): Boolean { return true }

        fun leaveFile(file: Changelist.FileOperation) {}

        interface FileDeltaLineVisitor {
            fun visitChangedLine(name: String, line: Changelist.PatchLine)
        }

        interface FileAfterLineVisitor {
            fun visitAfterLine(name: String, line: FileVisitors.FileLine): Boolean
        }
        interface FileAfterSequentialVisitor {
            suspend fun visitAfterFile(name: String, inputStream: InputStream)
        }
        interface FileAfterRandomVisitor {
            suspend fun visitAfterFile(name: String, byteBuffer: ByteBuffer)
        }

        interface FileBeforeLineVisitor {
            fun visitBeforeLine(name: String, line: FileVisitors.FileLine): Boolean
        }
        interface FileBeforeSequentialVisitor {
            suspend fun visitBeforeFile(name: String, inputStream: InputStream)
        }
        interface FileBeforeRandomVisitor {
            suspend fun visitBeforeFile(name: String, byteBuffer: ByteBuffer)
        }
    }
}

suspend fun Changelist.visit(repository: Repository, visitors: Collection<ChangelistVisitor>) {
    val changelist = this
    val changelistVisitors = visitors.filter {
        it.enterChangelist(changelist)
    }

    changelist.files.forEach { file ->
        val fileVisitors = changelistVisitors
            .filterIsInstance<ChangelistVisitor.FileVisitor>()
            .filter{
                it.enterFile(file)
            }

        val deltaVisitors = fileVisitors
            .filterIsInstance<ChangelistVisitor.FileVisitor.FileDeltaLineVisitor>()

        if (deltaVisitors.isNotEmpty()) {
            when (file) {
                is Changelist.FileOperation.AddedFile -> file.patchLines
                is Changelist.FileOperation.RemovedFile -> file.patchLines
                is Changelist.FileOperation.ModifiedFile -> file.patchLines
            }.forEach { patchLine ->
                deltaVisitors.forEach {
                    it.visitChangedLine(file.name, patchLine)
                }
            }
        }

        if (file !is Changelist.FileOperation.RemovedFile) {
            FileVisitors.visitFile(
                {
                    repository.readFile(file.name, when (file) {
                        is Changelist.FileOperation.AddedFile -> file.afterRef
                        is Changelist.FileOperation.ModifiedFile -> file.afterRef
                        else -> TODO()
                    })
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
                isLineModified = when (file) {
                    is Changelist.FileOperation.ModifiedFile -> {
                        val modifiedLines = file.patchLines
                            .filter { it.operation == Changelist.ChangeOperation.ADDED }
                            .map { it.line }
                            .toSet()
                        fun (line: Int): Boolean { return modifiedLines.contains(line) }
                    }
                    is Changelist.FileOperation.AddedFile -> fun (_): Boolean { return true }
                    else -> TODO()
                }
            )
        }

        if (file !is Changelist.FileOperation.AddedFile) {
            FileVisitors.visitFile(
                {
                    repository.readFile(file.name, when (file) {
                        is Changelist.FileOperation.RemovedFile -> file.beforeRef
                        is Changelist.FileOperation.ModifiedFile -> file.beforeRef
                        else -> TODO()
                    })
                },
                lineVisitors = fileVisitors
                    .filterIsInstance<FileBeforeLineVisitor>().map { v ->
                        { v.visitBeforeLine(file.name, it) }
                    },
                rawFileVisitorsSequential = fileVisitors
                    .filterIsInstance<FileBeforeSequentialVisitor>().map { v ->
                        { v.visitBeforeFile(file.name, it) }
                    },
                rawFileVisitorsRandom = fileVisitors
                    .filterIsInstance<FileBeforeRandomVisitor>().map { v ->
                        { v.visitBeforeFile(file.name, it) }
                    },
                isLineModified = when (file) {
                    is Changelist.FileOperation.ModifiedFile -> {
                        val modifiedLines = file.patchLines
                            .filter { it.operation == Changelist.ChangeOperation.REMOVED }
                            .map { it.line }
                            .toSet()
                        fun (line: Int): Boolean { return modifiedLines.contains(line) }
                    }
                    is Changelist.FileOperation.RemovedFile -> fun (_): Boolean { return true }
                    else -> TODO()
                }
            )
        }

        fileVisitors.forEach { it.leaveFile(file) }
    }

    changelistVisitors.forEach { it.leaveChangelist(changelist) }
}
