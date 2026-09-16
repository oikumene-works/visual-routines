package io.github.ewoc2026.visualroutines

import java.io.File
import java.nio.charset.CharacterCodingException
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

internal const val CURRENT_ROUTINE_STORE_FORMAT_VERSION = 3
private const val LEGACY_ROUTINE_STORE_FORMAT_VERSION = 1

@Serializable
internal data class StoredRoutineDocument(
    val formatVersion: Int = CURRENT_ROUTINE_STORE_FORMAT_VERSION,
    val routines: List<StoredRoutine> = emptyList(),
) {
    init {
        require(formatVersion == CURRENT_ROUTINE_STORE_FORMAT_VERSION) {
            "Unsupported routine store formatVersion: $formatVersion"
        }
        require(routines.distinctBy { it.id }.size == routines.size) {
            "Routine ids must be unique."
        }
    }
}

@Serializable
internal data class StoredRoutine(
    val id: String,
    val title: String,
    val steps: List<StoredRoutineStep>,
) {
    init {
        require(id.isUuidLike()) { "Routine id must be a UUID string." }
        require(title.trim().isNotEmpty()) { "Routine title must not be blank." }
        require(steps.isNotEmpty()) { "Routine must have at least one step." }
        require(steps.distinctBy { it.id }.size == steps.size) {
            "Step ids must be unique inside a routine."
        }
    }
}

@Serializable
internal data class StoredRoutineStep(
    val id: String,
    val instruction: String,
    @SerialName("note")
    val note: String? = null,
    val image: StoredRoutineStepImage? = null,
) {
    init {
        require(id.isUuidLike()) { "Step id must be a UUID string." }
        require(instruction.trim().isNotEmpty()) { "Step instruction must not be blank." }
        require(note == null || note.trim().isNotEmpty()) { "Step note must not be blank." }
    }
}

@Serializable
internal data class StoredRoutineStepImage(
    val source: StoredImageSource,
    val assetId: String,
    val semanticRole: StoredImageSemanticRole,
    val descriptionOverride: String? = null,
) {
    init {
        require(assetId.trim().isNotEmpty()) { "Image asset id must not be blank." }
        require(descriptionOverride == null || descriptionOverride.trim().isNotEmpty()) {
            "Image description override must not be blank."
        }
    }
}

@Serializable
internal enum class StoredImageSource {
    @SerialName("bundled")
    BUNDLED,

    @SerialName("imported")
    IMPORTED,
}

@Serializable
internal enum class StoredImageSemanticRole {
    @SerialName("informative")
    INFORMATIVE,

    @SerialName("redundant")
    REDUNDANT,
}

