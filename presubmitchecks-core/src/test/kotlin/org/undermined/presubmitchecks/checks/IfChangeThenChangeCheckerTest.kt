package org.undermined.presubmitchecks.checks

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.undermined.presubmitchecks.core.Changelist
import org.undermined.presubmitchecks.core.CheckResultMessage
import strikt.api.expectThat
import strikt.assertions.filterIsInstance
import strikt.assertions.hasSize
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.withSingle

internal class IfChangeThenChangeCheckerTest {
    companion object {
        private const val LINT_IC = "LINT.${""}IfChange"
        private const val LINT_TC = "LINT.${""}ThenChange"
    }

    val testRepository = CheckerTests.TestRepository()

    @Test
    fun testNoBlocks() {
        testRepository.files["foo.txt:1"] = """
            foo
            bar
        """.trimIndent()

        val changelist = Changelist(
            title = "",
            description = "",
            files = listOf(
                Changelist.FileOperation.AddedFile(
                    name = "foo.txt",
                    patchLines = emptyList(),
                    afterRef = "1",
                    isBinary = false,
                )
            ),
        )

        runBlocking {
            val reporter = CheckerTests.runChecker(
                testRepository,
                changelist,
                IfChangeThenChangeChecker.PROVIDER
            )
            expectThat(reporter.results).isEmpty()
        }
    }

    @Test
    fun testReciprocalBlockPass() {
        testRepository.files["foo.txt:1"] = """
            foo
            $LINT_IC
            bar
            $LINT_TC(bar.txt)
        """.trimIndent()
        testRepository.files["bar.txt:2"] = """
            $LINT_IC
            baz
            $LINT_TC(foo.txt)
        """.trimIndent()

        val changelist = Changelist(
            title = "",
            description = "",
            files = listOf(
                Changelist.FileOperation.AddedFile(
                    name = "foo.txt",
                    patchLines = emptyList(),
                    afterRef = "1",
                    isBinary = false,
                ),
                Changelist.FileOperation.ModifiedFile(
                    name = "bar.txt",
                    patchLines = listOf(
                        Changelist.PatchLine(Changelist.ChangeOperation.ADDED, 2, "baz")
                    ),
                    beforeRef = "1",
                    afterRef = "2",
                    isBinary = false,
                )
            ),
        )

        runBlocking {
            val reporter = CheckerTests.runChecker(
                testRepository,
                changelist,
                IfChangeThenChangeChecker.PROVIDER
            )
            expectThat(reporter.results).isEmpty()
        }
    }

    @Test
    fun testReciprocalBlockFail() {
        testRepository.files["foo.txt:1"] = """
            foo
            $LINT_IC
            bar
            $LINT_TC(bar.txt:a)
        """.trimIndent()
        testRepository.files["bar.txt:2"] = """
            $LINT_IC(a)
            baz
            $LINT_TC(foo.txt)
            bob
        """.trimIndent()
        val changelist = Changelist(
            title = "",
            description = "",
            files = listOf(
                Changelist.FileOperation.AddedFile(
                    name = "foo.txt",
                    patchLines = emptyList(),
                    afterRef = "1",
                    isBinary = false,
                ),
                Changelist.FileOperation.ModifiedFile(
                    name = "bar.txt",
                    patchLines = listOf(
                        Changelist.PatchLine(Changelist.ChangeOperation.ADDED, 4, "bob")
                    ),
                    beforeRef = "1",
                    afterRef = "2",
                    isBinary = false,
                )
            )
        )

        runBlocking {
            val reporter = CheckerTests.runChecker(
                testRepository,
                changelist,
                IfChangeThenChangeChecker.PROVIDER
            )
            expectThat(reporter.results)
                .filterIsInstance<CheckResultMessage>()
                .hasSize(1)
                .withSingle {
                    this.get { toConsoleOutput() }.isEqualTo(
                        """
                        IfChangeThenChange (ERROR)
                        Missing Changes
                        The following locations should also be changed:
                          //bar.txt:a
                        foo.txt 3:null null:null
                        """.trimIndent()
                    )
                }
        }
    }

    @Test
    fun testNamedModifiedUnnamedNot() {
        testRepository.files["FileA.txt:2"] = """
            $LINT_IC(a)
            a
            $LINT_TC(FileB.txt:a)
            
            $LINT_IC
            
            $LINT_TC(FileB.txt:c)
        """.trimIndent()
        val changelist = Changelist(
            title = "",
            description = "",
            files = listOf(
                Changelist.FileOperation.ModifiedFile(
                    name = "FileA.txt",
                    patchLines = listOf(
                        Changelist.PatchLine(Changelist.ChangeOperation.ADDED, 2, "bob")
                    ),
                    beforeRef = "1",
                    afterRef = "2",
                    isBinary = false,
                )
            )
        )

        runBlocking {
            val reporter = CheckerTests.runChecker(
                testRepository,
                changelist,
                IfChangeThenChangeChecker.PROVIDER
            )
            expectThat(reporter.results)
                .filterIsInstance<CheckResultMessage>()
                .hasSize(1)
                .withSingle {
                    this.get { toConsoleOutput() }.isEqualTo(
                        """
                        IfChangeThenChange (ERROR)
                        Missing Changes
                        The following locations should also be changed:
                          //FileB.txt:a
                        FileA.txt 2:null null:null
                        """.trimIndent()
                    )
                }
        }
    }
}
