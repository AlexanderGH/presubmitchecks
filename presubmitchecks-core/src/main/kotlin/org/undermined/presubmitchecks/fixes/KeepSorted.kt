package org.undermined.presubmitchecks.fixes

import com.google.re2j.Pattern
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.min

class KeepSorted {
    fun sort(
        config: KeepSortedConfig,
        lines: Sequence<String>,
    ): Sequence<String> {
        return sequence {
            val sectionStack = ArrayDeque<SortSectionState>()

            lines.forEach { line ->
                val match = config.matchRegexp.matchEntire(line)
                if (match != null) {
                    // Found keep-sorted directive
                    val groups = match.groups
                    if (groups["start"] != null) {
                        val configString = groups["config"]?.value
                        val sectionConfig = if (configString != null) {
                            KeepSortedSectionConfig.parse(
                                configString,
                                templates = config.templates,
                            )
                        } else {
                            KeepSortedSectionConfig()
                        }.apply {
                            this.leadingWhiteSpace = groups["prefix"]!!.value
                            this.commentPrefix = groups["comment"]!!.value
                        }
                        SortSectionState(config, sectionConfig).apply {
                            sectionStack.addLast(this)
                            addPrefixLine(line)
                        }
                    } else if (groups["end"] != null) {
                        val endedSectionState = sectionStack.removeLastOrNull()
                        check(endedSectionState != null) { "No keep-sorted block started" }
                        endedSectionState.addSuffixLine(line)
                        val currentSectionBlock = sectionStack.lastOrNull()
                        if (currentSectionBlock != null) {
                            var sortKeyPosition = currentSectionBlock.groupSortKey.length
                            endedSectionState.sorted().forEachIndexed { i, it ->
                                if (i == 0) {
                                    currentSectionBlock.nextLine(it)
                                    sortKeyPosition = min(currentSectionBlock.groupSortKey.length, sortKeyPosition)
                                } else {
                                    currentSectionBlock.allLines.add(it)
                                }
                            }
                            currentSectionBlock.groupSortKey.setLength(sortKeyPosition)
                            currentSectionBlock.groupSortKey.append(endedSectionState.groupSortKey)
                        } else {
                            endedSectionState.sorted().forEach {
                                yield(it)
                            }
                        }
                    }
                } else if (sectionStack.isEmpty()) {
                    // Not in a keep-sorted section. Just emit the line.
                    yield(line)
                } else {
                    sectionStack.last().nextLine(line)
                }
            }
        }
    }

