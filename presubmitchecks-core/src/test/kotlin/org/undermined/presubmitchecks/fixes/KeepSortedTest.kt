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

    // TODO: Automate all tests using https://github.com/google/keep-sorted/tree/main/goldens

    @Test
    fun testBasic() {
        val config = KeepSortedConfig(matchRegexp = KeepSortedConfig.pattern("kt"))
        keepSorted.checkSorted(
            config,
            """
                before
            // keep-sorted start
            
            b
            a
            c
            // keep-sorted end
            end
            """.trimIndent(),
            """
                before
            // keep-sorted start
            
            a
            b
            c
            // keep-sorted end
            end
            """.trimIndent()
        )
    }

    @Test
    fun testWhitespace() {
        val config = KeepSortedConfig(matchRegexp = KeepSortedConfig.pattern("kt"))
        keepSorted.checkSorted(
            config,
            """
            // keep-sorted start
                b
              c
              a
            // keep-sorted end
            middle
            // keep-sorted start
                b
            c
            a
            // keep-sorted end
            """.trimIndent(),
            """
            // keep-sorted start
                b
              a
              c
            // keep-sorted end
            middle
            // keep-sorted start
                b
            a
            c
            // keep-sorted end
            """.trimIndent()
        )
    }

    @Test
    fun testStickyComments() {
        val config = KeepSortedConfig(matchRegexp = KeepSortedConfig.pattern("kt"))
        keepSorted.checkSorted(
            config,
            """
            # keep-sorted start sticky_comments=no
            # alice
            username: al1
            # bob
            username: bo2
            # charlie
            username: ch3
            # keep-sorted end
            """.trimIndent(),
            """
            # keep-sorted start sticky_comments=no
            # alice
            # bob
            # charlie
            username: al1
            username: bo2
            username: ch3
            # keep-sorted end
            """.trimIndent()
        )
        keepSorted.checkSorted(
            config,
            """
            # keep-sorted start sticky_comments=yes
            # alice
            username: al1
            # bob
            username: bo2
            # charlie
            username: ch3
            # keep-sorted end
            """.trimIndent(),
            """
            # keep-sorted start sticky_comments=yes
            # alice
            username: al1
            # bob
            username: bo2
            # charlie
            username: ch3
            # keep-sorted end
            """.trimIndent()
        )
    }

    @Test
    fun testBlockAndTemplate() {
        val config = KeepSortedConfig(
            matchRegexp = KeepSortedConfig.pattern("kt"),
            templates = mapOf(
                "gradle.kts" to KeepSortedSectionConfig(
                    block = true
                )
            )
        )
        keepSorted.checkSorted(
            config,
            """
            // keep-sorted start template=gradle.kts
            implementation(b.c) {
                because("c")
            }
            implementation(a.a) {
                because("a")
            }
            implementation(a.b) {
                because("b")
            }
            // keep-sorted end
            """.trimIndent(),
            """
            // keep-sorted start template=gradle.kts
            implementation(a.a) {
                because("a")
            }
            implementation(a.b) {
                because("b")
            }
            implementation(b.c) {
                because("c")
            }
            // keep-sorted end
            """.trimIndent()
        )
    }

    @Test
    fun testGroup() {
        val config = KeepSortedConfig(matchRegexp = KeepSortedConfig.pattern("kt"))
        keepSorted.checkSorted(
            config,
            """
            // keep-sorted start group=yes
            private final Bar bar;
            private final Baz baz =
                new Baz()
            private final Foo foo;
            // keep-sorted end
            """.trimIndent(),
            """
            // keep-sorted start group=yes
            private final Bar bar;
            private final Baz baz =
                new Baz()
            private final Foo foo;
            // keep-sorted end
            """.trimIndent()
        )
        keepSorted.checkSorted(
            config,
            """
            // keep-sorted start group=no
            private final Bar bar;
            private final Baz baz =
                new Baz()
            private final Foo foo;
            // keep-sorted end
            """.trimIndent(),
            """
            // keep-sorted start group=no
                new Baz()
            private final Bar bar;
            private final Baz baz =
            private final Foo foo;
            // keep-sorted end
            """.trimIndent()
        )
    }

    @Test
    fun testRemoveDuplicates() {
        val config = KeepSortedConfig(matchRegexp = KeepSortedConfig.pattern("kt"))
        keepSorted.checkSorted(
            config,
            """
            # keep-sorted start remove_duplicates=yes
            rotation: bar
            rotation: bar
            rotation: baz
            rotation: foo
            # keep-sorted end
            """.trimIndent(),
            """
            # keep-sorted start remove_duplicates=yes
            rotation: bar
            rotation: baz
            rotation: foo
            # keep-sorted end
            """.trimIndent()
        )
        keepSorted.checkSorted(
            config,
            """
            # keep-sorted start remove_duplicates=no
            rotation: bar
            rotation: bar
            rotation: baz
            rotation: foo
            # keep-sorted end
            """.trimIndent(),
            """
            # keep-sorted start remove_duplicates=no
            rotation: bar
            rotation: bar
            rotation: baz
            rotation: foo
            # keep-sorted end
            """.trimIndent()
        )
    }

    @Test
    fun testNewlineSeparated() {
        val config = KeepSortedConfig(matchRegexp = KeepSortedConfig.pattern("kt"))
        keepSorted.checkSorted(
            config,
            """
            # keep-sorted start
            Apples
            Bananas
            Oranges
            Pineapples
            # keep-sorted end
            """.trimIndent(),
            """
            # keep-sorted start
            Apples
            Bananas
            Oranges
            Pineapples
            # keep-sorted end
            """.trimIndent()
        )
        keepSorted.checkSorted(
            config,
            """
            # keep-sorted start newline_separated=yes
            Apples
            Bananas
            Oranges
            Pineapples
            # keep-sorted end
            """.trimIndent(),
            """
            # keep-sorted start newline_separated=yes
            Apples
            
            Bananas
            
            Oranges
            
            Pineapples
            # keep-sorted end
            """.trimIndent()
        )
    }

    @Test
    fun testSkipLines() {
        val config = KeepSortedConfig(matchRegexp = KeepSortedConfig.pattern("kt"))
        keepSorted.checkSorted(
            config,
            """
            # keep-sorted start skip_lines=2
            Name    | Value
            ------- | -----
            Charlie | Baz
            Delta   | Qux
            Bravo   | Bar
            Alpha   | Foo
            # keep-sorted end
            """.trimIndent(),
            """
            # keep-sorted start skip_lines=2
            Name    | Value
            ------- | -----
            Alpha   | Foo
            Bravo   | Bar
            Charlie | Baz
            Delta   | Qux
            # keep-sorted end
            """.trimIndent()
        )
    }

    @Test
    fun testCase() {
        val config = KeepSortedConfig(matchRegexp = KeepSortedConfig.pattern("kt"))
        keepSorted.checkSorted(
            config,
            """
            # keep-sorted start case=yes
            Bravo
            Delta
            Foxtrot
            alpha
            charlie
            echo
            # keep-sorted end
            """.trimIndent(),
            """
            # keep-sorted start case=yes
            Bravo
            Delta
            Foxtrot
            alpha
            charlie
            echo
            # keep-sorted end
            """.trimIndent()
        )
        keepSorted.checkSorted(
            config,
            """
            # keep-sorted start case=no
            Bravo
            Delta
            Foxtrot
            alpha
            charlie
            echo
            # keep-sorted end
            """.trimIndent(),
            """
            # keep-sorted start case=no
            alpha
            Bravo
            charlie
            Delta
            echo
            Foxtrot
            # keep-sorted end
            """.trimIndent()
        )
    }

    @Test
    fun testGroupPrefixes() {
        val config = KeepSortedConfig(matchRegexp = KeepSortedConfig.pattern("kt"))
        keepSorted.checkSorted(
            config,
            """
            # keep-sorted start group_prefixes=and,with
            spaghetti
            with meatballs
            peanut butter
            and jelly
            hamburger
            with lettuce
            and tomatoes
            # keep-sorted end
            """.trimIndent(),
            """
            # keep-sorted start group_prefixes=and,with
            hamburger
            with lettuce
            and tomatoes
            peanut butter
            and jelly
            spaghetti
            with meatballs
            # keep-sorted end
            """.trimIndent()
        )
        keepSorted.checkSorted(
            config,
            """
            # keep-sorted start group_prefixes=["and", "with"]
            spaghetti
            with meatballs
            peanut butter
            and jelly
            hamburger
            with lettuce
            and tomatoes
            # keep-sorted end
            """.trimIndent(),
            """
            # keep-sorted start group_prefixes=["and", "with"]
            hamburger
            with lettuce
            and tomatoes
            peanut butter
            and jelly
            spaghetti
            with meatballs
            # keep-sorted end
            """.trimIndent()
        )
    }

    @Test
    fun testIgnorePrefixes() {
        val config = KeepSortedConfig(matchRegexp = KeepSortedConfig.pattern("kt"))
        keepSorted.checkSorted(
            config,
            """
            // keep-sorted start ignore_prefixes=fs.setBoolFlag,fs.setIntFlag
            fs.setBoolFlag("paws_with_cute_toebeans", true)
            fs.setBoolFlag("whiskered_adorable_dog", true)
            fs.setIntFlag("pretty_whiskered_kitten", 6)
            // keep-sorted end
            """.trimIndent(),
            """
            // keep-sorted start ignore_prefixes=fs.setBoolFlag,fs.setIntFlag
            fs.setBoolFlag("paws_with_cute_toebeans", true)
            fs.setIntFlag("pretty_whiskered_kitten", 6)
            fs.setBoolFlag("whiskered_adorable_dog", true)
            // keep-sorted end
            """.trimIndent()
        )
    }

    @Test
    fun testTrailingCommas() {
        val config = KeepSortedConfig(matchRegexp = KeepSortedConfig.pattern("kt"))
        keepSorted.checkSorted(
            config,
            """
            // keep-sorted start
            c,
            b,
            a
            // keep-sorted end
            """.trimIndent(),
            """
            // keep-sorted start
            a,
            b,
            c
            // keep-sorted end
            """.trimIndent()
        )
    }

    @Test
    fun testPrefixOrder() {
        val config = KeepSortedConfig(matchRegexp = KeepSortedConfig.pattern("kt"))
        keepSorted.checkSorted(
            config,
            """
            // keep-sorted start prefix_order=INIT_,,FINAL_
            DO_SOMETHING_WITH_BAR
            DO_SOMETHING_WITH_FOO
            FINAL_BAR
            FINAL_FOO
            INIT_BAR
            INIT_FOO
            // keep-sorted end
            """.trimIndent(),
            """
            // keep-sorted start prefix_order=INIT_,,FINAL_
            INIT_BAR
            INIT_FOO
            DO_SOMETHING_WITH_BAR
            DO_SOMETHING_WITH_FOO
            FINAL_BAR
            FINAL_FOO
            // keep-sorted end
            """.trimIndent()
        )
    }

    @Test
    fun testByRegex() {
        val config = KeepSortedConfig(matchRegexp = KeepSortedConfig.pattern("kt"))
        keepSorted.checkSorted(
            config,
            """
            // keep-sorted start by_regex=\w+;
            List<String> foo;
            Object baz;
            String bar;
            // keep-sorted end
            """.trimIndent(),
            """
            // keep-sorted start by_regex=\w+;
            String bar;
            Object baz;
            List<String> foo;
            // keep-sorted end
            """.trimIndent()
        )
        keepSorted.checkSorted(
            config,
            """
            // keep-sorted start by_regex=\w+; prefix_order=foo
            List<String> foo;
            Object baz;
            String bar;
            // keep-sorted end
            """.trimIndent(),
            """
            // keep-sorted start by_regex=\w+; prefix_order=foo
            List<String> foo;
            String bar;
            Object baz;
            // keep-sorted end
            """.trimIndent()
        )
    }

    @Test
    fun testNumeric() {
        val config = KeepSortedConfig(matchRegexp = KeepSortedConfig.pattern("kt"))
        keepSorted.checkSorted(
            config,
            """
            // keep-sorted start numeric=yes
            FOO_100
            FOO_2
            FOO_3
            BAR_1
            BAR_2
            BAR_10
            BAR_00000000000000000000000000000000000000000000009
            BAR_99999999999999999999999999999999999999999999999
            // keep-sorted end
            """.trimIndent(),
            """
            // keep-sorted start numeric=yes
            BAR_1
            BAR_2
            BAR_00000000000000000000000000000000000000000000009
            BAR_10
            BAR_99999999999999999999999999999999999999999999999
            FOO_2
            FOO_3
            FOO_100
            // keep-sorted end
            """.trimIndent()
        )
        keepSorted.checkSorted(
            config,
            """
            deployment_state = [
              // keep-sorted start numeric=yes prefix_order=INIT,ROLLOUT,COMPLETE
              // All done.
              COMPLETE,
              // Start initialisation
              INIT_1,
              INIT_5,
              INIT_10,
              // Only deploy to 0.1%
              ROLLOUT_0_1,
              // just one percent.
              ROLLOUT_1,
              // Nearly done...
              ROLLOUT_100,
              ROLLOUT_10,
              ROLLOUT_5,
              ROLLOUT_50,
              // keep-sorted end
            ]
            """.trimIndent(),
            """
            deployment_state = [
              // keep-sorted start numeric=yes prefix_order=INIT,ROLLOUT,COMPLETE
              // Start initialisation
              INIT_1,
              INIT_5,
              INIT_10,
              // Only deploy to 0.1%
              ROLLOUT_0_1,
              // just one percent.
              ROLLOUT_1,
              ROLLOUT_5,
              ROLLOUT_10,
              ROLLOUT_50,
              // Nearly done...
              ROLLOUT_100,
              // All done.
              COMPLETE,
              // keep-sorted end
            ]
            """.trimIndent()
        )
        keepSorted.checkSorted(
            config,
            """
            droid_components = [
              // keep-sorted start numeric=yes prefix_order=R2,C3
              C3PO_HEAD,
              C3PO_ARM_L,
              R4_MOTIVATOR,
              C3PO_ARM_R,
              R2D2_BOLTS_10_MM,
              R2D2_PROJECTOR,
              R2D2_BOLTS_5_MM,
              // keep-sorted end
            ]
            """.trimIndent(),
            """
            droid_components = [
              // keep-sorted start numeric=yes prefix_order=R2,C3
              R2D2_BOLTS_5_MM,
              R2D2_BOLTS_10_MM,
              R2D2_PROJECTOR,
              C3PO_ARM_L,
              C3PO_ARM_R,
              C3PO_HEAD,
              R4_MOTIVATOR,
              // keep-sorted end
            ]
            """.trimIndent()
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
    ]) // six numbers
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
        val dataDir = File("src/test/resources/fixes/keepsorted").absoluteFile
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