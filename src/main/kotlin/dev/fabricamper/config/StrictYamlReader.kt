package dev.fabricamper.config

import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import org.snakeyaml.engine.v2.api.lowlevel.Parse
import org.snakeyaml.engine.v2.events.AliasEvent
import org.snakeyaml.engine.v2.events.CollectionStartEvent
import org.snakeyaml.engine.v2.events.DocumentStartEvent
import org.snakeyaml.engine.v2.events.Event
import org.snakeyaml.engine.v2.events.NodeEvent
import org.snakeyaml.engine.v2.events.ScalarEvent
import org.snakeyaml.engine.v2.exceptions.MarkedYamlEngineException
import org.snakeyaml.engine.v2.exceptions.YamlEngineException
import org.snakeyaml.engine.v2.schema.JsonSchema
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.ArrayDeque

/** First parse the whole event stream, then independently load the document. */
object StrictYamlReader {
    const val MAX_FILE_BYTES = 1_048_576
    private const val MAX_DEPTH = 32
    private const val MAX_ITEMS = 10_000
    /** One-based coordinates taken directly from SnakeYAML's node start marks. */
    internal data class FieldMark(val line: Int, val column: Int)
    internal data class ParsedDocument(
        val tree: Map<String, Any?>,
        val fieldMarks: Map<String, FieldMark>,
    )

    private data class Frame(
        val mapping: Boolean,
        val path: String,
        var expectingKey: Boolean = true,
        var pendingKey: String? = null,
        var sequenceIndex: Int = 0,
    )

    @JvmStatic
    fun readBytes(bytes: ByteArray, source: Path): Map<String, Any?> =
        readDocumentBytes(bytes, source).tree

