package dev.fabricamper.generator

import dev.fabricamper.util.failAmper

/**
 * Small JSON writer for Loader metadata. It avoids a runtime JSON implementation
 * dependency in the Settings plugin and guarantees a single LF at EOF.
 *
 * Root entries retain their authored order (schemaVersion first); nested objects
 * are sorted by key so two equivalent YAML mappings produce identical bytes.
 */
internal object DeterministicJson {
    fun encodeRoot(fields: Map<String, Any?>): String = buildString {
        appendObject(fields, level = 0, sortKeys = false)
        append('\n')
    }

    private fun StringBuilder.appendObject(
        fields: Map<*, *>,
        level: Int,
        sortKeys: Boolean = true,
    ) {
        val entries = fields.entries.map { entry ->
            val key = entry.key as? String
                ?: failAmper("FA301", "mod.custom", "JSON 객체 키는 문자열이어야 합니다.")
            key to entry.value
        }.let { if (sortKeys) it.sortedBy { (key, _) -> key } else it }
        append('{')
        if (entries.isNotEmpty()) {
            append('\n')
            entries.forEachIndexed { index, (key, value) ->
                indent(level + 1)
                appendString(key)
                append(": ")
                appendValue(value, level + 1)
                if (index != entries.lastIndex) append(',')
                append('\n')
            }
            indent(level)
        }
        append('}')
    }

    private fun StringBuilder.appendArray(elements: Iterable<*>, level: Int) {
        val values = elements.toList()
        append('[')
        if (values.isNotEmpty()) {
            append('\n')
            values.forEachIndexed { index, value ->
                indent(level + 1)
                appendValue(value, level + 1)
                if (index != values.lastIndex) append(',')
                append('\n')
            }
            indent(level)
        }
        append(']')
    }

    private fun StringBuilder.appendValue(value: Any?, level: Int) {
        when (value) {
            null -> append("null")
            is String -> appendString(value)
            is Boolean -> append(value)
            is Byte, is Short, is Int, is Long, is java.math.BigInteger -> append(value)
            is Float -> {
                if (!value.isFinite()) failAmper("FA302", "mod.custom", "JSON 숫자는 유한해야 합니다.")
                append(value)
            }
            is Double -> {
                if (!value.isFinite()) failAmper("FA302", "mod.custom", "JSON 숫자는 유한해야 합니다.")
                append(value)
            }
            is java.math.BigDecimal -> append(value.toPlainString())
            is Map<*, *> -> appendObject(value, level)
            is Iterable<*> -> appendArray(value, level)
            is Array<*> -> appendArray(value.asIterable(), level)
            else -> failAmper(
                "FA303", "mod.custom",
                "지원하지 않는 JSON 값 타입: ${value.javaClass.name}",
            )
        }
    }

    private fun StringBuilder.appendString(value: String) {
        append('"')
        for (character in value) {
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000c' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) {
                    append("\\u")
                    append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
        append('"')
    }

    private fun StringBuilder.indent(level: Int) {
        repeat(level * 2) { append(' ') }
    }
}
