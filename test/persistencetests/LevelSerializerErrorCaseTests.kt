package persistencetests

import persistence.LevelSerializationException
import persistence.LevelSerializer
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Error case tests for LevelSerializer.
 * 
 * These tests verify error handling and validation:
 * - Invalid JSON parsing
 * - Semantic validation (unknown references, invalid values)
 * - Exception messages and handling
 */
class LevelSerializerErrorCaseTests {

    /** ------------------------------------------------------------------------
     * Error cases: one test per validation step in the load pipeline
     * -------------------------------------------------------------------------
     */

    /** 
     * Test if invalid JSON content is rejected as malformed and throws a LevelSerializationException
     * 
     * This test verifies that the structural validation (step 1) works as expected.
     */
    @Test
    fun fromJsonString_invalidJson_throwsException() {
        assertFailsWith<LevelSerializationException> {
            LevelSerializer.fromJsonString(LevelSerializerTestData.INVALID_JSON)
        }
    }

    /** 
     * Test if a street references a node ID that was never defined in the nodes array and throws a LevelSerializationException
     * 
     * This test verifies that the semantic validation (step 4) works as expected.
     */
    @Test
    fun fromJsonString_unknownStreetNodeId_throwsException() {
        assertFailsWith<LevelSerializationException> {
            LevelSerializer.fromJsonString(LevelSerializerTestData.STREET_WITH_UNKNOWN_NODE_JSON)
        }
    }

    /** 
     * Test if a street path with only one point has no defined geometry and throws a LevelSerializationException
     * 
     * This test verifies that the semantic validation (step 4) works as expected.
     */
    @Test
    fun fromJsonString_streetPathTooShort_throwsException() {
        assertFailsWith<LevelSerializationException> {
            LevelSerializer.fromJsonString(LevelSerializerTestData.STREET_PATH_TOO_SHORT_JSON)
        }
    }

    /** 
     * Test if a TargetNode's colorName does not exist in the colors lookup table and throws a LevelSerializationException
     * 
     * This test verifies that the semantic validation (step 3) works as expected.
     */
    @Test
    fun fromJsonString_targetNodeUnknownColor_throwsException() {
        assertFailsWith<LevelSerializationException> {
            LevelSerializer.fromJsonString(LevelSerializerTestData.TARGET_NODE_UNKNOWN_COLOR_JSON)
        }
    }

    /** 
     * Test if a configuration's vehicleTypeName does not exist in the vehicleTypes lookup table and throws a LevelSerializationException
     * 
     * This test verifies that the semantic validation (step 3) works as expected.
     */
    @Test
    fun fromJsonString_sourceConfigUnknownVehicleType_throwsException() {
        assertFailsWith<LevelSerializationException> {
            LevelSerializer.fromJsonString(LevelSerializerTestData.SOURCE_CONFIG_UNKNOWN_VEHICLE_TYPE_JSON)
        }
    }

    /**
     * Verifies that a street referencing an unknown start node id is rejected.
     */
    @Test
    fun fromJsonString_unknownStreetStartNodeId_throwsException() {
        val exception = assertFailsWith<LevelSerializationException> {
            LevelSerializer.fromJsonString(LevelSerializerTestData.STREET_WITH_UNKNOWN_START_NODE_JSON)
        }

        assertTrue(exception.message!!.contains("startNodeId"))
    }

    /**
     * Verifies that a source configuration referencing an unknown color is rejected.
     */
    @Test
    fun fromJsonString_sourceConfigUnknownColor_throwsException() {
        val exception = assertFailsWith<LevelSerializationException> {
            LevelSerializer.fromJsonString(LevelSerializerTestData.SOURCE_CONFIG_UNKNOWN_COLOR_JSON)
        }

        assertTrue(exception.message!!.contains("color"))
    }

    /**
     * Verifies that misspelled or unsupported weather values fail loudly during loading.
     */
    @Test
    fun fromJsonString_unsupportedWeatherCondition_throwsException() {
        val exception = assertFailsWith<LevelSerializationException> {
            LevelSerializer.fromJsonString(LevelSerializerTestData.LEVEL_WITH_INVALID_WEATHER_JSON, "broken-weather.json")
        }

        assertTrue(exception.message!!.contains("weatherCondition"))
        assertTrue(exception.message!!.contains("storm"))
    }
}