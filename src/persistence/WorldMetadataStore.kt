package persistence

import korlibs.io.file.std.applicationVfs
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Repository abstraction for local metadata that the current server API does not store.
 */
interface WorldMetadataRepository {
    /** Loads all known world metadata keyed by server world id. */
    suspend fun loadAll(): Map<String, WorldMetadata>

    /** Inserts or replaces one world metadata entry. */
    suspend fun save(metadata: WorldMetadata)
}

/**
 * Locally persisted metadata for a server-created world.
 *
 * @property worldId server-generated world id.
 * @property name user-facing display name.
 * @property levelSelection selected starting level.
 * @property deleteToken token generated at creation time for future delete support.
 */
@Serializable
data class WorldMetadata(
    val worldId: String,
    val name: String,
    val levelSelection: WorldLevelSelection,
    val deleteToken: String
)

@Serializable
private data class WorldMetadataFile(
    /** Metadata entries persisted in the local JSON file. */
    val worlds: List<WorldMetadata> = emptyList()
)

/**
 * File-backed metadata store under `savedworld/server-world-metadata.json`.
 *
 * @property metadataPath app VFS path to the metadata JSON file.
 */
class WorldMetadataStore(
    private val metadataPath: String = DEFAULT_METADATA_PATH
) : WorldMetadataRepository {

    /** Reads the metadata JSON file and returns entries keyed by server world id. */
    override suspend fun loadAll(): Map<String, WorldMetadata> {
        val file = applicationVfs[metadataPath]
        if (!file.exists()) return emptyMap()

        val decoded = try {
            json.decodeFromString<WorldMetadataFile>(file.readString())
        } catch (_: SerializationException) {
            return emptyMap()
        } catch (_: Exception) {
            return emptyMap()
        }

        return decoded.worlds.associateBy { it.worldId }
    }

    /** Writes one metadata entry while preserving all other known server worlds. */
    override suspend fun save(metadata: WorldMetadata) {
        val updated = loadAll().toMutableMap()
        updated[metadata.worldId] = metadata

        val file = applicationVfs[metadataPath]
        file.ensureParents()
        file.writeString(
            json.encodeToString(
                WorldMetadataFile(updated.values.sortedBy { it.name.lowercase() })
            )
        )
    }

    companion object {
        /** Directory used for saved multi-table worlds. */
        const val DIRECTORY = "savedworld"

        /** Metadata file name, excluded from saved-world level listings. */
        const val METADATA_FILE_NAME = "server-world-metadata.json"

        /** Default metadata file path. */
        const val DEFAULT_METADATA_PATH = "$DIRECTORY/$METADATA_FILE_NAME"

        private val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
            classDiscriminator = "type"
        }
    }
}