    private class SortSectionState(
        val globalConfig: KeepSortedConfig,
        val sectionConfig: KeepSortedSectionConfig
    ) {
        val groupSortKey = StringBuilder()
        val groupBlockStack = ArrayDeque<Char>()
        var minIndent = Int.MAX_VALUE
        var groupBlockStackIgnoreUntil: Regex? = null
        var commentStartLine = 0
        var contentStartLine = 0
        var lastBlankLines = 0

        val allLines = mutableListOf<String>()
        val groups = mutableListOf<GroupRecord>()
        var prefixes = 0
        var suffixes = 0

        fun addPrefixLine(line: String) {
            check(allLines.size == prefixes)
            prefixes++
            commentStartLine++
            contentStartLine++
            allLines.add(line)
        }

        fun addSuffixLine(line: String) {
            if (!groupBlockStack.isEmpty()) {
                //error("Unbalanced Blocks: '${groupBlockStack}'")
            }
            groupBlockStack.clear()
            processPendingState()
            if (suffixes == 0) {
                val oneLines = groups.filter { it.isOneLine }
                val hasWhitespace = oneLines.any {
                    it.sortKey.comparisonValue.any { it.isWhitespace() }
                }
                if (oneLines.isNotEmpty() && !hasWhitespace) {
                    groups.replaceAll {
                        it.copy(
                            sortKey = it.sortKey.copy(
                                comparisonValue = it.sortKey.comparisonValue.replace(" ", "")
                            )
                        )
                    }
                }
                suffixes += allLines.size - commentStartLine
            }
            suffixes++
            commentStartLine++
            contentStartLine++
            allLines.add(line)
        }

        fun nextLine(line: String) {
            check(suffixes == 0)
            if (prefixes - 1 < sectionConfig.skipLines) {
                addPrefixLine(line);
                return
            }

            if (line.isBlank()) {
                if (sectionConfig.newlineSeparated) {
                    lastBlankLines++
                    allLines.add(line)
                } else {
                    if (allLines.size == prefixes
                        && (prefixes == 0 || allLines.last().isNotBlank())) {
                        addPrefixLine(line);
                        return
                    }
                    processPendingState()
                    lastBlankLines++
                    allLines.add(line)
                }
                return
            }
            // We only want to keep trailing new lines. If we got here that means we're not at the
            // end of the sort section and want to drop the previous new lines.
            while (lastBlankLines > 0) {
                allLines.removeLast()
                lastBlankLines--
            }

            val indent = line.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
            val isIndented = indent > minIndent
            if (groupBlockStack.isEmpty()) {
                minIndent = min(minIndent, indent)
                if (!sectionConfig.group) {
                    processPendingState()
                } else {
                    if (line.length >= minIndent) {
                        val isCustomGroup = sectionConfig.groupPrefixes.any {
                            line.startsWith(it, indent)
                        }
                        if (!isCustomGroup) {
                            if (indent == minIndent) {
                                processPendingState()
                            }
                        }
                    }
                }
            }

            if (!line.startsWith(sectionConfig.leadingWhiteSpace)) {
                //error("Line has invalid indent: $line")
            }
            if (sectionConfig.stickyComments && sectionConfig.commentPrefix.isNotEmpty()) {
                if (line.startsWith(sectionConfig.commentPrefix, sectionConfig.leadingWhiteSpace.length)) {
                    allLines.add(line)
                    contentStartLine++
                    // We expect a continuation so don't process the current block
                    return
                }
            }
            if (sectionConfig.stickyPrefixes.isNotEmpty()) {
                for (prefix in sectionConfig.stickyPrefixes) {
                    if (line.startsWith(prefix, sectionConfig.leadingWhiteSpace.length)) {
                        allLines.add(line)
                        contentStartLine++
                        // We expect a continuation so don't process the current block
                        return
                    }
                }
            }
            if (sectionConfig.block) {
                var i = 0
                while (i < line.length) {
                    val ignoreUntil = groupBlockStackIgnoreUntil
                    if (ignoreUntil != null) {
                        val end = ignoreUntil.matchAt(line, i)
                        if (end != null) {
                            i += end.value.length
                            groupBlockStackIgnoreUntil = null
                            groupBlockStack.removeLast()
                            continue
                        } else {
                            i = line.length
                            break
                        }
                    }
                    val startIgnore = globalConfig.ignoreBlocks.entries.firstOrNull {
                        line.startsWith(it.key, i)
                    }
                    if (startIgnore != null) {
                        groupBlockStackIgnoreUntil = startIgnore.value
                        groupBlockStack.addLast(' ')
                        i += startIgnore.key.length
                        continue
                    }

                    val char = line[i]
                    if (char == ' ') {
                        i++
                        continue
                    }
                    val expectClosing = globalConfig.blocks[char]
                    if (expectClosing != null) {
                        groupBlockStack.addLast(expectClosing)
                    } else if (groupBlockStack.isNotEmpty() && char == groupBlockStack.last()) {
                        groupBlockStack.removeLast()
                    }
                    i++
                }
            }
            allLines.add(line)
            groupSortKey.append(
                if (groupBlockStackIgnoreUntil == null) {
                    if (groupBlockStack.isNotEmpty()) {
                        line.trimStart()
                    } else if (allLines.size - contentStartLine > 1 && isIndented) {
                        line.substring(indent - 1)
                    } else if (indent == minIndent) {
                        if (line.startsWith(sectionConfig.leadingWhiteSpace)) {
                            line.removePrefix(sectionConfig.leadingWhiteSpace)
                        } else {
                            line.trimStart()
                        }
                    } else {
                        line
                    }
                } else {
                    line
                }
            )
        }

        fun processPendingState() {
            check(groupBlockStack.isEmpty())
            if (contentStartLine == allLines.size - lastBlankLines) {
                return
            }
            groups.add(GroupRecord(
                startComment = commentStartLine,
                startContent = contentStartLine,
                endContent = allLines.size - lastBlankLines,
                sortKey = sortKeyForLineConfig(sectionConfig, groupSortKey.toString())
            ))
            groupSortKey.clear()
            commentStartLine = allLines.size - lastBlankLines
            contentStartLine = allLines.size - lastBlankLines
        }

        fun sorted(): Sequence<String> {
            return sequence {
                val suffixInfo = if (sectionConfig.maintainSuffixOrder != null) {
                    val suffixes = mutableMapOf<Int, String>()
                    val matcher = sectionConfig.maintainSuffixOrder
                    groups.forEachIndexed { index, it ->
                        val lastLine = last(it)
                        val match = matcher.matcher(lastLine)
                        if (match.find()) {
                            suffixes[index] = match.group(1)
                        }
                    }
                    fun(line: String, index: Int, _: Boolean): String {
                        val match = matcher.matcher(line)
                        return if (match.find() && suffixes.contains(index)) {
                            match.replaceFirst("${suffixes[index] ?: ""}${match.group(2) ?: ""}")
                        } else if (suffixes.contains(index)) {
                            line + (suffixes[index] ?: "")
                        } else {
                            line
                        }
                    }
                } else if (groups.count {
                        last(it).endsWith(sectionConfig.maintainGroupSeparator)
                    } == groups.size - 1) {
                    // Google's keep-sorted behavior seems to always shuffle the "missing"
                    // suffix to the end, even if the missing suffix was not at the end in the
                    // original. This is likely so that you can always insert an unsuffixed
                    // item at any position and end up with valid code. However, it's kind of
                    // weird since if the list already had commas on all items, it makes sense
                    // to just add it on the new item too, and if the last item didn't have a comma
                    // it not becomes unclear at which % of groups having a comma we enable this
                    // logic.
                    fun(line: String, _: Int, isLast: Boolean) = if (isLast) {
                        line.trim(sectionConfig.maintainGroupSeparator)
                    } else if (!line.endsWith(sectionConfig.maintainGroupSeparator)) {
                        line + sectionConfig.maintainGroupSeparator
                    } else {
                        line
                    }
                } else {
                    fun(line: String, index: Int, _: Boolean) = line
                }

                if (globalConfig.debug) {
                    sectionConfig.let {
                        println("Config: ${it} '${it.leadingWhiteSpace}' '${it.commentPrefix}'")
                    }
                    println("Sort Before: ${groups.map { it.sortKey }}")
                }

                val sorted = groups.toMutableList()
                sorted.sortWith { a, b ->
                    val prefixOrder = a.sortKey.prefixOrder.compareTo(b.sortKey.prefixOrder)
                    if (prefixOrder == 0) {
                        a.sortKey.comparisonValue.compareTo(b.sortKey.comparisonValue, ignoreCase = false)
                    } else {
                        prefixOrder
                    }
                }
                if (!sectionConfig.case) {
                    sorted.sortWith { a, b ->
                        val prefixOrder = a.sortKey.prefixOrder.compareTo(b.sortKey.prefixOrder)
                        if (prefixOrder == 0) {
                            a.sortKey.comparisonValue.compareTo(b.sortKey.comparisonValue, ignoreCase = true)
                        } else {
                            prefixOrder
                        }
                    }
                }
                if (globalConfig.debug) {
                    println("Sort After: ${sorted.map { it.sortKey }}")
                }

                groupSortKey.clear()

                allLines.take(prefixes).forEach {
                    yield(it)
                }

                var lastKey = GroupRecord.NULL_GROUP_RECORD
                sorted.forEachIndexed { i, it ->
                    val isLast = i == sorted.size - 1
                    if (!sectionConfig.removeDuplicates || !areEqual(it, lastKey)) {
                        groupSortKey.append(it.sortKey.comparisonValue)
                        forEachLine(it) { line, _, isLastInGroup ->
                            yield(if (isLastInGroup) {
                                suffixInfo(line, i, isLast)
                            } else {
                                line
                            })
                            lastKey = it
                        }
                    }
                    if (sectionConfig.newlineSeparated && !isLast) {
                        yield("")
                    }
                }

                allLines.takeLast(suffixes).forEach {
                    yield(it)
                }
            }
        }

        private fun sortKeyForLineConfig(config: KeepSortedSectionConfig, line: String): GroupRecord.SortKey {
            var comparisonValue = line
            for (prefix in config.ignorePrefixes) {
                if (comparisonValue.startsWith(prefix, ignoreCase = !config.case)) {
                    comparisonValue = comparisonValue.substring(prefix.length).trimStart()
                    break
                }
            }
            if (config.byRegex.isNotEmpty()) {
                val matches = mutableListOf<String>()
                config.byRegex.forEach { regex ->
                    val matcher = regex.matcher(comparisonValue)
                    while (matcher.find()) {
                        if (matcher.groupCount() == 0) {
                            matches.add(matcher.group())
                        } else {
                            for (i in 0 until  matcher.groupCount()) {
                                matches.add(matcher.group(i + 1))
                            }
                        }
                    }
                }
                if (matches.isNotEmpty()) {
                    // Tie-breaker
                    matches.add(comparisonValue)
                    // This is pretty hacky. Ideally we would support segment-based sorting similar to
                    // the real keep-sorted.
                    comparisonValue = matches.joinToString("\n")
                }
            }

            var prefixOrder = config.prefixOrder.size
            if (prefixOrder > 0) {
                var maxLength = -1
                config.prefixOrder.forEachIndexed { order, prefix ->
                    if (comparisonValue.startsWith(prefix, ignoreCase = !config.case) && prefix.length > maxLength) {
                        prefixOrder = order
                        maxLength = prefix.length
                    }
                }
            }
            if (config.numeric) {
                comparisonValue = "(\\d+)".toRegex().replace(comparisonValue) {
                    // This is pretty hacky. Ideally we would support segment-based sorting similar to
                    // the real keep-sorted.
                    it.value.padStart(100, '0')
                }
            }
            return GroupRecord.SortKey(comparisonValue = comparisonValue, prefixOrder = prefixOrder)
        }

        data class GroupRecord(
            val startComment: Int,
            val startContent: Int,
            val endContent: Int,
            val sortKey: SortKey,
        ) {
            val isOneLine = startContent + 1 == endContent

            data class SortKey(val comparisonValue: String, val prefixOrder: Int)

            companion object {
                val NULL_GROUP_RECORD = GroupRecord(0, 0, 0, SortKey("", 0))
            }
        }

        fun first(group: GroupRecord): String = allLines[group.startContent]
        fun last(group: GroupRecord): String = if (group.endContent > group.startComment) {
            allLines[group.endContent - 1]
        } else first(group)

        fun areEqual(a: GroupRecord, b: GroupRecord): Boolean {
            val la = a.endContent - a.startComment
            if (la != b.endContent - b.startComment) {
                return false
            }
            for (i in 0 until la) {
                if (allLines[a.startComment + i] != allLines[b.startComment + i]) {
                    return false
                }
                // TODO: Handle sticky suffixes on the last line (ignore the positional sticky part)
            }
            return true
        }

        inline fun forEachLine(
            group: GroupRecord,
            f: (line: String, isComment: Boolean, isLast: Boolean) -> Unit,
        ) {
            for (i in group.startComment until group.endContent) {
                f(allLines[i], i < group.startContent, i == group.endContent - 1)
            }
        }
    }
}

