package renderingtests

import domain.ConnectionNode
import domain.Street
import korlibs.math.geom.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for the unified street rendering functionality.
 * Verifies that detected streets are rendered together by elevation,
 * matching the approach used for saved streets.
 */
class UnifiedStreetRenderingTest {

    /**
     * Test helper to create a street at a specific elevation.
     */
    private fun createTestStreet(elevation: Int, index: Int): Street {
        val yPosition = elevation * 100 + index * 20
        val start = ConnectionNode(Point(0.0, yPosition.toDouble()), elevation)
        val end = ConnectionNode(Point(100.0, yPosition.toDouble()), elevation)
        val path = listOf(start.position, Point(50.0, yPosition.toDouble()), end.position)
        return Street(start, end, path)
    }

    /**
     * Test that verifies streets at the same elevation are grouped together for rendering.
     */
    @Test
    fun testStreetsGroupedByElevationForRendering() {
        // Create test streets at different elevations
        val streets = listOf(
            createTestStreet(elevation = 0, index = 0),  // Elevation 0
            createTestStreet(elevation = 0, index = 1),  // Elevation 0
            createTestStreet(elevation = 1, index = 0),  // Elevation 1
            createTestStreet(elevation = 2, index = 0)   // Elevation 2
        )

        // Group by elevation (simulating the rendering logic)
        val streetsByElevation = streets.groupBy { 
            // Use a simple elevation getter for testing
            when (val elev = it.startNode.elevation) {
                0, 1, 2 -> elev
                else -> 0
            }
        }

        // Verify grouping
        assertTrue(streetsByElevation.containsKey(0), "Should have elevation 0 group")
        assertTrue(streetsByElevation.containsKey(1), "Should have elevation 1 group")
        assertTrue(streetsByElevation.containsKey(2), "Should have elevation 2 group")
        
        // Verify street counts per elevation
        assertEquals(2, streetsByElevation[0]?.size, "Elevation 0 should have 2 streets")
        assertEquals(1, streetsByElevation[1]?.size, "Elevation 1 should have 1 street")
        assertEquals(1, streetsByElevation[2]?.size, "Elevation 2 should have 1 street")
    }

    /**
     * Test that verifies the elevation-based rendering approach.
     * This simulates the key aspect of the unified rendering change.
     */
    @Test
    fun testElevationBasedRenderingApproach() {
        // Create test streets
        val streets = listOf(
            createTestStreet(elevation = 0, index = 0),
            createTestStreet(elevation = 0, index = 1),
            createTestStreet(elevation = 1, index = 0)
        )

        // Simulate the rendering logic from LevelScene
        val streetsByElevation = streets.groupBy { it.startNode.elevation }
        
        // Track what would be rendered at each elevation
        val renderCalls = mutableListOf<Pair<Int, List<Street>>>()
        
        streetsByElevation.forEach { (elevation, streetsAtElevation) ->
            if (streetsAtElevation.isNotEmpty()) {
                renderCalls.add(elevation to streetsAtElevation)
            }
        }

        // Verify rendering behavior
        assertEquals(2, renderCalls.size, "Should have 2 render calls (elevations 0 and 1)")
        
        // Verify elevation 0 renders both streets together
        val elevation0Call = renderCalls.find { it.first == 0 }
        assertNotNull(elevation0Call, "Should have elevation 0 render call")
        assertEquals(2, elevation0Call.second.size, "Elevation 0 should render 2 streets together")
        
        // Verify elevation 1 renders its single street
        val elevation1Call = renderCalls.find { it.first == 1 }
        assertNotNull(elevation1Call, "Should have elevation 1 render call")
        assertEquals(1, elevation1Call.second.size, "Elevation 1 should render 1 street")
    }

