package org.undermined.presubmitchecks.core

interface ChangelistVisitor {
    fun enterChangelist(changelist: Changelist) {}

    fun enterFile(file: Changelist.FileOperation) {}

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
    visitors.forEach { it.enterChangelist(changelist) }

    changelist.files.forEach { file ->
        visitors.forEach { it.enterFile(file) }

        when (file) {
            is Changelist.FileOperation.AddedFile -> {
                file.afterRevision().forEachIndexed { i, content ->
                    visitors.forEach {
                        it.visitChangedLine(Changelist.ChangeOperation.ADDED, i + 1, content)
                        (it as? ChangelistVisitor.FileAfterVisitor)?.visitAfterLine(i + 1, content, true)
                    }
                }
            }
            is Changelist.FileOperation.RemovedFile -> {
                file.beforeRevision().forEachIndexed { i, content ->
                    visitors.forEach {
                        it.visitChangedLine(Changelist.ChangeOperation.REMOVED, i + 1, content)
                        (it as? ChangelistVisitor.FileBeforeVisitor)?.visitBeforeLine(i + 1, content, true)
                    }
                }
            }
            is Changelist.FileOperation.ModifiedFile -> {
                visitors.filterIsInstance<ChangelistVisitor.FileBeforeVisitor>().let { fileBeforeVisitors ->
                    if (fileBeforeVisitors.isNotEmpty()) {
                        val modifiedLines = file.patchLines
                            .filter { it.operation == Changelist.ChangeOperation.REMOVED }
                            .map { it.line }
                            .toSet()
                        file.beforeRevision().forEachIndexed { i, content ->
                            fileBeforeVisitors.forEach { fileBeforeVisitor ->
                                fileBeforeVisitor.visitBeforeLine(i + 1, content, modifiedLines.contains(i + 1))
                            }
                        }
                    }
                }
                file.patchLines.forEach { patchLine ->
                    visitors.forEach {
                        it.visitChangedLine(patchLine.operation, patchLine.line, patchLine.content)
                    }
                }
                visitors.filterIsInstance<ChangelistVisitor.FileAfterVisitor>().let { fileAfterVisitors ->
                    if (fileAfterVisitors.isNotEmpty()) {
                        val modifiedLines = file.patchLines
                            .filter { it.operation == Changelist.ChangeOperation.ADDED }
                            .map { it.line }
                            .toSet()
                        file.afterRevision().forEachIndexed { i, content ->
                            fileAfterVisitors.forEach { fileBeforeVisitor ->
                                fileBeforeVisitor.visitAfterLine(i + 1, content, modifiedLines.contains(i + 1))
                            }
                        }
                    }
                }
            }
        }

        visitors.forEach { it.leaveFile(file) }
    }

    visitors.forEach { it.leaveChangelist(changelist) }
}
