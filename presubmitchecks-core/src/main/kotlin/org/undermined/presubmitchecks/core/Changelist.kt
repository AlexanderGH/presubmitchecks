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
