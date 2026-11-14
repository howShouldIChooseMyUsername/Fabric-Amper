package dev.fabricamper.util

import org.gradle.api.GradleException

/** A stable, actionable error at a specific Fabric Amper YAML field. */
data class FabricAmperDiagnostic(
    val code: String,
    val location: String,
    val explanation: String,
) {
    override fun toString(): String = "FabricAmper $code $location: $explanation"
}

class FabricAmperException(
    val diagnostic: FabricAmperDiagnostic,
    cause: Throwable? = null,
) : GradleException(diagnostic.toString(), cause) {
    constructor(code: String, location: String, explanation: String) :
        this(FabricAmperDiagnostic(publicDiagnosticCode(code), location, explanation))
}

internal fun failAmper(code: String, location: String, explanation: String): Nothing =
    throw FabricAmperException(code, location, explanation)

/**
 * Earlier internal checks used implementation-only FA10x/FA30x identifiers.
 * Expose only the stable FA001–FA015 contract from specs.md §8.
 */
private fun publicDiagnosticCode(code: String): String = when (code) {
    "FA103", "FA104", "FA105", "FA107", "FA108", "FA110",
    "FA116", "FA117", "FA118", "FA121", "FA122", "FA123",
    "FA127", "FA130", "FA131" -> "FA009"
    "FA109", "FA115" -> "FA006"
    "FA112" -> "FA004"
    "FA113" -> "FA005"
    "FA119" -> "FA014"
    "FA101", "FA102", "FA106", "FA111", "FA114", "FA120",
    "FA124", "FA125", "FA126", "FA128", "FA129",
    "FA301", "FA302", "FA303" -> "FA003"
    else -> if (code.matches(Regex("""FA[13]\d{2}"""))) "FA003" else code
}
