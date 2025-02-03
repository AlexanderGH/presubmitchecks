package org.undermined.presubmitchecks.core

import java.util.Optional

data class Changelist(
    val title: String,
    val description: String,
    val target: String? = null,
    val files: Collection<FileOperation>,
) {
    val tags: Map<String, String> by lazy {
        val tagRegex = """([A-Za-z0-9_]+)(?:=|: )(.*?)""".toRegex()
        description.lineSequence().mapNotNull {
            tagRegex.matchEntire(it)
        }.map {
            val (key, value) = it.destructured
            key to value
        }.toMap()
    }

    sealed interface FileOperation {
        val name: String
        val isBinary: Boolean
        val isText: Boolean get() = !isBinary

        data class AddedFile(
            override val name: String,
            val patchLines: Collection<PatchLine>,
            val afterRef: String,
            override val isBinary: Boolean,
        ) : FileOperation

        data class RemovedFile(
            override val name: String,
            val patchLines: Collection<PatchLine>,
            val beforeRef: String,
            override val isBinary: Boolean,
        ) : FileOperation

        data class ModifiedFile(
            override val name: String,
            val beforeName: String = name,
            val patchLines: Collection<PatchLine>,
            val beforeRef: String,
            val afterRef: String,
            override val isBinary: Boolean,
        ) : FileOperation
    }

    data class PatchLine(
        val operation: ChangeOperation,
        val line: Int,
        val content: String,
        val hasNewLine: Boolean = true,
    )

    enum class ChangeOperation {
        ADDED,
        REMOVED,
        CONTEXT
    }
}

interface CheckerChangelistVisitorFactory {
    fun newCheckVisitor(
        repository: Repository,
        changelist: Changelist,
        reporter: CheckerReporter,
    ): Optional<ChangelistVisitor>
}

suspend fun CheckerService.runChecks(
    repository: Repository,
    changelist: Changelist,
    reporter: CheckerReporter,
) {
    checkers.values.filterIsInstance<CheckerChangelistVisitorFactory>().map {
        it.newCheckVisitor(repository, changelist, reporter = reporter)
    }
        .filter { it.isPresent }
        .map { it.get() }
        .takeIf { it.isNotEmpty() }?.let {
            changelist.visit(repository, it)
        }
}