    /** Keeps per-field locations with the parsed document without shared mutable state. */
    internal fun readDocumentBytes(bytes: ByteArray, source: Path): ParsedDocument {
        if (bytes.size > MAX_FILE_BYTES) {
            fail(source, "mod.yaml", "YAML must be at most 1 MiB")
        }
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = try {
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (e: java.nio.charset.CharacterCodingException) {
            throw FabricAmperConfigException("FA002", "mod.yaml", "invalid UTF-8 input", source, cause = e)
        }
        if (text.startsWith('\uFEFF')) {
            fail(source, "mod.yaml", "UTF-8 BOM is not supported")
        }
        return readDocument(text, source)
    }

    @JvmStatic
    fun read(text: String, source: Path): Map<String, Any?> = readDocument(text, source).tree

    private fun readDocument(text: String, source: Path): ParsedDocument {
        val settings = LoadSettings.builder()
            .setLabel(source.toString())
            .setUseMarks(true)
            .setAllowDuplicateKeys(false)
            .setAllowRecursiveKeys(false)
            .setMaxAliasesForCollections(0)
            .setCodePointLimit(1_000_000)
            .setSchema(JsonSchema())
            .build()
        try {
            val marks = validateEvents(text, settings, source)
            val loaded = Load(settings).loadFromString(text)
            val root = loaded as? Map<*, *> ?: fail(source, "mod.yaml", "document root must be a non-empty map")
            if (root.isEmpty()) fail(source, "mod.yaml", "document root cannot be empty")
            return ParsedDocument(checkedMap(root, source, "mod.yaml"), marks)
        } catch (e: FabricAmperConfigException) {
            throw e
        } catch (e: MarkedYamlEngineException) {
            val mark = e.problemMark.orElse(null)
            throw FabricAmperConfigException(
                "FA002", "mod.yaml", e.problem ?: e.message ?: "malformed YAML",
                source, mark?.line?.plus(1), mark?.column?.plus(1), e,
            )
        } catch (e: YamlEngineException) {
            throw FabricAmperConfigException("FA002", "mod.yaml", e.message ?: "malformed YAML", source, cause = e)
        }
    }

    private fun validateEvents(text: String, settings: LoadSettings, source: Path): Map<String, FieldMark> {
        var documents = 0
        var rootSeen = false
        var elements = 0
        val parents = ArrayDeque<Frame>()
        val fieldMarks = LinkedHashMap<String, FieldMark>()

        fun fieldPath(parent: String, child: String): String =
            if (parent.isEmpty()) child else "$parent.$child"

        fun remember(path: String, event: Event, overwrite: Boolean = true) {
            val mark = event.startMark.orElse(null) ?: return
            if (overwrite || path !in fieldMarks) {
                fieldMarks[path] = FieldMark(mark.line + 1, mark.column + 1)
            }
        }

        // A mapping entry counts once (at its key); a sequence element counts
        // once (at the start of its scalar/collection node). Nested collections
        // count toward the same global 10,000-element bound.
        fun registerNode(event: Event): String {
            if (!rootSeen) {
                rootSeen = true
                if (event.eventId.name != "MappingStart") {
                    fail(source, "mod.yaml", "document root must be a mapping", event)
                }
                return ""
            }
            val parent = parents.peekLast() ?: return ""
            val path = if (parent.mapping) {
                if (parent.expectingKey) {
                    if (event !is ScalarEvent) {
                        fail(source, "mod.yaml", "mapping keys must be scalars", event)
                    }
                    if (event.value == "<<") {
                        fail(source, "mod.yaml.<<", "YAML merge keys are forbidden", event)
                    }
                    elements++
                    parent.expectingKey = false
                    parent.pendingKey = event.value
                    val keyPath = fieldPath(parent.path, event.value)
                    remember(keyPath, event, overwrite = false)
                    if (elements > MAX_ITEMS) {
                        fail(source, "mod.yaml", "YAML exceeds $MAX_ITEMS collection elements", event)
                    }
                    return keyPath
                }
                parent.expectingKey = true
                val valuePath = fieldPath(parent.path, parent.pendingKey!!)
                parent.pendingKey = null
                valuePath
            } else {
                elements++
                "${parent.path}[${parent.sequenceIndex++}]"
            }
            if (elements > MAX_ITEMS) {
                fail(source, "mod.yaml", "YAML exceeds $MAX_ITEMS collection elements", event)
            }
            remember(path, event)
            return path
        }

        for (event in Parse(settings).parseString(text)) {
            if (event is DocumentStartEvent) {
                documents++
                if (documents > 1) fail(source, "mod.yaml", "multiple YAML documents are forbidden", event)
            }
            if (event is AliasEvent) fail(source, "mod.yaml", "YAML aliases are forbidden", event)
            if (event is NodeEvent && event.anchor.isPresent) {
                fail(source, "mod.yaml", "YAML anchors are forbidden", event)
            }
            if (event is ScalarEvent) {
                if (event.tag.isPresent) fail(source, "mod.yaml", "explicit YAML tags are forbidden", event)
                registerNode(event)
            }
            if (event is CollectionStartEvent) {
                if (event.tag.isPresent) fail(source, "mod.yaml", "explicit YAML tags are forbidden", event)
                val path = registerNode(event)
                parents.addLast(Frame(event.eventId.name == "MappingStart", path))
                if (parents.size > MAX_DEPTH) {
                    fail(source, "mod.yaml", "YAML nesting exceeds $MAX_DEPTH", event)
                }
            }
            if (event.eventId.name == "MappingEnd" || event.eventId.name == "SequenceEnd") {
                parents.removeLast()
            }
        }
        if (documents != 1) fail(source, "mod.yaml", "exactly one non-empty YAML document is required")
        return fieldMarks
    }

    private fun checkedMap(value: Map<*, *>, source: Path, path: String): Map<String, Any?> {
        return value.entries.associateTo(LinkedHashMap()) { (key, item) ->
            val name = key as? String ?: fail(source, path, "mapping keys must be strings")
            // Reject a merge *mapping key*, not a scalar value containing "<<".
            // SnakeYAML Engine's ScalarStyle.toString() is not a reliable PLAIN check.
            if (name == "<<") fail(source, "$path.$name", "YAML merge keys are forbidden")
            name to normalize(item, source, "$path.$name")
        }
    }

    private fun normalize(value: Any?, source: Path, path: String): Any? = when (value) {
        null, is String, is Boolean, is Int, is Long, is Float, is Double -> value
        is Map<*, *> -> checkedMap(value, source, path)
        is List<*> -> value.mapIndexed { index, item -> normalize(item, source, "$path[$index]") }
        else -> fail(source, path, "unsupported YAML scalar or collection type ${value::class.java.simpleName}")
    }

    private fun fail(source: Path, path: String, reason: String): Nothing =
        throw FabricAmperConfigException("FA002", path, reason, source)

    private fun fail(source: Path, path: String, reason: String, event: Event): Nothing {
        val mark = event.startMark.orElse(null)
        throw FabricAmperConfigException(
            "FA002", path, reason, source,
            mark?.line?.plus(1), mark?.column?.plus(1),
        )
    }
}
