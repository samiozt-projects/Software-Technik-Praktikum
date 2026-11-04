# Application Configuration

This folder contains configuration files for the application that allow users to customize certain settings without needing to recompile the code.

## app-config.properties

This file contains application configuration settings organized into sections.

### Camera Settings

- `camera.model.name`: The model name of the preferred USB camera
- `camera.default.index`: Default camera index to use if the preferred camera is not found
- `camera.width`: Camera resolution width in pixels
- `camera.height`: Camera resolution height in pixels

### Image Processing Settings

- `imageprocessing.pre_crop.x`: Horizontal start position of the table area crop (pixels from left)
- `imageprocessing.pre_crop.y`: Vertical start position of the table area crop (pixels from top)
- `imageprocessing.pre_crop.width`: Width of the cropped table area in pixels
- `imageprocessing.pre_crop.height`: Height of the cropped table area in pixels
- `imageprocessing.kernel.red.width`: Width of the kernel for red street detection (pixels)
- `imageprocessing.kernel.red.height`: Height of the kernel for red street detection (pixels)
- `imageprocessing.polyline.approx_epsilon`: Epsilon value for polyline approximation (lower = more precise)
- `imageprocessing.polyline.min_arc_length`: Minimum arc length for valid polylines (pixels)
- `imageprocessing.table.threshold`: Threshold value for table detection (0-255)
- `imageprocessing.node.cluster_radius`: Radius for clustering nearby connection nodes (pixels)
- `imageprocessing.node.search_radius`: Radius for searching nearest skeleton pixels (pixels)
- `imageprocessing.node.snap_radius`: Radius for snapping to key nodes (pixels)

### Simulation Settings

- `simulation.speed`: Simulation speed multiplier (1.0 = normal speed, higher = faster)
- `simulation.vehicles_to_spawn`: Number of vehicles to spawn in simulations
- `simulation.challenge.node_limit`: Maximum number of connection nodes in challenge mode

### Network Settings

- `network.base_url`: Base URL of the coupling server for REST requests
- `network.host`: Host name or IP address of the coupling server
- `network.port`: Port of the coupling server (valid range: 1-65535)
- `network.active`: Enables table coupling when set to `true`

The table position is not stored in this configuration. It is selected at runtime through the table-tile selection UI.
WebSocket live traffic uses the same base URL and connects to `/ws/fields` with `wss://` for HTTPS servers.

## Configuration Format

The file uses Java Properties format with dot notation for hierarchical organization:
```properties
# Comment lines start with #
# Sections are separated by dots
section.subsection.key=value
```

## Default Values

If the configuration file is missing or contains invalid values, the following defaults will be used:

### Camera Settings
- Model Name: "1080P FHD Camera"
- Default Index: 0
- Width: 1920
- Height: 1080

### Image Processing Settings
- Pre-crop X: 90
- Pre-crop Y: 23
- Pre-crop Width: 1582
- Pre-crop Height: 1004
- Kernel Red Width: 40
- Kernel Red Height: 40
- Polyline Approx Epsilon: 3.0
- Polyline Min Arc Length: 100.0
- Table Threshold: 180.0
- Node Cluster Radius: 50
- Node Search Radius: 80
- Node Snap Radius: 15

### Simulation Settings
- Speed: 1.0
- Vehicles to Spawn: 7
- Challenge Node Limit: 4

### Network Settings
- Base URL: "https://traficsim.se.uni-kassel.de"
- Host: "traficsim.se.uni-kassel.de"
- Port: 443
- Active: false

## Modifying Configuration

1. Edit the `app-config.properties` file
2. Save your changes
3. Restart the application for changes to take effect

## Adding New Configuration Values

To add new configurable parameters:

1. Add the new key-value pair to `app-config.properties`
2. Add a default constant in `ConfigManager.kt`
3. Add the property to the appropriate configuration data class
4. Load the property in the corresponding `load*Config()` function
5. Update any code that uses the hardcoded value to use the configuration instead

## Notes

- The application does not automatically save configuration changes
- Configuration changes require a restart to take effect
- Invalid values will be ignored and defaults will be used instead
- The configuration system is designed to be backward compatible - if the config file is missing, the application will use sensible defaults
