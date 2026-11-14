package dev.fabricamper.unit

import dev.fabricamper.plugin.FabricAmperMissingArtifactDiagnostic
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy

/** Offline coverage of FA011's actual cause/suppressed traversal and exact GAV matching. */
class FA011ExceptionGraphUnitTest {
    @Suppress("UNCHECKED_CAST")
    private fun classify(failure: Throwable, declared: Set<String>): Set<String> {
        val diagnostic = object : FabricAmperMissingArtifactDiagnostic() {}
        val method = FabricAmperMissingArtifactDiagnostic::class.java.getDeclaredMethod(
            "missingCoordinates", Throwable::class.java, Set::class.java,
        )
        method.isAccessible = true
        return method.invoke(diagnostic, failure, declared) as Set<String>
    }

    @Test
    fun `nested TLS HTTP 401 and offline failures cannot yield FA011 even in a cyclic graph`() {
        val gav = "dev.example:dependency:1.0.0"
        val textOnlyMissing = IllegalStateException("Could not find $gav.")
        val root = IllegalStateException("Could not resolve dependency", textOnlyMissing)
        val tls = IllegalStateException("PKIX path building failed for $gav")
        val unauthorized = IllegalStateException("HTTP 401 Unauthorized for $gav", tls)
        val offline = IllegalStateException("No cached version of $gav available for offline mode")
        root.addSuppressed(unauthorized)
        root.addSuppressed(offline)
        unauthorized.addSuppressed(root) // A cycle must terminate without revisiting exceptions.

        assertTrue(classify(root, setOf(gav)).isEmpty())
        assertTrue(classify(unauthorized, setOf(gav)).isEmpty())
        assertTrue(classify(offline, setOf(gav)).isEmpty())
    }

    @Test
    fun `real Gradle not found cause nested in suppressed branches matches only declared exact GAV`() {
        val missingGav = "dev.example:fa011-module:1.0"
        val lookalikeGav = "dev.example:fa011-module:1.0-suffix"
        // Build Gradle's own exception through its resolution-result implementation.
        // Do not substitute a Throwable whose class name merely resembles Gradle's.
        val actualMissing = createGradleModuleNotFound(missingGav)
        assumeTrue(actualMissing != null, "Gradle's actual missing-module exception is unavailable in the unit runtime")
        actualMissing ?: return
        assertEquals("org.gradle.internal.resolve.ModuleVersionNotFoundException", actualMissing.javaClass.name)
        assertTrue(actualMissing.message.orEmpty().startsWith("Could not find $missingGav."))

        val outer = IllegalStateException("Resolution failed", IllegalStateException("TLS handshake failed"))
        val suppressedWrapper = IllegalStateException("Dependency failure", actualMissing)
        outer.addSuppressed(IllegalStateException("Could not find $lookalikeGav."))
        outer.addSuppressed(suppressedWrapper)
        suppressedWrapper.addSuppressed(outer) // Exercise identity-based cycle detection.

        assertEquals(setOf(missingGav), classify(outer, setOf(missingGav, lookalikeGav)))
        assertTrue(classify(outer, setOf(lookalikeGav)).isEmpty())
        assertTrue(classify(outer, emptySet()).isEmpty())
    }

    /**
     * Use a genuine Gradle missing-module failure, if its internal result class
     * is reachable. Reflection keeps these unstable internals out of compilation.
     */
    private fun createGradleModuleNotFound(gav: String): Throwable? {
        val pieces = gav.split(':')
        require(pieces.size == 3)
        return try {
            val identifierType = Class.forName("org.gradle.api.artifacts.component.ModuleComponentIdentifier")
            val resultType = Class.forName("org.gradle.internal.resolve.result.DefaultBuildableComponentResolveResult")
            val identifier = Proxy.newProxyInstance(
                identifierType.classLoader,
                arrayOf(identifierType),
            ) { self, method, args ->
                when (method.name) {
                    "getGroup" -> pieces[0]
                    "getModule" -> pieces[1]
                    "getVersion" -> pieces[2]
                    "getDisplayName", "toString" -> gav
                    "hashCode" -> gav.hashCode()
                    "equals" -> self === args?.firstOrNull()
                    else -> null
                }
            }
            val result = resultType.getConstructor().newInstance()
            resultType.getMethod("notFound", identifierType).invoke(result, identifier)
            resultType.getMethod("getFailure").invoke(result) as? Throwable
        } catch (_: ReflectiveOperationException) {
            null
        } catch (_: LinkageError) {
            null
        }
    }
}
