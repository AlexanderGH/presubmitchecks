package org.undermined.presubmitchecks.checks

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.undermined.presubmitchecks.core.Changelist
import org.undermined.presubmitchecks.core.FileContents
import org.undermined.presubmitchecks.core.visit
import strikt.api.expectThat
import strikt.assertions.containsExactly
import strikt.assertions.isEmpty

internal class IfChangeThenChangeCheckerTest {
    private val LINT_IC = "LINT.${""}IfChange"
    private val LINT_TC = "LINT.${""}ThenChange"

    @Test
    fun testNoBlocks() {
        val changelist = Changelist(
            title = "",
            description = "",
            files = listOf(
                Changelist.FileOperation.AddedFile(
                    name = "foo.txt",
                    afterRevision = FileContents.Text(
                        suspend {
                            """
                            foo
                            bar
                            """.trimIndent().lineSequence()
                        }
                    )
                )
            )
        )

        runBlocking {
            val checker = IfChangeThenChangeChecker()
            changelist.visit(listOf(checker))
            expectThat(checker.getMissingChanges()).isEmpty()
        }
    }

    @Test
    fun testReciprocalBlockPass() {
        val changelist = Changelist(
            title = "",
            description = "",
            files = listOf(
                Changelist.FileOperation.AddedFile(
                    name = "foo.txt",
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
            )
        )

        runBlocking {
            val checker = IfChangeThenChangeChecker()
            changelist.visit(listOf(checker))
            expectThat(checker.getMissingChanges()).isEmpty()
        }
    }


    @Test
    fun testReciprocalBlockFail() {
        val changelist = Changelist(
            title = "",
            description = "",
            files = listOf(
                Changelist.FileOperation.AddedFile(
                    name = "foo.txt",
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
            val checker = IfChangeThenChangeChecker()
            changelist.visit(listOf(checker))
            expectThat(checker.getMissingChanges()).containsExactly("//bar.txt:a")
        }
    }
}