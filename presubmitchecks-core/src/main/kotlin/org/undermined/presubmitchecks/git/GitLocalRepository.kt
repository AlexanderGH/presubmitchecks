package org.undermined.presubmitchecks.git

import org.undermined.presubmitchecks.core.Repository
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class GitLocalRepository(
    root: File = File("."),
) : Repository, Repository.WritableRepository {
    val rootPath = root.absoluteFile
    init {
        check(File(rootPath, ".git").exists()) {
            "$rootPath is not a git repository"
        }
    }

    private val currentRef: String by lazy {
        getGitRevision(rootPath) ?: error("Unable to get current repository revision")
    }

    override suspend fun readFile(path: String, ref: String): InputStream {
        return if (ref == currentRef || ref == "") {
            File(rootPath, path).inputStream().buffered()
        } else {
            ProcessBuilder(listOf("git", "show", "$ref:$path"))
                .directory(rootPath)
                .exec { it.inputStream.buffered() }
                ?: throw IOException()
        }
    }

    override suspend fun writeFile(path: String, writer: (OutputStream) -> Unit) {
        val targetFile = File(rootPath, path).absoluteFile
        val tmpFile = File.createTempFile(targetFile.name, ".tmp", targetFile.parentFile)
        tmpFile.outputStream().use {
            writer(it)
        }
        if (!tmpFile.renameTo(targetFile)) {
            tmpFile.delete()
            throw IOException("Could not write: $path")
        }
    }

    companion object {
        fun getGitRevision(repoPath: File): String? {
            return ProcessBuilder("git", "rev-parse", "HEAD")
                .directory(repoPath)
                .exec {
                    it.inputStream.bufferedReader().use {
                        it.readLine()
                    }
                }
        }

        fun <T> ProcessBuilder.exec(map: (Process) -> T): T? {
            val process = this.start()
            val revision = map(process)
            process.waitFor() // Wait for the process to finish
            return if (process.exitValue() == 0) revision else null
        }
    }
}
