package org.undermined.presubmitchecks.fixes

import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

typealias FileFixFilter = (InputStream, OutputStream) -> Boolean

object Fixes {

    fun chainStreamModifiers(modifiers: List<FileFixFilter>): FileFixFilter {
        return { inputStream, outputStream ->
            var currentInputStream = inputStream
            val isDifferent = AtomicBoolean(false)

            val threads = modifiers.map { modifier ->
                val pipedOutputStream = PipedOutputStream()
                val pipedInputStream = PipedInputStream(pipedOutputStream)

                val thisInput = currentInputStream
                Thread {
                    try {
                        if (modifier(thisInput, pipedOutputStream)) {
                            isDifferent.set(true)
                        }
                    } finally {
                        pipedOutputStream.close()
                    }
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

    fun didLinesChange(
        input: Sequence<String>,
        firstChangedLine: AtomicInteger,
        transform: suspend SequenceScope<String>.(Sequence<String>) -> Unit
    ): Sequence<String> {
        var changed = false
        val queue = ArrayDeque<String>(32)
        var line = 0
        firstChangedLine.set(-1)
        return sequence {
            transform(input.onEach {
                if (!changed) {
                    queue.addLast(it)
                }
            })
        }.onEach { out ->
            line++
            if (!changed && queue.removeFirstOrNull() != out) {
                changed = true
                firstChangedLine.set(line)
                queue.clear()
            }
        }
    }

    fun InputStream.transformLines(
        outputStream: OutputStream,
        transform: suspend SequenceScope<String>.(Sequence<String>) -> Unit
    ): Boolean {
        val trackedInputStream = TrackingInputStream(this)
        val inLines = trackedInputStream.bufferedReader().lineSequence()
        val outWriter = outputStream.writer()
        val changed = AtomicInteger()
        didLinesChange(inLines, changed) {
            this.transform(inLines)
        }.forEachIndexed { i, out ->
            if (i != 0) {
                outWriter.write("\n")
            }
            outWriter.write(out)
        }
        if (trackedInputStream.getLastByte() == '\n'.code) {
            outWriter.write("\n")
        }
        outWriter.close()
        return changed.get() != -1
    }

    private class TrackingInputStream(private val inputStream: InputStream) : InputStream() {

        private var lastByte: Int = -1 // Initialize with -1 to indicate no byte read yet

        fun getLastByte(): Int = lastByte

        override fun read(): Int {
            val byte = inputStream.read()
            if (byte != -1) {
                lastByte = byte // Update lastByte only if a byte was actually read
            }
            return byte
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val bytesRead = inputStream.read(b, off, len)
            if (bytesRead > 0) {
                lastByte = b[off + bytesRead - 1].toInt() and 0xFF // Update lastByte with the last byte read
            }
            return bytesRead
        }

        override fun available(): Int = inputStream.available()
        override fun close() = inputStream.close()
    }
}
