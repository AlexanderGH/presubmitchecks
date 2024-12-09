package org.undermined.presubmitchecks.core

import java.nio.file.Path

interface Repository {
    suspend fun readFile(path: Path): FileContents
}