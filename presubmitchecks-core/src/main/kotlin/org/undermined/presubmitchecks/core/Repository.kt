package org.undermined.presubmitchecks.core

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

interface Repository {
    suspend fun readFile(path: String, ref: String): InputStream

    interface WritableRepository {
        suspend fun writeFile(path: String, writer: (OutputStream) -> Unit)
    }
}

class LocalFilesystemRepository(
    private val rootPath: File,
) : Repository, Repository.WritableRepository {

    override suspend fun readFile(path: String, ref: String): InputStream {
        check(ref == "")
        return File(rootPath, path).inputStream().buffered()
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
}