data class KeepSortedConfig(
    val matchRegexp: Regex,
    val blocks: Map<Char, Char> = mapOf(
        '(' to ')',
        '[' to ']',
        '{' to '}',
    ),
    val ignoreBlocks: Map<String, Regex> = mapOf(
        "\"\"\"" to ".*?[^\\\\]\"\"\"".toRegex(),
        "\'\'\'" to ".*?[^\\\\]\'\'\'".toRegex(),
        "\"" to ".*?\"".toRegex(),
        "\'" to ".*?\'".toRegex(),
        "`" to ".*?`".toRegex(),
        "\\" to "['\"\\\\]".toRegex(),
        "//" to ".*?$".toRegex(),
    ),
    val templates: Map<String, KeepSortedSectionConfig> = emptyMap(),
    val debug: Boolean = false,
) {
    companion object {
        private val patterns = mutableMapOf<String, Lazy<Regex>>().apply {
            this["kt"] = lazy {
                "(?<prefix>\\s*)(?<comment>#|//)\\s+keep-sorted (?:(?<start>start(?<config> .*)?)|(?<end>end))".toRegex()
            }
            this["test"] = lazy {
                "(?<prefix>\\s*)\\*?(?<comment>#|//|<!--|--|;|/\\*|)\\s*?(?:.+\\s+)?keep-sorted-test (?:(?<start>start(?<config> .*)?)|(?<end>end).*)".toRegex()
            }
        }.toMap()

        fun pattern(type: String): Regex {
            return patterns.getValue(type).value
        }
    }
}

