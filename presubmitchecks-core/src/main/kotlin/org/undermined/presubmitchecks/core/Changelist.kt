package org.undermined.presubmitchecks.core

data class Changelist(
    val title: String,
    val description: String,
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
        data class AddedFile(
            val name: String,
            val afterRevision: FileContents,
        ) : FileOperation

        data class RemovedFile(
            val name: String,
            val beforeRevision: FileContents,
        ) : FileOperation

        data class ModifiedFile(
            val name: String,
            val beforeName: String = name,
            val patchLines: Collection<PatchLine>,
            val afterRevision: FileContents,
            val beforeRevision: FileContents = afterRevision.reversePatch(patchLines),
        ) : FileOperation
    }

    data class PatchLine(
        val operation: ChangeOperation,
        val line: Int,
        val content: String,
    )

    enum class ChangeOperation {
        ADDED,
        REMOVED,
    }
}