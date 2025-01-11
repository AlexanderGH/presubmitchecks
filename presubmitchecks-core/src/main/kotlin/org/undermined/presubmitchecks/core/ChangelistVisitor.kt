package org.undermined.presubmitchecks.core

interface ChangelistVisitor {
    fun enterChangelist(changelist: Changelist): Boolean { return true }

    fun enterFile(file: Changelist.FileOperation): Boolean { return true }

    fun visitChangedLine(operation: Changelist.ChangeOperation, line: Int, content: String) {}

    fun leaveFile(file: Changelist.FileOperation) {}

    fun leaveChangelist(changelist: Changelist) {}

    interface FileBeforeVisitor {
        fun visitBeforeLine(line: Int, content: String, modified: Boolean)
    }

    interface FileAfterVisitor {
        fun visitAfterLine(line: Int, content: String, modified: Boolean)
    }
}

suspend fun Changelist.visit(visitors: Collection<ChangelistVisitor>) {
    val changelist = this
    val changelistVisitors = visitors.filter { it.enterChangelist(changelist) }

    changelist.files.forEach { file ->
        val fileVisitors = changelistVisitors.filter { it.enterFile(file) }

        when (file) {
            is Changelist.FileOperation.AddedFile -> {
                (file.afterRevision as? FileContents.Text)?.lines?.invoke()?.forEachIndexed { i, content ->
                    fileVisitors.forEach {
                        it.visitChangedLine(Changelist.ChangeOperation.ADDED, i + 1, content)
                        (it as? ChangelistVisitor.FileAfterVisitor)?.visitAfterLine(i + 1, content, true)
                    }
                }
            }
            is Changelist.FileOperation.RemovedFile -> {
                (file.beforeRevision as? FileContents.Text)?.lines?.invoke()?.forEachIndexed { i, content ->
                    fileVisitors.forEach {
                        it.visitChangedLine(Changelist.ChangeOperation.REMOVED, i + 1, content)
                        (it as? ChangelistVisitor.FileBeforeVisitor)?.visitBeforeLine(i + 1, content, true)
                    }
                }
            }
            is Changelist.FileOperation.ModifiedFile -> {
                fileVisitors.filterIsInstance<ChangelistVisitor.FileBeforeVisitor>().let { fileBeforeVisitors ->
                    if (fileBeforeVisitors.isNotEmpty()) {
                        val modifiedLines = file.patchLines
                            .filter { it.operation == Changelist.ChangeOperation.REMOVED }
                            .map { it.line }
                            .toSet()
                        (file.beforeRevision as? FileContents.Text)?.lines?.invoke()?.forEachIndexed { i, content ->
                            fileBeforeVisitors.forEach { fileBeforeVisitor ->
                                fileBeforeVisitor.visitBeforeLine(i + 1, content, modifiedLines.contains(i + 1))
                            }
                        }
                    }
                }
                file.patchLines.forEach { patchLine ->
                    fileVisitors.forEach {
                        it.visitChangedLine(patchLine.operation, patchLine.line, patchLine.content)
                    }
                }
                fileVisitors.filterIsInstance<ChangelistVisitor.FileAfterVisitor>().let { fileAfterVisitors ->
                    if (fileAfterVisitors.isNotEmpty()) {
                        val modifiedLines = file.patchLines
                            .filter { it.operation == Changelist.ChangeOperation.ADDED }
                            .map { it.line }
                            .toSet()
                        (file.afterRevision as? FileContents.Text)?.lines?.invoke()?.forEachIndexed { i, content ->
                            fileAfterVisitors.forEach { fileBeforeVisitor ->
                                fileBeforeVisitor.visitAfterLine(i + 1, content, modifiedLines.contains(i + 1))
                            }
                        }
                    }
                }
            }
        }

        fileVisitors.forEach { it.leaveFile(file) }
    }

    changelistVisitors.forEach { it.leaveChangelist(changelist) }
}