data class KeepSortedSectionConfig(
    // Pre
    val block: Boolean = false,
    val group: Boolean = true,
    val groupPrefixes: List<String> = emptyList(),
    val stickyComments: Boolean = true,
    val stickyPrefixes: List<String> = emptyList(),
    val skipLines: Int = 0,
    // Sort
    val case: Boolean = true,
    val numeric: Boolean = false,
    val byRegex: List<Pattern> = emptyList(),
    val prefixOrder: List<String> = emptyList(),
    val ignorePrefixes: List<String> = emptyList(),
    // Post
    val removeDuplicates: Boolean = true,
    val newlineSeparated: Boolean = false,
    val maintainGroupSeparator: Char = ',',
    val maintainSuffixOrder: Pattern? = null,
) {
    internal lateinit var leadingWhiteSpace: String
    internal lateinit var commentPrefix: String

    companion object {
        private val yamlFlowSequence by lazy {
            "\\[(?:\\s*(['\"])[^'\"]*(['\"])\\s*,?\\s*)*]".toRegex()
        }

        internal fun parse(
            configString: String,
            templates: Map<String, KeepSortedSectionConfig>,
        ): KeepSortedSectionConfig {
            var config = KeepSortedSectionConfig()
            var currentIndex = 0

            fun parseNoWhitespaceValue(configString: String, key: String): String {
                val endIndex = configString.indexOf(' ', currentIndex).let {
                    if (it == -1) {
                        configString.length
                    } else {
                        it
                    }
                }
                val value = configString.substring(currentIndex, endIndex)
                currentIndex = endIndex
                check(value.isNotEmpty()) { "Invalid value for '$key'" }
                return value
            }

            fun parseBool(configString: String, key: String): Boolean {
                return when (parseNoWhitespaceValue(configString, key)) {
                    "yes" -> true
                    "no" -> false
                    else -> error("Invalid value for '$key'")
                }
            }

            fun parseIntValue(configString: String, key: String): Int {
                return parseNoWhitespaceValue(configString, key).let {
                    Integer.parseInt(it)
                }
            }

            fun parseStringList(configString: String, key: String): List<String> {
                val yaml = yamlFlowSequence.matchAt(configString, currentIndex)
                if (yaml != null) {
                    fun replaceSingleQuotesWithDoubleQuotes(input: String): String {
                        val pattern = "'((?:[^'\\\\]|\\.)*)'".toRegex()
                        return input.replace(pattern) { matchResult ->
                            "\"" + matchResult.groupValues[1].replace("'", "\"") + "\""
                        }
                    }
                    currentIndex += yaml.value.length
                    return Json.parseToJsonElement(replaceSingleQuotesWithDoubleQuotes(yaml.value)).jsonArray.map {
                        it.jsonPrimitive.content
                    }
                }
                return parseNoWhitespaceValue(configString, key).split(',')
            }

            while (currentIndex < configString.length) {
                if (configString[currentIndex].isWhitespace()) {
                    currentIndex++
                    continue
                }
                val keyEndIndex = configString.indexOf('=', currentIndex)
                if (keyEndIndex == -1) {
                    break
                }
                val key = configString.substring(currentIndex, keyEndIndex)
                currentIndex = keyEndIndex + 1
                config = when (key) {
                    "sticky_comments" -> config.copy(
                        stickyComments = parseBool(configString, key)
                    )
                    "sticky_prefixes" -> config.copy(
                        stickyPrefixes = parseStringList(configString, key).sortedByDescending { it.length }
                    )
                    "skip_lines" -> config.copy(
                        skipLines = parseIntValue(configString, key)
                    )
                    "block" -> config.copy(
                        block = parseBool(configString, key)
                    )
                    "case" -> config.copy(
                        case = parseBool(configString, key)
                    )
                    "numeric" -> config.copy(
                        numeric = parseBool(configString, key)
                    )
                    "group" -> config.copy(
                        group = parseBool(configString, key)
                    )
                    "group_prefixes" -> config.copy(
                        groupPrefixes = parseStringList(configString, key)
                    )
                    "prefix_order" -> config.copy(
                        prefixOrder = parseStringList(configString, key)
                    )
                    "by_regex" -> config.copy(
                        byRegex = kotlin.runCatching {
                            parseStringList(configString, key).map { Pattern.compile(it) }
                        }.onFailure { it.printStackTrace() }.getOrDefault(config.byRegex)
                    )
                    "ignore_prefixes" -> config.copy(
                        ignorePrefixes = parseStringList(configString, key).sortedByDescending { it.length }
                    )
                    "remove_duplicates" -> config.copy(
                        removeDuplicates = parseBool(configString, key)
                    )
                    "newline_separated" -> config.copy(
                        newlineSeparated = parseBool(configString, key)
                    )
                    "maintain_suffix_order" -> config.copy(
                        maintainSuffixOrder = kotlin.runCatching {
                            parseNoWhitespaceValue(configString, key)
                                .takeIf { it.isNotEmpty() }?.let {
                                    Pattern.compile("$it$")
                                }
                        }.onFailure {
                            it.printStackTrace()
                        }.getOrDefault(config.maintainSuffixOrder)
                    )
                    "template" -> {
                        val templateId = parseNoWhitespaceValue(configString, key)
                        templates.getValue(templateId)
                    }
                    else -> config // error("Unsupported key '$key'")
                }
            }
            return config
        }
    }
}