package org.undermined.presubmitchecks.core

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

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

    companion object {
        fun isText(inputStream: InputStream, bufferSize: Int = 4096): Boolean {
            val buffer = ByteArray(bufferSize)
            var bytesRead: Int

            inputStream.use { // Ensure the stream is closed
                bytesRead = it.read(buffer)
            }

            if (bytesRead == -1) {
                return true // Empty file is considered text
            }

            // Check for common binary byte sequences (adjust as needed)
            for (i in 0 until bytesRead) {
                val byte = buffer[i]
                if (byte == 0.toByte()) { // Null byte
                    return false
                }
                // Check for control characters outside the printable ASCII range
                if (byte < 9 || byte > 13 && byte < 32) { // excluding \t \n \r
                    return false
                }

            }

            // Try decoding as UTF-8. If it doesn't throw an exception, it's likely text.
            return try {
                String(buffer, 0, bytesRead, StandardCharsets.UTF_8)
                true
            } catch (e: Exception) {
                false
            }
        }
    }
}
