package org.undermined.presubmitchecks.checks

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test
import org.undermined.presubmitchecks.core.Changelist
import org.undermined.presubmitchecks.core.ChangelistVisitor
import org.undermined.presubmitchecks.core.CheckResultMessage
import org.undermined.presubmitchecks.core.CheckerConfig
import org.undermined.presubmitchecks.core.CoreConfig
import org.undermined.presubmitchecks.core.FileContents
import org.undermined.presubmitchecks.core.visit
import org.undermined.presubmitchecks.git.GitChangelists.parseFilePatch
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.containsExactly
import strikt.assertions.filterIsInstance
import strikt.assertions.hasSize
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.single
import strikt.assertions.withSingle

internal class IfChangeThenChangeCheckerTest {
    companion object {
        private const val LINT_IC = "LINT.${""}IfChange"
        private const val LINT_TC = "LINT.${""}ThenChange"
    }

    @Test
    fun testNoBlocks() {
        val changelist = Changelist(
            title = "",
            description = "",
            patchOnly = false,
            files = listOf(
                Changelist.FileOperation.AddedFile(
                    name = "foo.txt",
                    patchLines = emptyList(),
                    afterRef = "",
                    afterRevision = FileContents.Text(
                        suspend {
                            """
                            foo
                            bar
                            """.trimIndent().lineSequence()
                        }
                    )
                )
            ),
        )

        runBlocking {
            val reporter = CheckerTests.runChecker(
                changelist,
                IfChangeThenChangeChecker.PROVIDER
            )
            expectThat(reporter.results).isEmpty()
        }
    }

    @Test
    fun testReciprocalBlockPass() {
        val changelist = Changelist(
            title = "",
            description = "",
            patchOnly = false,
            files = listOf(
                Changelist.FileOperation.AddedFile(
                    name = "foo.txt",
                    patchLines = emptyList(),
                    afterRef = "",
                    afterRevision = FileContents.Text(
                        suspend {
                            """
                            foo
                            $LINT_IC
                            bar
                            $LINT_TC(bar.txt)
                            """.trimIndent().lineSequence()
                        }
                    )
                ),
                Changelist.FileOperation.ModifiedFile(
                    name = "bar.txt",
                    patchLines = listOf(
                        Changelist.PatchLine(Changelist.ChangeOperation.ADDED, 2, "baz")
                    ),
                    beforeRef = "",
                    afterRef = "",
                    afterRevision = FileContents.Text(
                        suspend {
                            """
                            $LINT_IC
                            baz
                            $LINT_TC(foo.txt)
                            """.trimIndent().lineSequence()
                        }
                    )
                )
            ),
        )

        runBlocking {
            val reporter = CheckerTests.runChecker(
                changelist,
                IfChangeThenChangeChecker.PROVIDER
            )
            expectThat(reporter.results).isEmpty()
        }
    }

    @Test
    fun testReciprocalBlockFail() {
        val changelist = Changelist(
            title = "",
            description = "",
            patchOnly = false,
            files = listOf(
                Changelist.FileOperation.AddedFile(
                    name = "foo.txt",
                    patchLines = emptyList(),
                    afterRef = "",
                    afterRevision = FileContents.Text(
                        suspend {
                            """
                            foo
                            $LINT_IC
                            bar
                            $LINT_TC(bar.txt:a)
                            """.trimIndent().lineSequence()
                        }
                    )
                ),
                Changelist.FileOperation.ModifiedFile(
                    name = "bar.txt",
                    patchLines = listOf(
                        Changelist.PatchLine(Changelist.ChangeOperation.ADDED, 4, "bob")
                    ),
                    beforeRef = "",
                    afterRef = "",
                    afterRevision = FileContents.Text(
                        suspend {
                            """
                            $LINT_IC(a)
                            baz
                            $LINT_TC(foo.txt)
                            bob
                            """.trimIndent().lineSequence()
                        }
                    )
                )
            )
        )

        runBlocking {
            val reporter = CheckerTests.runChecker(
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
        val changelist = Changelist(
            title = "",
            description = "",
            patchOnly = false,
            files = listOf(
                Changelist.FileOperation.ModifiedFile(
                    name = "FileA.txt",
                    patchLines = listOf(
                        Changelist.PatchLine(Changelist.ChangeOperation.ADDED, 2, "bob")
                    ),
                    beforeRef = "",
                    afterRef = "",
                    afterRevision = FileContents.Text(
                        suspend {
                            """
                            $LINT_IC(a)
                            a
                            $LINT_TC(FileB.txt:a)
                            
                            $LINT_IC
                            
                            $LINT_TC(FileB.txt:c)
                            """.trimIndent().lineSequence()
                        }
                    )
                )
            )
        )

        runBlocking {
            val reporter = CheckerTests.runChecker(
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
