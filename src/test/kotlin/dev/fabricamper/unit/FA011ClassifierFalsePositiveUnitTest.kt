package dev.fabricamper.unit

import dev.fabricamper.plugin.FabricAmperMissingArtifactDiagnostic
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Offline guard against misleading FA011 messages for non-missing failures. */
class FA011ClassifierFalsePositiveUnitTest {
    /** The classifier is internal to the FlowAction; exercise it without Gradle execution. */
    @Suppress("UNCHECKED_CAST")
    private fun classify(failure: Throwable, coordinates: Set<String>): Set<String> {
        val classifier = object : FabricAmperMissingArtifactDiagnostic() {}
        val method = FabricAmperMissingArtifactDiagnostic::class.java.getDeclaredMethod(
            "missingCoordinates", Throwable::class.java, Set::class.java,
        )
        method.isAccessible = true
        return method.invoke(classifier, failure, coordinates) as Set<String>
    }

    @Test
    fun `generic errors mentioning declared Maven coordinates never count as missing artifacts`() {
        val coordinate = "com.example:client-utility:0.0.0"
        val declared = setOf(coordinate)
        val fakeMissing = IllegalStateException("Could not find $coordinate.")
        val errors = listOf(
            fakeMissing,
            IllegalStateException("Could not resolve $coordinate: HTTP 401 Unauthorized"),
            IllegalStateException("Could not resolve $coordinate: TLS handshake failed"),
            IllegalStateException("No matching variant of $coordinate was found"),
            IllegalStateException("No cached version of $coordinate available for offline mode"),
            IllegalStateException("Could not resolve dependencies", fakeMissing),
            IllegalStateException("Could not find $coordinate (authentication required)"),
        )
        for (error in errors) {
            assertTrue(classify(error, declared).isEmpty(),
                "FA011 must fail closed for ${error.javaClass.simpleName}: ${error.message}")
        }
        val withSuppressed = IllegalStateException("Could not resolve project dependencies")
        withSuppressed.addSuppressed(fakeMissing)
        assertTrue(classify(withSuppressed, declared).isEmpty(),
            "suppressed ordinary exceptions containing missing-module text must not trigger FA011")
    }
}
