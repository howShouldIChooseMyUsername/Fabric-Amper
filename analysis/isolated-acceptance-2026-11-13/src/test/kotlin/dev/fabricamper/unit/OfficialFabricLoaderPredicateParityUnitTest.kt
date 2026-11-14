package dev.fabricamper.unit

import dev.fabricamper.util.LoaderVersionConstraints
import net.fabricmc.loader.api.Version
import net.fabricmc.loader.api.metadata.version.VersionPredicate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * G26 parity against the REAL public Fabric Loader 0.20.1 API, not a handwritten
 * approximation of its grammar or expected result. No game, Gradle runner,
 * downloads, or project configuration is performed by these tests.
 *
 * Loader's parse(Collection<String>) handles Loader dependency OR arrays, and
 * each individual VersionPredicate internally handles whitespace-delimited AND.
 * A disagreement must fail the regression; do not silently alter either side.
 */
class OfficialFabricLoaderPredicateParityUnitTest {
    private data class Case(val expressions: List<String>, val version: String)

    private fun officialMatches(expressions: List<String>, selectedVersion: String): Boolean {
        val version = Version.parse(selectedVersion)
        return VersionPredicate.parse(expressions).any { predicate -> predicate.test(version) }
    }

    private fun assertParity(vararg cases: Case) {
        for (case in cases) {
            val expected = officialMatches(case.expressions, case.version)
            val actual = LoaderVersionConstraints.matchesAny(case.expressions, case.version)
            assertNotNull(actual,
                "Fabric Amper returned unknown for valid Loader predicates ${case.expressions} @ ${case.version}")
            assertEquals(expected, actual,
                "Fabric Loader 0.20.1 mismatch: ${case.expressions} @ ${case.version}")
        }
    }

    @Test
    fun `official single exact equality and ordered comparisons agree`() {
        assertParity(
            Case(listOf("26.3"), "26.3"),
            Case(listOf("26.3"), "26.2"),
            Case(listOf("=26.3"), "26.3"),
            Case(listOf(">=26.3"), "26.3"),
            Case(listOf(">=26.3"), "26.4"),
            Case(listOf(">=26.3"), "26.2"),
            Case(listOf("<26.3"), "26.2"),
            Case(listOf("<26.3"), "26.3"),
            Case(listOf(">26.3"), "26.4"),
            Case(listOf("<=26.3"), "26.3"),
        )
    }

    @Test
    fun `official whitespace AND and list OR agree including boundary cases`() {
        assertParity(
            Case(listOf(">=26.2 <26.4"), "26.2"),
            Case(listOf(">=26.2 <26.4"), "26.3"),
            Case(listOf(">=26.2 <26.4"), "26.4"),
            Case(listOf(">=26.2 <26.4", "27.1"), "26.3"),
            Case(listOf(">=26.2 <26.4", "27.1"), "27.1"),
            Case(listOf(">=26.2 <26.4", "27.1"), "28.0"),
            Case(listOf(">=26.2 <26.4", ">=27.0 <27.3"), "27.2"),
            Case(listOf(">=26.2 <26.4", ">=27.0 <27.3"), "27.3"),
        )
    }

    @Test
    fun `official caret tilde and x wildcard predicates agree`() {
        assertParity(
            Case(listOf("^26.2"), "26.2"),
            Case(listOf("^26.2"), "26.3"),
            Case(listOf("^26.2"), "27.0"),
            Case(listOf("~26.2"), "26.2.9"),
            Case(listOf("~26.2"), "26.3"),
            Case(listOf("26.3.x"), "26.3.0"),
            Case(listOf("26.3.x"), "26.3.7"),
            Case(listOf("26.3.x"), "26.4.0"),
            Case(listOf("26.3.*", "~27.1"), "27.1.4"),
            Case(listOf("*"), "26.3"),
        )
    }

    @Test
    fun `official bare x and X are opaque exact versions rather than universal wildcards`() {
        for (predicate in listOf("x", "X")) {
            val official = VersionPredicate.parse(predicate)
            assertFalse(official.test(Version.parse("26.3")),
                "Fabric Loader treats bare '$predicate' as an opaque exact version, not '*'")
            assertTrue(official.test(Version.parse(predicate)),
                "Fabric Loader must match bare '$predicate' against its identical opaque version")
            for (selected in listOf("26.3", predicate, "future-release")) {
                assertEquals(official.test(Version.parse(selected)),
                    LoaderVersionConstraints.matches(predicate, selected),
                    "Fabric Loader and Amper disagree on bare '$predicate' at $selected")
            }
        }
    }

