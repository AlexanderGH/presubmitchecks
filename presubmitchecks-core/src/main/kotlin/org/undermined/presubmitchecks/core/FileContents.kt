package org.undermined.presubmitchecks.core

import java.io.InputStream

sealed interface FileContents {
    data class Binary(val stream: suspend () -> InputStream): FileContents

    data class Text(val lines: suspend () -> Sequence<String>): FileContents
}