internal class RoutineStoreException(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {
    val mayHaveCommitted: Boolean
        get() = (cause as? AtomicDocumentIoException)?.commitOutcome ==
            DocumentCommitOutcome.MAY_HAVE_COMMITTED
}

internal data class RoutineCatalog(
    val routines: List<Routine>,
    val userRoutineIds: Set<String> = emptySet(),
    val userRoutineLoadError: RoutineStoreException? = null,
)

internal data class RoutineStepContent(
    val instruction: String,
    val note: String? = null,
    val id: String? = null,
    val image: RoutineStepImage? = null,
)

internal interface RoutineRepository {
    fun loadCatalog(): RoutineCatalog

    /**
     * Creates one routine using [routineId] as the stable identity of the
     * editor draft. Retrying the same draft updates that identity in place.
     */
    fun createRoutine(routineId: String, title: String, steps: List<RoutineStepContent>): RoutineCatalog
    fun updateRoutine(routineId: String, title: String, steps: List<RoutineStepContent>): RoutineCatalog
    fun deleteRoutine(routineId: String): RoutineCatalog
    fun reorderUserRoutines(routineIds: List<String>): RoutineCatalog
}

internal object BundledRoutineRepository : RoutineRepository {
    override fun loadCatalog(): RoutineCatalog = RoutineCatalog(BundledRoutines.all)

    override fun createRoutine(
        routineId: String,
        title: String,
        steps: List<RoutineStepContent>,
    ): RoutineCatalog {
        val routine = Routine(
            id = routineId,
            name = title.trim(),
            steps = steps.mapIndexed { index, step ->
                RoutineStep(
                    id = step.id ?: generatedStepId(routineId, index),
                    instruction = step.instruction.trim(),
                    supportingInstruction = step.note?.trim(),
                    image = step.image?.normalized(),
                )
            },
        )
        return RoutineCatalog(
            routines = listOf(routine) + BundledRoutines.all,
            userRoutineIds = setOf(routine.id),
        )
    }

    override fun updateRoutine(
        routineId: String,
        title: String,
        steps: List<RoutineStepContent>,
    ): RoutineCatalog = loadCatalog()

    override fun deleteRoutine(routineId: String): RoutineCatalog = loadCatalog()

    override fun reorderUserRoutines(routineIds: List<String>): RoutineCatalog = loadCatalog()
}

internal class JsonRoutineRepository(
    private val store: JsonRoutineStore,
    private val bundledRoutines: List<Routine> = BundledRoutines.all,
) : RoutineRepository {
    override fun loadCatalog(): RoutineCatalog {
        return try {
            catalogFor(store.load())
        } catch (error: RoutineStoreException) {
            RoutineCatalog(bundledRoutines, userRoutineLoadError = error)
        }
    }

    override fun createRoutine(
        routineId: String,
        title: String,
        steps: List<RoutineStepContent>,
    ): RoutineCatalog {
        val routine = StoredRoutine(
            id = routineId,
            title = title.trim(),
            steps = steps.mapIndexed { index, step ->
                StoredRoutineStep(
                    id = step.id ?: generatedStepId(routineId, index),
                    instruction = step.instruction.trim(),
                    note = step.note?.trim(),
                    image = step.image?.normalized()?.toStoredImage(),
                )
            },
        )
        val document = updateAndReconcile(
            committedState = { current ->
                current.routines.find { it.id == routine.id } == routine
            },
        ) { current ->
            when (val existing = current.routines.find { it.id == routine.id }) {
                null -> current.copy(routines = current.routines + routine)
                routine -> current
                else -> current.copy(
                    routines = current.routines.map { stored ->
                        if (stored.id == existing.id) routine else stored
                    },
                )
            }
        }
        return catalogFor(document)
    }

    override fun updateRoutine(
        routineId: String,
        title: String,
        steps: List<RoutineStepContent>,
    ): RoutineCatalog {
        val updatedRoutine = StoredRoutine(
            id = routineId,
            title = title.trim(),
            steps = steps.mapIndexed { index, step ->
                StoredRoutineStep(
                    id = step.id ?: generatedStepId(routineId, index),
                    instruction = step.instruction.trim(),
                    note = step.note?.trim(),
                    image = step.image?.normalized()?.toStoredImage(),
                )
            },
        )
        val document = updateAndReconcile(
            committedState = { current ->
                current.routines.find { it.id == routineId } == updatedRoutine
            },
        ) { current ->
            require(current.routines.any { it.id == routineId }) { "Unknown user routine: $routineId" }
            current.copy(
                routines = current.routines.map { stored ->
                    if (stored.id == routineId) updatedRoutine else stored
                },
            )
        }
        return catalogFor(document)
    }

    override fun deleteRoutine(routineId: String): RoutineCatalog {
        val document = updateAndReconcile(
            committedState = { current -> current.routines.none { it.id == routineId } },
        ) { current ->
            current.copy(routines = current.routines.filterNot { it.id == routineId })
        }
        return catalogFor(document)
    }

    override fun reorderUserRoutines(routineIds: List<String>): RoutineCatalog {
        require(routineIds.distinct().size == routineIds.size) { "Routine order must not contain duplicates." }
        val document = updateAndReconcile(
            committedState = { current -> current.routines.map(StoredRoutine::id) == routineIds },
        ) { current ->
            require(routineIds.toSet() == current.routines.map(StoredRoutine::id).toSet()) {
                "Routine order must contain every user routine exactly once."
            }
            val routinesById = current.routines.associateBy(StoredRoutine::id)
            current.copy(routines = routineIds.map(routinesById::getValue))
        }
        return catalogFor(document)
    }

    private fun updateAndReconcile(
        committedState: (StoredRoutineDocument) -> Boolean,
        transform: (StoredRoutineDocument) -> StoredRoutineDocument,
    ): StoredRoutineDocument {
        return try {
            store.update(transform)
        } catch (error: RoutineStoreException) {
            if (!error.mayHaveCommitted) throw error
            val document = try {
                store.load()
            } catch (_: RoutineStoreException) {
                throw error
            }
            if (!committedState(document)) throw error
            document
        }
    }

    private fun catalogFor(document: StoredRoutineDocument): RoutineCatalog {
        val userRoutines = document.routines.map { it.toRoutine() }
        return RoutineCatalog(
            routines = userRoutines + bundledRoutines,
            userRoutineIds = userRoutines.mapTo(mutableSetOf(), Routine::id),
        )
    }
}

/**
 * Reads and writes the user-authored routine document as one app-private JSON
 * file.
 */
internal class JsonRoutineStore(
    private val documentIo: AtomicDocumentIo,
    private val documentDescription: String,
) {
    constructor(file: File) : this(
        documentIo = AtomicDocumentIo(file),
        documentDescription = file.path,
    )

    fun load(): StoredRoutineDocument {
        return try {
            val currentBytes = documentIo.readOrReplace { existingBytes ->
                val decoded = decodeVersioned(existingBytes)
                if (decoded.requiresMigration) {
                    encodeValidated(decoded.document)
                } else {
                    null
                }
            }
            decodeVersioned(currentBytes).document
        } catch (error: AtomicDocumentIoException) {
            throw RoutineStoreException("Could not read routine store: $documentDescription", error)
        }
    }

    /**
     * Applies one JSON-specific mutation inside the document I/O boundary's
     * serialized read-modify-write operation. A current document is not
     * rewritten when [transform] returns equal content.
     */
    fun update(transform: (StoredRoutineDocument) -> StoredRoutineDocument): StoredRoutineDocument {
        try {
            val committedBytes = documentIo.readOrReplace { existingBytes ->
                val decoded = decodeVersioned(existingBytes)
                val replacement = transform(decoded.document)
                if (!decoded.requiresMigration && replacement == decoded.document) {
                    null
                } else {
                    encodeValidated(replacement)
                }
            }
            return decodeVersioned(committedBytes).document
        } catch (error: AtomicDocumentIoException) {
            throw RoutineStoreException("Could not update routine store: $documentDescription", error)
        }
    }

    private fun decodeVersioned(bytes: ByteArray?): DecodedRoutineDocument {
        if (bytes == null) {
            return DecodedRoutineDocument(StoredRoutineDocument(), requiresMigration = false)
        }
        return try {
            val encoded = bytes.decodeToString(throwOnInvalidSequence = true)
            val formatVersion = routineStoreHeaderJson
                .decodeFromString<StoredRoutineDocumentHeader>(encoded)
                .formatVersion
            when (formatVersion) {
                LEGACY_ROUTINE_STORE_FORMAT_VERSION -> DecodedRoutineDocument(
                    document = routineStoreJson
                        .decodeFromString<StoredRoutineDocumentV1>(encoded)
                        .migrateToCurrent(),
                    requiresMigration = true,
                )
                2 -> DecodedRoutineDocument(
                    document = routineStoreJson.decodeFromString<StoredRoutineDocumentV2>(encoded).let {
                        StoredRoutineDocument(routines = it.routines)
                    },
                    requiresMigration = true,
                )
                CURRENT_ROUTINE_STORE_FORMAT_VERSION -> DecodedRoutineDocument(
                    document = routineStoreJson.decodeFromString<StoredRoutineDocument>(encoded),
                    requiresMigration = false,
                )
                else -> throw RoutineStoreException(
                    "Unsupported routine store formatVersion $formatVersion: $documentDescription",
                )
            }
        } catch (error: CharacterCodingException) {
            throw RoutineStoreException("Invalid routine store UTF-8: $documentDescription", error)
        } catch (error: SerializationException) {
            throw RoutineStoreException("Invalid routine store JSON: $documentDescription", error)
        } catch (error: IllegalArgumentException) {
            throw RoutineStoreException("Invalid routine store: $documentDescription", error)
        }
    }

    private fun encodeValidated(document: StoredRoutineDocument): ByteArray {
        val bytes = routineStoreJson
            .encodeToString(StoredRoutineDocument.serializer(), document)
            .encodeToByteArray()
        val validated = decodeVersioned(bytes)
        check(!validated.requiresMigration)
        check(validated.document == document)
        return bytes
    }

    companion object {
        const val FILE_NAME = "routines.json"

        fun appPrivateFile(filesDir: File): File = File(filesDir, FILE_NAME)
    }
}

private val routineStoreJson = Json {
    prettyPrint = true
    encodeDefaults = true
    explicitNulls = false
}

private val routineStoreHeaderJson = Json {
    ignoreUnknownKeys = true
}

private data class DecodedRoutineDocument(
    val document: StoredRoutineDocument,
    val requiresMigration: Boolean,
)

@Serializable
private data class StoredRoutineDocumentHeader(
    val formatVersion: Int,
)

@Serializable
private data class StoredRoutineDocumentV2(
    val formatVersion: Int,
    val routines: List<StoredRoutine> = emptyList(),
) {
    init {
        require(formatVersion == 2)
        require(routines.distinctBy { it.id }.size == routines.size)
        require(routines.flatMap { it.steps }.all { it.image == null || it.image.source == StoredImageSource.BUNDLED })
    }
}

@Serializable
private data class StoredRoutineDocumentV1(
    val formatVersion: Int,
    val routines: List<StoredRoutineV1> = emptyList(),
) {
    init {
        require(formatVersion == LEGACY_ROUTINE_STORE_FORMAT_VERSION) {
            "Unsupported legacy routine store formatVersion: $formatVersion"
        }
        require(routines.distinctBy { it.id }.size == routines.size) {
            "Routine ids must be unique."
        }
    }
}

@Serializable
private data class StoredRoutineV1(
    val id: String,
    val title: String,
    val steps: List<StoredRoutineStepV1>,
) {
    init {
        require(id.isUuidLike()) { "Routine id must be a UUID string." }
        require(title.trim().isNotEmpty()) { "Routine title must not be blank." }
        require(steps.isNotEmpty()) { "Routine must have at least one step." }
        require(steps.distinctBy { it.id }.size == steps.size) {
            "Step ids must be unique inside a routine."
        }
    }
}

@Serializable
private data class StoredRoutineStepV1(
    val id: String,
    val instruction: String,
    @SerialName("note")
    val note: String? = null,
) {
    init {
        require(id.isUuidLike()) { "Step id must be a UUID string." }
        require(instruction.trim().isNotEmpty()) { "Step instruction must not be blank." }
        require(note == null || note.trim().isNotEmpty()) { "Step note must not be blank." }
    }
}

private val uuidPattern = Regex(
    "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
)

private fun String.isUuidLike(): Boolean = uuidPattern.matches(this)

private fun generatedStepId(routineId: String, index: Int): String {
    return UUID.nameUUIDFromBytes("$routineId:step:$index".encodeToByteArray()).toString()
}

private fun StoredRoutineDocumentV1.migrateToCurrent(): StoredRoutineDocument {
    return StoredRoutineDocument(
        routines = routines.map { routine ->
            StoredRoutine(
                id = routine.id,
                title = routine.title,
                steps = routine.steps.map { step ->
                    StoredRoutineStep(
                        id = step.id,
                        instruction = step.instruction,
                        note = step.note,
                        image = null,
                    )
                },
            )
        },
    )
}

private fun StoredRoutine.toRoutine(): Routine {
    return Routine(
        id = id,
        name = title,
        steps = steps.map { it.toRoutineStep() },
    )
}

private fun StoredRoutineStep.toRoutineStep(): RoutineStep {
    return RoutineStep(
        id = id,
        instruction = instruction,
        supportingInstruction = note,
        image = image?.toRoutineImage(),
    )
}

private fun StoredRoutineStepImage.toRoutineImage(): RoutineStepImage {
    return RoutineStepImage(
        source = when (source) {
            StoredImageSource.BUNDLED -> RoutineImageSource.BUNDLED
            StoredImageSource.IMPORTED -> RoutineImageSource.IMPORTED
        },
        assetId = assetId,
        semanticRole = when (semanticRole) {
            StoredImageSemanticRole.INFORMATIVE -> RoutineImageSemanticRole.INFORMATIVE
            StoredImageSemanticRole.REDUNDANT -> RoutineImageSemanticRole.REDUNDANT
        },
        descriptionOverride = descriptionOverride,
    )
}

private fun RoutineStepImage.toStoredImage(): StoredRoutineStepImage {
    return StoredRoutineStepImage(
        source = when (source) {
            RoutineImageSource.BUNDLED -> StoredImageSource.BUNDLED
            RoutineImageSource.IMPORTED -> StoredImageSource.IMPORTED
        },
        assetId = assetId,
        semanticRole = when (semanticRole) {
            RoutineImageSemanticRole.INFORMATIVE -> StoredImageSemanticRole.INFORMATIVE
            RoutineImageSemanticRole.REDUNDANT -> StoredImageSemanticRole.REDUNDANT
        },
        descriptionOverride = descriptionOverride,
    )
}

private fun RoutineStepImage.normalized(): RoutineStepImage {
    return copy(
        assetId = assetId.trim(),
        descriptionOverride = descriptionOverride?.trim(),
    )
}