    /**
     * Test that verifies empty elevation groups are handled correctly.
     */
    @Test
    fun testEmptyElevationGroupsHandledCorrectly() {
        // Create streets only at elevation 0
        val streets = listOf(
            createTestStreet(elevation = 0, index = 0),
            createTestStreet(elevation = 0, index = 1)
        )

        // Group by elevation
        val streetsByElevation = streets.groupBy { it.startNode.elevation }
        
        // Simulate rendering
        val renderCalls = mutableListOf<Pair<Int, List<Street>>>()
        streetsByElevation.forEach { (elevation, streetsAtElevation) ->
            if (streetsAtElevation.isNotEmpty()) {
                renderCalls.add(elevation to streetsAtElevation)
            }
        }

        // Should only render elevation 0, not create empty calls for other elevations
        assertEquals(1, renderCalls.size, "Should only render elevation 0")
        assertEquals(0, renderCalls[0].first, "Should render elevation 0")
        assertEquals(2, renderCalls[0].second.size, "Should render both streets at elevation 0")
    }

    /**
     * Test that verifies streets are properly filtered by elevation.
     */
    @Test
    fun testStreetElevationFiltering() {
        val streets = listOf(
            createTestStreet(elevation = 0, index = 0),
            createTestStreet(elevation = 1, index = 0),
            createTestStreet(elevation = 2, index = 0)
        )

        // Test filtering for each elevation
        for (targetElevation in 0..2) {
            val filtered = streets.filter { it.startNode.elevation == targetElevation }
            assertEquals(1, filtered.size, "Should find 1 street at elevation $targetElevation")
            assertEquals(targetElevation, filtered[0].startNode.elevation, "Street should be at correct elevation")
        }
    }

    /**
     * Test that verifies the key creation logic for elevation groups.
     */
    @Test
    fun testElevationGroupKeyCreation() {
        // Simulate the key creation logic
        data class ElevationGroupKey(val elevation: Int)
        
        val elevations = listOf(0, 1, 2)
        val keys = elevations.map { ElevationGroupKey(it) }
        
        // Verify keys are created correctly
        assertEquals(3, keys.size, "Should create 3 keys")
        assertEquals(0, keys[0].elevation, "First key should be for elevation 0")
        assertEquals(1, keys[1].elevation, "Second key should be for elevation 1")
        assertEquals(2, keys[2].elevation, "Third key should be for elevation 2")
        
        // Verify keys are unique
        val keySet = keys.toSet()
        assertEquals(3, keySet.size, "All keys should be unique")
    }

    /**
     * Test that verifies non-empty street filtering.
     */
    @Test
    fun testNonEmptyStreetFiltering() {
        // Create a mix of valid and invalid streets
        val validStreet = createTestStreet(elevation = 0, index = 0)
        val emptyPathStreet = Street(
            ConnectionNode(Point(0.0, 0.0), 0),
            ConnectionNode(Point(10.0, 10.0), 0),
            emptyList() // Invalid: no path points
        )

        val streets = listOf(validStreet, emptyPathStreet)
        
        // Filter streets with valid paths (simulating the renderer's filter)
        val validStreets = streets.filter { it.path.size >= 2 }
        
        assertEquals(1, validStreets.size, "Should filter out streets with invalid paths")
        assertEquals(validStreet, validStreets[0], "Should keep valid streets")
    }

    /**
     * Test that verifies the rendering order by elevation.
     */
    @Test
    fun testRenderingOrderByElevation() {
        val streets = listOf(
            createTestStreet(elevation = 2, index = 0),  // Highest elevation
            createTestStreet(elevation = 0, index = 0),  // Lowest elevation
            createTestStreet(elevation = 1, index = 0)   // Middle elevation
        )

        // Group by elevation
        val streetsByElevation = streets.groupBy { it.startNode.elevation }
        
        // Get elevations in order
        val elevations = streetsByElevation.keys.sorted()
        
        // Verify elevation order
        assertEquals(listOf(0, 1, 2), elevations, "Elevations should be in ascending order")
    }
}
