package persistencetests

import korlibs.io.async.suspendTest
import korlibs.io.file.std.applicationVfs
import persistence.WorldLevelSelection
import persistence.WorldMetadata
import persistence.WorldMetadataStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies local metadata persistence for server-created worlds.
 */
class WorldMetadataStoreTest {

    /** Verifies that one server-world metadata entry can be saved and loaded unchanged. */
    @Test
    fun saveThenLoad_preservesServerWorldMetadata() = suspendTest {
        val path = "build/tmp/world-metadata-test/server-world-metadata.json"
        val store = WorldMetadataStore(path)
        val metadata = WorldMetadata(
            worldId = "world-created",
            name = "Stadtkreuzung 2",
            levelSelection = WorldLevelSelection.PredefinedLevel(2),
            deleteToken = "delete-token"
        )

        try {
            store.save(metadata)

            val loaded = store.loadAll()

            assertEquals(metadata, loaded["world-created"])
            assertTrue(applicationVfs[path].exists(), "metadata store should create its JSON file")
        } finally {
            applicationVfs[path].delete()
        }
    }
}
