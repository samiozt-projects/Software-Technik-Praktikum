# Test Resources

## ExampleLevel.json
Golden master file for unit tests of the LevelSerializer.

Contains a minimal but complete level with:
- 2 vehicle types (Car, Truck)
- 2 colors (Red, Blue)
- 1 SourceNode → 1 ConnectionNode → 1 TargetNode
- 2 streets (straight, horizontal)

Do not modify this file, as tests rely on its exact structure.

## Level File Format (JSON Schema)

Level files are stored and loaded as JSON. The following schema defines the expected structure and matches the format of `ExampleLevel.json`:

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "Level",
  "type": "object",
  "required": ["name", "number", "adhesionCoefficientModifier", "weatherCondition", "vehicleTypes", "colors", "nodes", "streets"],
  "properties": {
    "name": {
      "type": "string",
      "description": "Display name of the level"
    },
    "number": {
      "type": "integer",
      "description": "Level number"
    },
    "adhesionCoefficientModifier": {
      "type": "number",
      "description": "Global multiplier applied to the adhesion coefficient of all vehicles"
    },
    "weatherCondition": {
      "type": "string",
      "enum": ["dry", "wet", "snowy"],
      "description": "Weather condition the level starts with"
    },
    "vehicleTypes": {
      "type": "array",
      "description": "Vehicle types available in this level",
      "items": {
        "type": "object",
        "required": ["name", "maxSpeed", "length", "adhesionCoefficient", "acceleration"],
        "properties": {
          "name": { "type": "string", "description": "Unique name of the vehicle type" },
          "maxSpeed": { "type": "number", "description": "Maximum speed in km/h" },
          "length": { "type": "number", "description": "Vehicle length in metres" },
          "adhesionCoefficient": { "type": "number", "description": "Adhesion coefficient (0–1)" },
          "acceleration": { "type": "number", "description": "Acceleration in m/s²" }
        }
      }
    },
    "colors": {
      "type": "array",
      "description": "Named colors used by vehicles and target nodes",
      "items": {
        "type": "object",
        "required": ["name", "argb"],
        "properties": {
          "name": { "type": "string", "description": "Unique color name" },
          "argb": { "type": "integer", "description": "Color value as a 32-bit ARGB integer (0xAARRGGBB)" }
        }
      }
    },
    "nodes": {
      "type": "array",
      "description": "All nodes in the road network",
      "items": {
        "type": "object",
        "required": ["id", "type", "x", "y", "elevation"],
        "properties": {
          "id": { "type": "string", "description": "Unique node ID" },
          "type": {
            "type": "string",
            "enum": ["SourceNode", "TargetNode", "ConnectionNode"],
            "description": "Node type"
          },
          "x": { "type": "number", "description": "X coordinate" },
          "y": { "type": "number", "description": "Y coordinate" },
          "elevation": { "type": "number", "description": "Elevation of the node" },
          "orientationDegrees": {
            "type": "number",
            "description": "Orientation in degrees — required for SourceNode and TargetNode"
          },
          "configurations": {
            "type": "array",
            "description": "Spawn configurations — SourceNode only",
            "items": {
              "type": "object",
              "required": ["colorName", "vehicleTypeName", "generationSpeed", "initialVehicleSpeed"],
              "properties": {
                "colorName": { "type": "string", "description": "Color name (reference to colors)" },
                "vehicleTypeName": { "type": "string", "description": "Vehicle type name (reference to vehicleTypes)" },
                "generationSpeed": { "type": "number", "description": "Vehicles spawned per minute" },
                "initialVehicleSpeed": { "type": "number", "description": "Initial speed of spawned vehicles in km/h" }
              }
            }
          },
          "colorName": {
            "type": "string",
            "description": "Expected color of arriving vehicles — TargetNode only (reference to colors)"
          }
        }
      }
    },
    "streets": {
      "type": "array",
      "description": "All streets connecting nodes",
      "items": {
        "type": "object",
        "required": ["startNodeId", "endNodeId", "path"],
        "properties": {
          "startNodeId": { "type": "string", "description": "ID of the start node (reference to nodes)" },
          "endNodeId": { "type": "string", "description": "ID of the end node (reference to nodes)" },
          "path": {
            "type": "array",
            "description": "Waypoints of the street as a list of coordinates",
            "items": {
              "type": "object",
              "required": ["x", "y"],
              "properties": {
                "x": { "type": "number" },
                "y": { "type": "number" }
              }
            }
          }
        }
      }
    }
  }
}
```

### Node types at a glance

| Type | Required additional fields | Notes |
|---|---|---|
| `SourceNode` | `orientationDegrees`, `configurations` | Spawns vehicles |
| `TargetNode` | `orientationDegrees`, `colorName` | Accepts arriving vehicles |
| `ConnectionNode` | — | Routing point only |
