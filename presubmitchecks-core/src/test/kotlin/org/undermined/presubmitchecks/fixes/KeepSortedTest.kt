package org.undermined.presubmitchecks.fixes

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.io.File
import java.net.URL
import java.nio.file.Files

class KeepSortedTest {
    private val keepSorted = KeepSorted()

    @Test
    fun testBasic() {
        val config = KeepSortedConfig(matchRegexp = KeepSortedConfig.pattern("test"))
        keepSorted.checkSorted(
            config,
            """
                before
            // keep-sorted-test start
            
            b
            a
            c
            // keep-sorted-test end
            end
            """.trimIndent(),
            """
                before
            // keep-sorted-test start
            
            a
            b
            c
            // keep-sorted-test end
            end
            """.trimIndent()
        )
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "template",
        "maintain_suffix",
        "separator", // More edge-cases the google tests do not catch
        "readme_examples",
        "whitespace",
    ])
    fun testExtensions(checkId: String) {
        val config = KeepSortedConfig(
            matchRegexp = KeepSortedConfig.pattern("test"),
            templates = mapOf(
                "gradle.kts" to KeepSortedSectionConfig(
                    block = true
                )
            )
        )

        val dataDir = File("src/test/resources/fixes/keepsorted/extended").absoluteFile
        keepSorted.checkSorted(
            config,
            File(dataDir, "$checkId.in").readText(),
            File(dataDir, "$checkId.out").readText(),
        )
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "block",
        "by_regex",
        "case_insensitive",
        "comments",
        "duplicates",
        "group",
        "ignore_prefixes",
        "numeric",
        "prefixes",
        "separator",
        "simple",
        "skip_lines",
        "sticky_prefixes",
    ])
    fun testGoogleCompat(checkId: String) {
        val config = KeepSortedConfig(matchRegexp = KeepSortedConfig.pattern("test"))

        keepSorted.checkGoogleCompatibility(config, checkId)
    }

    private fun KeepSorted.checkSorted(config: KeepSortedConfig, input: String, output: String) {
        val actualOutput = sort(
            config,
            input.lineSequence()
        ).joinToString("\n")
        Assertions.assertEquals(output, actualOutput)
        expectThat(actualOutput).isEqualTo(output)
        val secondSort = sort(config, actualOutput.lineSequence()).joinToString("\n")
        Assertions.assertEquals(output, secondSort)
        expectThat(secondSort)
            .describedAs("Produces stable sort output")
            .isEqualTo(output)
    }

    private fun KeepSorted.checkGoogleCompatibility(
        config: KeepSortedConfig,
        checkId: String,
    ) {
        val dataDir = File("src/test/resources/fixes/keepsorted/google").absoluteFile
        if (!dataDir.exists()) {
            dataDir.mkdirs()
        }
        val inputFile = File(dataDir, "$checkId.in")
        if (!inputFile.exists()) {
            val url = URL("https://raw.githubusercontent.com/google/keep-sorted/refs/heads/main/goldens/$checkId.in")
            url.openStream().use {
                Files.copy(it, inputFile.toPath())
            }
        }
        val outputFile = File(dataDir, "$checkId.out")
        if (!outputFile.exists()) {
            val url = URL("https://raw.githubusercontent.com/google/keep-sorted/refs/heads/main/goldens/$checkId.out")
            url.openStream().use {
                Files.copy(it, outputFile.toPath())
            }
        }
        checkSorted(config, inputFile.readText(), outputFile.readText())
    }
}
