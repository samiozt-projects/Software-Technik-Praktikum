package persistencetests

/**
 * Test data for LevelSerializer tests.
 * 
 * Contains all the JSON constants used in LevelSerializerTest.kt
 */
object LevelSerializerTestData {
    /**
     * Inline copy of test/ExampleLevel.json — changes to that file must be mirrored here
     */
    val EXAMPLE_LEVEL_JSON = """
        {
          "name": "Beispiel-Level", "number": 1, "adhesionCoefficientModifier": 1.0,
          "weatherCondition": "dry",
          "vehicleTypes": [
            {
              "name": "Car", "maxSpeed": 50.0, "length": 4.0,
              "adhesionCoefficient": 0.8, "acceleration": 3.0
            },
            {
              "name": "Truck", "maxSpeed": 80.0, "length": 8.0,
              "adhesionCoefficient": 0.6, "acceleration": 1.5
            }
          ],
          "colors": [
            { "name": "Rot",  "argb": 4294901760 },
            { "name": "Blau", "argb": 4278190335 }
          ],
          "nodes": [
            {
              "id": "node-1",
              "type": "SourceNode",
              "x": 100.0,
              "y": 200.0,
              "elevation": 0,
              "orientationDegrees": 90.0,
              "configurations": [
                {
                  "colorName": "Rot",
                  "vehicleTypeName": "Car",
                  "generationSpeed": 5.0,
                  "initialVehicleSpeed": 10.0,
                  "vehiclesToSpawn": 7
                }
              ]
            },
            {
              "id": "node-2",
              "type": "TargetNode",
              "x": 500.0,
              "y": 200.0,
              "elevation": 0,
              "orientationDegrees": 270.0,
              "colorName": "Rot"
            },
            {
              "id": "node-3",
              "type": "ConnectionNode",
              "x": 300.0,
              "y": 200.0,
              "elevation": 0
            }
          ],
          "streets": [
            {
              "startNodeId": "node-1",
              "endNodeId": "node-3",
              "path": [
                { "x": 100.0, "y": 200.0 },
                { "x": 200.0, "y": 200.0 },
                { "x": 300.0, "y": 200.0 }
              ]
            },
            {
              "startNodeId": "node-3",
              "endNodeId": "node-2",
              "path": [
                { "x": 300.0, "y": 200.0 },
                { "x": 400.0, "y": 200.0 },
                { "x": 500.0, "y": 200.0 }
              ]
            }
          ]
        }
    """.trimIndent()

    /**
     * JSON with simulation state fields for testing that they are excluded from saved JSON
     */
    val LEVEL_WITH_SIMULATION_STATE_JSON = """
        {
          "name": "Test Level", "number": 1,
          "vehicleTypes": [
            { "name": "Car", "maxSpeed": 50.0, "length": 4.0, "adhesionCoefficient": 0.8, "acceleration": 3.0 }
          ],
          "colors": [
            { "name": "Rot", "argb": 4294901760 }
          ],
          "nodes": [
            {
              "id": "source",
              "type": "SourceNode",
              "x": 0.0, "y": 0.0,
              "elevation": 0,
              "orientationDegrees": 0.0,
              "vehiclesGenerated": 5,
              "configurations": [
                {
                  "colorName": "Rot",
                  "vehicleTypeName": "Car",
                  "generationSpeed": 5.0,
                  "initialVehicleSpeed": 10.0,
                  "vehiclesToSpawn": 7,
                  "vehiclesSpawned": 3,
                  "vehiclesArrived": 2
                }
              ]
            },
            {
              "id": "target",
              "type": "TargetNode",
              "x": 200.0, "y": 0.0,
              "elevation": 0,
              "orientationDegrees": 0.0,
              "colorName": "Rot",
              "vehiclesArrived": 4
            }
          ],
          "streets": [
            {
              "startNodeId": "source",
              "endNodeId": "target",
              "path": [{"x": 0.0, "y": 0.0}, {"x": 200.0, "y": 0.0}]
            }
          ],
          "simulationSpeed": 2.0
        }
    """.trimIndent()

    /**
     * JSON with legacy simulation state fields that should be ignored
     */
    val LEGACY_SIM_STATE_JSON = """
        {
          "name": "In-flight progress", "number": 5,
          "vehicleTypes": [
            { "name": "Car", "maxSpeed": 50.0, "length": 4.0,
              "adhesionCoefficient": 0.8, "acceleration": 3.0 }
          ],
          "colors": [
            { "name": "Rot", "argb": 4294901760 }
          ],
          "nodes": [
            {
              "id": "source",
              "type": "SourceNode",
              "x": 0.0, "y": 0.0,
              "elevation": 0,
              "orientationDegrees": 0.0,
              "vehiclesGenerated": 3,
              "configurations": [
                {
                  "colorName": "Rot",
                  "vehicleTypeName": "Car",
                  "generationSpeed": 1.0,
                  "initialVehicleSpeed": 10.0,
                  "vehiclesToSpawn": 7,
                  "vehiclesSpawned": 3,
                  "vehiclesArrived": 2
                }
              ]
            },
            {
              "id": "target",
              "type": "TargetNode",
              "x": 100.0, "y": 0.0,
              "elevation": 0,
              "orientationDegrees": 180.0,
              "colorName": "Rot",
              "vehiclesArrived": 2
            }
          ],
          "streets": [
            {
              "startNodeId": "source",
              "endNodeId": "target",
              "path": [{"x": 0.0, "y": 0.0}, {"x": 100.0, "y": 0.0}]
            }
          ]
        }
    """.trimIndent()

