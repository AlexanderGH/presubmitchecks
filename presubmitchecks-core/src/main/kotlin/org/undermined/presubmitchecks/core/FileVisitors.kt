package org.undermined.presubmitchecks.core

import java.io.InputStream
import java.nio.ByteBuffer

object FileVisitors {
    suspend fun visitFile(
        inputStreamProvider: suspend () -> InputStream,
        lineVisitors: Collection<(FileLine) -> Boolean> = emptyList(),
        rawFileVisitorsSequential: Collection<suspend (InputStream) -> Unit> = emptyList(),
        rawFileVisitorsRandom: Collection<suspend (ByteBuffer) -> Unit> = emptyList(),
        isLineModified: (Int) -> Boolean = { false }
    ) {
        if (lineVisitors.isEmpty()
            && rawFileVisitorsSequential.isEmpty()
            && rawFileVisitorsRandom.isEmpty()) {
            return
        }

        lateinit var bb: ByteBuffer
        if (
            rawFileVisitorsRandom.isNotEmpty()
            || rawFileVisitorsSequential.size + (if (lineVisitors.isEmpty()) 0 else 1) > 1
        ) {
            val byteArrayOutputStream = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            var bytesRead: Int

            val inputStream = inputStreamProvider.invoke()
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                byteArrayOutputStream.write(buffer, 0, bytesRead)
            }
            inputStream.close()

            bb = ByteBuffer.wrap(byteArrayOutputStream.toByteArray()).asReadOnlyBuffer()
            object : InputStream() {
                override fun read(): Int {
                    return if (bb.hasRemaining()) {
                        bb.get().toInt() and 0xFF //Read byte as unsigned int
                    } else {
                        -1 // End of stream
                    }
                }

                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    if (!bb.hasRemaining()) {
                        return -1
                    }

                    val actualLen = minOf(len, bb.remaining()) // Don't read past buffer limit
                    bb.get(b, off, actualLen)
                    return actualLen
                }

                override fun available(): Int = bb.remaining()
            }
        } else {
            bb = ByteBuffer.allocate(0)
            inputStreamProvider.invoke()
        }.use { inputStream ->
            if (lineVisitors.isNotEmpty()) {
                val lineVisitorsLocal = mutableListOf<((FileLine) -> Boolean)?>().apply {
                    addAll(lineVisitors)
                }
                val buffer = ByteArray(4096)
                val currentLine = StringBuilder()
                var previousCharWasCR = false
                val fileLine = FileLine()
                var shouldContinue = true

                fun processLine(
                    content: String,
                    nl: Boolean
                ) {
                    fileLine.content = content
                    fileLine.line++
                    fileLine.nl = nl
                    fileLine.isModified = isLineModified(fileLine.line)

                    shouldContinue = false
                    lineVisitorsLocal.forEachIndexed { index, function ->
                        if (function != null) {
                            if (!function.invoke(fileLine)) {
                                lineVisitorsLocal[index] = null
                            } else {
                                shouldContinue = true
                            }
                        }
                    }
                }

                while (shouldContinue) {
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead == -1) {
                        if (currentLine.isNotEmpty()) {
                            processLine(currentLine.toString(), false)
                        }
                        break
                    }

                    for (i in 0 until bytesRead) {
                        val char = buffer[i].toInt().toChar()

                        if (previousCharWasCR && char == '\n') {
                            processLine(currentLine.toString(), true)
                            currentLine.clear()
                            previousCharWasCR = false
                        } else if (char == '\n') {
                            processLine(currentLine.toString(), true)
                            currentLine.clear()
                            previousCharWasCR = false
                        } else if (char == '\r') {
                            previousCharWasCR = true
                        } else {
                            if (previousCharWasCR) {
                                processLine(currentLine.toString(), true)
                                currentLine.clear()
                                previousCharWasCR = false
                            }
                            currentLine.append(char)
                        }
                    }
                }
            }
            rawFileVisitorsSequential.forEach {
                bb.rewind()
                it.invoke(inputStream)
            }
            rawFileVisitorsRandom.forEach {
                bb.rewind()
                it.invoke(bb)
            }
        }
    }

    class FileLine {
        var content: String = ""
            internal set
        var line: Int = 0
            internal set
        var nl: Boolean = false
            internal set
        var isModified: Boolean = false
            internal set
    }
}
