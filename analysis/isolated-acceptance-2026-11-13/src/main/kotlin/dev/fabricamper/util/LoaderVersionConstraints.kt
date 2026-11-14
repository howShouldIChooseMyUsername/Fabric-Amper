package dev.fabricamper.util

import java.math.BigInteger

/**
 * Fabric Loader's extended SemVer predicates: arbitrary numeric components,
 * zero padding, an optional empty prerelease boundary, caret, tilde,
 * and x, X, or star wildcard ranges.
 *
 * One expression is a whitespace-separated AND; a list of expressions is OR.
 * null means an unsupported/invalid expression, never a successful match.
 * Build metadata after '+' does not participate in SemVer ordering.
 *
 * See https://wiki.fabricmc.net/documentation:fabric_mod_json_spec
 */
object LoaderVersionConstraints {
    private val whitespace = Regex("\\s+")
    private val operators = listOf(">=", "<=", ">", "<", "=", "^", "~")

    fun matchesAny(expressions: List<String>, selectedVersion: String): Boolean? {
        if (expressions.isEmpty()) return false
        // Loader parses the complete OR collection before evaluating any arm.
        // An invalid sibling cannot be masked by an otherwise matching arm.
        if (expressions.any { !isValidExpression(it) }) return null
        var unknown = false
        for (expression in expressions) {
            when (matches(expression, selectedVersion)) {
                true -> return true
                null -> unknown = true
                false -> Unit
            }
        }
        return if (unknown) null else false
    }

    fun matches(expression: String, selectedVersion: String): Boolean? {
        if (!isValidExpression(expression)) return null
        val tokens = tokens(expression)
        var unknown = false
        for (token in tokens) {
            when (matchesToken(token, selectedVersion)) {
                false -> return false
                null -> unknown = true
                true -> Unit
            }
        }
        return if (unknown) null else true
    }

    private fun tokens(expression: String): List<String> =
        expression.trim().split(whitespace).filter { it.isNotBlank() }

    /** Syntax validation is separate from version comparison: null can also
     * mean an opaque version cannot be ordered against the selected version. */
    private fun isValidExpression(expression: String): Boolean {
        val terms = tokens(expression)
        return terms.isNotEmpty() && terms.all(::isValidToken)
    }

    private fun isValidToken(token: String): Boolean {
        if (token == "*") return true
        // Loader 0.20.1 accepts these as opaque exact version tokens. In
        // particular, whitespace-delimited "||" is an AND term, not OR.
        if (token == "||" || token.equals("x", ignoreCase = true)) return true
        // Loader 0.20.1 parses '!=1.2.3' as an *opaque exact version*, not a
        // not-equals operator. Its leading punctuation is part of the value.
        if (token.startsWith("!=")) return token.length > 2
        val operator = operators.firstOrNull(token::startsWith).orEmpty()
        val version = token.removePrefix(operator)
        if (version.isEmpty() || version.first() in "=<>^~!|&" ||
            "||" in version || "&&" in version
        ) return false
        if ('*' in version || version.split('.').any { it.equals("x", ignoreCase = true) }) {
            // A valid trailing wildcard is a range. Loader 0.20.1 also
            // accepts odd forms such as "1.2.*.3" as *opaque exact* versions,
            // rather than rejecting them as malformed wildcard ranges.
            val wildcard = parseWildcard(version)
            return if (wildcard != null) operator.isEmpty()
            else version.contains('.') && (operator.isEmpty() || operator == "=")
        }
        return true
    }

    private fun matchesToken(token: String, selectedVersion: String): Boolean? {
        // Only a standalone '*' is an unconditional match. Loader 0.20.1
        // accepts bare x/X and the standalone '||' term as opaque exact
        // versions: none is a wildcard or a logical OR operator.
        if (token == "*") return true
        if (token == "||" || token.equals("x", ignoreCase = true)) return token == selectedVersion
        if (token.startsWith("!=")) return token == selectedVersion
        val operator = operators.firstOrNull(token::startsWith).orEmpty()
        val version = token.removePrefix(operator)
        if (version.isBlank()) return null

        // "||" and "&&" are not Fabric's OR syntax (OR uses a YAML array).
        // Avoid accepting unknown operators as opaque version literals.
        if (version.first() in "=<>^~!|&" || "||" in version || "&&" in version) return null
        val wildcard = parseWildcard(version)
        if (wildcard != null) {
            if (operator.isNotEmpty()) return null
            val selected = ComparableVersion.parse(selectedVersion) ?: return version == selectedVersion
            val lower = ComparableVersion(wildcard, listOf(""))
            val next = wildcard.toMutableList()
            next[next.lastIndex] = next.last() + BigInteger.ONE
            val upper = ComparableVersion(next, listOf(""))
            return selected >= lower && selected < upper
        }
        // The official parser falls back to an opaque exact version for
        // non-range dotted forms like "1.2.*.3"; don't interpret the star
        // as an unconditional match. Standalone '*' is handled above.
        if (version.split('.').any { it == "*" || it.equals("x", true) } || "*" in version) {
            return if (operator.isEmpty() || operator == "=") version == selectedVersion else null
        }

        val actual = ComparableVersion.parse(selectedVersion)
        val expected = ComparableVersion.parse(version)
        if (actual == null || expected == null) {
            // Loader permits opaque versions, but does not order them by range.
            // For them <=, >=, ^, ~ and exact predicates reduce to equality.
            return when (operator) {
                "", "=", ">=", "<=", "^", "~" -> version == selectedVersion
                else -> null
            }
        }

        val compared = actual.compareTo(expected)
        return when (operator) {
            "", "=" -> compared == 0
            ">=" -> compared >= 0
            "<=" -> compared <= 0
            ">" -> compared > 0
            "<" -> compared < 0
            "^" -> {
                val ceiling = upperBound(expected, 0)
                actual >= expected && actual < ceiling
            }
            "~" -> {
                val axis = if (expected.parts.size > 1) 1 else 0
                val ceiling = upperBound(expected, axis)
                actual >= expected && actual < ceiling
            }
            else -> null
        }
    }