    /**
     * JSON without weather condition (should default to DRY)
     */
    val LEVEL_WITHOUT_WEATHER_JSON = """
        {
          "name": "Legacy level", "number": 1, "vehicleTypes": [],
          "colors": [], "nodes": [], "streets": []
        }
    """.trimIndent()

    /**
     * JSON with invalid weather condition (should throw exception)
     */
    val LEVEL_WITH_INVALID_WEATHER_JSON = """
        {
          "name": "Broken weather", "number": 1, "weatherCondition": "storm",
          "vehicleTypes": [], "colors": [], "nodes": [], "streets": []
        }
    """.trimIndent()

    /**
     * JSON for testing red color loading
     */
    val RED_COLOR_JSON = """
        {
          "name": "ColorTest", "number": 1, "vehicleTypes": [],
          "colors": [{ "name": "Rot", "argb": 4294901760 }],
          "nodes": [
            { "id": "t1", "type": "TargetNode", "x": 0.0, "y": 0.0,
              "elevation": 0, "orientationDegrees": 0.0, "colorName": "Rot", "vehiclesArrived": 0 }
          ],
          "streets": []
        }
    """.trimIndent()

    /**
     * JSON for testing blue color loading
     */
    val BLUE_COLOR_JSON = """
        {
          "name": "ColorTest", "number": 1, "vehicleTypes": [],
          "colors": [{ "name": "Blau", "argb": 4278190335 }],
          "nodes": [
            { "id": "t1", "type": "TargetNode", "x": 0.0, "y": 0.0,
              "elevation": 0, "orientationDegrees": 0.0, "colorName": "Blau", "vehiclesArrived": 0 }
          ],
          "streets": []
        }
    """.trimIndent()

    /**
     * Invalid JSON string for testing error handling
     */
    val INVALID_JSON = "das ist kein gueltiges { JSON {{"

    /**
     * JSON with street referencing unknown node
     */
    val STREET_WITH_UNKNOWN_NODE_JSON = """
        {
          "name": "Test", "number": 1,
          "vehicleTypes": [], "colors": [],
          "nodes": [
            { "id": "node-1", "type": "ConnectionNode", "x": 0.0, "y": 0.0, "elevation": 0 }
          ],
          "streets": [
            { "startNodeId": "node-1", "endNodeId": "node-999",
              "path": [{"x": 0.0, "y": 0.0}, {"x": 1.0, "y": 0.0}] }
          ]
        }
    """.trimIndent()

    /**
     * JSON with street path that's too short
     */
    val STREET_PATH_TOO_SHORT_JSON = """
        {
          "name": "Test", "number": 1,
          "vehicleTypes": [], "colors": [],
          "nodes": [
            { "id": "node-1", "type": "ConnectionNode", "x": 0.0, "y": 0.0, "elevation": 0 },
            { "id": "node-2", "type": "ConnectionNode", "x": 1.0, "y": 0.0, "elevation": 0 }
          ],
          "streets": [
            { "startNodeId": "node-1", "endNodeId": "node-2",
              "path": [{"x": 0.0, "y": 0.0}] }
          ]
        }
    """.trimIndent()

    /**
     * JSON with target node having unknown color
     */
    val TARGET_NODE_UNKNOWN_COLOR_JSON = """
        {
          "name": "Test", "number": 1,
          "vehicleTypes": [], "colors": [],
          "nodes": [
            { "id": "node-1", "type": "TargetNode", "x": 0.0, "y": 0.0,
              "elevation": 0, "orientationDegrees": 0.0, "colorName": "NichtVorhanden" }
          ],
          "streets": []
        }
    """.trimIndent()

    /**
     * JSON with source configuration having unknown vehicle type
     */
    val SOURCE_CONFIG_UNKNOWN_VEHICLE_TYPE_JSON = """
        {
          "name": "Test", "number": 1,
          "vehicleTypes": [],
          "colors": [{ "name": "Rot", "argb": 4294901760 }],
          "nodes": [
            { "id": "node-1", "type": "SourceNode", "x": 0.0, "y": 0.0,
              "elevation": 0, "orientationDegrees": 0.0,
              "configurations": [
                { "colorName": "Rot", "vehicleTypeName": "NichtVorhanden",
                  "generationSpeed": 1.0, "initialVehicleSpeed": 1.0 }
              ]
            }
          ],
          "streets": []
        }
    """.trimIndent()

    /**
     * JSON with street having unknown start node
     */
    val STREET_WITH_UNKNOWN_START_NODE_JSON = """
        {
          "name": "Test", "number": 1,
          "vehicleTypes": [], "colors": [],
          "nodes": [
            { "id": "node-1", "type": "ConnectionNode", "x": 0.0, "y": 0.0, "elevation": 0 }
          ],
          "streets": [
            { "startNodeId": "node-999", "endNodeId": "node-1",
              "path": [{"x": 0.0, "y": 0.0}, {"x": 1.0, "y": 0.0}] }
          ]
        }
    """.trimIndent()

    /**
     * JSON with source configuration having unknown color
     */
    val SOURCE_CONFIG_UNKNOWN_COLOR_JSON = """
        {
          "name": "Test", "number": 1,
          "vehicleTypes": [
            { "name": "Car", "maxSpeed": 50.0, "length": 4.0,
              "adhesionCoefficient": 0.8, "acceleration": 3.0 }
          ],
          "colors": [],
          "nodes": [
            { "id": "node-1", "type": "SourceNode", "x": 0.0, "y": 0.0,
              "elevation": 0, "orientationDegrees": 0.0,
              "configurations": [
                { "colorName": "NichtVorhanden", "vehicleTypeName": "Car",
                  "generationSpeed": 1.0, "initialVehicleSpeed": 1.0 }
              ]
            }
          ],
          "streets": []
        }
    """.trimIndent()
}