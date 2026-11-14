package dev.fabricamper.config

/** Syntax-only checks. A Loader predicate need not have a selected version yet. */
internal object VersionSyntax {
    private val operators = listOf(">=", "<=", ">", "<", "=", "^", "~")
    private val whitespace = Regex("\\s+")
    private val fixedVersion = Regex("[A-Za-z0-9][A-Za-z0-9._-]*(?:\\+[A-Za-z0-9._-]+)?")

    /** Gradle dependency coordinates must select one fixed, published version. */
    fun isFixedGradleVersion(version: String): Boolean {
        if (!fixedVersion.matches(version) || version.startsWith("latest.", ignoreCase = true)) return false
        val pieces = version.split('+')
        if (pieces[0].split('.').any { it.isBlank() || it.equals("x", ignoreCase = true) } ||
            pieces.drop(1).any { part -> part.split('.').any(String::isBlank) }
        ) {
            return false
        }
        return true
    }

    /**
     * Fabric Loader uses whitespace-separated AND terms and YAML arrays for OR.
     * Check every term without evaluating it against a possibly unavailable mod
     * version. Loader may accept opaque tokens; a literal "||" in the string
     * does not turn it into a Loader OR expression.
     */
    fun isLoaderPredicate(expression: String): Boolean {
        if (expression.isBlank()) return false
        return expression.trim().split(whitespace).all(::validTerm)
    }

    private fun validTerm(term: String): Boolean {
        if (term == "*") return true
        // Loader 0.20.1 accepts expressions with this standalone token;
        // it is not an OR operator (Loader's OR is a collection of strings).
        if (term == "||") return true
        // Loader 0.20.1 parses "!=1.2.3" as an opaque *exact version*, not
        // as a not-equal operator. Do not discard this valid literal simply
        // because '!' is not one of Loader's range operators.
        if (term.startsWith("!=")) {
            return term.length > 2 && "||" !in term && "&&" !in term
        }
        val operator = operators.firstOrNull(term::startsWith).orEmpty()
        val version = term.removePrefix(operator)
        if (version.isEmpty() || version.first() in "=<>^~!|&") return false
        if ("||" in version || "&&" in version) return false
        if (version.any { it in "=<>^~!|&" }) return false

        // Bare x/X are ordinary opaque exact versions; only dotted numeric
        // prefixes such as 26.3.x have Loader wildcard-range semantics.
        if (operator.isEmpty() && (version == "x" || version == "X")) return true

        val components = version.split('.')
        val wildcardIndex = components.indexOfFirst { it == "*" || it.equals("x", ignoreCase = true) }
        if (wildcardIndex >= 0) {
            if (operator.isNotEmpty()) return false
            // Numeric prefixes with trailing wildcard components (26.3.x)
            // remain ranges when evaluated downstream. Loader 0.20.1 ALSO
            // accepts "1.2.*.3" as an opaque exact version: syntax-only
            // validation cannot require the token to have range semantics.
            return true
        }
        return '*' !in version
    }
}
