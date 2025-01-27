package org.undermined.presubmitchecks.git

import org.undermined.presubmitchecks.core.LocalFilesystemRepository
import org.undermined.presubmitchecks.core.Repository
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

class GitLocalRepository(
    private val rootPath: File,
    private val currentRef: Lazy<String>,
) : Repository, Repository.WritableRepository {
    init {
        check(File(rootPath, ".git").exists()) {
            "$rootPath is not a git repository"
        }
    }

    private val localFilesystemRepository = LocalFilesystemRepository(rootPath)

    override suspend fun readFile(path: String, ref: String): InputStream {
        return if (ref == currentRef.value || ref == "") {
            localFilesystemRepository.readFile(path, "")
        } else {
            ProcessBuilder(listOf("git", "show", "$ref:$path"))
                .directory(rootPath)
                .exec { it.inputStream.buffered() }
                ?: throw IOException("$path @ $ref")
        }
    }

    override suspend fun writeFile(path: String, writer: (OutputStream) -> Unit) {
        localFilesystemRepository.writeFile(path, writer)
    }

    companion object {
        fun getGitRevision(repoPath: File): String? {
            return ProcessBuilder("git", "rev-parse", "HEAD")
                .directory(repoPath)
                .exec { process ->
                    process.inputStream.bufferedReader().use { inputStream ->
                        inputStream.readLine()
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
