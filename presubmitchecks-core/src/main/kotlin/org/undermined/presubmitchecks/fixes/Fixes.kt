package org.undermined.presubmitchecks.fixes

import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.atomic.AtomicBoolean

typealias FileFixFilter = (InputStream, OutputStream) -> Boolean

object Fixes {

    fun chainStreamModifiers(modifiers: List<FileFixFilter>): FileFixFilter {
        return { inputStream, outputStream ->
            var currentInputStream = inputStream
            val isDifferent = AtomicBoolean()

            val threads = modifiers.map { modifier ->
                val pipedOutputStream = PipedOutputStream()
                val pipedInputStream = PipedInputStream(pipedOutputStream)

                val thisInput = currentInputStream
                Thread {
                    if (modifier(thisInput, pipedOutputStream)) {
                        isDifferent.set(true)
                    }
                    pipedOutputStream.close()
                }.also {
                    currentInputStream = pipedInputStream
                }
            }
            threads.forEach { it.start() }

            currentInputStream.copyTo(outputStream)

            threads.forEach { it.join() }

            isDifferent.get()
        }
    }
}
