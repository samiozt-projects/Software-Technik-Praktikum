package worldeditortests

import domain.SourceNode
import domain.TargetNode
import persistence.LevelSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the editor's orientation control for sources and sinks: whole degrees from 0 to 359
 * (clockwise, 0 = up), validated with a user-facing error and persisted through the level JSON.
 */
class WorldEditorSessionOrientationTest {

    /** Verifies that a valid degree value is applied to the selected source. */
    @Test
    fun setSelectedOrientation_appliesValidDegreesToSource() {
        val session = sessionWithSourceNode()

        assertTrue(session.setSelectedOrientation(135))

        assertEquals(135, session.selectedOrientationDegrees())
        assertNull(session.errorMessage)
        val source = session.level.nodes.filterIsInstance<SourceNode>().single()
        assertEquals(135.0, source.orientation.degrees, absoluteTolerance = 0.0001)
    }

    /**
     * Verifies that sink display degrees mean the visible arrow direction: the sink arrow
     * sprite is authored 180 degrees rotated compared to the source arrow, so an entered 180
     * (arrow down) must land as 0 degrees on the domain node — otherwise the arrow of a sink
     * set to 180 would point up.
     */
    @Test
    fun setSelectedOrientation_appliesValidDegreesToSink() {
        val session = sessionWithSinkNode()

        assertTrue(session.setSelectedOrientation(180))

        assertEquals(180, session.selectedOrientationDegrees())
        val sink = session.level.nodes.filterIsInstance<TargetNode>().single()
        assertEquals(0.0, sink.orientation.degrees, absoluteTolerance = 0.0001)
    }

    /** Verifies that an existing sink's domain orientation is shown as its arrow direction. */
    @Test
    fun selectedOrientationDegrees_showsSinkArrowDirection() {
        val session = sessionWithSinkNode()
        val sink = session.level.nodes.filterIsInstance<TargetNode>().single()
        sink.orientation = korlibs.math.geom.Angle.fromDegrees(270.0)

        assertEquals(90, session.selectedOrientationDegrees())
    }

    /** Verifies that out-of-range and non-numeric input is rejected with a visible error. */
    @Test
    fun setSelectedOrientation_rejectsInvalidInput() {
        val session = sessionWithSourceNode()
        val originalDegrees = session.selectedOrientationDegrees()

        assertFalse(session.setSelectedOrientation(360))
        assertNotNull(session.errorMessage)
        assertFalse(session.setSelectedOrientation(-1))
        assertFalse(session.setSelectedOrientation(null))

        assertEquals(originalDegrees, session.selectedOrientationDegrees())
    }

    /** Verifies that without a selection the change is rejected instead of crashing. */
    @Test
    fun setSelectedOrientation_requiresASelection() {
        val session = emptySession()

        assertFalse(session.setSelectedOrientation(90))

        assertNotNull(session.errorMessage)
        assertNull(session.selectedOrientationDegrees())
    }

    /** Verifies that an edited orientation survives the level JSON roundtrip. */
    @Test
    fun editedOrientation_survivesLevelJsonRoundtrip() {
        val session = sessionWithSourceNode()
        assertTrue(session.setSelectedOrientation(135))

        val json = LevelSerializer.toJsonString(session.level)
        val loaded = LevelSerializer.fromJsonString(json, "orientation-roundtrip-test")
        val source = loaded.nodes.filterIsInstance<SourceNode>().single()

        assertEquals(135.0, source.orientation.degrees, absoluteTolerance = 0.0001)
    }
}