    @Test
    fun `official accepted inline operator sequence retains Loader evaluation semantics`() {
        val expression = ">=1.0 || <2.0"
        val official = VersionPredicate.parse(expression)
        for (selected in listOf("0.9", "1.5", "2.0", "26.3", "future-release")) {
            val actual = LoaderVersionConstraints.matches(expression, selected)
            assertNotNull(actual, "Fabric Loader accepts '$expression' at $selected")
            assertEquals(official.test(Version.parse(selected)), actual,
                "Fabric Loader and Amper disagree on '$expression' at $selected")
        }
    }

    @Test
    fun `official build metadata and extended SemVer agree`() {
        assertParity(
            Case(listOf("1.2.3+fabric.1"), "1.2.3+fabric.2"),
            Case(listOf(">=0.165.2"), "0.165.2+26.3"),
            Case(listOf(">0.165.2"), "0.165.2+26.3"),
            Case(listOf("<=1.2.3.4"), "1.2.3.4"),
            Case(listOf(">=1.2.3.4"), "1.2.3.5"),
            Case(listOf("1.2.3"), "1.2.3.0"),
        )
    }

    @Test
    fun `official prerelease lower bounds and release ordering agree`() {
        assertParity(
            Case(listOf("<26.3"), "26.3-rc.1"),
            Case(listOf(">=26.3"), "26.3-rc.1"),
            Case(listOf(">=26.3"), "26.3"),
            Case(listOf("~26.2-rc.2"), "26.2-rc.1"),
            Case(listOf("~26.2-rc.2"), "26.2-rc.2"),
            Case(listOf("~26.2-rc.2"), "26.2.1"),
            Case(listOf("~26.2-rc.2"), "26.3"),
            Case(listOf(">=26.3-"), "26.3-alpha"),
        )
    }

    @Test
    fun `official opaque selected version semantics agree`() {
        assertParity(
            Case(listOf("future-release"), "future-release"),
            Case(listOf("future-release"), "another-release"),
            Case(listOf("=future-release"), "future-release"),
            Case(listOf(">=future-release"), "future-release"),
            Case(listOf(">=future-release"), "another-release"),
            Case(listOf("*"), "future-release"),
            Case(listOf("future-release", "another-release"), "another-release"),
            // This is an opaque exact version, not an unsupported != operator.
            Case(listOf("!=1.2.3"), "!=1.2.3"),
            Case(listOf("!=1.2.3"), "1.2.3"),
            Case(listOf("!=1.2.3"), "!=1.2.4"),
        )
    }

    @Test
    fun `official rejects incomplete range operators with no version`() {
        for (malformed in listOf(">=", "<=")) {
            assertTrue(runCatching { VersionPredicate.parse(malformed) }.isFailure,
                "Fabric Loader 0.20.1 unexpectedly accepted incomplete range '$malformed'")
            assertNull(LoaderVersionConstraints.matches(malformed, "26.3"),
                "Fabric Amper must reject incomplete range '$malformed'")
        }
    }

    @Test
    fun `official non trailing star token is opaque exact rather than a wildcard range`() {
        val expression = "1.2.*.3"
        val official = VersionPredicate.parse(expression)
        assertTrue(official.test(Version.parse(expression)),
            "Fabric Loader accepts the exact opaque version '$expression'")
        assertFalse(official.test(Version.parse("26.3")),
            "Fabric Loader must not treat '$expression' as a universal wildcard")
        for (selected in listOf(expression, "26.3", "1.2.5.3")) {
            assertEquals(official.test(Version.parse(selected)),
                LoaderVersionConstraints.matches(expression, selected),
                "Fabric Loader and Amper disagree on opaque '$expression' at $selected")
        }
    }

    @Test
    fun `invalid OR arm may not be masked by a matching valid arm`() {
        val expressions = listOf(">=", "26.3")
        assertTrue(runCatching { VersionPredicate.parse(expressions) }.isFailure,
            "Fabric Loader 0.20.1 unexpectedly accepted malformed OR arm")
        assertNull(LoaderVersionConstraints.matchesAny(expressions, "26.3"),
            "Fabric Amper must not accept malformed OR arm merely because 26.3 matches")
    }
}