    /**
     * Fabric x-ranges are lower-bounded by the earliest prerelease in the
     * numeric prefix and upper-bounded by the next prefix's earliest prerelease.
     * Repeated trailing wildcard components have no additional effect.
     */
    private fun parseWildcard(version: String): List<BigInteger>? {
        val components = version.split('.')
        val wildcardIndex = components.indexOfFirst { it == "*" || it.equals("x", ignoreCase = true) }
        if (wildcardIndex < 1) return null
        if (!components.drop(wildcardIndex).all { it == "*" || it.equals("x", ignoreCase = true) }) return null
        if (!components.take(wildcardIndex).all { it.isNotEmpty() && it.all(Char::isDigit) }) return null
        return components.take(wildcardIndex).map(::BigInteger)
    }

    private fun upperBound(base: ComparableVersion, index: Int): ComparableVersion {
        val numbers = MutableList(base.parts.size.coerceAtLeast(index + 1)) { BigInteger.ZERO }
        for (i in 0 until index) numbers[i] = base.parts[i]
        numbers[index] = base.parts[index] + BigInteger.ONE
        // The "-" suffix is lower than any prerelease of that next series.
        return ComparableVersion(numbers, listOf(""))
    }

    private data class ComparableVersion(
        val parts: List<BigInteger>,
        val prerelease: List<String>?,
    ) : Comparable<ComparableVersion> {
        override fun compareTo(other: ComparableVersion): Int {
            for (index in 0 until maxOf(parts.size, other.parts.size)) {
                val order = parts.getOrElse(index) { BigInteger.ZERO }
                    .compareTo(other.parts.getOrElse(index) { BigInteger.ZERO })
                if (order != 0) return order
            }
            if (prerelease == null && other.prerelease == null) return 0
            if (prerelease == null) return 1
            if (other.prerelease == null) return -1
            for (index in 0 until minOf(prerelease.size, other.prerelease.size)) {
                val lhs = prerelease[index]
                val rhs = other.prerelease[index]
                val numericLhs = lhs.isNotEmpty() && lhs.all(Char::isDigit)
                val numericRhs = rhs.isNotEmpty() && rhs.all(Char::isDigit)
                val order = when {
                    lhs.isEmpty() && rhs.isNotEmpty() -> -1
                    lhs.isNotEmpty() && rhs.isEmpty() -> 1
                    numericLhs && numericRhs -> BigInteger(lhs).compareTo(BigInteger(rhs))
                    numericLhs -> -1
                    numericRhs -> 1
                    else -> lhs.compareTo(rhs)
                }
                if (order != 0) return order
            }
            return prerelease.size.compareTo(other.prerelease.size)
        }

        companion object {
            private val pattern = Regex(
                "^([0-9]+(?:\\.[0-9]+)*)(?:-([0-9A-Za-z.-]*))?(?:\\+([0-9A-Za-z.-]+))?$",
            )

            fun parse(version: String): ComparableVersion? {
                val match = pattern.matchEntire(version) ?: return null
                val rawParts = match.groupValues[1].split('.')
                val parts = rawParts.map(::BigInteger)
                // In Loader's extended SemVer, "1.2-" is a valid lower-bound
                // prerelease marker, strictly earlier than "1.2-alpha".
                val prerelease = if (version.substringBefore('+').contains('-')) {
                    match.groupValues[2].split('.')
                } else null
                if (prerelease != null && prerelease.size > 1 && prerelease.any(String::isEmpty)) return null
                return ComparableVersion(parts, prerelease)
            }
        }
    }
}
