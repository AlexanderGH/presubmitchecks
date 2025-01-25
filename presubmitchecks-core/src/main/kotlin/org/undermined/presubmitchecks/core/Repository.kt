package org.undermined.presubmitchecks.core

import java.io.InputStream
import java.io.OutputStream

interface Repository {
    suspend fun readFile(path: String, ref: String): InputStream

    interface WritableRepository {
        suspend fun writeFile(path: String, writer: (OutputStream) -> Unit)
    }
}
